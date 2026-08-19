package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryQuery
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: HistoryRepository
    private lateinit var adapter: HistoryAdapter
    private var currentFilter = FilterType.ALL
    private var currentSearchQuery = ""
    private var currentTag: String? = null
    private var loadHistoryJob: Job? = null
    private var searchJob: Job? = null
    private val historyQuery = MutableStateFlow<HistoryQuery?>(null)
    private var unlockPromptShowing = false
    private var biometricAttempted = false
    private var pinDialog: AlertDialog? = null
    private var historyUiLocked = false
    private var lockMonitorJob: Job? = null
    private val sensitiveDialogs = mutableSetOf<AlertDialog>()

    private var sortNewestFirst = true
    private var timeRangeDays = 0
    private var typeFilter: HistoryType? = null
    private var formatFilter: String? = null

    enum class FilterType {
        ALL, SCANNED, GENERATED, FAVORITE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 列表区域（单/双栏布局共用，经 <include> 复用同一布局）。 */
    private val listBinding get() = binding.listPart

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            repository = HistoryRepository(requireContext())
            setupRecyclerView()
            setupFilterTabs()
            setupTagFilter()
            setupSearchView()
            setupClearButton()
            setupSortAndFilterButtons()
            setupStatsToggle()
            observeHistory()
        } catch (e: Exception) {
            android.util.Log.e("HistoryFragment", "DB init failed", e)
            Toast.makeText(requireContext(), getString(R.string.history_unavailable_with_reason, e.message), Toast.LENGTH_LONG).show()
            listBinding.tvEmpty.text = getString(R.string.history_unavailable)
            listBinding.tvEmpty.visibility = View.VISIBLE
            return
        }

        if (AppLockManager.isUnlocked()) {
            loadHistory()
        } else {
            enforceLockedState()
        }
        launchLockMonitor()
    }

    override fun onStart() {
        super.onStart()
        if (::repository.isInitialized && !AppLockManager.isUnlocked()) {
            enforceLockedState()
        } else if (::repository.isInitialized && historyUiLocked) {
            loadHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized && !AppLockManager.isUnlocked()) enforceLockedState()
    }

    private fun launchLockMonitor() {
        lockMonitorJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitorAppLockTimeout(
                    onUnlocked = {
                        if (::repository.isInitialized && historyUiLocked) loadHistory()
                    },
                    onLocked = ::enforceLockedState
                )
            }
        }
    }

    private fun enforceLockedState() {
        showLockedState()
        showAppLockDialog()
    }

    private fun setupStatsToggle() {
        listBinding.statsCard.setOnClickListener {
            val expanded = !QRCodeApp.isHistoryStatsExpanded(requireContext())
            QRCodeApp.setHistoryStatsExpanded(requireContext(), expanded)
            applyStatsExpanded(expanded)
        }
        applyStatsExpanded(QRCodeApp.isHistoryStatsExpanded(requireContext()))
    }

    private fun applyStatsExpanded(expanded: Boolean) {
        if (_binding == null) return
        listBinding.statsDetailContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        listBinding.ivStatsToggle.rotation = if (expanded) 180f else 0f
    }

    private fun refreshStats() {
        if (!::repository.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()
                val day7 = now - 7L * 24 * 60 * 60 * 1000
                val day30 = now - 30L * 24 * 60 * 60 * 1000
                val count7 = repository.scannedCountSince(day7)
                val count30 = repository.scannedCountSince(day30)
                val timestamps = repository.scannedTimestampsSince(now - 14L * 24 * 60 * 60 * 1000)
                val buckets = DailyBuckets.bucketize(timestamps, 14, now)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (!AppLockManager.isUnlocked()) {
                        showLockedState()
                        return@withContext
                    }
                    listBinding.tvStatsSummary.text = getString(R.string.stats_summary, count7, count30)
                    listBinding.tvStats7d.text = getString(R.string.stats_7d, count7)
                    listBinding.tvStats30d.text = getString(R.string.stats_30d, count30)
                    if (timestamps.isNotEmpty()) {
                        listBinding.statsBarChart.setData(buckets)
                        listBinding.statsBarChart.visibility = View.VISIBLE
                    } else {
                        listBinding.statsBarChart.visibility = View.GONE
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 统计是可选增强；失败时整卡隐藏并记日志
                android.util.Log.w("HistoryFragment", "stats refresh failed: ${e.message}")
                listBinding.statsCard.visibility = View.GONE
            }
        }
    }

    private fun showAppLockDialog() {
        if (unlockPromptShowing || AppLockManager.isUnlocked()) return

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
                    AppLockManager.recordUnlock()
                    if (_binding != null) {
                        loadHistory()
                    }
                },
                onFailed = {
                    // BiometricPrompt may remain visible after a rejected sample. Keep PIN retry
                    // on the locked page rather than opening a second dialog over it.
                }
            )
        } else {
            showPinDialog()
        }
    }

    private fun showPinDialog() {
        if (unlockPromptShowing || AppLockManager.isUnlocked()) return
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
                if (_binding != null && !AppLockManager.isUnlocked()) showLockedState()
            }
            .create()

        pinDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = input.text?.toString() ?: ""
                if (AppLockManager.verifyPin(pin)) {
                    AppLockManager.recordUnlock()
                    loadHistory()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.pin_incorrect), Toast.LENGTH_SHORT).show()
                    adapter.submitList(emptyList())
                }
            }
        }
        dialog.show()
    }

    private fun showLockedState() {
        if (_binding == null) return
        sensitiveDialogs.toList().forEach { it.dismiss() }
        sensitiveDialogs.clear()
        historyUiLocked = true
        historyQuery.value = null
        adapter.submitList(emptyList())
        setHistoryControlsEnabled(false)
        listBinding.statsCard.visibility = View.GONE
        listBinding.chipGroupTags.visibility = View.GONE
        listBinding.btnClearAll.visibility = View.GONE
        binding.detailPaneContainer?.visibility = View.GONE
        listBinding.tvEmpty.text = getString(R.string.unlock)
        listBinding.tvEmpty.visibility = View.VISIBLE
        listBinding.recyclerView.visibility = View.GONE
        listBinding.tvEmpty.setOnClickListener {
            if (biometricAttempted) {
                unlockPromptShowing = false
                showPinDialog()
            } else {
                showAppLockDialog()
            }
        }
    }

    private fun clearLockedState() {
        val wasLocked = historyUiLocked
        historyUiLocked = false
        setHistoryControlsEnabled(true)
        listBinding.statsCard.visibility = View.VISIBLE
        listBinding.btnClearAll.visibility = View.VISIBLE
        binding.detailPaneContainer?.visibility = View.VISIBLE
        listBinding.tvEmpty.text = getString(R.string.no_history)
        listBinding.tvEmpty.setOnClickListener(null)
        if (wasLocked) setupTagFilter()
    }

    private fun setHistoryControlsEnabled(enabled: Boolean) {
        listBinding.statsCard.isEnabled = enabled
        listBinding.searchView.isEnabled = enabled
        listOf(
            listBinding.btnFilterAll,
            listBinding.btnFilterScanned,
            listBinding.btnFilterGenerated,
            listBinding.btnFilterFavorite,
            listBinding.btnSort,
            listBinding.btnAdvancedFilter,
            listBinding.btnClearAll
        ).forEach { it.isEnabled = enabled }
        for (index in 0 until listBinding.chipGroupTags.childCount) {
            listBinding.chipGroupTags.getChildAt(index).isEnabled = enabled
        }
    }

    private fun requireHistoryUnlocked(): Boolean {
        if (AppLockManager.isUnlocked()) return true
        enforceLockedState()
        return false
    }

    private fun showSensitiveDialog(dialog: AlertDialog) {
        sensitiveDialogs += dialog
        dialog.setOnDismissListener { sensitiveDialogs -= dialog }
        dialog.show()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { item -> openHistoryDetail(item) },
            onEdit = { item -> showEditDialog(item) },
            onShare = { item -> shareContent(item.content) },
            onShareQR = { item -> shareQRCode(item) },
            onDelete = { item -> deleteItem(item) },
            onFavorite = { item -> toggleFavorite(item) },
            onAddNote = { item -> showAddNoteDialog(item) }
        )
        listBinding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        listBinding.recyclerView.adapter = adapter
    }

    private fun openHistoryDetail(item: HistoryItem) {
        if (!requireHistoryUnlocked()) return
        val detailPane = binding.detailPaneContainer
        if (detailPane != null) {
            // 平板双栏：详情嵌入右侧面板
            childFragmentManager.beginTransaction()
                .replace(R.id.detailPaneContainer, HistoryDetailFragment.newInstance(item.id))
                .commit()
        } else {
            // 手机单栏：打开独立详情页
            val intent = Intent(requireContext(), HistoryDetailActivity::class.java).apply {
                putExtra(HistoryDetailActivity.EXTRA_ITEM_ID, item.id)
            }
            startActivity(intent)
        }
    }

    private fun setupFilterTabs() {
        listBinding.btnFilterAll.setOnClickListener {
            currentFilter = FilterType.ALL
            loadHistory()
        }
        listBinding.btnFilterScanned.setOnClickListener {
            currentFilter = FilterType.SCANNED
            loadHistory()
        }
        listBinding.btnFilterGenerated.setOnClickListener {
            currentFilter = FilterType.GENERATED
            loadHistory()
        }
        listBinding.btnFilterFavorite.setOnClickListener {
            currentFilter = FilterType.FAVORITE
            loadHistory()
        }
    }

    private fun setupSearchView() {
        listBinding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query ?: ""
                loadHistory()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText ?: ""
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    loadHistory()
                }
                return true
            }
        })
    }

    private fun setupSortAndFilterButtons() {
        updateSortButtonText()
        listBinding.btnSort.setOnClickListener {
            sortNewestFirst = !sortNewestFirst
            updateSortButtonText()
            loadHistory()
        }
        listBinding.btnAdvancedFilter.setOnClickListener {
            showAdvancedFilterDialog()
        }
    }

    private fun updateSortButtonText() {
        val order = getString(if (sortNewestFirst) R.string.chip_sort_newest else R.string.chip_sort_oldest)
        listBinding.btnSort.text = order
        listBinding.btnSort.contentDescription = getString(R.string.sort_order) + ": " + order
    }

    private fun typeLabel(type: HistoryType): String = when (type) {
        HistoryType.QR_CODE -> getString(R.string.type_qr_code)
        HistoryType.BARCODE -> getString(R.string.type_barcode)
        HistoryType.TEXT -> getString(R.string.type_text)
        else -> type.name
    }

    private fun showAdvancedFilterDialog() {
        if (!requireHistoryUnlocked()) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_history_filter, null)
        val rbSortNewest = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSortNewest)
        val rbSortOldest = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSortOldest)
        val rbTimeAll = dialogView.findViewById<android.widget.RadioButton>(R.id.rbTimeAll)
        val rbTimeToday = dialogView.findViewById<android.widget.RadioButton>(R.id.rbTimeToday)
        val rbTime7d = dialogView.findViewById<android.widget.RadioButton>(R.id.rbTime7d)
        val rbTime30d = dialogView.findViewById<android.widget.RadioButton>(R.id.rbTime30d)
        val btnPickType = dialogView.findViewById<MaterialButton>(R.id.btnPickType)
        val btnPickFormat = dialogView.findViewById<MaterialButton>(R.id.btnPickFormat)

        if (sortNewestFirst) rbSortNewest.isChecked = true else rbSortOldest.isChecked = true
        when (timeRangeDays) {
            1 -> rbTimeToday.isChecked = true
            7 -> rbTime7d.isChecked = true
            30 -> rbTime30d.isChecked = true
            else -> rbTimeAll.isChecked = true
        }

        var pendingType = typeFilter
        var pendingFormat = formatFilter
        fun typeButtonText() = getString(R.string.filter_type) + ": " +
            (pendingType?.let { typeLabel(it) } ?: getString(R.string.all))
        fun formatButtonText() = getString(R.string.filter_format) + ": " +
            (pendingFormat ?: getString(R.string.all))
        btnPickType.text = typeButtonText()
        btnPickFormat.text = formatButtonText()

        btnPickType.setOnClickListener {
            showTypePickDialog { picked ->
                pendingType = picked
                btnPickType.text = typeButtonText()
            }
        }
        btnPickFormat.setOnClickListener {
            showFormatPickDialog { picked ->
                pendingFormat = picked
                btnPickFormat.text = formatButtonText()
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.advanced_filter))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                sortNewestFirst = rbSortNewest.isChecked
                timeRangeDays = when {
                    rbTimeToday.isChecked -> 1
                    rbTime7d.isChecked -> 7
                    rbTime30d.isChecked -> 30
                    else -> 0
                }
                typeFilter = pendingType
                formatFilter = pendingFormat
                updateSortButtonText()
                loadHistory()
            }
            .setNeutralButton(getString(R.string.filter_reset)) { _, _ ->
                sortNewestFirst = true
                timeRangeDays = 0
                typeFilter = null
                formatFilter = null
                updateSortButtonText()
                loadHistory()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showTypePickDialog(onPick: (HistoryType?) -> Unit) {
        if (!requireHistoryUnlocked()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val types = repository.getAllTypes()
            if (!requireHistoryUnlocked()) return@launch
            val labels = listOf(getString(R.string.all)) + types.map { typeLabel(it) }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.filter_type))
                .setSingleChoiceItems(labels.toTypedArray(), 0) { dialog, which ->
                    onPick(if (which == 0) null else types[which - 1])
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun showFormatPickDialog(onPick: (String?) -> Unit) {
        if (!requireHistoryUnlocked()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val formats = repository.getAllBarcodeFormats()
            if (!requireHistoryUnlocked()) return@launch
            val labels = listOf(getString(R.string.all)) + formats
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.filter_format))
                .setSingleChoiceItems(labels.toTypedArray(), 0) { dialog, which ->
                    onPick(if (which == 0) null else formats[which - 1])
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun setupClearButton() {
        listBinding.btnClearAll.setOnClickListener {
            if (!requireHistoryUnlocked()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.clear_history))
                .setMessage(getString(R.string.clear_history_confirm))
                .setPositiveButton(getString(R.string.clear_all)) { _, _ ->
                    if (!requireHistoryUnlocked()) return@setPositiveButton
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (!requireHistoryUnlocked()) return@launch
                        repository.deleteAll()
                        Toast.makeText(requireContext(), getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun setupTagFilter() {
        viewLifecycleOwner.lifecycleScope.launch {
            val tags = repository.getAllTags()
            if (!AppLockManager.isUnlocked()) {
                listBinding.chipGroupTags.visibility = View.GONE
                return@launch
            }
            if (tags.isEmpty()) {
                listBinding.chipGroupTags.visibility = View.GONE
                return@launch
            }
            listBinding.chipGroupTags.visibility = View.VISIBLE
            listBinding.chipGroupTags.removeAllViews()

            val allChip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = getString(R.string.all_tags)
                isCheckable = true
                isChecked = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        currentTag = null
                        loadHistory()
                    }
                }
            }
            listBinding.chipGroupTags.addView(allChip)

            for (tag in tags) {
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = tag
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            currentTag = tag
                            loadHistory()
                        }
                    }
                }
                listBinding.chipGroupTags.addView(chip)
            }
        }
    }

    private fun loadHistory() {
        if (!requireHistoryUnlocked()) return
        clearLockedState()
        refreshStats()
        val startTime = when (timeRangeDays) {
            1 -> startOfToday()
            7, 30 -> System.currentTimeMillis() - timeRangeDays * 24L * 60 * 60 * 1000
            else -> null
        }
        historyQuery.value = HistoryQuery(
            search = currentSearchQuery,
            tag = currentTag,
            isGenerated = when (currentFilter) {
                FilterType.SCANNED -> false
                FilterType.GENERATED -> true
                else -> null
            },
            favoritesOnly = currentFilter == FilterType.FAVORITE,
            type = typeFilter,
            barcodeFormat = formatFilter,
            startTime = startTime,
            newestFirst = sortNewestFirst
        )
    }

    private fun observeHistory() {
        loadHistoryJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                historyQuery.filterNotNull().collectLatest { query ->
                    if (!AppLockManager.isUnlocked()) {
                        showLockedState()
                        return@collectLatest
                    }
                    try {
                        repository.getHistory(query).collectLatest { items ->
                            if (!AppLockManager.isUnlocked()) {
                                showLockedState()
                                return@collectLatest
                            }
                            adapter.submitList(items)
                            updateEmptyState(items.isEmpty())
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("HistoryFragment", "loadHistory failed", e)
                        if (!AppLockManager.isUnlocked()) {
                            showLockedState()
                            return@collectLatest
                        }
                        adapter.submitList(emptyList())
                        updateEmptyState(true)
                        listBinding.tvEmpty.text = getString(R.string.history_unavailable_with_reason, e.message)
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        listBinding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        listBinding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun toggleFavorite(item: HistoryItem) {
        if (!requireHistoryUnlocked()) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireHistoryUnlocked()) return@launch
            repository.toggleFavorite(item)
            val message = getString(if (!item.isFavorite) R.string.added_to_favorites else R.string.removed_from_favorites)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddNoteDialog(item: HistoryItem) {
        if (!requireHistoryUnlocked()) return
        val editText = EditText(requireContext()).apply {
            setText(item.notes ?: "")
            setSelection(item.notes?.length ?: 0)
            hint = getString(R.string.notes_hint)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_notes))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                if (!requireHistoryUnlocked()) return@setPositiveButton
                val notes = editText.text.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireHistoryUnlocked()) return@launch
                    repository.addNotes(item.id, notes)
                    Toast.makeText(requireContext(), getString(R.string.notes_saved), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        showSensitiveDialog(dialog)
    }

    private fun showEditDialog(item: HistoryItem) {
        if (!requireHistoryUnlocked()) return
        val editText = EditText(requireContext()).apply {
            setText(item.content)
            setSelection(item.content.length)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_content))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!requireHistoryUnlocked()) return@setOnClickListener
                val newContent = editText.text.toString()
                if (newContent.isBlank()) {
                    editText.error = getString(R.string.please_enter_content)
                    return@setOnClickListener
                }
                editText.error = null
                if (newContent != item.content) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (!requireHistoryUnlocked()) return@launch
                        repository.updateContent(item.id, newContent)
                        Toast.makeText(requireContext(), getString(R.string.updated), Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
        }
        showSensitiveDialog(dialog)
    }

    private fun shareContent(content: String) {
        if (!requireHistoryUnlocked()) return
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun shareQRCode(item: HistoryItem) {
        if (!requireHistoryUnlocked()) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (!requireHistoryUnlocked()) return@launch
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    val format = item.barcodeFormat?.let { BarcodeFormat.fromString(it) } ?: BarcodeFormat.QR_CODE
                    val rawStyle = item.styleJson?.let { styleConfigFromJson(it) } ?: AdvancedBarcodeGenerator.StyleConfig()
                    val style = AdvancedBarcodeGenerator.sanitize(rawStyle, format)
                    AdvancedBarcodeGenerator.generateStyled(item.content, format, 1024, 1024, style)
                }
                if (bitmap == null) {
                    Toast.makeText(requireContext(), getString(R.string.barcode_generation_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (!requireHistoryUnlocked()) return@launch

                val cachePath = File(requireContext().cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "qr_share_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.qr_code_for, item.content))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Grant permission to all potential receivers
                val resInfoList = requireContext().packageManager.queryIntentActivities(intent, 0)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    requireContext().grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                startActivity(Intent.createChooser(intent, getString(R.string.share_qr_code)))

            } catch (e: Exception) {
                android.util.Log.e("HistoryFragment", "Error sharing QR code", e)
                Toast.makeText(requireContext(), getString(R.string.qr_generation_failed_with_reason, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteItem(item: HistoryItem) {
        if (!requireHistoryUnlocked()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_item))
            .setMessage(getString(R.string.delete_item_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                if (!requireHistoryUnlocked()) return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    if (!requireHistoryUnlocked()) return@launch
                    repository.delete(item)
                    Toast.makeText(requireContext(), getString(R.string.deleted), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        loadHistoryJob?.cancel()
        lockMonitorJob?.cancel()
        lockMonitorJob = null
        sensitiveDialogs.toList().forEach { it.dismiss() }
        sensitiveDialogs.clear()
        pinDialog?.dismiss()
        pinDialog = null
        unlockPromptShowing = false
        biometricAttempted = false
        historyUiLocked = false
        super.onDestroyView()
        _binding = null
    }
}
