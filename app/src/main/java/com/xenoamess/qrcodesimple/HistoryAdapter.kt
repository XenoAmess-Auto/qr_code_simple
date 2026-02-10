package com.xenoamess.qrcodesimple

import android.view.LayoutInflater
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
    private val onDelete: (HistoryItem) -> Unit
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
            binding.tvContent.text = item.content
            binding.tvType.text = buildString {
                append(if (item.isGenerated) "Generated" else "Scanned")
                append(" • ")
                append(when (item.type) {
                    HistoryType.QR_CODE -> "QR Code"
                    HistoryType.BARCODE -> "Barcode"
                    HistoryType.TEXT -> "Text"
                })
            }
            binding.tvTime.text = dateFormat.format(Date(item.timestamp))

            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnShare.setOnClickListener { onShare(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
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