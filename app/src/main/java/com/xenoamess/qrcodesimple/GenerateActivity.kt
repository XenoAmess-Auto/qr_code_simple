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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityGenerateBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

class GenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenerateBinding
    private var currentQRBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnGenerate.setOnClickListener {
            generateQRCode()
        }

        binding.btnSave.setOnClickListener {
            saveQRCode()
        }

        binding.btnShare.setOnClickListener {
            shareQRCode()
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
            Toast.makeText(this, "Please enter content", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val size = resources.getDimensionPixelSize(R.dimen.qr_code_size)
            val bitmap = createQRCode(content, size)
            currentQRBitmap = bitmap
            binding.ivQRCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate QR code: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Please generate QR code first", Toast.LENGTH_SHORT).show()
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

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Toast.makeText(this, "Saved to gallery: $fileName", Toast.LENGTH_SHORT).show()
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(picturesDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(this, "Saved to: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareQRCode() {
        val bitmap = currentQRBitmap
        if (bitmap == null) {
            Toast.makeText(this, "Please generate QR code first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "qr_code.png")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share QR Code"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
