package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentHistoryDetailBinding
import kotlinx.coroutines.Job
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
    private var itemId = -1L
    private var itemCollectorJob: Job? = null
    private var lockMonitorJob: Job? = null
    private var pinDialog: AlertDialog? = null
    private var unlockPromptShowing = false
    private var biometricAttempted = false
    private val activeDialogs = mutableSetOf<AlertDialog>()

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

        itemId = arguments?.getLong(ARG_ITEM_ID, -1) ?: -1
        if (itemId == -1L) {
            closeSelf()
            return
        }
        if (AppLockManager.isUnlocked()) {
            showUnlockedState()
        } else {
            enforceLockedState()
        }
        launchLockMonitor()
    }

    override fun onStart() {
        super.onStart()
        if (itemId == -1L) return
        if (AppLockManager.isUnlocked()) {
            if (itemCollectorJob?.isActive != true) showUnlockedState()
        } else {
            enforceLockedState()
        }
    }

    override fun onDestroyView() {
        itemCollectorJob?.cancel()
        itemCollectorJob = null
        lockMonitorJob?.cancel()
        lockMonitorJob = null
        activeDialogs.toList().forEach { it.dismiss() }
        activeDialogs.clear()
        pinDialog?.dismiss()
        pinDialog = null
        unlockPromptShowing = false
        biometricAttempted = false
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

    private fun launchLockMonitor() {
        lockMonitorJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitorAppLockTimeout(
                    onUnlocked = {
                        if (itemId != -1L && itemCollectorJob?.isActive != true) showUnlockedState()
                    },
                    onLocked = ::enforceLockedState
                )
            }
        }
    }

    private fun loadItem() {
        itemCollectorJob?.cancel()
        itemCollectorJob = viewLifecycleOwner.lifecycleScope.launch {
            repository.allHistory
                .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { items ->
                if (!AppLockManager.isUnlocked()) {
                    enforceLockedState()
                    return@collect
                }
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

    private fun showUnlockedState() {
        if (_binding == null || !AppLockManager.isUnlocked()) return
        binding.root.visibility = View.VISIBLE
        setDetailActionsEnabled(true)
        loadItem()
    }

    private fun enforceLockedState() {
        if (_binding == null) return
        itemCollectorJob?.cancel()
        itemCollectorJob = null
        item = null
        activeDialogs.toList().forEach { it.dismiss() }
        activeDialogs.clear()
        binding.tvContent.text = ""
        binding.tvType.text = ""
        binding.tvTime.text = ""
        binding.tvNotes.text = ""
        binding.tvNotes.visibility = View.GONE
        binding.chipGroupTags.removeAllViews()
        binding.chipGroupTags.visibility = View.GONE
        binding.ivBarcode.setImageDrawable(null)
        setDetailActionsEnabled(false)
        binding.root.visibility = View.GONE
        if (parentFragment == null) showAppLockDialog()
    }

    private fun setDetailActionsEnabled(enabled: Boolean) {
        listOf(
            binding.btnShare,
            binding.btnEdit,
            binding.btnDelete,
            binding.btnToggleFavorite,
            binding.btnEditTags,
            binding.btnOpenGenerate
        ).forEach { it.isEnabled = enabled }
    }

    private fun requireDetailUnlocked(): Boolean {
        if (AppLockManager.isUnlocked()) return true
        enforceLockedState()
        return false
    }

    private fun showAppLockDialog() {
        if (unlockPromptShowing || AppLockManager.isUnlocked() || _binding == null) return
        if (!biometricAttempted &&
            AppLockManager.isBiometricEnabled() &&
            AppLockManager.isBiometricAvailable(requireContext())
        ) {
            biometricAttempted = true
            unlockPromptShowing = true
            AppLockManager.showBiometricPrompt(
                requireActivity(),
                onSuccess = {
                    unlockPromptShowing = false
                    biometricAttempted = false
                    if (_binding != null) {
                        showUnlockedState()
                    }
                },
                onError = {
                    fallbackToPinAfterBiometricError()
                }
            )
        } else {
            showPinDialog()
        }
    }

    internal fun fallbackToPinAfterBiometricError() {
        unlockPromptShowing = false
        if (_binding == null ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            AppLockManager.isUnlocked()
        ) return
        binding.root.post {
            if (_binding != null &&
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                !AppLockManager.isUnlocked()
            ) showPinDialog()
        }
    }

    private fun showPinDialog() {
        if (unlockPromptShowing || AppLockManager.isUnlocked() || _binding == null) return
        unlockPromptShowing = true
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.enter_pin)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.app_lock))
            .setView(input)
            .setPositiveButton(getString(R.string.unlock), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnDismissListener {
                unlockPromptShowing = false
                pinDialog = null
            }
            .create()
        pinDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = input.text?.toString().orEmpty()
                if (AppLockManager.verifyPin(pin)) {
                    AppLockManager.recordUnlock()
                    showUnlockedState()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.pin_incorrect), Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun showTrackedDialog(dialog: AlertDialog) {
        activeDialogs += dialog
        dialog.setOnDismissListener { activeDialogs -= dialog }
        dialog.show()
    }

    private fun bindItem(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
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
        binding.btnShare.setOnClickListener { showShareOptionsDialog(item) }
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
        if (!requireDetailUnlocked()) return
        val editText = EditText(requireContext()).apply {
            setText(item.tags ?: "")
            setSelection(item.tags?.length ?: 0)
            hint = getString(R.string.comma_separated_tags)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_tags))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                if (!requireDetailUnlocked()) return@setPositiveButton
                val tags = editText.text.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireDetailUnlocked()) return@launch
                    repository.setTags(item.id, TagManager.parseTags(tags))
                    Toast.makeText(requireContext(), getString(R.string.tags_saved), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        showTrackedDialog(dialog)
    }

    private fun openGeneratePage(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
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
        if (!requireDetailUnlocked()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun showShareOptionsDialog(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
        val items = arrayOf(
            getString(R.string.share_option_text),
            getString(R.string.share_option_card)
        )
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.share))
            .setItems(items) { dialog, which ->
                if (!requireDetailUnlocked()) return@setItems
                when (which) {
                    0 -> shareContent(item.content)
                    1 -> shareCard(item)
                }
                dialog.dismiss()
            }
            .create()
        showTrackedDialog(dialog)
    }

    private fun shareCard(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
        val ctx = context ?: return
        val format = item.barcodeFormat?.let { BarcodeFormat.fromString(it) } ?: BarcodeFormat.QR_CODE
        val rawStyle = item.styleJson?.let { styleConfigFromJson(it) } ?: AdvancedBarcodeGenerator.StyleConfig()
        val style = AdvancedBarcodeGenerator.sanitize(rawStyle, format)
        val bitmap = AdvancedBarcodeGenerator.generateStyled(item.content, format, 600, 600, style)
        if (bitmap == null) {
            Toast.makeText(ctx, getString(R.string.failed_to_generate, getString(R.string.unknown_error)), Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireDetailUnlocked()) return@launch
            val uri = ShareTemplateGenerator.generateShareImage(ctx, bitmap, item.content, item.type)
            if (!requireDetailUnlocked()) return@launch
            if (uri == null) {
                Toast.makeText(ctx, getString(R.string.failed_to_save, getString(R.string.unknown_error)), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }
    }

    private fun showEditDialog(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
        val editText = EditText(requireContext()).apply {
            setText(item.content)
            setSelection(item.content.length)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!requireDetailUnlocked()) return@setOnClickListener
                val newContent = editText.text.toString()
                if (newContent.isBlank()) {
                    editText.error = getString(R.string.please_enter_content)
                    return@setOnClickListener
                }
                editText.error = null
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireDetailUnlocked()) return@launch
                    repository.updateContent(item.id, newContent)
                    Toast.makeText(requireContext(), getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
        }
        showTrackedDialog(dialog)
    }

    private fun deleteItem(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_item))
            .setMessage(getString(R.string.delete_item_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (!requireDetailUnlocked()) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireDetailUnlocked()) return@launch
                    repository.delete(item)
                    Toast.makeText(requireContext(), getString(R.string.deleted), Toast.LENGTH_SHORT).show()
                    closeSelf()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        showTrackedDialog(dialog)
    }

    private fun toggleFavorite(item: HistoryItem) {
        if (!requireDetailUnlocked()) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireDetailUnlocked()) return@launch
            repository.toggleFavorite(item)
            val message = getString(if (!item.isFavorite) R.string.added_to_favorites else R.string.removed_from_favorites)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
