package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.graphics.Bitmap
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 批量生成结果页面
 */
class BatchResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchResultBinding
    private lateinit var adapter: BatchResultAdapter
    private val results = mutableListOf<BatchResult>()
    private var generatedCount = 0

    data class BatchResult(
        val content: String,
        val bitmap: Bitmap?,
        val fileName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBatchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.batch_result)

        setupRecyclerView()

        val contents = intent.getStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS)
        val formatName = intent.getStringExtra(BatchGenerateActivity.EXTRA_FORMAT)
        val format = formatName?.let { BarcodeFormat.valueOf(it) } ?: BarcodeFormat.QR_CODE

        if (contents.isNullOrEmpty()) {
            Toast.makeText(this, "No content to generate", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        generateBatch(contents, format)
    }

    private fun setupRecyclerView() {
        adapter = BatchResultAdapter(results) { position, bitmap ->
            bitmap?.let { saveSingleImage(it, results[position].fileName) }
        }
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter
    }

    private fun generateBatch(contents: List<String>, format: BarcodeFormat) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvProgress.text = "0/${contents.size}"

            val items = contents.mapIndexed { index, content ->
                BatchGenerator.BatchItem(
                    content = content,
                    format = format,
                    fileName = "batch_${index + 1}"
                )
            }

            val generated = BatchGenerator.generateBatch(items) { current, total ->
                binding.tvProgress.text = "$current/$total"
                binding.progressBar.progress = (current * 100 / total)
            }

            results.clear()
            results.addAll(generated.map { (item, bitmap) ->
                BatchResult(item.content, bitmap, item.fileName ?: "batch")
            })

            generatedCount = results.count { it.bitmap != null }

            binding.progressBar.visibility = View.GONE
            binding.tvProgress.text = "Generated: $generatedCount/${results.size}"

            adapter.notifyDataSetChanged()

            if (generatedCount == 0) {
                Toast.makeText(this@BatchResultActivity, "Failed to generate all barcodes", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveSingleImage(bitmap: Bitmap, fileName: String) {
        lifecycleScope.launch {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fullFileName = "${fileName}_$timeStamp.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fullFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BatchQR")
                    }

                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        }
                        Toast.makeText(this@BatchResultActivity, "Saved: $fullFileName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BatchQR")
                    dir.mkdirs()
                    val file = File(dir, fullFileName)
                    FileOutputStream(file).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Toast.makeText(this@BatchResultActivity, "Saved: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BatchResultActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAllAsZip() {
        lifecycleScope.launch {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFileName = "batch_qr_$timeStamp.zip"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let { zipUri ->
                        contentResolver.openOutputStream(zipUri)?.use { outputStream ->
                            ZipOutputStream(outputStream).use { zipOut ->
                                results.filter { it.bitmap != null }.forEach { result ->
                                    val entry = ZipEntry("${result.fileName}.png")
                                    zipOut.putNextEntry(entry)
                                    result.bitmap?.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
                                    zipOut.closeEntry()
                                }
                            }
                        }
                        Toast.makeText(this@BatchResultActivity, "Saved ZIP: $zipFileName", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(dir, zipFileName)
                    FileOutputStream(file).use { outputStream ->
                        ZipOutputStream(outputStream).use { zipOut ->
                            results.filter { it.bitmap != null }.forEach { result ->
                                val entry = ZipEntry("${result.fileName}.png")
                                zipOut.putNextEntry(entry)
                                result.bitmap?.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
                                zipOut.closeEntry()
                            }
                        }
                    }
                    Toast.makeText(this@BatchResultActivity, "Saved ZIP: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@BatchResultActivity, "Failed to save ZIP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_batch_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save_all -> {
                saveAllAsZip()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    inner class BatchResultAdapter(
        private val items: List<BatchResult>,
        private val onSaveClick: (Int, Bitmap?) -> Unit
    ) : RecyclerView.Adapter<BatchResultAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemBatchResultBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBatchResultBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                tvContent.text = item.content
                tvFileName.text = item.fileName

                if (item.bitmap != null) {
                    ivBarcode.setImageBitmap(item.bitmap)
                    btnSave.visibility = View.VISIBLE
                    tvError.visibility = View.GONE
                } else {
                    ivBarcode.setImageResource(R.drawable.ic_qr_code)
                    btnSave.visibility = View.GONE
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Failed to generate"
                }

                btnSave.setOnClickListener {
                    onSaveClick(position, item.bitmap)
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
