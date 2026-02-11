package com.xenoamess.qrcodesimple

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
import com.google.android.material.chip.Chip
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentHistoryBinding
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: HistoryRepository
    private lateinit var adapter: HistoryAdapter
    private var currentFilter = FilterType.ALL
    private var currentSearchQuery = ""

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = HistoryRepository(requireContext())
        setupRecyclerView()
        setupFilterTabs()
        setupSearchView()
        setupFilterChips()
        setupClearButton()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onEdit = { item -> showEditDialog(item) },
            onShare = { item -> shareContent(item.content) },
            onShareQR = { item -> shareQRCode(item.content) },
            onDelete = { item -> deleteItem(item) },
            onFavorite = { item -> toggleFavorite(item) },
            onAddNote = { item -> showAddNoteDialog(item) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupFilterTabs() {
        binding.filterTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = when (tab?.position) {
                    0 -> FilterType.ALL
                    1 -> FilterType.SCANNED
                    2 -> FilterType.GENERATED
                    3 -> FilterType.FAVORITE
                    else -> FilterType.ALL
                }
                loadHistory()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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

    private fun setupFilterChips() {
        lifecycleScope.launch {
            // 加载类型筛选
            val types = repository.getAllTypes()
            types.forEach { type ->
                val chip = Chip(requireContext()).apply {
                    text = getTypeDisplayName(type)
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            filterByType(type)
                        } else {
                            loadHistory()
                        }
                    }
                }
                binding.chipGroupType.addView(chip)
            }
        }
    }

    private fun getTypeDisplayName(type: HistoryType): String {
        return when (type) {
            HistoryType.QR_CODE -> getString(R.string.type_qr_code)
            HistoryType.BARCODE -> getString(R.string.type_barcode)
            HistoryType.DATA_MATRIX -> getString(R.string.type_data_matrix)
            HistoryType.AZTEC -> getString(R.string.type_aztec)
            HistoryType.PDF417 -> getString(R.string.type_pdf417)
            HistoryType.TEXT -> getString(R.string.type_text)
        }
    }

    private fun filterByType(type: HistoryType) {
        lifecycleScope.launch {
            repository.getHistoryByType(type).collectLatest { items ->
                adapter.submitList(items)
                updateEmptyState(items.isEmpty())
            }
        }
    }

    private fun setupClearButton() {
        binding.btnClearAll.setOnClickListener {
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

    private fun loadHistory() {
        lifecycleScope.launch {
            val flow = when {
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
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun toggleFavorite(item: HistoryItem) {
        lifecycleScope.launch {
            repository.toggleFavorite(item)
            val message = if (!item.isFavorite) "Added to favorites" else "Removed from favorites"
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

    private fun shareQRCode(content: String) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    generateQRCode(content, 1024)
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
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.qr_code_for, content))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_qr_code)))

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to generate QR: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateQRCode(content: String, size: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 2)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
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
