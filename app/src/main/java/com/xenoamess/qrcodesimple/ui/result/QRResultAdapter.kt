package com.xenoamess.qrcodesimple.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.xenoamess.qrcodesimple.ContentActionHandler
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.R
import com.xenoamess.qrcodesimple.SecurityManager
import com.xenoamess.qrcodesimple.databinding.ItemQrResultBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 通用扫描结果数据项，用于图片扫描（ResultActivity）和视频扫描（VideoScanActivity）。
 */
data class QRResult(
    val text: String,
    var isSelected: Boolean = false,
    val library: QRCodeScanner.Library? = null,
    val format: com.google.zxing.BarcodeFormat = com.google.zxing.BarcodeFormat.QR_CODE
)

/**
 * 通用扫描结果列表 Adapter。
 *
 * - 传入 [contentActionHandler] 时显示内容类型标签、图标和智能操作按钮。
 * - 传入 [lifecycleScope] 时对 URL 内容执行安全检测。
 * - 传入 [onEdit] 时启用编辑按钮，保存时通过回调通知调用方更新数据。
 */
class QRResultAdapter(
    private val items: List<QRResult>,
    private val onItemChecked: (Int, Boolean) -> Unit,
    private val contentActionHandler: ContentActionHandler? = null,
    private val lifecycleScope: CoroutineScope? = null,
    private val onEdit: ((Int, String) -> Unit)? = null
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
        val context = holder.itemView.context
        holder.binding.apply {
            // 图片扫描结果显示识别库前缀；视频扫描不显示
            val libPrefix = if (contentActionHandler != null) {
                item.library?.let { "[${it.name}] " } ?: ""
            } else {
                ""
            }
            tvResult.text = "$libPrefix${item.text}"

            // 内容类型标签和图标
            if (contentActionHandler != null) {
                val contentTypeLabel = contentActionHandler.getContentTypeLabel(item.text)
                tvTypeLabel.text = contentTypeLabel
                ivTypeIcon.setImageResource(contentActionHandler.getContentTypeIcon(item.text))
                if (contentTypeLabel == context.getString(R.string.content_type_text)) {
                    tvTypeLabel.visibility = View.GONE
                } else {
                    tvTypeLabel.visibility = View.VISIBLE
                }
                ivTypeIcon.visibility = View.VISIBLE
            } else {
                tvTypeLabel.visibility = View.GONE
                ivTypeIcon.visibility = View.GONE
            }

            checkbox.isChecked = item.isSelected
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onItemChecked(position, isChecked)
            }
            root.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }

            // 安全检测
            if (lifecycleScope != null &&
                (item.text.startsWith("http://") || item.text.startsWith("https://"))
            ) {
                checkSecurityAsync(holder, item.text)
            } else {
                layoutSecurityIndicator.visibility = View.GONE
            }

            btnCopy.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", item.text))
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
            }

            btnShare.setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.text)
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
            }

            btnEdit.setOnClickListener {
                showEditDialog(holder, position, item.text)
            }
            btnEdit.visibility = if (onEdit != null) View.VISIBLE else View.GONE

            // 智能操作按钮
            if (contentActionHandler != null) {
                setupSmartActions(layoutSmartActions, item.text)
            } else {
                layoutSmartActions.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = items.size

    private fun checkSecurityAsync(holder: ViewHolder, content: String) {
        val binding = holder.binding
        binding.layoutSecurityIndicator.visibility = View.VISIBLE
        binding.tvSecurityStatus.text = holder.itemView.context.getString(R.string.checking_security)
        binding.ivSecurityIcon.setColorFilter(android.graphics.Color.GRAY)

        lifecycleScope?.launch {
            val result = SecurityManager.checkUrl(content)
            val context = holder.itemView.context
            binding.tvSecurityStatus.text = result.message
            binding.tvSecurityStatus.setTextColor(SecurityManager.getRiskColor(result.riskLevel))
            binding.ivSecurityIcon.setColorFilter(SecurityManager.getRiskColor(result.riskLevel))

            if (result.riskLevel == SecurityManager.RiskLevel.HIGH ||
                result.riskLevel == SecurityManager.RiskLevel.MEDIUM
            ) {
                binding.layoutSecurityIndicator.setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle(result.message)
                        .setMessage(result.details + "\n\n" + SecurityManager.getSecurityTip(result))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun setupSmartActions(container: FlexboxLayout, content: String) {
        container.removeAllViews()
        val actions = contentActionHandler?.getActionButtons(content) ?: emptyList()
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

    private fun showEditDialog(holder: ViewHolder, position: Int, currentText: String) {
        val context = holder.itemView.context
        val editText = android.widget.EditText(context).apply {
            setText(currentText)
        }
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.edit_qr_code_content))
            .setView(editText)
            .setPositiveButton(context.getString(R.string.save_action)) { _, _ ->
                onEdit?.invoke(position, editText.text.toString())
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }
}
