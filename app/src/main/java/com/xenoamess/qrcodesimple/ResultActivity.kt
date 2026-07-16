package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ActivityResultBinding
import com.xenoamess.qrcodesimple.ui.result.QRResult
import com.xenoamess.qrcodesimple.ui.result.QRResultAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var adapter: QRResultAdapter
    private lateinit var historyRepository: HistoryRepository
    private lateinit var contentActionHandler: ContentActionHandler
    private val results = mutableListOf<QRResult>()
    private val scanResults = mutableListOf<QRCodeScanner.ScanResult>()
    private var scanJob: Job? = null

    companion object {
        const val EXTRA_BITMAP_URI = "bitmap_uri"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        historyRepository = HistoryRepository(this)
        contentActionHandler = ContentActionHandler(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupButtons()

        val uriString = intent.getStringExtra(EXTRA_BITMAP_URI)
        if (uriString != null) {
            processImage(Uri.parse(uriString))
        } else {
            Toast.makeText(this, getString(R.string.no_image_provided), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
    }

    private fun setupRecyclerView() {
        adapter = QRResultAdapter(
            results,
            onItemChecked = { position, isSelected ->
                results[position].isSelected = isSelected
                updateSelectionCount()
            },
            contentActionHandler = contentActionHandler,
            lifecycleScope = lifecycleScope,
            onEdit = { position, newText ->
                results[position] = results[position].copy(text = newText)
                adapter.notifyItemChanged(position)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnCopySelected.setOnClickListener {
            copySelected()
        }

        binding.btnShareSelected.setOnClickListener {
            shareSelected()
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelected()
        }

        binding.btnSelectAll.setOnClickListener {
            selectAll(true)
        }

        binding.btnDeselectAll.setOnClickListener {
            selectAll(false)
        }
    }

    private fun processImage(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvScanningMore.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.layoutButtons.visibility = View.GONE
        binding.ivProcessedImage.visibility = View.GONE
        scanJob?.cancel()

        results.clear()
        scanResults.clear()
        adapter.notifyDataSetChanged()
        updateSelectionCount()

        scanJob = lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(uri)
                }

                if (bitmap == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ResultActivity, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                binding.ivProcessedImage.setImageBitmap(bitmap)
                binding.ivProcessedImage.visibility = View.VISIBLE

                var hasResults = false

                fun showBatch(batch: List<QRCodeScanner.ScanResult>) {
                    if (batch.isEmpty()) return

                    if (!hasResults) {
                        hasResults = true
                        binding.progressBar.visibility = View.GONE
                        binding.tvNoResults.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.layoutButtons.visibility = View.VISIBLE
                    }

                    val startIndex = results.size
                    results.addAll(batch.map {
                        QRResult(it.text, false, it.library)
                    })
                    scanResults.addAll(batch)

                    adapter.notifyItemRangeInserted(startIndex, batch.size)
                    updateSelectionCount()
                    saveToHistory(batch)

                    val libsUsed = batch.map { it.library.name }.distinct().joinToString(", ")
                    Toast.makeText(this@ResultActivity, getString(R.string.detected_with, libsUsed), Toast.LENGTH_SHORT).show()
                }

                QRCodeScanner.scanAsFlow(this@ResultActivity, bitmap, QRCodeScanner.IMAGE_SCAN_CONFIG)
                    .collect { batch ->
                        showBatch(batch)
                    }

                if (!isActive) return@launch

                if (!hasResults) {
                    // 常规识别失败，尝试图像修复后重扫
                    binding.tvScanningMore.text = getString(R.string.restoration_retrying)
                    binding.tvScanningMore.visibility = View.VISIBLE

                    val restored = RestorationRescan.rescan(this@ResultActivity, bitmap)
                    if (!isActive) return@launch

                    if (restored.isNotEmpty()) {
                        showBatch(restored)
                        // 弱提示：结果来自修复后的图像
                        binding.tvScanningMore.text = getString(R.string.restored_after_enhancement)
                        binding.tvScanningMore.visibility = View.VISIBLE
                        binding.progressBar.visibility = View.GONE
                        return@launch
                    }
                }

                binding.tvScanningMore.visibility = View.GONE
                binding.progressBar.visibility = View.GONE

                if (!hasResults) {
                    binding.tvNoResults.visibility = View.VISIBLE
                    binding.tvNoResults.text = getString(R.string.no_qr_codes_found_detail)
                    binding.recyclerView.visibility = View.GONE
                    binding.layoutButtons.visibility = View.GONE
                    binding.ivProcessedImage.visibility = View.GONE
                }

            } catch (e: Throwable) {
                if (isActive) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvScanningMore.visibility = View.GONE
                    Toast.makeText(this@ResultActivity, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
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

                contentResolver.openInputStream(uri)?.use { decodeStream ->
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

    private fun updateSelectionCount() {
        val count = results.count { it.isSelected }
        binding.tvSelectionCount.text = getString(R.string.selected_n_of_m, count, results.size)
    }

    private fun selectAll(select: Boolean) {
        results.forEach { it.isSelected = select }
        adapter.notifyDataSetChanged()
        updateSelectionCount()
    }

    private fun copySelected() {
        val selected = results.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val text = selected.joinToString("\n") { it.text }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Codes", text))
        Toast.makeText(this, getString(R.string.copied_n_items, selected.size), Toast.LENGTH_SHORT).show()
    }

    private fun shareSelected() {
        val selected = results.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val text = selected.joinToString("\n") { it.text }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_qr_code_content)))
    }

    private fun deleteSelected() {
        val selected = results.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_selected))
            .setMessage(getString(R.string.delete_selected_confirm, selected.size))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                results.removeAll { it.isSelected }
                adapter.notifyDataSetChanged()
                updateSelectionCount()

                if (results.isEmpty()) {
                    binding.tvNoResults.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.layoutButtons.visibility = View.GONE
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_copy_all -> {
                copyAll()
                true
            }
            R.id.action_share_all -> {
                shareAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyAll() {
        if (results.isEmpty()) return
        val text = results.joinToString("\n") { it.text }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Codes", text))
        Toast.makeText(this, getString(R.string.copied_all_n_items, results.size), Toast.LENGTH_SHORT).show()
    }

    private fun shareAll() {
        if (results.isEmpty()) return
        val text = results.joinToString("\n") { it.text }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_all_qr_code_content)))
    }

    private fun saveToHistory(detectedResults: List<QRCodeScanner.ScanResult>) {
        lifecycleScope.launch {
            try {
                detectedResults.forEach { result ->
                    historyRepository.insertScan(result.text, result.format.toHistoryType())
                }
            } catch (e: Exception) {
                // 静默失败，不影响用户体验
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
