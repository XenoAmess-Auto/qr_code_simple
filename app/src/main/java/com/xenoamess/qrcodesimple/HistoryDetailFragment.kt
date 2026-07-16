package com.xenoamess.qrcodesimple

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentHistoryDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录详情内容页。
 * 既可由 [HistoryDetailActivity] 单独承载（手机单栏），
 * 也可嵌入 [HistoryFragment] 的右侧详情面板（平板双栏）。
 */
class HistoryDetailFragment : Fragment() {

    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: HistoryRepository
    private var item: HistoryItem? = null

    companion object {
        const val ARG_ITEM_ID = "item_id"

        fun newInstance(itemId: Long): HistoryDetailFragment {
            return HistoryDetailFragment().apply {
                arguments = Bundle().apply { putLong(ARG_ITEM_ID, itemId) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = HistoryRepository(requireContext())

        val itemId = arguments?.getLong(ARG_ITEM_ID, -1) ?: -1
        if (itemId == -1L) {
            closeSelf()
            return
        }
        loadItem(itemId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 关闭自身：独立 Activity 承载时 finish；嵌入双栏面板时从面板移除。
     */
    private fun closeSelf() {
        if (activity is HistoryDetailActivity) {
            activity?.finish()
        } else {
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    private fun loadItem(itemId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.allHistory.collect { items ->
                val found = items.find { it.id == itemId }
                if (found != null) {
                    item = found
                    bindItem(found)
                } else {
                    closeSelf()
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
            binding.chipGroupTags.visibility = View.VISIBLE
            binding.chipGroupTags.removeAllViews()
            for (tag in tags) {
                val chip = Chip(requireContext()).apply {
                    text = tag
                    isClickable = false
                }
                binding.chipGroupTags.addView(chip)
            }
        } else {
            binding.chipGroupTags.visibility = View.GONE
        }

        // 备注
        if (!item.notes.isNullOrEmpty()) {
            binding.tvNotes.visibility = View.VISIBLE
            binding.tvNotes.text = item.notes
        } else {
            binding.tvNotes.visibility = View.GONE
        }

        // 条码图片：使用历史记录的格式和样式参数重新生成
        val format = item.barcodeFormat?.let { BarcodeFormat.fromString(it) } ?: BarcodeFormat.QR_CODE
        val rawStyle = item.styleJson?.let { styleConfigFromJson(it) } ?: AdvancedBarcodeGenerator.StyleConfig()
        val style = AdvancedBarcodeGenerator.sanitize(rawStyle, format)
        val bitmap = AdvancedBarcodeGenerator.generateStyled(item.content, format, 600, 600, style)
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
        binding.btnOpenGenerate.setOnClickListener { openGeneratePage(item) }
    }

    private fun showEditTagsDialog(item: HistoryItem) {
        val editText = android.widget.EditText(requireContext()).apply {
            setText(item.tags ?: "")
            setSelection(item.tags?.length ?: 0)
            hint = getString(R.string.comma_separated_tags)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit_tags))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                val tags = editText.text.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.setTags(item.id, TagManager.parseTags(tags))
                    Toast.makeText(requireContext(), getString(R.string.tags_saved), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openGeneratePage(item: HistoryItem) {
        MainActivity.navigateToGenerate(requireContext(), item.content, null, null)
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
            HistoryType.GENERATED_ONLY -> "Generated Only"
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
        val editText = android.widget.EditText(requireContext()).apply {
            setText(item.content)
            setSelection(item.content.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                val newContent = editText.text.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateContent(item.id, newContent)
                    Toast.makeText(requireContext(), getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteItem(item: HistoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_item))
            .setMessage(getString(R.string.delete_item_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.delete(item)
                    Toast.makeText(requireContext(), getString(R.string.deleted), Toast.LENGTH_SHORT).show()
                    closeSelf()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun toggleFavorite(item: HistoryItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.toggleFavorite(item)
            val message = getString(if (!item.isFavorite) R.string.added_to_favorites else R.string.removed_from_favorites)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
