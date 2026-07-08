package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentGenerateBinding
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.model.AspectRatio
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
    internal var currentBitmap: Bitmap? = null
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
    private var moduleShape = AdvancedBarcodeGenerator.ModuleShape.SQUARE
    private var moduleFillRatio = 0.8f
    private var positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.SQUARE
    private var gradientAngle = 0f
    private var gradientStops = mutableListOf<AdvancedBarcodeGenerator.ColorStop>()
    private var gradientEnabled = false
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
            binding.logoScaleSection.visibility = View.VISIBLE
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
            val free = AspectRatio(getString(R.string.crop_ratio_free), 0f, 0f)
            val square = AspectRatio(getString(R.string.crop_ratio_square), 1f, 1f)
            val ratio4_3 = AspectRatio(getString(R.string.crop_ratio_4_3), 4f, 3f)
            val ratio3_4 = AspectRatio(getString(R.string.crop_ratio_3_4), 3f, 4f)
            val ratio16_9 = AspectRatio(getString(R.string.crop_ratio_16_9), 16f, 9f)
            val ratio9_16 = AspectRatio(getString(R.string.crop_ratio_9_16), 9f, 16f)

            val options = UCrop.Options().apply {
                setFreeStyleCropEnabled(true)
                setAspectRatioOptions(
                    0,
                    square,
                    free,
                    ratio4_3,
                    ratio3_4,
                    ratio16_9,
                    ratio9_16
                )
                setAllowedGestures(
                    UCropActivity.ALL,
                    UCropActivity.ALL,
                    UCropActivity.ALL
                )
                setShowCropFrame(true)
                setShowCropGrid(true)
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
        // ECL 纠错等级切换
        binding.toggleEcLevel.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val ecLevel = when (checkedId) {
                R.id.btnEcL -> ErrorCorrectionLevel.L
                R.id.btnEcM -> ErrorCorrectionLevel.M
                R.id.btnEcQ -> ErrorCorrectionLevel.Q
                else -> ErrorCorrectionLevel.H
            }
            selectedStyle = selectedStyle.copy(ecLevel = ecLevel)
            generateBarcode()
        }

        // 双色方案按钮：外圈=背景色，中心圆=前景色
        buildSchemeButtons()

        binding.seekBarCornerRadius.addOnChangeListener { _, value, _ ->
            cornerRadius = value / 100f
            binding.tvCornerRadiusValue.text = "${value.toInt()}%"
        }
        binding.seekBarCornerRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { generateBarcode() }
        })

        // 模块形状
        binding.chipGroupModuleShape.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            moduleShape = when (checkedIds.first()) {
                R.id.chipModuleSquare -> AdvancedBarcodeGenerator.ModuleShape.SQUARE
                R.id.chipModuleCircle -> AdvancedBarcodeGenerator.ModuleShape.CIRCLE
                R.id.chipModuleRounded -> AdvancedBarcodeGenerator.ModuleShape.ROUNDED
                else -> AdvancedBarcodeGenerator.ModuleShape.SQUARE
            }
            generateBarcode()
        }

        // 点填充比例
        binding.seekBarModuleFillRatio.addOnChangeListener { _, value, _ ->
            moduleFillRatio = value / 100f
            binding.tvModuleFillRatioValue.text = "${value.toInt()}%"
        }
        binding.seekBarModuleFillRatio.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { generateBarcode() }
        })

        // 定位点形状
        binding.chipGroupPositionPattern.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            positionPatternShape = when (checkedIds.first()) {
                R.id.chipPositionSquare -> AdvancedBarcodeGenerator.PositionPatternShape.SQUARE
                R.id.chipPositionCircle -> AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE
                R.id.chipPositionFollow -> AdvancedBarcodeGenerator.PositionPatternShape.FOLLOW_MODULE
                else -> AdvancedBarcodeGenerator.PositionPatternShape.SQUARE
            }
            generateBarcode()
        }

        // 渐变开关
        binding.switchGradient.setOnCheckedChangeListener { _, isChecked ->
            gradientEnabled = isChecked
            if (isChecked && gradientStops.size < 2) {
                gradientStops.addAll(listOf(
                    AdvancedBarcodeGenerator.ColorStop(0f, selectedStyle.foregroundColor),
                    AdvancedBarcodeGenerator.ColorStop(1f, selectedStyle.backgroundColor)
                ))
            }
            updateGradientControlsVisibility()
            buildGradientStopViews()
            updateGradientPreview()
            generateBarcode()
        }

        // 渐变方向
        binding.angleDial.onAngleChanged = { degrees ->
            gradientAngle = degrees
            binding.seekBarGradientAngle.value = degrees
            binding.tvGradientAngleValue.text = "${degrees.toInt()}°"
            generateBarcode()
        }
        binding.seekBarGradientAngle.addOnChangeListener { _, value, _ ->
            gradientAngle = value
            binding.angleDial.angle = value
            binding.tvGradientAngleValue.text = "${value.toInt()}°"
        }
        binding.seekBarGradientAngle.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { generateBarcode() }
        })

        // 添加渐变节点
        binding.btnAddGradientStop.setOnClickListener {
            if (gradientStops.size >= 5) return@setOnClickListener
            if (gradientStops.size < 2) {
                gradientStops.addAll(listOf(
                    AdvancedBarcodeGenerator.ColorStop(0f, selectedStyle.foregroundColor),
                    AdvancedBarcodeGenerator.ColorStop(1f, selectedStyle.backgroundColor)
                ))
            } else {
                var maxGap = 0f
                var insertPos = 0.5f
                var startColor = selectedStyle.foregroundColor
                var endColor = selectedStyle.backgroundColor
                for (i in 0 until gradientStops.size - 1) {
                    val gap = gradientStops[i + 1].position - gradientStops[i].position
                    if (gap > maxGap) {
                        maxGap = gap
                        insertPos = (gradientStops[i].position + gradientStops[i + 1].position) / 2f
                        startColor = gradientStops[i].color
                        endColor = gradientStops[i + 1].color
                    }
                }
                val color = AdvancedBarcodeGenerator.interpolateColor(startColor, endColor, 0.5f)
                gradientStops.add(AdvancedBarcodeGenerator.ColorStop(insertPos, color))
                gradientStops.sortBy { it.position }
            }
            buildGradientStopViews()
            updateGradientPreview()
            binding.btnAddGradientStop.isEnabled = gradientStops.size < 5
            generateBarcode()
        }

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
        updateStyleControlUIs()
        binding.seekBarLogoScale.value = logoScale * 100f
        binding.tvLogoScaleValue.text = "${(logoScale * 100).toInt()}%"

        binding.btnAddLogo.setOnClickListener {
            pickLogoLauncher.launch("image/*")
        }

        binding.btnRemoveLogo.setOnClickListener {
            logoBitmap = null
            binding.ivLogoPreview.setImageBitmap(null)
            binding.ivLogoPreview.visibility = View.GONE
            binding.logoScaleSection.visibility = View.GONE
            generateBarcode()
        }
    }

    private fun buildSchemeButtons() {
        val schemes = listOf(
            AdvancedBarcodeGenerator.ColorSchemes.CLASSIC,
            AdvancedBarcodeGenerator.ColorSchemes.BLUE,
            AdvancedBarcodeGenerator.ColorSchemes.GREEN,
            AdvancedBarcodeGenerator.ColorSchemes.RED,
            AdvancedBarcodeGenerator.ColorSchemes.PURPLE,
            AdvancedBarcodeGenerator.ColorSchemes.ORANGE,
            AdvancedBarcodeGenerator.ColorSchemes.CYAN,
            AdvancedBarcodeGenerator.ColorSchemes.DARK,
            AdvancedBarcodeGenerator.ColorSchemes.QQ
        )
        val container = binding.schemeContainer
        container.removeAllViews()
        val size = (resources.displayMetrics.density * 48).toInt()
        val innerRadius = (resources.displayMetrics.density * 12).toInt()
        val margin = (resources.displayMetrics.density * 4).toInt()

        for (scheme in schemes) {
            val view = View(requireContext()).apply {
                layoutParams = FlexboxLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                background = createDonutDrawable(scheme, innerRadius)
                setOnClickListener { applyColorScheme(scheme) }
                isClickable = true
                isFocusable = true
            }
            container.addView(view)
        }
    }

    private fun createDonutDrawable(scheme: AdvancedBarcodeGenerator.StyleConfig, innerRadius: Int): android.graphics.drawable.Drawable {
        val size = innerRadius * 6
        val corner = (size * 0.2f)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scheme.backgroundColor }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), corner, corner, bgPaint)

        val innerMargin = size * 0.25f
        val innerRect = RectF(innerMargin, innerMargin, size - innerMargin, size - innerMargin)
        if (scheme.gradientStops.size >= 2) {
            val sorted = scheme.gradientStops.sortedBy { it.position }
            val gradient = android.graphics.LinearGradient(
                innerRect.left, (innerRect.top + innerRect.bottom) / 2f,
                innerRect.right, (innerRect.top + innerRect.bottom) / 2f,
                sorted.map { it.color }.toIntArray(),
                sorted.map { it.position }.toFloatArray(),
                android.graphics.Shader.TileMode.CLAMP
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
            canvas.drawRoundRect(innerRect, corner * 0.5f, corner * 0.5f, paint)
        } else {
            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scheme.foregroundColor }
            canvas.drawRoundRect(innerRect, corner * 0.5f, corner * 0.5f, fgPaint)
        }

        return android.graphics.drawable.BitmapDrawable(resources, bmp)
    }

    private fun applyColorScheme(scheme: AdvancedBarcodeGenerator.StyleConfig) {
        selectedStyle = scheme
        foregroundImageBitmap = null
        backgroundImageBitmap = null
        updateImagePreview(binding.viewFgImagePreview, null)
        updateImagePreview(binding.viewBgImagePreview, null)
        binding.btnRemoveForegroundImage.visibility = View.GONE
        binding.btnRemoveBackgroundImage.visibility = View.GONE

        moduleShape = scheme.moduleShape
        moduleFillRatio = scheme.moduleFillRatio
        positionPatternShape = scheme.positionPatternShape
        gradientAngle = scheme.gradientAngle
        gradientStops.clear()
        gradientStops.addAll(scheme.gradientStops)
        gradientEnabled = scheme.gradientStops.size >= 2

        updateColorPreviews()
        updateStyleControlUIs()
        generateBarcode()
    }

    private fun updateStyleControlUIs() {
        binding.chipGroupModuleShape.check(
            when (moduleShape) {
                AdvancedBarcodeGenerator.ModuleShape.SQUARE -> R.id.chipModuleSquare
                AdvancedBarcodeGenerator.ModuleShape.CIRCLE -> R.id.chipModuleCircle
                AdvancedBarcodeGenerator.ModuleShape.ROUNDED -> R.id.chipModuleRounded
            }
        )
        binding.seekBarModuleFillRatio.value = moduleFillRatio * 100f
        binding.tvModuleFillRatioValue.text = "${(moduleFillRatio * 100).toInt()}%"

        binding.chipGroupPositionPattern.check(
            when (positionPatternShape) {
                AdvancedBarcodeGenerator.PositionPatternShape.SQUARE -> R.id.chipPositionSquare
                AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE -> R.id.chipPositionCircle
                AdvancedBarcodeGenerator.PositionPatternShape.FOLLOW_MODULE -> R.id.chipPositionFollow
            }
        )

        binding.angleDial.angle = gradientAngle
        binding.seekBarGradientAngle.value = gradientAngle
        binding.tvGradientAngleValue.text = "${gradientAngle.toInt()}°"

        updateGradientControlsVisibility()
        buildGradientStopViews()
        updateGradientPreview()
        binding.btnAddGradientStop.isEnabled = gradientStops.size < 5
    }

    private fun updateGradientControlsVisibility() {
        binding.switchGradient.isChecked = gradientEnabled
        binding.gradientControlsContainer.visibility = if (gradientEnabled) View.VISIBLE else View.GONE
    }

    private fun buildGradientStopViews() {
        binding.gradientStopsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        for ((index, stop) in gradientStops.withIndex()) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt()) }
            }

            val colorView = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply {
                    marginEnd = (8 * density).toInt()
                }
                background = ColorDrawable(stop.color)
                setOnClickListener {
                    if (!isAdded) return@setOnClickListener
                    val tag = "gradient_stop_${index}_${System.currentTimeMillis()}"
                    ColorPickerDialog().apply {
                        setInitialColor(stop.color)
                        onColorSelected = { color ->
                            if (index < gradientStops.size) {
                                gradientStops[index] = stop.copy(color = color)
                                buildGradientStopViews()
                                updateGradientPreview()
                                generateBarcode()
                            }
                        }
                    }.show(parentFragmentManager, tag)
                }
            }

            val slider = Slider(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                valueFrom = 0f
                valueTo = 100f
                value = stop.position * 100f
                stepSize = 1f
                addOnChangeListener { _, value, _ ->
                    gradientStops[index] = stop.copy(position = value / 100f)
                    gradientStops.sortBy { it.position }
                    updateGradientPreview()
                }
                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {}
                    override fun onStopTrackingTouch(slider: Slider) {
                        buildGradientStopViews()
                        generateBarcode()
                    }
                })
            }

            val deleteBtn = MaterialButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply {
                    marginStart = (8 * density).toInt()
                }
                text = "×"
                textSize = 18f
                isEnabled = gradientStops.size > 2
                setOnClickListener {
                    if (gradientStops.size > 2) {
                        gradientStops.removeAt(index)
                        buildGradientStopViews()
                        updateGradientPreview()
                        binding.btnAddGradientStop.isEnabled = gradientStops.size < 5
                        generateBarcode()
                    }
                }
            }

            row.addView(colorView)
            row.addView(slider)
            row.addView(deleteBtn)
            binding.gradientStopsContainer.addView(row)
        }
    }

    private fun updateGradientPreview() {
        val sorted = gradientStops.sortedBy { it.position }
        if (sorted.size >= 2) {
            val drawable = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = sorted.map { it.color }.toIntArray()
            }
            binding.viewGradientPreview.background = drawable
        } else {
            binding.viewGradientPreview.setBackgroundColor(selectedStyle.foregroundColor)
        }
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
        if (content.isNullOrEmpty()) return

        // 每次重新生成前清除之前的告警/成功标记
        binding.tvGenerationWarning.apply {
            text = ""
            visibility = View.GONE
            background = null
        }

        val validation = BarcodeGenerator.validateContent(content, selectedFormat)
        if (!validation.isValid) {
            binding.tvGenerationWarning.apply {
                text = validation.errorMessage ?: getString(R.string.invalid_content_for_format)
                background = resources.getDrawable(R.drawable.bg_warning, null)
                setTextColor(resources.getColor(R.color.yellow_700, null))
                visibility = View.VISIBLE
            }
            return
        }

        try {
            val style = selectedStyle.copy(
                cornerRadius = cornerRadius,
                logoBitmap = logoBitmap,
                logoScale = logoScale,
                foregroundBitmap = foregroundImageBitmap,
                backgroundBitmap = backgroundImageBitmap,
                moduleShape = moduleShape,
                moduleFillRatio = moduleFillRatio,
                positionPatternShape = positionPatternShape,
                gradientAngle = gradientAngle,
                gradientStops = if (gradientEnabled) gradientStops.toList() else emptyList()
            )
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, selectedFormat, 800, style)
            if (bitmap == null) {
                Toast.makeText(ctx, getString(R.string.failed_to_generate, getString(R.string.unknown_error)), Toast.LENGTH_SHORT).show()
                return
            }
            currentBitmap = bitmap
            binding.ivQRCode.setImageBitmap(bitmap)
            if (selectedFormat.isScannable) {
                validateGeneratedBarcode(content, selectedFormat, bitmap)
            } else {
                binding.tvGenerationWarning.apply {
                    text = getString(R.string.warning_generate_only_format)
                    background = resources.getDrawable(R.drawable.bg_warning, null)
                    setTextColor(resources.getColor(R.color.yellow_700, null))
                    visibility = View.VISIBLE
                }
            }

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
                                background = resources.getDrawable(R.drawable.bg_warning, null)
                                setTextColor(resources.getColor(R.color.yellow_700, null))
                                visibility = View.VISIBLE
                            } else {
                                text = getString(R.string.warning_scan_success)
                                background = resources.getDrawable(R.drawable.bg_success, null)
                                setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                                visibility = View.VISIBLE
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
