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
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentGenerateBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

class GenerateFragment : Fragment() {

    private var _binding: FragmentGenerateBinding? = null
    private val binding get() = _binding!!
    private var currentQRBitmap: Bitmap? = null
    private lateinit var historyRepository: HistoryRepository
    private var lastGeneratedContent: String? = null

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

        binding.btnGenerate.setOnClickListener {
            generateQRCode()
        }

        binding.btnSave.setOnClickListener {
            saveQRCode()
        }

        binding.btnShare.setOnClickListener {
            shareQRCode()
        }

        binding.btnBatchGenerate.setOnClickListener {
            startActivity(Intent(requireContext(), BatchGenerateActivity::class.java))
        }

        binding.btnClear.setOnClickListener {
            binding.etContent.text?.clear()
            currentQRBitmap = null
            binding.ivQRCode.setImageBitmap(null)
        }
    }

    private fun generateQRCode() {
        val content = binding.etContent.text?.toString()?.trim()
        if (content.isNullOrEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.please_enter_content), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val size = resources.getDimensionPixelSize(R.dimen.qr_code_size)
            val bitmap = createQRCode(content, size)
            currentQRBitmap = bitmap
            binding.ivQRCode.setImageBitmap(bitmap)
            
            // 保存到历史记录
            if (content != lastGeneratedContent) {
                lastGeneratedContent = content
                lifecycleScope.launch {
                    try {
                        historyRepository.insertGenerate(content, HistoryType.QR_CODE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save history", e)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.failed_to_generate, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun createQRCode(content: String, size: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
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

    private fun saveQRCode() {
        val bitmap = currentQRBitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "QR_$timeStamp.png"

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

    private fun shareQRCode() {
        val bitmap = currentQRBitmap
        if (bitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "qr_code.png")
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
            startActivity(Intent.createChooser(intent, "Share QR Code"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
