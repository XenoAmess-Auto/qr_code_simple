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
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.EnumMap

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: HistoryRepository
    private lateinit var adapter: HistoryAdapter
    private var currentFilter = FilterType.ALL

    enum class FilterType {
        ALL, SCANNED, GENERATED
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
        setupClearButton()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onEdit = { item -> showEditDialog(item) },
            onShare = { item -> shareContent(item.content) },
            onShareQR = { item -> shareQRCode(item.content) },
            onDelete = { item -> deleteItem(item) }
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
                    else -> FilterType.ALL
                }
                loadHistory()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupClearButton() {
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all history?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        repository.deleteAll()
                        Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            when (currentFilter) {
                FilterType.ALL -> repository.allHistory
                FilterType.SCANNED -> repository.scannedHistory
                FilterType.GENERATED -> repository.generatedHistory
            }.collectLatest { items ->
                adapter.submitList(items)
                binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showEditDialog(item: HistoryItem) {
        val editText = EditText(requireContext()).apply {
            setText(item.content)
            setSelection(item.content.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Content")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newContent = editText.text.toString()
                if (newContent.isNotBlank() && newContent != item.content) {
                    lifecycleScope.launch {
                        repository.updateContent(item.id, newContent)
                        Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareContent(content: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(shareIntent, "Share"))
    }

    private fun shareQRCode(content: String) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    generateQRCode(content, 1024)
                }
                
                // 保存到缓存
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
                    putExtra(Intent.EXTRA_TEXT, "QR Code for: $content")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share QR Code"))
                
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
            .setTitle("Delete")
            .setMessage("Delete this item?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.delete(item)
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}