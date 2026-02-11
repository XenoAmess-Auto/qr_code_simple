package com.xenoamess.qrcodesimple

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.xenoamess.qrcodesimple.databinding.ItemContinuousScanResultBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 连续扫描结果适配器
 */
class ContinuousScanAdapter(
    private val items: List<ContinuousScanActivity.ScanResult>,
    private val onCopy: (Int) -> Unit,
    private val onShare: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ContinuousScanAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(val binding: ItemContinuousScanResultBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContinuousScanResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            tvContent.text = item.content
            tvTime.text = dateFormat.format(Date(item.timestamp))
            
            // 显示已保存状态
            ivSaved.visibility = if (item.isSaved) View.VISIBLE else View.GONE
            
            btnCopy.setOnClickListener { onCopy(position) }
            btnShare.setOnClickListener { onShare(position) }
            btnDelete.setOnClickListener { onDelete(position) }
        }
    }

    override fun getItemCount() = items.size
}
