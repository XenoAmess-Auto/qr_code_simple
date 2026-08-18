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
    private val onItemClick: (HistoryItem) -> Unit = {},
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
                if (item.type == HistoryType.GENERATED_ONLY) {
                    append(item.barcodeFormat ?: context.getString(R.string.type_generated))
                } else {
                    append(when (item.type) {
                        HistoryType.QR_CODE -> context.getString(R.string.type_qr_code)
                        HistoryType.BARCODE -> context.getString(R.string.type_barcode)
                        HistoryType.DATA_MATRIX -> context.getString(R.string.type_data_matrix)
                        HistoryType.AZTEC -> context.getString(R.string.type_aztec)
                        HistoryType.PDF417 -> context.getString(R.string.type_pdf417)
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
                        HistoryType.TEXT -> context.getString(R.string.type_text)
                        HistoryType.GENERATED_ONLY -> context.getString(R.string.type_generated)
                    })
                    item.barcodeFormat?.let {
                        append(" • ")
                        append(it)
                    }
                }
            }
            
            binding.tvTime.text = dateFormat.format(Date(item.timestamp))

            binding.btnFavorite.setImageResource(if (item.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border)
            binding.btnFavorite.imageTintList = context.getColorStateList(
                if (item.isFavorite) R.color.app_primary else R.color.app_text_secondary
            )
            binding.btnFavorite.contentDescription = context.getString(
                if (item.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
            )
            
            // 备注预览
            if (!item.notes.isNullOrEmpty()) {
                binding.tvNotes.visibility = View.VISIBLE
                binding.tvNotes.text = item.notes
            } else {
                binding.tvNotes.visibility = View.GONE
            }

            binding.btnNote.setOnClickListener { onAddNote(item) }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnShare.setOnClickListener { onShare(item) }
            binding.btnShareQR.setOnClickListener { onShareQR(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
            binding.btnFavorite.setOnClickListener { onFavorite(item) }
            binding.root.setOnClickListener { onItemClick(item) }
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
