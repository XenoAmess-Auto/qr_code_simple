package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ActivityResultBinding
import com.xenoamess.qrcodesimple.databinding.ItemQrResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var adapter: QRResultAdapter
    private lateinit var historyRepository: HistoryRepository
    private lateinit var contentActionHandler: ContentActionHandler
    private val results = mutableListOf<QRResult>()
    private val scanResults = mutableListOf<QRCodeScanner.ScanResult>()

    companion object {
        const val EXTRA_BITMAP_URI = "bitmap_uri"
    }

    data class QRResult(
        val text: String,
        var isSelected: Boolean = false,
        val library: QRCodeScanner.Library? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun setupRecyclerView() {
        adapter = QRResultAdapter(
            results,
            contentActionHandler,
            onItemChecked = { position, isSelected ->
                results[position].isSelected = isSelected
                updateSelectionCount()
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

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(uri)
                }

                if (bitmap == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ResultActivity, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val detectedResults = withContext(Dispatchers.Default) {
                    QRCodeScanner.scan(this@ResultActivity, bitmap)
                }

                binding.progressBar.visibility = View.GONE

                if (detectedResults.isEmpty()) {
                    binding.tvNoResults.visibility = View.VISIBLE
                    binding.tvNoResults.text = getString(R.string.no_qr_codes_found_detail)
                    binding.recyclerView.visibility = View.GONE
                    binding.layoutButtons.visibility = View.GONE
                } else {
                    binding.tvNoResults.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.layoutButtons.visibility = View.VISIBLE
                    binding.ivProcessedImage.visibility = View.VISIBLE

                    // 显示原图
                    binding.ivProcessedImage.setImageBitmap(bitmap)

                    results.clear()
                    results.addAll(detectedResults.map {
                        QRResult(it.text, false, it.library)
                    })
                    scanResults.clear()
                    scanResults.addAll(detectedResults)

                    adapter.notifyDataSetChanged()
                    updateSelectionCount()

                    // 保存到历史记录
                    saveToHistory(detectedResults)

                    // 显示使用了哪个库
                    val libsUsed = detectedResults.map { it.library.name }.distinct().joinToString(", ")
                    Toast.makeText(this@ResultActivity, getString(R.string.detected_with, libsUsed), Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ResultActivity, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
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
                    historyRepository.insertScan(result.text, HistoryType.QR_CODE)
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

    inner class QRResultAdapter(
        private val items: List<QRResult>,
        private val actionHandler: ContentActionHandler,
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
                // 显示内容和使用库的信息
                val libPrefix = item.library?.let { "[${it.name}] " } ?: ""
                tvResult.text = "$libPrefix${item.text}"

                // 显示内容类型标签和图标
                val contentTypeLabel = actionHandler.getContentTypeLabel(item.text)
                tvTypeLabel.text = contentTypeLabel
                ivTypeIcon.setImageResource(actionHandler.getContentTypeIcon(item.text))

                // 显示/隐藏类型标签（纯文本不显示）
                if (contentTypeLabel == getString(R.string.content_type_text)) {
                    tvTypeLabel.visibility = View.GONE
                } else {
                    tvTypeLabel.visibility = View.VISIBLE
                }

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
                    Toast.makeText(this@ResultActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
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

                // 添加智能操作按钮
                setupSmartActions(layoutSmartActions, item.text)
            }
        }

        private fun setupSmartActions(container: FlexboxLayout, content: String) {
            container.removeAllViews()

            val actions = actionHandler.getActionButtons(content)
            if (actions.isEmpty()) {
                container.visibility = View.GONE
                return
            }

            container.visibility = View.VISIBLE

            actions.forEach { action ->
                val button = Button(container.context, null, android.R.attr.borderlessButtonStyle).apply {
                    text = action.text
                    setCompoundDrawablesWithIntrinsicBounds(action.iconResId, 0, 0, 0)
                    compoundDrawablePadding = 8
                    setPadding(24, 12, 24, 12)
                    setOnClickListener { action.onClick() }
                }
                container.addView(button)
            }
        }

        override fun getItemCount() = items.size

        private fun showEditDialog(position: Int, currentText: String) {
            val editText = android.widget.EditText(this@ResultActivity).apply {
                setText(currentText)
            }

            AlertDialog.Builder(this@ResultActivity)
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
