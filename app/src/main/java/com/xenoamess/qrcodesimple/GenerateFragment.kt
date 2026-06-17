package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentGenerateBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenerateFragment : Fragment() {

    private var _binding: FragmentGenerateBinding? = null
    private val binding get() = _binding!!
    private var currentBitmap: Bitmap? = null
    private lateinit var historyRepository: HistoryRepository
    private var lastGeneratedContent: String? = null
    private var lastGeneratedFormat: BarcodeFormat = BarcodeFormat.QR_CODE
    private var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE

    companion object {
        private const val TAG = "GenerateFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGenerateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyRepository = HistoryRepository(requireContext())

        setupFormatSelector()

        binding.btnGenerate.setOnClickListener {
            generateBarcode()
        }

        binding.btnSave.setOnClickListener {
            saveBarcode()
        }

        binding.btnShare.setOnClickListener {
            shareBarcode()
        }

        binding.btnClear.setOnClickListener {
            binding.etContent.text?.clear()
            currentBitmap = null
            binding.ivQRCode.setImageBitmap(null)
        }
    }

    private fun setupFormatSelector() {
        val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            formats.map { it.displayName }
        )
        binding.spinnerFormat.setAdapter(adapter)

        val initialPosition = formats.indexOf(selectedFormat)
        if (initialPosition >= 0) {
            binding.spinnerFormat.setText(formats[initialPosition].displayName, false)
        }

        binding.spinnerFormat.setOnItemClickListener { _, _, position, _ ->
            selectedFormat = formats[position]
        }
    }

    private fun generateBarcode() {
        val content = binding.etContent.text?.toString()?.trim()
        if (content.isNullOrEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.please_enter_content), Toast.LENGTH_SHORT).show()
            return
        }

        val validation = BarcodeGenerator.validateContent(content, selectedFormat)
        if (!validation.isValid) {
            Toast.makeText(requireContext(), validation.errorMessage ?: getString(R.string.invalid_content_for_format), Toast.LENGTH_LONG).show()
            return
        }

        try {
            val size = resources.getDimensionPixelSize(R.dimen.qr_code_size)
            val config = BarcodeGenerator.BarcodeConfig(
                format = selectedFormat,
                width = size,
                height = size,
                foregroundColor = Color.BLACK,
                backgroundColor = Color.WHITE
            )
            val bitmap = BarcodeGenerator.generate(content, config)
            if (bitmap == null) {
                Toast.makeText(requireContext(), getString(R.string.failed_to_generate, null), Toast.LENGTH_SHORT).show()
                return
            }
            currentBitmap = bitmap
            binding.ivQRCode.setImageBitmap(bitmap)

            if (content != lastGeneratedContent || selectedFormat != lastGeneratedFormat) {
                lastGeneratedContent = content
                lastGeneratedFormat = selectedFormat
                lifecycleScope.launch {
                    try {
                        historyRepository.insertGenerate(content, selectedFormat.toHistoryType(), selectedFormat.name)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save history", e)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.failed_to_generate, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBarcode() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${selectedFormat.name}_$timeStamp.png"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Toast.makeText(requireContext(), getString(R.string.saved_to_gallery, fileName), Toast.LENGTH_SHORT).show()
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(picturesDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(requireContext(), getString(R.string.saved_to, file.absolutePath), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareBarcode() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "barcode.png")
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
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_barcode)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun BarcodeFormat.toHistoryType(): HistoryType {
        return when (this) {
            BarcodeFormat.QR_CODE -> HistoryType.QR_CODE
            BarcodeFormat.DATA_MATRIX -> HistoryType.DATA_MATRIX
            BarcodeFormat.AZTEC -> HistoryType.AZTEC
            BarcodeFormat.PDF417 -> HistoryType.PDF417
            else -> HistoryType.BARCODE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
