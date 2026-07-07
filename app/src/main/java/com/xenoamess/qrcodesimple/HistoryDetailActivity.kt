package com.xenoamess.qrcodesimple

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ActivityHistoryDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史记录详情页
 */
class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private lateinit var repository: HistoryRepository
    private var item: HistoryItem? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_detail)

        repository = HistoryRepository(this)

        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1)
        if (itemId == -1L) {
            finish()
            return
        }

        loadItem(itemId)
    }

    private fun loadItem(itemId: Long) {
        lifecycleScope.launch {
            val historyItems = repository.allHistory
            historyItems.collect { items ->
                val found = items.find { it.id == itemId }
                if (found != null) {
                    item = found
                    bindItem(found)
                } else {
                    finish()
                }
            }
        }
    }

    private fun bindItem(item: HistoryItem) {
        binding.tvContent.text = item.content
        binding.tvType.text = buildString {
            append(if (item.isGenerated) getString(R.string.type_generated) else getString(R.string.type_scanned))
            append(" • ")
            append(formatHistoryType(item.type))
            item.barcodeFormat?.let {
                append(" • ")
                append(it)
            }
        }
        binding.tvTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))

        // 标签
        val tags = TagManager.parseTags(item.tags)
        if (tags.isNotEmpty()) {
            binding.chipGroupTags.visibility = android.view.View.VISIBLE
            binding.chipGroupTags.removeAllViews()
            for (tag in tags) {
                val chip = Chip(this).apply {
                    text = tag
                    isClickable = false
                }
                binding.chipGroupTags.addView(chip)
            }
        } else {
            binding.chipGroupTags.visibility = android.view.View.GONE
        }

        // 备注
        if (!item.notes.isNullOrEmpty()) {
            binding.tvNotes.visibility = android.view.View.VISIBLE
            binding.tvNotes.text = item.notes
        } else {
            binding.tvNotes.visibility = android.view.View.GONE
        }

        // 条码图片
        val format = item.barcodeFormat?.let { BarcodeFormat.fromString(it) } ?: BarcodeFormat.QR_CODE
        val bitmap = BarcodeGenerator.generate(
            item.content,
            BarcodeGenerator.BarcodeConfig(format = format, width = 600, height = 600)
        )
        bitmap?.let { binding.ivBarcode.setImageBitmap(it) }

        // 按钮
        binding.btnShare.setOnClickListener { shareContent(item.content) }
        binding.btnEdit.setOnClickListener { showEditDialog(item) }
        binding.btnDelete.setOnClickListener { deleteItem(item) }
        binding.btnToggleFavorite.text = if (item.isFavorite) {
            getString(R.string.remove_from_favorites)
        } else {
            getString(R.string.add_to_favorites)
        }
        binding.btnToggleFavorite.setOnClickListener { toggleFavorite(item) }
        binding.btnEditTags.setOnClickListener { showEditTagsDialog(item) }
    }

    private fun showEditTagsDialog(item: HistoryItem) {
        val editText = android.widget.EditText(this).apply {
            setText(item.tags ?: "")
            setSelection(item.tags?.length ?: 0)
            hint = "Comma separated tags"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_tags))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                val tags = editText.text.toString()
                lifecycleScope.launch {
                    repository.setTags(item.id, TagManager.parseTags(tags))
                    Toast.makeText(this@HistoryDetailActivity, "Tags saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun formatHistoryType(type: HistoryType): String {
        return when (type) {
            HistoryType.QR_CODE -> getString(R.string.type_qr_code)
            HistoryType.BARCODE -> getString(R.string.type_barcode)
            HistoryType.DATA_MATRIX -> "Data Matrix"
            HistoryType.AZTEC -> "Aztec"
            HistoryType.PDF417 -> "PDF417"
            HistoryType.RSS_14 -> "RSS-14"
            HistoryType.RSS_EXPANDED -> "RSS Expanded"
            HistoryType.MAXICODE -> "MaxiCode"
            HistoryType.MICRO_QR -> "Micro QR"
            HistoryType.UPC_EAN_EXTENSION -> "UPC/EAN Extension"
            HistoryType.PHARMACODE -> "Pharmacode"
            HistoryType.PLESSEY -> "Plessey"
            HistoryType.MSI_PLESSEY -> "MSI Plessey"
            HistoryType.TELEPEN -> "Telepen"
            HistoryType.HAN_XIN -> "Han Xin"
            HistoryType.TEXT -> getString(R.string.type_text)
        }
    }

    private fun shareContent(content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun showEditDialog(item: HistoryItem) {
        val editText = android.widget.EditText(this).apply {
            setText(item.content)
            setSelection(item.content.length)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                val newContent = editText.text.toString()
                lifecycleScope.launch {
                    repository.updateContent(item.id, newContent)
                    Toast.makeText(this@HistoryDetailActivity, "Saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteItem(item: HistoryItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_item))
            .setMessage(getString(R.string.delete_item_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    repository.delete(item)
                    Toast.makeText(this@HistoryDetailActivity, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleFavorite(item: HistoryItem) {
        lifecycleScope.launch {
            repository.toggleFavorite(item)
            val message = if (!item.isFavorite) "Added to favorites" else "Removed from favorites"
            Toast.makeText(this@HistoryDetailActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
    }
}
