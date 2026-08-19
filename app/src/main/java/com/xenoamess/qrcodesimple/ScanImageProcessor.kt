package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Routes image/video Uris and safely imports transient media shared by other apps. */
object ScanImageProcessor {

    const val EXTRA_OWNED_TEMP_FILE = "owned_shared_media_temp_file"
    const val EXTRA_OWNED_TEMP_FILE_LEASE = "owned_shared_media_temp_file_lease"

    internal const val MAX_SHARED_IMAGE_BYTES = 32L * 1024 * 1024
    internal const val MAX_SHARED_VIDEO_BYTES = 512L * 1024 * 1024
    internal const val SHARED_MEDIA_MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val SHARED_MEDIA_TIMEOUT_MS = 15_000L
    private const val SHARED_MEDIA_DIRECTORY = "shared_media"
    private val sharedMediaNamePattern = Regex("shared_([0-9a-f-]{36})(?:\\..+)?")
    private val activeLeaseFiles = ConcurrentHashMap<String, String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = ThreadPoolExecutor(
        2,
        4,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(8),
        { runnable ->
            Thread(runnable, "shared-media-import").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy()
    ).apply { allowCoreThreadTimeOut(true) }

    internal enum class Destination {
        IMAGE,
        VIDEO
    }

    internal sealed interface SharedMediaResult {
        data class Ready(
            val uri: Uri,
            val destination: Destination,
            val ownsTempFile: Boolean,
            val leaseToken: String? = null
        ) : SharedMediaResult

        data object TooLarge : SharedMediaResult
        data object Failed : SharedMediaResult
    }

    internal sealed interface CopyResult {
        data class Success(val uri: Uri, val leaseToken: String) : CopyResult
        data object TooLarge : CopyResult
        data object Failed : CopyResult
    }

    /** According to MIME type, video enters [VideoScanActivity], otherwise image recognition. */
    fun processMedia(context: Context, uri: Uri, mimeTypeHint: String? = null) {
        try {
            val mimeType = mimeTypeHint ?: context.contentResolver.getType(uri)
            if (mimeType?.startsWith("video/") == true) {
                val intent = Intent(context, VideoScanActivity::class.java).apply {
                    putExtra(VideoScanActivity.EXTRA_VIDEO_URI, uri.toString())
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                processImage(context, uri)
            }
        } catch (_: RejectedExecutionException) {
            showFailure(context.applicationContext, R.string.scan_queue_busy)
        } catch (_: Exception) {
            showFailure(context.applicationContext, R.string.failed_to_load_image)
        }
    }

    /**
     * Imports a shared Uri without retaining the host Activity. The returned operation survives
     * host recreation when owned by a ViewModel and can be cancelled when that ViewModel clears.
     */
    internal fun prepareSharedMedia(
        context: Context,
        uri: Uri,
        mimeTypeHint: String?,
        callback: (SharedMediaResult) -> Unit
    ): SharedMediaOperation {
        val appContext = context.applicationContext
        val operation = SharedMediaOperation(appContext, callback)
        val cancellationSignal = CancellationSignal()
        operation.cancellationSignal = cancellationSignal
        mainHandler.postDelayed(operation.timeout, SHARED_MEDIA_TIMEOUT_MS)

        val future = try {
            executor.submit {
                cleanupExpiredSharedMedia(appContext)
                val mimeType = mimeTypeHint ?: runCatching {
                    appContext.contentResolver.getType(uri)
                }.getOrNull()
                val destination = if (mimeType?.startsWith("video/") == true) {
                    Destination.VIDEO
                } else {
                    Destination.IMAGE
                }

                val prepared = if (uri.scheme.equals("content", ignoreCase = true)) {
                    val declaredSize = declaredSize(appContext, uri, cancellationSignal)
                    copySharedContentToCache(
                        appContext,
                        mimeType,
                        declaredSize,
                        onFileCreated = operation::setPartialFile
                    ) {
                        if (cancellationSignal.isCanceled) error("Shared media import cancelled")
                        val descriptor = appContext.contentResolver
                            .openFileDescriptor(uri, "r", cancellationSignal)
                            ?: error("Unable to open shared media")
                        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    }
                } else {
                    CopyResult.Failed
                }

                val result = when (prepared) {
                    is CopyResult.Success -> {
                        val ownsTempFile = true
                        retainOwnedSharedMedia(appContext, prepared.uri, prepared.leaseToken)
                        if (destination == Destination.IMAGE &&
                            !isDecodable(appContext, prepared.uri)
                        ) {
                            deleteOwnedSharedMedia(
                                appContext,
                                prepared.uri,
                                ownsTempFile,
                                prepared.leaseToken
                            )
                            SharedMediaResult.Failed
                        } else {
                            SharedMediaResult.Ready(
                                prepared.uri,
                                destination,
                                ownsTempFile,
                                prepared.leaseToken
                            )
                        }
                    }
                    CopyResult.TooLarge -> SharedMediaResult.TooLarge
                    CopyResult.Failed -> SharedMediaResult.Failed
                }
                operation.complete(result)
            }
        } catch (_: RejectedExecutionException) {
            operation.complete(SharedMediaResult.Failed)
            null
        }
        future?.let(operation::attachFuture)
        return operation
    }

    internal class SharedMediaOperation(
        private val context: Context,
        private val callback: (SharedMediaResult) -> Unit
    ) {
        private val finished = AtomicBoolean(false)
        private val cancelled = AtomicBoolean(false)
        private val partialFile = AtomicReference<File?>()
        private val completedResult = AtomicReference<SharedMediaResult?>()
        var cancellationSignal: CancellationSignal? = null
        private val future = AtomicReference<Future<*>?>()
        val timeout = Runnable { cancelAndReportFailure() }

        fun attachFuture(value: Future<*>) {
            if (!future.compareAndSet(null, value) || finished.get()) {
                discardFuture(value)
            }
        }

        fun setPartialFile(file: File) {
            partialFile.set(file)
            if (finished.get()) {
                file.delete()
                throw InterruptedException("Shared media import cancelled")
            }
        }

        fun complete(result: SharedMediaResult) {
            if (finished.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout)
                partialFile.set(null)
                completedResult.set(result)
                mainHandler.post {
                    if (cancelled.get()) {
                        (result as? SharedMediaResult.Ready)?.let(::deleteReadyFile)
                    } else {
                        callback(result)
                    }
                }
            } else if (result is SharedMediaResult.Ready && result.ownsTempFile) {
                deleteReadyFile(result)
            }
        }

        fun cancel() {
            cancelled.set(true)
            (completedResult.get() as? SharedMediaResult.Ready)?.let(::deleteReadyFile)
            cancelInternal(null)
        }

        private fun cancelAndReportFailure() {
            cancelInternal(SharedMediaResult.Failed)
        }

        private fun cancelInternal(result: SharedMediaResult?) {
            if (!finished.compareAndSet(false, true)) return
            mainHandler.removeCallbacks(timeout)
            cancellationSignal?.cancel()
            future.get()?.let(::discardFuture)
            partialFile.getAndSet(null)?.delete()
            if (result != null) mainHandler.post { callback(result) }
        }

        private fun discardFuture(value: Future<*>) {
            value.cancel(true)
            (value as? Runnable)?.let(executor::remove)
            executor.purge()
        }

        private fun deleteReadyFile(result: SharedMediaResult.Ready) {
            deleteOwnedSharedMedia(context, result.uri, result.ownsTempFile, result.leaseToken)
        }
    }

    /**
     * Copies with both a declared-size preflight and an independent streaming byte count. A
     * provider-controlled declared size is only an optimization and is never trusted as a limit.
     */
    internal fun copySharedContentToCache(
        context: Context,
        mimeType: String?,
        declaredSize: Long?,
        onFileCreated: (File) -> Unit = {},
        openInput: () -> InputStream
    ): CopyResult {
        val maxBytes = maxSharedBytes(mimeType)
        if (declaredSize != null && declaredSize >= 0 && declaredSize > maxBytes) {
            return CopyResult.TooLarge
        }

        var outputFile: File? = null
        return try {
            val directory = sharedMediaDirectory(context)
            if (!directory.exists() && !directory.mkdirs()) return CopyResult.Failed
            val extension = mimeType
                ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
                ?.takeIf(String::isNotBlank)
            val leaseToken = PrivateStateFileStore.newToken()
            val file = File(directory, "shared_$leaseToken${extension?.let { ".$it" }.orEmpty()}")
            if (!file.createNewFile()) return CopyResult.Failed
            outputFile = file
            onFileCreated(file)
            openInput().use { source ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        if (total > maxBytes - read) throw SharedMediaTooLargeException()
                        output.write(buffer, 0, read)
                        total += read
                    }
                }
            }
            CopyResult.Success(Uri.fromFile(file), leaseToken)
        } catch (_: SharedMediaTooLargeException) {
            outputFile?.delete()
            CopyResult.TooLarge
        } catch (_: Exception) {
            outputFile?.delete()
            CopyResult.Failed
        }
    }

    private class SharedMediaTooLargeException : Exception()

    private fun maxSharedBytes(mimeType: String?): Long =
        if (mimeType?.startsWith("video/") == true) {
            MAX_SHARED_VIDEO_BYTES
        } else {
            MAX_SHARED_IMAGE_BYTES
        }

    private fun declaredSize(
        context: Context,
        uri: Uri,
        cancellationSignal: CancellationSignal
    ): Long? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
                cancellationSignal
            )
            val resultCursor = cursor
            if (resultCursor != null && resultCursor.moveToFirst() && !resultCursor.isNull(0)) {
                resultCursor.getLong(0)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    fun processImage(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        val host = (context as? Activity)?.let(::WeakReference)
        val nonActivityContext = context.takeUnless { it is Activity }
        try {
            executor.execute {
                val decodable = isDecodable(appContext, uri)
                mainHandler.post {
                    val routeContext = if (host == null) {
                        nonActivityContext ?: return@post
                    } else {
                        host.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return@post
                    }
                    if (decodable) {
                        try {
                            val intent = Intent(routeContext, ResultActivity::class.java).apply {
                                putExtra(ResultActivity.EXTRA_BITMAP_URI, uri.toString())
                                if (routeContext !is Activity) {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            routeContext.startActivity(intent)
                        } catch (_: RuntimeException) {
                            showFailure(appContext, R.string.failed_to_load_image)
                        }
                    } else {
                        showFailure(appContext, R.string.failed_to_load_image)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            showFailure(appContext, R.string.scan_queue_busy)
        }
    }

    private fun showFailure(context: Context, message: Int) {
        mainHandler.post {
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        }
    }

    /** Only decodes bounds to determine whether the image is readable. */
    fun isDecodable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                options.outWidth > 0 && options.outHeight > 0
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)

                val maxDimension = 2048
                val maxDim = maxOf(options.outWidth, options.outHeight)
                val sampleSize = if (maxDim > maxDimension) {
                    Integer.highestOneBit((maxDim / maxDimension).coerceAtLeast(1))
                } else {
                    1
                }

                context.contentResolver.openInputStream(uri)?.use { decodeStream ->
                    BitmapFactory.decodeStream(
                        decodeStream,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Keeps cleanup from deleting an owned file while its consumer can still be recreated. */
    fun retainOwnedSharedMedia(context: Context, uri: Uri?, leaseToken: String?) {
        if (leaseToken == null) return
        val file = ownedSharedMediaFile(context, uri, leaseToken) ?: return
        if (file.isFile) activeLeaseFiles[leaseToken] = file.path
    }

    /** Releases the in-memory lease without deleting the file needed for Activity recreation. */
    fun releaseOwnedSharedMedia(context: Context, uri: Uri?, leaseToken: String?) {
        if (leaseToken == null) return
        val file = ownedSharedMediaFile(context, uri, leaseToken) ?: return
        activeLeaseFiles.remove(leaseToken, file.path)
    }

    /** Deletes only files created inside this component's private shared-media cache. */
    fun deleteOwnedSharedMedia(
        context: Context,
        uri: Uri?,
        ownsTempFile: Boolean,
        leaseToken: String? = null
    ) {
        if (!ownsTempFile || uri?.scheme != "file") return
        val file = ownedSharedMediaFile(context, uri, leaseToken) ?: return
        leaseToken?.let(activeLeaseFiles::remove)
        deleteFileWithRetry(file)
    }

    fun cleanupExpiredSharedMedia(context: Context, now: Long = System.currentTimeMillis()) {
        val cutoff = now - SHARED_MEDIA_MAX_AGE_MS
        sharedMediaDirectory(context).listFiles()?.forEach { file ->
            val leaseToken = sharedMediaNamePattern.matchEntire(file.name)?.groupValues?.get(1)
            val hasActiveLease = leaseToken != null && activeLeaseFiles.containsKey(leaseToken)
            if (file.isFile &&
                file.name.startsWith("shared_") &&
                !hasActiveLease &&
                file.lastModified() < cutoff
            ) {
                file.delete()
            }
        }
    }

    private fun ownedSharedMediaFile(context: Context, uri: Uri?, leaseToken: String?): File? {
        if (uri?.scheme != "file") return null
        return runCatching {
            val directory = sharedMediaDirectory(context).canonicalFile
            val file = uri.path?.let(::File)?.canonicalFile ?: return null
            if (leaseToken != null) {
                val fileToken = sharedMediaNamePattern.matchEntire(file.name)?.groupValues?.get(1)
                if (fileToken != leaseToken) return null
                val retainedPath = activeLeaseFiles[leaseToken]
                if (retainedPath != file.path && file.parentFile != directory) return null
            } else if (file.parentFile != directory) {
                return null
            }
            file
        }.getOrNull()
    }

    private fun deleteFileWithRetry(file: File, attemptsRemaining: Int = 20) {
        if (!file.exists() || file.delete() || attemptsRemaining <= 1) return
        mainHandler.postDelayed(
            { deleteFileWithRetry(file, attemptsRemaining - 1) },
            250L
        )
    }

    internal fun sharedMediaDirectory(context: Context): File =
        File(context.cacheDir, SHARED_MEDIA_DIRECTORY)
}
