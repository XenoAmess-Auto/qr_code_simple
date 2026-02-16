package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xenoamess.qrcodesimple.databinding.ActivityVideoScanBinding
import com.xenoamess.qrcodesimple.databinding.ItemQrResultBinding
import java.util.LinkedHashSet

class VideoScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoScanBinding
    private lateinit var adapter: QRResultAdapter
    private val results = mutableListOf<QRResult>()
    private val detectedTexts = LinkedHashSet<String>() // 用于去重
    private var isProcessing = false
    private var processingThread: Thread? = null

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        private const val TAG = "VideoScanActivity"
        // 提取帧的间隔（毫秒）
        private const val FRAME_INTERVAL_MS = 500L
    }

    data class QRResult(
        val text: String,
        var isSelected: Boolean = false,
        val library: QRCodeScanner.Library? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityVideoScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupButtons()

        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriString != null) {
            startVideoProcessing(Uri.parse(uriString))
        } else {
            Toast.makeText(this, getString(R.string.no_video_provided), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = QRResultAdapter(results) { position, isSelected ->
            results[position].isSelected = isSelected
            updateSelectionCount()
        }
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

        binding.btnStopScan.setOnClickListener {
            stopProcessing()
        }
    }

    private fun startVideoProcessing(uri: Uri) {
        isProcessing = true
        binding.progressBar.visibility = View.VISIBLE
        binding.btnStopScan.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.scanning_video)
        binding.tvStatus.visibility = View.VISIBLE

        processingThread = Thread {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)

                // 获取视频时长（毫秒）
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L

                if (durationMs <= 0) {
                    showError(getString(R.string.invalid_video_duration))
                    return@Thread
                }

                var currentTime = 0L
                var frameCount = 0

                while (isProcessing && currentTime <= durationMs) {
                    // 提取帧
                    val bitmap = retriever.getFrameAtTime(
                        currentTime * 1000, // 转换为微秒
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )

                    if (bitmap != null) {
                        frameCount++
                        processFrame(bitmap, currentTime, durationMs)
                        bitmap.recycle()
                    }

                    currentTime += FRAME_INTERVAL_MS

                    // 更新进度
                    val progress = ((currentTime.toFloat() / durationMs) * 100).toInt()
                    updateProgress(progress, frameCount)
                }

                // 扫描完成
                if (isProcessing) {
                    showComplete()
                }

            } catch (e: Exception) {
                showError(getString(R.string.error_processing_video, e.message))
            } finally {
                try {
                    retriever?.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }.apply { start() }
    }

    private fun processFrame(bitmap: Bitmap, currentTime: Long, totalDuration: Long) {
        try {
            // 使用多库扫描器
            val scanResults = QRCodeScanner.scanSync(this, bitmap)

            if (scanResults.isNotEmpty()) {
                var hasNewResult = false
                scanResults.forEach { text ->
                    if (detectedTexts.add(text)) {
                        hasNewResult = true
                        // 记录使用了哪个库（只记录第一个检测到的库）
                        // 由于是同步方法，scanSync 不返回库信息，这里设为 null
                        addResult(QRResult(text, false, null))
                    }
                }
                if (hasNewResult) {
                    updateUI()
                }
            }
        } catch (e: Exception) {
            // 忽略单帧处理错误，继续扫描
        }
    }

    private fun addResult(result: QRResult) {
        runOnUiThread {
            results.add(result)
            adapter.notifyItemInserted(results.size - 1)
            updateSelectionCount()
        }
    }

    private fun updateUI() {
        runOnUiThread {
            if (results.isNotEmpty()) {
                binding.tvNoResults.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                binding.layoutButtons.visibility = View.VISIBLE
            }
        }
    }

    private fun updateProgress(progress: Int, frameCount: Int) {
        runOnUiThread {
            binding.tvStatus.text = getString(R.string.scanning_progress, progress, results.size, frameCount)
        }
    }

    private fun showComplete() {
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.btnStopScan.visibility = View.GONE
            binding.tvStatus.text = getString(R.string.scan_complete, results.size)
            
            if (results.isEmpty()) {
                binding.tvNoResults.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.layoutButtons.visibility = View.GONE
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.btnStopScan.visibility = View.GONE
            binding.tvStatus.text = message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopProcessing() {
        isProcessing = false
        processingThread?.interrupt()
        showComplete()
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        isProcessing = false
        processingThread?.interrupt()
    }

    inner class QRResultAdapter(
        private val items: List<QRResult>,
        private val onItemChecked: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<QRResultAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemQrResultBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemQrResultBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                tvResult.text = item.text
                checkbox.isChecked = item.isSelected

                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    onItemChecked(position, isChecked)
                }

                root.setOnClickListener {
                    checkbox.isChecked = !checkbox.isChecked
                }

                btnCopy.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", item.text))
                    Toast.makeText(this@VideoScanActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }

                btnShare.setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.text)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share)))
                }

                btnEdit.setOnClickListener {
                    showEditDialog(position, item.text)
                }
            }
        }

        override fun getItemCount() = items.size

        private fun showEditDialog(position: Int, currentText: String) {
            val editText = android.widget.EditText(this@VideoScanActivity).apply {
                setText(currentText)
            }

            AlertDialog.Builder(this@VideoScanActivity)
                .setTitle(getString(R.string.edit_qr_code_content))
                .setView(editText)
                .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                    results[position] = results[position].copy(text = editText.text.toString())
                    notifyItemChanged(position)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }
}
