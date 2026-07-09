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
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

class GenerateFragment : Fragment() {

    private var _binding: FragmentGenerateBinding? = null
    private val binding get() = _binding!!
    internal var currentBitmap: Bitmap? = null
    private lateinit var historyRepository: HistoryRepository
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
    internal var gradientAngle = 0f
    private var gradientStops = mutableListOf<AdvancedBarcodeGenerator.ColorStop>()
    private var gradientEnabled = false
    internal var selectedScheme: AdvancedBarcodeGenerator.StyleConfig? = null
    private var validationJob: Job? = null
    private var pendingImageType: ImageType? = null
    private var updatingAngleFromCode = false

    private enum class ImageType {
        FOREGROUND, BACKGROUND
    }

    companion object {
        private const val TAG = "GenerateFragment"
        private const val MAX_LOGO_PX = 512
        private const val MAX_STYLE_IMAGE_PX = 1024
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "UI callback failed", e)
        }
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

        // 处理从历史详情页跳转回生成页的参数
        val activity = requireActivity() as? MainActivity
        if (activity != null) {
            val (content, format, styleJson) = activity.consumePendingGenerate()
            if (!content.isNullOrEmpty()) {
                loadFromHistory(content, format?.let { BarcodeFormat.fromString(it) }, styleJson)
            }
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
            safe {
                selectedFormat = formats[position]
                updateHintForFormat()
                updateStyleControlsVisibility()
                generateBarcode()
            }
        }

        updateStyleControlsVisibility()
    }

    private fun setupStyleControls() {
        // ECL 纠错等级切换
        binding.toggleEcLevel.addOnButtonCheckedListener { _, checkedId, isChecked ->
            safe {
                if (!isChecked) return@safe
                val ecLevel = when (checkedId) {
                    R.id.btnEcL -> ErrorCorrectionLevel.L
                    R.id.btnEcM -> ErrorCorrectionLevel.M
                    R.id.btnEcQ -> ErrorCorrectionLevel.Q
                    else -> ErrorCorrectionLevel.H
                }
                selectedStyle = selectedStyle.copy(ecLevel = ecLevel)
                generateBarcode()
            }
        }

        // 双色方案按钮：外圈=背景色，中心圆=前景色
        buildSchemeButtons()

        binding.seekBarCornerRadius.addOnChangeListener { _, value, _ ->
            safe {
                cornerRadius = value / 100f
                binding.tvCornerRadiusValue.text = "${value.toInt()}%"
            }
        }
        binding.seekBarCornerRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { safe { generateBarcode() } }
        })

        // 模块形状
        binding.chipGroupModuleShape.setOnCheckedStateChangeListener { _, checkedIds ->
            safe {
                if (checkedIds.isEmpty()) return@safe
                moduleShape = when (checkedIds.first()) {
                    R.id.chipModuleSquare -> AdvancedBarcodeGenerator.ModuleShape.SQUARE
                    R.id.chipModuleCircle -> AdvancedBarcodeGenerator.ModuleShape.CIRCLE
                    R.id.chipModuleRounded -> AdvancedBarcodeGenerator.ModuleShape.ROUNDED
                    else -> AdvancedBarcodeGenerator.ModuleShape.SQUARE
                }
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }

        // 点填充比例
        binding.seekBarModuleFillRatio.addOnChangeListener { _, value, _ ->
            safe {
                moduleFillRatio = value / 100f
                binding.tvModuleFillRatioValue.text = "${value.toInt()}%"
            }
        }
        binding.seekBarModuleFillRatio.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { safe { clearSchemeSelectionIfDiverged(); generateBarcode() } }
        })

        // 定位点形状
        binding.chipGroupPositionPattern.setOnCheckedStateChangeListener { _, checkedIds ->
            safe {
                if (checkedIds.isEmpty()) return@safe
                positionPatternShape = when (checkedIds.first()) {
                    R.id.chipPositionSquare -> AdvancedBarcodeGenerator.PositionPatternShape.SQUARE
                    R.id.chipPositionCircle -> AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE
                    R.id.chipPositionFollow -> AdvancedBarcodeGenerator.PositionPatternShape.FOLLOW_MODULE
                    else -> AdvancedBarcodeGenerator.PositionPatternShape.SQUARE
                }
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }

        // 渐变开关
        binding.switchGradient.setOnCheckedChangeListener { _, isChecked ->
            safe {
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
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }

        // 渐变方向
        binding.angleDial.onAngleChanged = { degrees ->
            safe {
                gradientAngle = degrees
                binding.seekBarGradientAngle.value = degrees
                binding.tvGradientAngleValue.text = "${degrees.toInt()}°"
                setGradientAngleEditText(degrees)
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }
        binding.seekBarGradientAngle.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            safe {
                gradientAngle = value
                binding.angleDial.angle = value
                binding.tvGradientAngleValue.text = "${value.toInt()}°"
                setGradientAngleEditText(value)
            }
        }
        binding.seekBarGradientAngle.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { safe { clearSchemeSelectionIfDiverged(); generateBarcode() } }
        })
        binding.etGradientAngle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (updatingAngleFromCode) return
                safe {
                    val text = s?.toString() ?: return@safe
                    if (text.isBlank()) return@safe
                    val value = text.toFloatOrNull() ?: return@safe
                    val degrees = value.coerceIn(0f, 360f)
                    if (degrees != gradientAngle) {
                        gradientAngle = degrees
                        binding.angleDial.angle = degrees
                        binding.seekBarGradientAngle.value = degrees
                        binding.tvGradientAngleValue.text = "${degrees.toInt()}°"
                        clearSchemeSelectionIfDiverged()
                        generateBarcode()
                    }
                }
            }
        })

        // 添加渐变节点
        binding.btnAddGradientStop.setOnClickListener {
            safe {
                if (gradientStops.size >= 5) return@safe
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
                            insertPos = sanitizePosition((gradientStops[i].position + gradientStops[i + 1].position) / 2f)
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
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }

        binding.seekBarLogoScale.addOnChangeListener { _, value, _ ->
            safe {
                logoScale = value / 100f
                binding.tvLogoScaleValue.text = "${value.toInt()}%"
            }
        }
        binding.seekBarLogoScale.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) { safe { generateBarcode() } }
        })

        binding.btnPickForegroundColor.setOnClickListener {
            safe {
                ColorPickerDialog().apply {
                    setInitialColor(selectedStyle.foregroundColor)
                    onColorSelected = { color ->
                        safe {
                            selectedStyle = selectedStyle.copy(foregroundColor = color)
                            foregroundImageBitmap = null
                            updateImagePreview(binding.viewFgImagePreview, null)
                            binding.btnRemoveForegroundImage.visibility = View.GONE
                            updateColorPreviews()
                            clearSchemeSelectionIfDiverged()
                            generateBarcode()
                        }
                    }
                }.show(parentFragmentManager, "fg_color")
            }
        }
        binding.btnPickBackgroundColor.setOnClickListener {
            safe {
                ColorPickerDialog().apply {
                    setInitialColor(selectedStyle.backgroundColor)
                    onColorSelected = { color ->
                        safe {
                            selectedStyle = selectedStyle.copy(backgroundColor = color)
                            backgroundImageBitmap = null
                            updateImagePreview(binding.viewBgImagePreview, null)
                            binding.btnRemoveBackgroundImage.visibility = View.GONE
                            updateColorPreviews()
                            clearSchemeSelectionIfDiverged()
                            generateBarcode()
                        }
                    }
                }.show(parentFragmentManager, "bg_color")
            }
        }

        binding.btnPickForegroundImage.setOnClickListener {
            safe { clearSchemeSelectionIfDiverged(); pickForegroundImageLauncher.launch("image/*") }
        }
        binding.btnRemoveForegroundImage.setOnClickListener {
            safe {
                foregroundImageBitmap = null
                selectedStyle = selectedStyle.copy(foregroundBitmap = null)
                updateImagePreview(binding.viewFgImagePreview, null)
                binding.btnRemoveForegroundImage.visibility = View.GONE
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }
        binding.btnPickBackgroundImage.setOnClickListener {
            safe { clearSchemeSelectionIfDiverged(); pickBackgroundImageLauncher.launch("image/*") }
        }
        binding.btnRemoveBackgroundImage.setOnClickListener {
            safe {
                backgroundImageBitmap = null
                selectedStyle = selectedStyle.copy(backgroundBitmap = null)
                updateImagePreview(binding.viewBgImagePreview, null)
                binding.btnRemoveBackgroundImage.visibility = View.GONE
                clearSchemeSelectionIfDiverged()
                generateBarcode()
            }
        }

        updateColorPreviews()
        updateStyleControlUIs()
        binding.seekBarLogoScale.value = logoScale * 100f
        binding.tvLogoScaleValue.text = "${(logoScale * 100).toInt()}%"

        binding.btnAddLogo.setOnClickListener {
            safe { pickLogoLauncher.launch("image/*") }
        }

        binding.btnRemoveLogo.setOnClickListener {
            safe {
                logoBitmap = null
                binding.ivLogoPreview.setImageBitmap(null)
                binding.ivLogoPreview.visibility = View.GONE
                binding.logoScaleSection.visibility = View.GONE
                generateBarcode()
            }
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
        val borderPadding = (resources.displayMetrics.density * 3).toInt()
        val cornerRadius = resources.displayMetrics.density * 8

        for (scheme in schemes) {
            val isSelected = scheme == selectedScheme
            val schemeView = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(size, size)
                background = createDonutDrawable(scheme, innerRadius)
                setOnClickListener { safe { applyColorScheme(scheme) } }
                isClickable = true
                isFocusable = true
            }
            val wrapper = FrameLayout(requireContext()).apply {
                layoutParams = FlexboxLayout.LayoutParams(size + borderPadding * 2, size + borderPadding * 2).apply {
                    setMargins(margin, margin, margin, margin)
                }
                setPadding(borderPadding, borderPadding, borderPadding, borderPadding)
                background = if (isSelected) createSchemeBorderDrawable(cornerRadius) else null
                addView(schemeView)
            }
            container.addView(wrapper)
        }
    }

    private fun createSchemeBorderDrawable(cornerRadius: Float): android.graphics.drawable.Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            setStroke((resources.displayMetrics.density * 3).toInt(), Color.parseColor("#FFD700"))
            setColor(Color.TRANSPARENT)
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

    private fun applyStyleConfig(style: AdvancedBarcodeGenerator.StyleConfig) {
        selectedStyle = style
        selectedScheme = null
        foregroundImageBitmap = null
        backgroundImageBitmap = null
        logoBitmap = null
        cornerRadius = style.cornerRadius
        logoScale = style.logoScale
        moduleShape = style.moduleShape
        moduleFillRatio = style.moduleFillRatio
        positionPatternShape = style.positionPatternShape
        gradientAngle = style.gradientAngle
        gradientStops.clear()
        gradientStops.addAll(style.gradientStops.map { AdvancedBarcodeGenerator.ColorStop(sanitizePosition(it.position), it.color) })
        gradientEnabled = style.gradientStops.size >= 2

        updateImagePreview(binding.viewFgImagePreview, null)
        updateImagePreview(binding.viewBgImagePreview, null)
        updateImagePreview(binding.ivLogoPreview, null)
        binding.btnRemoveForegroundImage.visibility = View.GONE
        binding.btnRemoveBackgroundImage.visibility = View.GONE
        binding.logoScaleSection.visibility = View.GONE

        updateColorPreviews()
        updateStyleControlUIs()
        binding.seekBarLogoScale.value = logoScale * 100f
        binding.tvLogoScaleValue.text = "${(logoScale * 100).toInt()}%"
        updateHintForFormat()
    }

    fun loadFromHistory(content: String?, format: BarcodeFormat?, styleJson: String?) {
        if (content.isNullOrEmpty() || !isAdded) return
        binding.etContent.setText(content)
        binding.etContent.setSelection(binding.etContent.text?.length ?: 0)
        format?.let {
            selectedFormat = it
            val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
            val position = formats.indexOf(it)
            if (position >= 0) {
                binding.spinnerFormat.setText(formats[position].displayName, false)
            }
            updateStyleControlsVisibility()
        }
        val style = styleJson?.let { styleConfigFromJson(it) }
        if (style != null) {
            applyStyleConfig(style)
        } else {
            applyColorScheme(AdvancedBarcodeGenerator.ColorSchemes.CLASSIC)
        }
        generateBarcode()
    }

    private fun applyColorScheme(scheme: AdvancedBarcodeGenerator.StyleConfig) {
        selectedScheme = scheme
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
        gradientStops.addAll(scheme.gradientStops.map { AdvancedBarcodeGenerator.ColorStop(sanitizePosition(it.position), it.color) })
        gradientEnabled = scheme.gradientStops.size >= 2

        updateColorPreviews()
        updateStyleControlUIs()
        generateBarcode()
    }

    private fun setGradientAngleEditText(value: Float) {
        updatingAngleFromCode = true
        binding.etGradientAngle.setText(value.toInt().toString())
        binding.etGradientAngle.setSelection(binding.etGradientAngle.text?.length ?: 0)
        updatingAngleFromCode = false
    }

    private fun updateStyleControlUIs() {
        safe {
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
            setGradientAngleEditText(gradientAngle)

            updateGradientControlsVisibility()
            buildGradientStopViews()
            updateGradientPreview()
            buildSchemeButtons()
            binding.btnAddGradientStop.isEnabled = gradientStops.size < 5
        }
    }

    private fun clearSchemeSelectionIfDiverged() {
        val scheme = selectedScheme ?: return
        if (!matchesSelectedScheme(scheme)) {
            selectedScheme = null
            buildSchemeButtons()
        }
    }

    private fun matchesSelectedScheme(scheme: AdvancedBarcodeGenerator.StyleConfig): Boolean {
        if (scheme.foregroundColor != selectedStyle.foregroundColor) return false
        if (scheme.backgroundColor != selectedStyle.backgroundColor) return false
        if (scheme.moduleShape != moduleShape) return false
        if ((scheme.moduleFillRatio * 100).roundToInt() != (moduleFillRatio * 100).roundToInt()) return false
        if (scheme.positionPatternShape != positionPatternShape) return false
        if (scheme.gradientAngle.roundToInt() != gradientAngle.roundToInt()) return false
        val schemeGradientEnabled = scheme.gradientStops.size >= 2
        if (schemeGradientEnabled != gradientEnabled) return false
        if (schemeGradientEnabled) {
            return sameGradientStops(scheme.gradientStops, gradientStops)
        }
        return true
    }

    private fun sameGradientStops(a: List<AdvancedBarcodeGenerator.ColorStop>, b: List<AdvancedBarcodeGenerator.ColorStop>): Boolean {
        if (a.size != b.size) return false
        val sortedA = a.sortedBy { it.position }
        val sortedB = b.sortedBy { it.position }
        for (i in sortedA.indices) {
            if ((sortedA[i].position * 100).roundToInt() != (sortedB[i].position * 100).roundToInt()) return false
            if (sortedA[i].color != sortedB[i].color) return false
        }
        return true
    }

    private fun updateGradientControlsVisibility() {
        binding.switchGradient.isChecked = gradientEnabled
        binding.gradientControlsContainer.visibility = if (gradientEnabled) View.VISIBLE else View.GONE
    }

    private fun sanitizePosition(position: Float): Float {
        return position.coerceIn(0f, 1f).times(100).roundToInt().div(100f)
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
                    safe {
                        if (!isAdded) return@safe
                        val tag = "gradient_stop_${index}_${System.currentTimeMillis()}"
                        ColorPickerDialog().apply {
                            setInitialColor(stop.color)
                            onColorSelected = { color ->
                                safe {
                                    if (index < gradientStops.size) {
                                        gradientStops[index] = stop.copy(color = color)
                                        buildGradientStopViews()
                                        updateGradientPreview()
                                        clearSchemeSelectionIfDiverged()
                                        generateBarcode()
                                    }
                                }
                            }
                        }.show(parentFragmentManager, tag)
                    }
                }
            }

            val slider = Slider(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                valueFrom = 0f
                valueTo = 100f
                value = sanitizePosition(stop.position) * 100f
                stepSize = 1f
                addOnChangeListener { _, value, _ ->
                    safe {
                        if (index >= gradientStops.size) return@safe
                        gradientStops[index] = stop.copy(position = sanitizePosition(value / 100f))
                        updateGradientPreview()
                        clearSchemeSelectionIfDiverged()
                    }
                }
                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {}
                    override fun onStopTrackingTouch(slider: Slider) {
                        slider.post {
                            safe {
                                gradientStops.sortBy { it.position }
                                buildGradientStopViews()
                                clearSchemeSelectionIfDiverged()
                                generateBarcode()
                            }
                        }
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
                    safe {
                        if (gradientStops.size > 2) {
                            gradientStops.removeAt(index)
                            buildGradientStopViews()
                            updateGradientPreview()
                            binding.btnAddGradientStop.isEnabled = gradientStops.size < 5
                            clearSchemeSelectionIfDiverged()
                            generateBarcode()
                        }
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

    private fun updateStyleControlsVisibility() {
        val capabilities = AdvancedBarcodeGenerator.FormatStyleCapabilities.forFormat(selectedFormat)

        val ecVisibility = if (capabilities.ecLevel) View.VISIBLE else View.GONE
        binding.tvEcLevelLabel.visibility = ecVisibility
        binding.toggleEcLevel.visibility = ecVisibility

        val moduleShapeVisibility = if (capabilities.moduleShape) View.VISIBLE else View.GONE
        binding.tvModuleShapeLabel.visibility = moduleShapeVisibility
        binding.chipGroupModuleShape.visibility = moduleShapeVisibility

        val moduleFillRatioVisibility = if (capabilities.moduleFillRatio) View.VISIBLE else View.GONE
        binding.tvModuleFillRatioLabel.visibility = moduleFillRatioVisibility
        binding.seekBarModuleFillRatio.visibility = moduleFillRatioVisibility
        binding.tvModuleFillRatioValue.visibility = moduleFillRatioVisibility

        val positionPatternVisibility = if (capabilities.positionPatternShape) View.VISIBLE else View.GONE
        binding.tvPositionPatternShapeLabel.visibility = positionPatternVisibility
        binding.chipGroupPositionPattern.visibility = positionPatternVisibility
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
            safe { generateBarcode() }
        }

        binding.btnSave.setOnClickListener {
            safe { saveBarcode() }
        }

        binding.btnShare.setOnClickListener {
            safe { shareBarcode() }
        }

        binding.btnClear.setOnClickListener {
            safe {
                binding.etContent.text?.clear()
                currentBitmap = null
                binding.ivQRCode.setImageBitmap(null)
            }
        }
    }

    private fun buildCurrentStyleConfig(): AdvancedBarcodeGenerator.StyleConfig {
        return selectedStyle.copy(
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
    }

    private fun recordHistory() {
        val content = binding.etContent.text?.toString()?.trim() ?: return
        if (content.isEmpty()) return
        val style = buildCurrentStyleConfig()
        val sanitizedStyle = AdvancedBarcodeGenerator.sanitize(style, selectedFormat)
        val styleJson = sanitizedStyle.toJson()
        lifecycleScope.launch {
            try {
                historyRepository.insertGenerate(content, selectedFormat.toHistoryType(), selectedFormat.name, styleJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save history", e)
            }
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
            val style = buildCurrentStyleConfig()
            val capabilities = AdvancedBarcodeGenerator.FormatStyleCapabilities.forFormat(selectedFormat)
            val sanitizedStyle = AdvancedBarcodeGenerator.sanitize(style, selectedFormat)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, selectedFormat, 800, sanitizedStyle, capabilities)
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

            recordHistory()
        } catch (e: Exception) {
            Log.e(TAG, "generateBarcode failed", e)
            Toast.makeText(ctx, getString(R.string.failed_to_generate, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBarcode() {
        val ctx = context ?: return
        if (currentBitmap == null) {
            generateBarcode()
        }
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }
        recordHistory()

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
        if (currentBitmap == null) {
            generateBarcode()
        }
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }
        recordHistory()

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
