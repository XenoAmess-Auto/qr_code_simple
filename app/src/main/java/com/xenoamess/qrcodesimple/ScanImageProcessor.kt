package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 图片/视频 Uri 的扫描路由，供 [ScanImageFragment]（应用内选图）与
 * [ScanImageActivity]（系统分享进来的图片/视频）共用。
 *
 * 可解码性探测在后台线程执行（仅 bounds，不做全量解码），
 * 主线程只做 Activity 跳转和 Toast，避免 UI 线程磁盘 IO 与重复解码。
 */
object ScanImageProcessor {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 根据 MIME 类型路由：视频进 [VideoScanActivity]，其余按图片识别。 */
    fun processMedia(context: Context, uri: Uri, mimeTypeHint: String? = null) {
        try {
            val mimeType = mimeTypeHint ?: context.contentResolver.getType(uri)
            if (mimeType?.startsWith("video/") == true) {
                val intent = Intent(context, VideoScanActivity::class.java).apply {
                    putExtra(VideoScanActivity.EXTRA_VIDEO_URI, uri.toString())
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                processImage(context, uri)
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    fun processImage(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        executor.execute {
            val decodable = isDecodable(appContext, uri)
            mainHandler.post {
                if (decodable) {
                    val intent = Intent(context, ResultActivity::class.java).apply {
                        putExtra(ResultActivity.EXTRA_BITMAP_URI, uri.toString())
                        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } else {
                    Toast.makeText(appContext, appContext.getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 仅解码边界信息判断图片是否可解码；不落全量位图到内存。 */
    fun isDecodable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                options.outWidth > 0 && options.outHeight > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
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
        } catch (e: Exception) {
            null
        }
    }
}
