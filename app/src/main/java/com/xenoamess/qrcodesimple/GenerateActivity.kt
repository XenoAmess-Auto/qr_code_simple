package com.xenoamess.qrcodesimple

import android.app.Activity
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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityGenerateBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenerateBinding
    private var currentBitmap: Bitmap? = null
    private var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE
    private var selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.CLASSIC
    private var cornerRadius = 0f
    private var dotScale = 1f
    private var logoBitmap: Bitmap? = null

    private val pickLogoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadLogo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupFormatSpinner()
        setupStyleControls()
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
                updateStyleControlsVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupStyleControls() {
        // 颜色方案按钮
        binding.btnColorClassic.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.CLASSIC; generateBarcode() }
        binding.btnColorBlue.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.BLUE; generateBarcode() }
        binding.btnColorGreen.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.GREEN; generateBarcode() }
        binding.btnColorRed.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.RED; generateBarcode() }
        binding.btnColorPurple.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.PURPLE; generateBarcode() }
        binding.btnColorOrange.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.ORANGE; generateBarcode() }
        binding.btnColorDark.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.DARK; generateBarcode() }
        binding.btnColorCyan.setOnClickListener { selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.CYAN; generateBarcode() }

        // 圆角滑块
        binding.seekBarCornerRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                cornerRadius = progress / 100f * 20f  // 0-20px
                binding.tvCornerRadiusValue.text = "${progress}%"
                if (fromUser) generateBarcode()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 点阵大小滑块
        binding.seekBarDotScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dotScale = 0.3f + (progress / 100f) * 0.7f  // 0.3-1.0
                binding.tvDotScaleValue.text = "${(dotScale * 100).toInt()}%"
                if (fromUser) generateBarcode()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Logo 按钮
        binding.btnAddLogo.setOnClickListener {
            pickLogoLauncher.launch("image/*")
        }

        binding.btnRemoveLogo.setOnClickListener {
            logoBitmap = null
            binding.ivLogoPreview.setImageBitmap(null)
            binding.ivLogoPreview.visibility = View.GONE
            generateBarcode()
        }
    }

    private fun updateStyleControlsVisibility() {
        // 只有 QR Code 支持高级样式
        val isQR = selectedFormat == BarcodeFormat.QR_CODE
        binding.cardStyle.visibility = if (isQR) View.VISIBLE else View.GONE
    }

    private fun loadLogo(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    logoBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    binding.ivLogoPreview.setImageBitmap(logoBitmap)
                    binding.ivLogoPreview.visibility = View.VISIBLE
                    generateBarcode()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GenerateActivity, "Failed to load logo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
            val bitmap = if (selectedFormat == BarcodeFormat.QR_CODE) {
                // 使用高级生成器
                val style = selectedStyle.copy(
                    cornerRadius = cornerRadius,
                    dotScale = dotScale,
                    logoBitmap = logoBitmap
                )
                AdvancedBarcodeGenerator.generateStyled(content, selectedFormat, 800, style)
            } else {
                // 使用基础生成器
                val config = BarcodeGenerator.BarcodeConfig(
                    format = selectedFormat,
                    width = 800,
                    height = 600,
                    foregroundColor = selectedStyle.foregroundColor,
                    backgroundColor = selectedStyle.backgroundColor
                )
                BarcodeGenerator.generate(content, config)
            }

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
