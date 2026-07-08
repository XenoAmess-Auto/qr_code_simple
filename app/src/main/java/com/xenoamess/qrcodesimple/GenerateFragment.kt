package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentGenerateBinding
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.CLASSIC
    private var cornerRadius = 0f
    private var logoScale = 0.2f
    private var logoBitmap: Bitmap? = null
    private var foregroundImageBitmap: Bitmap? = null
    private var backgroundImageBitmap: Bitmap? = null
    private var validationJob: Job? = null
    private var pendingImageType: ImageType? = null

    private enum class ImageType {
        FOREGROUND, BACKGROUND
    }

    companion object {
        private const val TAG = "GenerateFragment"
        private const val MAX_LOGO_PX = 512
        private const val MAX_STYLE_IMAGE_PX = 1024
    }

    private val pickLogoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImage(it, MAX_LOGO_PX) { bitmap ->
            logoBitmap = bitmap
            updateImagePreview(binding.ivLogoPreview, bitmap)
            generateBarcode()
        } }
    }

    private val pickForegroundImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImageType = ImageType.FOREGROUND
            launchCrop(it, createCropDestination("fg"))
        }
    }

    private val pickBackgroundImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImageType = ImageType.BACKGROUND
            launchCrop(it, createCropDestination("bg"))
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val type = pendingImageType
        pendingImageType = null
        if (type == null) return@registerForActivityResult
        val resultUri = UCrop.getOutput(result.data ?: return@registerForActivityResult)
        if (result.resultCode != android.app.Activity.RESULT_OK || resultUri == null) return@registerForActivityResult
        loadImage(resultUri, MAX_STYLE_IMAGE_PX) { bitmap ->
            when (type) {
                ImageType.FOREGROUND -> {
                    foregroundImageBitmap = bitmap
                    selectedStyle = selectedStyle.copy(foregroundBitmap = bitmap)
                    updateImagePreview(binding.viewFgImagePreview, bitmap)
                    binding.btnRemoveForegroundImage.visibility = View.VISIBLE
                }
                ImageType.BACKGROUND -> {
                    backgroundImageBitmap = bitmap
                    selectedStyle = selectedStyle.copy(backgroundBitmap = bitmap)
                    updateImagePreview(binding.viewBgImagePreview, bitmap)
                    binding.btnRemoveBackgroundImage.visibility = View.VISIBLE
                }
            }
            generateBarcode()
        }
    }

    private fun createCropDestination(prefix: String): Uri {
        val dir = File(requireContext().cacheDir, "images")
        dir.mkdirs()
        val file = File(dir, "$prefix-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }

    private fun launchCrop(sourceUri: Uri, destinationUri: Uri) {
        try {
            val options = UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setCompressionQuality(100)
                setHideBottomControls(false)
                setToolbarTitle(getString(R.string.crop_image))
            }
            val intent = UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .getIntent(requireContext())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            cropLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchCrop failed", e)
            pendingImageType = null
            Toast.makeText(context, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
        }
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
        setupStyleControls()
        setupButtons()
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
            updateHintForFormat()
            generateBarcode()
        }
    }

    private fun setupStyleControls() {
        binding.btnColorClassic.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.CLASSIC) }
        binding.btnColorBlue.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.BLUE) }
        binding.btnColorGreen.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.GREEN) }
        binding.btnColorRed.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.RED) }
        binding.btnColorPurple.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.PURPLE) }
        binding.btnColorOrange.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.ORANGE) }
        binding.btnColorDark.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.DARK) }
        binding.btnColorCyan.setOnClickListener { applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.CYAN) }

        binding.seekBarCornerRadius.addOnChangeListener { _, value, _ ->
            cornerRadius = value / 100f
            binding.tvCornerRadiusValue.text = "${value.toInt()}%"
        }
        binding.seekBarCornerRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { generateBarcode() }
        })

        binding.seekBarLogoScale.addOnChangeListener { _, value, _ ->
            logoScale = value / 100f
            binding.tvLogoScaleValue.text = "${value.toInt()}%"
        }
        binding.seekBarLogoScale.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { generateBarcode() }
        })

        binding.btnPickForegroundColor.setOnClickListener {
            ColorPickerDialog().apply {
                setInitialColor(selectedStyle.foregroundColor)
                onColorSelected = { color ->
                    selectedStyle = selectedStyle.copy(foregroundColor = color)
                    foregroundImageBitmap = null
                    updateImagePreview(binding.viewFgImagePreview, null)
                    binding.btnRemoveForegroundImage.visibility = View.GONE
                    updateColorPreviews()
                    generateBarcode()
                }
            }.show(parentFragmentManager, "fg_color")
        }
        binding.btnPickBackgroundColor.setOnClickListener {
            ColorPickerDialog().apply {
                setInitialColor(selectedStyle.backgroundColor)
                onColorSelected = { color ->
                    selectedStyle = selectedStyle.copy(backgroundColor = color)
                    backgroundImageBitmap = null
                    updateImagePreview(binding.viewBgImagePreview, null)
                    binding.btnRemoveBackgroundImage.visibility = View.GONE
                    updateColorPreviews()
                    generateBarcode()
                }
            }.show(parentFragmentManager, "bg_color")
        }

        binding.btnPickForegroundImage.setOnClickListener {
            pickForegroundImageLauncher.launch("image/*")
        }
        binding.btnRemoveForegroundImage.setOnClickListener {
            foregroundImageBitmap = null
            selectedStyle = selectedStyle.copy(foregroundBitmap = null)
            updateImagePreview(binding.viewFgImagePreview, null)
            binding.btnRemoveForegroundImage.visibility = View.GONE
            generateBarcode()
        }
        binding.btnPickBackgroundImage.setOnClickListener {
            pickBackgroundImageLauncher.launch("image/*")
        }
        binding.btnRemoveBackgroundImage.setOnClickListener {
            backgroundImageBitmap = null
            selectedStyle = selectedStyle.copy(backgroundBitmap = null)
            updateImagePreview(binding.viewBgImagePreview, null)
            binding.btnRemoveBackgroundImage.visibility = View.GONE
            generateBarcode()
        }

        updateColorPreviews()
        binding.seekBarLogoScale.value = logoScale * 100f
        binding.tvLogoScaleValue.text = "${(logoScale * 100).toInt()}%"

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

    private fun applyColorScheme(scheme: AdvancedBarcodeGenerator.StyleConfig) {
        selectedStyle = scheme
        foregroundImageBitmap = null
        backgroundImageBitmap = null
        updateImagePreview(binding.viewFgImagePreview, null)
        updateImagePreview(binding.viewBgImagePreview, null)
        binding.btnRemoveForegroundImage.visibility = View.GONE
        binding.btnRemoveBackgroundImage.visibility = View.GONE
        updateColorPreviews()
        generateBarcode()
    }

    private fun updateImagePreview(imageView: android.widget.ImageView, bitmap: Bitmap?) {
        imageView.setImageBitmap(bitmap)
        imageView.visibility = if (bitmap != null) View.VISIBLE else View.GONE
    }

    private fun updateColorPreviews() {
        val fg = selectedStyle.foregroundColor
        val bg = selectedStyle.backgroundColor
        binding.viewFgColorPreview.background = ColorDrawable(fg)
        binding.viewBgColorPreview.background = ColorDrawable(bg)
    }

    private fun updateHintForFormat() {
        val hintRes = when (selectedFormat) {
            BarcodeFormat.EAN_13 -> R.string.hint_ean_13
            BarcodeFormat.EAN_8 -> R.string.hint_ean_8
            BarcodeFormat.UPC_A -> R.string.hint_upc_a
            BarcodeFormat.UPC_E -> R.string.hint_upc_e
            BarcodeFormat.CODE_39 -> R.string.hint_code_39
            BarcodeFormat.CODE_128 -> R.string.hint_code_128
            else -> R.string.enter_content
        }
        binding.tilContent.hint = getString(hintRes)
    }

    private fun loadImage(uri: Uri, maxPx: Int, onLoaded: (Bitmap) -> Unit) {
        val ctx = context ?: return
        lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(inputStream, null, bounds)
                        // 重新打开流（上一轮已消费）
                        ctx.contentResolver.openInputStream(uri)?.use { realStream ->
                            val opts = BitmapFactory.Options().apply {
                                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxPx)
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            BitmapFactory.decodeStream(realStream, null, opts)
                        }
                    }
                }
                if (bmp != null) {
                    onLoaded(bmp)
                } else if (_binding != null) {
                    Toast.makeText(ctx, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadImage failed", e)
                if (_binding != null) {
                    Toast.makeText(ctx, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxPx: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while ((width / sample) > maxPx || (height / sample) > maxPx) {
            sample *= 2
        }
        return sample
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
        val ctx = context ?: return
        val content = binding.etContent.text?.toString()?.trim()
        if (content.isNullOrEmpty()) {
            Toast.makeText(ctx, getString(R.string.please_enter_content), Toast.LENGTH_SHORT).show()
            return
        }

        val validation = BarcodeGenerator.validateContent(content, selectedFormat)
        if (!validation.isValid) {
            Toast.makeText(ctx, validation.errorMessage ?: getString(R.string.invalid_content_for_format), Toast.LENGTH_LONG).show()
            return
        }

        try {
            val style = selectedStyle.copy(
                cornerRadius = cornerRadius,
                logoBitmap = logoBitmap,
                logoScale = logoScale,
                foregroundBitmap = foregroundImageBitmap,
                backgroundBitmap = backgroundImageBitmap
            )
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, selectedFormat, 800, style)
            if (bitmap == null) {
                Toast.makeText(ctx, getString(R.string.failed_to_generate, getString(R.string.unknown_error)), Toast.LENGTH_SHORT).show()
                return
            }
            currentBitmap = bitmap
            binding.ivQRCode.setImageBitmap(bitmap)
            validateGeneratedBarcode(content, selectedFormat, bitmap)

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
            Log.e(TAG, "generateBarcode failed", e)
            Toast.makeText(ctx, getString(R.string.failed_to_generate, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBarcode() {
        val ctx = context ?: return
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
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

                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    ctx.contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    Toast.makeText(ctx, getString(R.string.saved_to_gallery, fileName), Toast.LENGTH_SHORT).show()
                }
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val file = File(picturesDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                Toast.makeText(ctx, getString(R.string.saved_to, file.absolutePath), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareBarcode() {
        val ctx = context ?: return
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cachePath = File(ctx.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "barcode.png")
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_qr)))
        } catch (e: Exception) {
            Toast.makeText(ctx, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateGeneratedBarcode(content: String, format: BarcodeFormat, bitmap: Bitmap) {
        validationJob?.cancel()
        val ctx = context ?: return
        validationJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val results = QRCodeScanner.scanSync(ctx, bitmap)
                val warning = when {
                    results.isEmpty() -> getString(R.string.warning_barcode_not_scannable)
                    !results.any { matchResult(content, format, it) } -> {
                        val scanned = results.firstOrNull()?.let { resultTextForFormat(format, it) } ?: ""
                        getString(R.string.warning_barcode_content_mismatch, content, scanned)
                    }
                    else -> null
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        binding.tvGenerationWarning.apply {
                            if (warning != null) {
                                text = warning
                                visibility = View.VISIBLE
                            } else {
                                visibility = View.GONE
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Validation failed", e)
            }
        }
    }

    private fun resultTextForFormat(format: BarcodeFormat, result: QRCodeScanner.ScanResult): String {
        return when (format) {
            BarcodeFormat.UPC_EAN_EXTENSION -> {
                result.resultMetadata?.get(com.google.zxing.ResultMetadataType.UPC_EAN_EXTENSION) as? String
                    ?: result.text
            }
            else -> result.text
        }
    }

    private fun matchResult(content: String, format: BarcodeFormat, result: QRCodeScanner.ScanResult): Boolean {
        return when (format) {
            BarcodeFormat.RSS_EXPANDED -> normalizeRss(content) == normalizeRss(result.text)
            BarcodeFormat.UPC_EAN_EXTENSION -> {
                val extension = result.resultMetadata?.get(com.google.zxing.ResultMetadataType.UPC_EAN_EXTENSION) as? String
                extension == content
            }
            else -> result.text == content
        }
    }

    private fun normalizeRss(text: String): String {
        return text.replace("[", "(").replace("]", ")")
    }

    private fun BarcodeFormat.toHistoryType(): HistoryType {
        return when (this) {
            BarcodeFormat.QR_CODE -> HistoryType.QR_CODE
            BarcodeFormat.DATA_MATRIX -> HistoryType.DATA_MATRIX
            BarcodeFormat.AZTEC -> HistoryType.AZTEC
            BarcodeFormat.PDF417 -> HistoryType.PDF417
            BarcodeFormat.RSS_14 -> HistoryType.RSS_14
            BarcodeFormat.RSS_EXPANDED -> HistoryType.RSS_EXPANDED
            BarcodeFormat.MAXICODE -> HistoryType.MAXICODE
            BarcodeFormat.MICRO_QR -> HistoryType.MICRO_QR
            BarcodeFormat.UPC_EAN_EXTENSION -> HistoryType.UPC_EAN_EXTENSION
            BarcodeFormat.PHARMACODE -> HistoryType.PHARMACODE
            BarcodeFormat.PLESSEY -> HistoryType.PLESSEY
            BarcodeFormat.MSI_PLESSEY -> HistoryType.MSI_PLESSEY
            BarcodeFormat.TELEPEN -> HistoryType.TELEPEN
            BarcodeFormat.HAN_XIN -> HistoryType.HAN_XIN
            else -> HistoryType.BARCODE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        validationJob?.cancel()
        _binding = null
    }
}
