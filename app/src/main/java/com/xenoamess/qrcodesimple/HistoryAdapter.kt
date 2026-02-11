package com.xenoamess.qrcodesimple

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onEdit: (HistoryItem) -> Unit,
    private val onShare: (HistoryItem) -> Unit,
    private val onShareQR: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit,
    private val onFavorite: (HistoryItem) -> Unit = {},
    private val onAddNote: (HistoryItem) -> Unit = {}
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(item: HistoryItem) {
            val context = binding.root.context
            binding.tvContent.text = item.content
            
            // 类型标签
            binding.tvType.text = buildString {
                append(if (item.isGenerated) context.getString(R.string.type_generated) else context.getString(R.string.type_scanned))
                append(" • ")
                append(when (item.type) {
                    HistoryType.QR_CODE -> context.getString(R.string.type_qr_code)
                    HistoryType.BARCODE -> context.getString(R.string.type_barcode)
                    HistoryType.DATA_MATRIX -> "Data Matrix"
                    HistoryType.AZTEC -> "Aztec"
                    HistoryType.PDF417 -> "PDF417"
                    HistoryType.TEXT -> context.getString(R.string.type_text)
                })
                item.barcodeFormat?.let {
                    append(" • ")
                    append(it)
                }
            }
            
            binding.tvTime.text = dateFormat.format(Date(item.timestamp))

            // 收藏图标
            binding.ivFavorite.visibility = if (item.isFavorite) View.VISIBLE else View.GONE
            
            // 备注预览
            if (!item.notes.isNullOrEmpty()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = item.notes
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            // 按钮点击
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnShare.setOnClickListener { onShare(item) }
            binding.btnShareQR.setOnClickListener { onShareQR(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
            binding.btnFavorite.setOnClickListener { onFavorite(item) }
            binding.btnNote.setOnClickListener { onAddNote(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
