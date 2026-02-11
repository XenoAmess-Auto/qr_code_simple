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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityGenerateBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenerateBinding
    private var currentBitmap: Bitmap? = null
    private var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupFormatSpinner()
        setupButtons()
    }

    private fun setupFormatSpinner() {
        val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            formats.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFormat.adapter = adapter

        binding.spinnerFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFormat = formats[position]
                updateHintForFormat()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateHintForFormat() {
        val hint = when (selectedFormat) {
            BarcodeFormat.EAN_13 -> "Enter 13 digits (e.g., 1234567890123)"
            BarcodeFormat.EAN_8 -> "Enter 8 digits"
            BarcodeFormat.UPC_A -> "Enter 12 digits"
            BarcodeFormat.UPC_E -> "Enter 6-8 digits"
            BarcodeFormat.CODE_39 -> "Enter alphanumeric (0-9, A-Z, -, ., space, $, /, +, %)"
            BarcodeFormat.CODE_128 -> "Enter any ASCII characters"
            else -> getString(R.string.enter_content)
        }
        binding.etContent.hint = hint
    }

    private fun setupButtons() {
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

    private fun generateBarcode() {
        val content = binding.etContent.text?.toString()?.trim()
        if (content.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.please_enter_content), Toast.LENGTH_SHORT).show()
            return
        }

        // 验证内容格式
        val validation = BarcodeGenerator.validateContent(content, selectedFormat)
        if (!validation.isValid) {
            Toast.makeText(this, validation.errorMessage, Toast.LENGTH_LONG).show()
            return
        }

        try {
            val config = BarcodeGenerator.BarcodeConfig(
                format = selectedFormat,
                width = 800,
                height = 600
            )
            
            val bitmap = BarcodeGenerator.generate(content, config)
            if (bitmap != null) {
                currentBitmap = bitmap
                binding.ivQRCode.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this, getString(R.string.failed_to_generate), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_generate, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBarcode() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val prefix = selectedFormat.name.lowercase().replace("_", "")
        val fileName = "${prefix}_$timeStamp.png"

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
                    Toast.makeText(this, getString(R.string.saved_to_gallery, fileName), Toast.LENGTH_SHORT).show()
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(picturesDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(this, getString(R.string.saved_to, file.absolutePath), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareBarcode() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "barcode.png")
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
            startActivity(Intent.createChooser(intent, getString(R.string.share_qr)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
