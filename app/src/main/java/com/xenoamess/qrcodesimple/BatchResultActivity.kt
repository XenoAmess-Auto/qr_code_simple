package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityBatchResultBinding
import com.xenoamess.qrcodesimple.databinding.ItemBatchResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BatchResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatchResultBinding
    private lateinit var adapter: BatchResultAdapter
    private val results = mutableListOf<BatchResult>()
    private var batchStyle: AdvancedBarcodeGenerator.StyleConfig? = null

    /** bitmap is deliberately a small preview; the full PNG always lives in [imageFile]. */
    data class BatchResult(
        val content: String,
        val bitmap: Bitmap?,
        val fileName: String,
        val item: BatchGenerator.BatchItem = BatchGenerator.BatchItem(content, fileName = fileName),
        val imageFile: File? = null,
        val errorMessage: String? = null
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.batch_result)
        setupRecyclerView()

        // The JSON is primitive Intent data, so Android restores it after process death.
        val items = BatchGenerator.itemsFromJson(intent.getStringExtra(BatchGenerateActivity.EXTRA_BATCH_ITEMS_JSON))
            .ifEmpty { legacyItemsFromIntent() }
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.no_content_to_generate, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        generateBatch(items)
    }

    private fun legacyItemsFromIntent(): List<BatchGenerator.BatchItem> {
        val format = intent.getStringExtra(BatchGenerateActivity.EXTRA_FORMAT)
            ?.let { runCatching { BarcodeFormat.valueOf(it) }.getOrNull() } ?: BarcodeFormat.QR_CODE
        return intent.getStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS)
            ?.mapIndexed { index, content -> BatchGenerator.BatchItem(content, format, fileName = "batch_${index + 1}") }
            .orEmpty()
    }

    private fun setupRecyclerView() {
        adapter = BatchResultAdapter(
            results,
            onRetryClick = { position -> retry(position) },
            onSaveClick = { position, _ -> saveSingleImage(results[position]) }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter
    }

    internal fun readStyleFromIntent(): AdvancedBarcodeGenerator.StyleConfig? {
        val styleJson = intent.getStringExtra(BatchGenerateActivity.EXTRA_STYLE_JSON) ?: return null
        var style = styleConfigFromJson(styleJson) ?: return null
        intent.getStringExtra(BatchGenerateActivity.EXTRA_LOGO_PATH)?.let { path ->
            val file = File(path)
            try {
                BitmapFactory.decodeFile(path)?.let { style = style.copy(logoBitmap = it) }
            } finally {
                file.delete()
            }
        }
        return style
    }

    /** Per-row colors replace only the batch style's two colors. */
    internal fun styleForItem(style: AdvancedBarcodeGenerator.StyleConfig, item: BatchGenerator.BatchItem) = style.copy(
        foregroundColor = item.foregroundColor ?: style.foregroundColor,
        backgroundColor = item.backgroundColor ?: style.backgroundColor
    )

    private fun generateBatch(items: List<BatchGenerator.BatchItem>) {
        val style = readStyleFromIntent().also { batchStyle = it }
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvProgress.text = getString(R.string.generating, 0, items.size)
            results.clear()
            items.forEachIndexed { index, item ->
                results += generateOne(item, style, index)
                adapter.notifyItemInserted(index)
                binding.tvProgress.text = getString(R.string.generating, index + 1, items.size)
                binding.progressBar.progress = (index + 1) * 100 / items.size
            }
            binding.progressBar.visibility = View.GONE
            val success = results.count { it.imageFile != null }
            binding.tvProgress.text = getString(R.string.batch_generated_count, success, results.size)
            if (success == 0) Toast.makeText(this@BatchResultActivity, R.string.batch_all_failed, Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun generateOne(item: BatchGenerator.BatchItem, style: AdvancedBarcodeGenerator.StyleConfig?, index: Int): BatchResult =
        withContext(Dispatchers.Default) {
            val fileName = item.fileName?.ifBlank { null } ?: "batch_${index + 1}"
            val validation = BarcodeGenerator.validateContent(item.content, item.format)
            if (!validation.isValid) return@withContext BatchResult(item.content, null, fileName, item, errorMessage = validation.errorMessage)
            try {
                val bitmap = if (style == null) {
                    BarcodeGenerator.generate(item.content, BarcodeGenerator.BarcodeConfig(
                        format = item.format, width = 800, height = 800,
                        foregroundColor = item.foregroundColor ?: android.graphics.Color.BLACK,
                        backgroundColor = item.backgroundColor ?: android.graphics.Color.WHITE
                    ))
                } else {
                    val itemStyle = AdvancedBarcodeGenerator.sanitize(styleForItem(style, item), item.format)
                    AdvancedBarcodeGenerator.generateStyled(item.content, item.format, 800, 800, itemStyle)
                } ?: throw IllegalStateException(getString(R.string.generation_returned_no_image))
                val file = withContext(Dispatchers.IO) { writeCacheImage(bitmap, index) }
                val thumbnail = withContext(Dispatchers.IO) { decodeThumbnail(file) }
                bitmap.recycle()
                BatchResult(item.content, thumbnail, fileName, item, file)
            } catch (e: Exception) {
                BatchResult(item.content, null, fileName, item, errorMessage = e.message ?: getString(R.string.unknown_error))
            }
        }

    private fun writeCacheImage(bitmap: Bitmap, index: Int): File {
        val directory = File(cacheDir, "batch-results").apply { mkdirs() }
        return File(directory, "${System.nanoTime()}_$index.png").also { file ->
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun decodeThumbnail(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        var sample = 1
        while (options.outWidth / sample > 240 || options.outHeight / sample > 240) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun retry(position: Int) {
        val previous = results.getOrNull(position) ?: return
        lifecycleScope.launch {
            val replacement = generateOne(previous.item, batchStyle, position)
            previous.bitmap?.recycle()
            previous.imageFile?.delete()
            results[position] = replacement
            adapter.notifyItemChanged(position)
        }
    }

    internal fun saveSingleImage(result: BatchResult) {
        val source = result.imageFile ?: return
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { copySingleToMediaStore(source, result.fileName) }
                Toast.makeText(this@BatchResultActivity, getString(R.string.saved_to, saved), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BatchResultActivity, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copySingleToMediaStore(source: File, fileName: String): String {
        val fullName = "${safeFileName(fileName)}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BatchQR")
            }) ?: error(getString(R.string.unknown_error))
            contentResolver.openOutputStream(uri)?.use { output -> FileInputStream(source).use { it.copyTo(output) } } ?: error(getString(R.string.unknown_error))
            return fullName
        }
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BatchQR").apply { mkdirs() }
        val target = File(directory, fullName)
        FileInputStream(source).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        return target.absolutePath
    }

    internal fun saveAllAsZip() {
        lifecycleScope.launch {
            try {
                val name = "batch_qr_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())}.zip"
                val saved = withContext(Dispatchers.IO) { writeZipToMediaStore(name, results) }
                Toast.makeText(this@BatchResultActivity, getString(R.string.zip_saved, saved), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@BatchResultActivity, R.string.zip_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeZipToMediaStore(name: String, sourceResults: List<BatchResult>): String {
        val usedNames = mutableMapOf<String, Int>()
        fun write(output: java.io.OutputStream) = ZipOutputStream(output).use { zip ->
            sourceResults.forEach { result -> result.imageFile?.takeIf(File::exists)?.let { file ->
                val baseName = safeFileName(result.fileName)
                val occurrence = (usedNames[baseName] ?: 0) + 1
                usedNames[baseName] = occurrence
                val entryName = if (occurrence == 1) "$baseName.png" else "${baseName}_$occurrence.png"
                zip.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            } }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }) ?: error(getString(R.string.unknown_error))
            contentResolver.openOutputStream(uri)?.use(::write) ?: error(getString(R.string.unknown_error))
            return name
        }
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
        val temporary = File(target.parentFile, ".${target.name}.partial")
        FileOutputStream(temporary).use(::write)
        if (!temporary.renameTo(target)) error(getString(R.string.unknown_error))
        return target.absolutePath
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.')
        .ifEmpty { "barcode" }

    override fun onDestroy() {
        results.forEach { result ->
            result.bitmap?.recycle()
            result.imageFile?.delete()
        }
        batchStyle?.logoBitmap?.recycle()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?) = menuInflater.inflate(R.menu.menu_batch_result, menu).let { true }
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_save_all -> { saveAllAsZip(); true }
        else -> super.onOptionsItemSelected(item)
    }
    override fun onSupportNavigateUp() = true.also { finish() }

    inner class BatchResultAdapter(
        private val items: List<BatchResult>,
        private val onRetryClick: ((Int) -> Unit)? = null,
        private val onSaveClick: (Int, Bitmap?) -> Unit
    ) : RecyclerView.Adapter<BatchResultAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemBatchResultBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemBatchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.binding.apply {
            val item = items[position]
            tvContent.text = item.content
            tvFileName.text = item.fileName
            if (item.bitmap != null) {
                ivBarcode.setImageBitmap(item.bitmap); btnSave.visibility = View.VISIBLE; tvError.visibility = View.GONE; btnRetry.visibility = View.GONE
            } else {
                ivBarcode.setImageResource(R.drawable.ic_qr_code); btnSave.visibility = View.GONE; tvError.visibility = View.VISIBLE
                tvError.text = item.errorMessage ?: getString(R.string.failed_to_generate)
                btnRetry.visibility = if (onRetryClick == null) View.GONE else View.VISIBLE
            }
            btnSave.setOnClickListener { onSaveClick(position, item.bitmap) }
            btnRetry.setOnClickListener { onRetryClick?.invoke(position) }
            }
        }
        override fun getItemCount() = items.size
    }
}
