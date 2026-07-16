package com.xenoamess.qrcodesimple

import android.app.AlertDialog
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
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Button
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
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
        } catch (e: Exception) {
            android.util.Log.e("HistoryFragment", "DB init failed", e)
            Toast.makeText(requireContext(), "History unavailable: ${e.message}", Toast.LENGTH_LONG).show()
            listBinding.tvEmpty.text = "History unavailable"
            listBinding.tvEmpty.visibility = View.VISIBLE
            return
        }

        if (AppLockManager.isUnlocked()) {
            loadHistory()
        } else {
            showAppLockDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized && !AppLockManager.isUnlocked()) {
            adapter.submitList(emptyList())
            showAppLockDialog()
        }
    }

    private fun showAppLockDialog() {
        if (AppLockManager.isBiometricEnabled() && AppLockManager.isBiometricAvailable(requireContext())) {
            AppLockManager.showBiometricPrompt(
                requireActivity(),
                onSuccess = {
                    AppLockManager.recordUnlock()
                    loadHistory()
                },
                onFailed = { showPinDialog() }
            )
        } else {
            showPinDialog()
        }
    }

    private fun showPinDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.enter_pin)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.app_lock))
            .setView(input)
            .setPositiveButton(getString(R.string.unlock)) { _, _ ->
                val pin = input.text?.toString() ?: ""
                if (AppLockManager.verifyPin(pin)) {
                    AppLockManager.recordUnlock()
                    loadHistory()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.pin_incorrect), Toast.LENGTH_SHORT).show()
                    adapter.submitList(emptyList())
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                adapter.submitList(emptyList())
            }
            .setOnCancelListener {
                adapter.submitList(emptyList())
            }
            .show()
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
                loadHistory()
                return true
            }
        })
    }

    private fun setupClearButton() {
        listBinding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.clear_history))
                .setMessage(getString(R.string.clear_history_confirm))
                .setPositiveButton(getString(R.string.clear_all)) { _, _ ->
                    lifecycleScope.launch {
                        repository.deleteAll()
                        Toast.makeText(requireContext(), getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun setupTagFilter() {
        lifecycleScope.launch {
            val tags = repository.getAllTags()
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
        loadHistoryJob?.cancel()
        loadHistoryJob = lifecycleScope.launch {
            try {
                val flow = when {
                    currentTag != null -> repository.getHistoryByTag(currentTag!!)
                    currentSearchQuery.isNotEmpty() -> repository.searchHistory(currentSearchQuery)
                    currentFilter == FilterType.FAVORITE -> repository.getFavoriteHistory()
                    else -> when (currentFilter) {
                        FilterType.ALL -> repository.allHistory
                        FilterType.SCANNED -> repository.scannedHistory
                        FilterType.GENERATED -> repository.generatedHistory
                        FilterType.FAVORITE -> repository.getFavoriteHistory()
                    }
                }

                flow.collectLatest { items ->
                    adapter.submitList(items)
                    updateEmptyState(items.isEmpty())
                }
            } catch (e: Exception) {
                android.util.Log.e("HistoryFragment", "loadHistory failed", e)
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        adapter.submitList(emptyList())
                        updateEmptyState(true)
                        listBinding.tvEmpty.text = "History unavailable: ${e.message}"
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
        lifecycleScope.launch {
            repository.toggleFavorite(item)
            val message = getString(if (!item.isFavorite) R.string.added_to_favorites else R.string.removed_from_favorites)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddNoteDialog(item: HistoryItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.notes ?: "")
            setSelection(item.notes?.length ?: 0)
            hint = "Add notes..."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Notes")
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                val notes = editText.text.toString()
                lifecycleScope.launch {
                    repository.addNotes(item.id, notes)
                    Toast.makeText(requireContext(), "Notes saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditDialog(item: HistoryItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.content)
            setSelection(item.content.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit_content))
            .setView(editText)
            .setPositiveButton(getString(R.string.save_action)) { _, _ ->
                val newContent = editText.text.toString()
                if (newContent.isNotBlank() && newContent != item.content) {
                    lifecycleScope.launch {
                        repository.updateContent(item.id, newContent)
                        Toast.makeText(requireContext(), getString(R.string.updated), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun shareContent(content: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun shareQRCode(item: HistoryItem) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    val format = item.barcodeFormat?.let { BarcodeFormat.fromString(it) } ?: BarcodeFormat.QR_CODE
                    val rawStyle = item.styleJson?.let { styleConfigFromJson(it) } ?: AdvancedBarcodeGenerator.StyleConfig()
                    val style = AdvancedBarcodeGenerator.sanitize(rawStyle, format)
                    AdvancedBarcodeGenerator.generateStyled(item.content, format, 1024, 1024, style)
                }
                if (bitmap == null) {
                    Toast.makeText(requireContext(), "Failed to generate barcode", Toast.LENGTH_SHORT).show()
                    return@launch
                }

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
                Toast.makeText(requireContext(), "Failed to generate QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteItem(item: HistoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_item))
            .setMessage(getString(R.string.delete_item_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    repository.delete(item)
                    Toast.makeText(requireContext(), getString(R.string.deleted), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
