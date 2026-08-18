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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentGenerateBinding
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
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
    private lateinit var generateViewModel: GenerateViewModel
    internal val exportState: GenerateExportState
        get() = generateViewModel.exportState.value
    private lateinit var historyRepository: HistoryRepository
    internal var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE
    private var selectedStyle = AdvancedBarcodeGenerator.ColorSchemes.CLASSIC
    private var cornerRadius = 0f
    private var logoScale = 0.2f
    private var logoBitmap: Bitmap? = null
    private var logoShape = AdvancedBarcodeGenerator.LogoShape.SQUARE
    private var logoCornerRadius = 0.2f
    private var foregroundImageBitmap: Bitmap? = null
    private var backgroundImageBitmap: Bitmap? = null
    private var moduleShape = AdvancedBarcodeGenerator.ModuleShape.DEFAULT
    private var moduleFillRatio = 1.0f
    private var positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.DEFAULT
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
        // Exports use four times the preview pixels without making share latency excessive.
        private const val OUTPUT_SIZE = 1024
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
            updateLogoPreview()
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
        CropImageContract()
    ) { result ->
        val type = pendingImageType
        pendingImageType = null
        if (type == null) return@registerForActivityResult
        val resultUri = result.uriContent
        if (!result.isSuccessful || resultUri == null) return@registerForActivityResult
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
            val options = CropImageContractOptions(
                uri = sourceUri,
                cropImageOptions = CropImageOptions(
                    customOutputUri = destinationUri,
                    guidelines = CropImageView.Guidelines.ON,
                    fixAspectRatio = false,
                    activityTitle = getString(R.string.crop_image),
                    outputCompressQuality = 100
                )
            )
            cropLauncher.launch(options)
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
        generateViewModel = ViewModelProvider(this)[GenerateViewModel::class.java]
        observePreviewState()

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

        // 系统分享入口：ACTION_SEND text/plain 时把分享文本带入并直接生成
        handleShareTextPrefill()
        binding.etContent.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = generateBarcode()
        })
    }

    private fun observePreviewState() {
        viewLifecycleOwner.lifecycleScope.launch {
            generateViewModel.previewState.collectLatest { state ->
                if (_binding == null) return@collectLatest
                when (state) {
                    GeneratePreviewState.Empty -> Unit
                    is GeneratePreviewState.Loading -> showGenerationWarning(null)
                    is GeneratePreviewState.Invalid -> showGenerationWarning(state.message)
                    is GeneratePreviewState.Failed -> {
                        showGenerationWarning(getString(R.string.failed_to_generate, state.message ?: getString(R.string.unknown_error)))
                    }
                    is GeneratePreviewState.Ready -> {
                        replacePreviewBitmap(state.bitmap)
                        if (state.request.format.isScannable) {
                            validateGeneratedBarcode(state.request, state.bitmap)
                        } else {
                            showGenerationWarning(getString(R.string.warning_generate_only_format))
                        }
                        recordHistory(state.request)
                    }
                }
            }
        }
    }

    private fun replacePreviewBitmap(bitmap: Bitmap) {
        val old = currentBitmap
        currentBitmap = bitmap
        binding.ivQRCode.setImageBitmap(bitmap)
        if (old != null && old !== bitmap && !old.isRecycled) old.recycle()
        AnimationUtils.fadeIn(binding.ivQRCode)
    }

    private fun showGenerationWarning(message: String?) {
        binding.tvGenerationWarning.apply {
            if (message == null) {
                text = ""
                visibility = View.GONE
                background = null
            } else {
                text = message
                background = resources.getDrawable(R.drawable.bg_warning, null)
                setTextColor(resources.getColor(R.color.yellow_700, null))
                visibility = View.VISIBLE
            }
        }
    }

    private fun handleShareTextPrefill() {
        val intent = activity?.intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/") != true) return
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            Intent.ACTION_VIEW -> {
                // 深链：qr-code-simple://generate?text=...&format=...
                val uri = intent.data ?: return
                if (uri.scheme != "qr-code-simple" || uri.host != "generate") return
                uri.getQueryParameter("text")
            }
            else -> return
        }
        if (!text.isNullOrBlank()) {
            val format = (activity?.intent?.data)?.getQueryParameter("format")
            loadFromHistory(text, format?.let { BarcodeFormat.fromString(it) }, null)
        }
    }

    private var pendingFormatBeforeFocus: BarcodeFormat? = null

    private fun setupFormatSelector() {
        val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
        val recent = requireContext().getSharedPreferences("generate", 0)
            .getStringSet("recent_formats", emptySet()).orEmpty()
            .mapNotNull { name -> BarcodeFormat.entries.firstOrNull { it.name == name } }
        val adapter = BarcodeFormatAdapter(requireContext(), formats, recent)
        binding.spinnerFormat.setAdapter(adapter)
        binding.spinnerFormat.threshold = 0

        binding.spinnerFormat.doOnAttach {
            val existingFocusListener = binding.spinnerFormat.onFocusChangeListener
            binding.spinnerFormat.setOnFocusChangeListener { v, hasFocus ->
                existingFocusListener?.onFocusChange(v, hasFocus)
                safe {
                    if (hasFocus) {
                        pendingFormatBeforeFocus = selectedFormat
                        binding.spinnerFormat.setText("", false)
                        adapter.resetFilter()
                        binding.spinnerFormat.showDropDown()
                    } else {
                        val text = binding.spinnerFormat.text?.toString()?.trim() ?: ""
                        val matched = formats.find {
                            it.localizedName(requireContext()).equals(text, ignoreCase = true) ||
                                it.displayName.equals(text, ignoreCase = true) ||
                                it.name.equals(text, ignoreCase = true)
                        }
                        selectedFormat = matched ?: pendingFormatBeforeFocus ?: selectedFormat
                        pendingFormatBeforeFocus = null
                        binding.spinnerFormat.setText(selectedFormat.localizedNameWithEnglish(requireContext()), false)
                        adapter.resetFilter()
                        updateHintForFormat()
                        updateStyleControlsVisibility()
                        generateBarcode()
                    }
                }
            }
        }

        binding.spinnerFormat.setOnItemClickListener { _, _, position, _ ->
            safe {
                val format = adapter.getItem(position) ?: return@safe
                selectedFormat = format
                rememberFormat(format)
                pendingFormatBeforeFocus = null
                binding.spinnerFormat.setText(format.localizedNameWithEnglish(requireContext()), false)
                adapter.resetFilter()
                updateHintForFormat()
                updateStyleControlsVisibility()
                generateBarcode()
            }
        }

        binding.spinnerFormat.setText(selectedFormat.localizedNameWithEnglish(requireContext()), false)
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
                    R.id.chipModuleSquare -> AdvancedBarcodeGenerator.ModuleShape.DEFAULT
                    R.id.chipModuleCircle -> AdvancedBarcodeGenerator.ModuleShape.CIRCLE
                    R.id.chipModuleRounded -> AdvancedBarcodeGenerator.ModuleShape.ROUNDED
                    else -> AdvancedBarcodeGenerator.ModuleShape.DEFAULT
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
                    R.id.chipPositionSquare -> AdvancedBarcodeGenerator.PositionPatternShape.DEFAULT
                    R.id.chipPositionCircle -> AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE
                    R.id.chipPositionFollow -> AdvancedBarcodeGenerator.PositionPatternShape.FOLLOW_MODULE
                    else -> AdvancedBarcodeGenerator.PositionPatternShape.DEFAULT
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

        // logo 形状切换：切换即重生成；圆角矩形时显示半径滑杆
        binding.toggleLogoShape.check(R.id.btnLogoShapeSquare)
        binding.toggleLogoShape.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            safe {
                logoShape = when (checkedId) {
                    R.id.btnLogoShapeRounded -> AdvancedBarcodeGenerator.LogoShape.ROUNDED_RECT
                    R.id.btnLogoShapeCircle -> AdvancedBarcodeGenerator.LogoShape.CIRCLE
                    else -> AdvancedBarcodeGenerator.LogoShape.SQUARE
                }
                binding.logoCornerRadiusSection.visibility =
                    if (logoShape == AdvancedBarcodeGenerator.LogoShape.ROUNDED_RECT) View.VISIBLE else View.GONE
                updateLogoPreview()
                generateBarcode()
            }
        }

        binding.seekBarLogoCornerRadius.addOnChangeListener { _, value, _ ->
            safe {
                logoCornerRadius = value / 100f
                binding.tvLogoCornerRadiusValue.text = "${value.toInt()}%"
                updateLogoPreview()
            }
        }
        binding.seekBarLogoCornerRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
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
                binding.logoCornerRadiusSection.visibility = View.GONE
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
        logoShape = style.logoShape
        logoCornerRadius = style.logoCornerRadius
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
        binding.toggleLogoShape.check(
            when (logoShape) {
                AdvancedBarcodeGenerator.LogoShape.ROUNDED_RECT -> R.id.btnLogoShapeRounded
                AdvancedBarcodeGenerator.LogoShape.CIRCLE -> R.id.btnLogoShapeCircle
                else -> R.id.btnLogoShapeSquare
            }
        )
        binding.logoCornerRadiusSection.visibility =
            if (logoShape == AdvancedBarcodeGenerator.LogoShape.ROUNDED_RECT) View.VISIBLE else View.GONE
        binding.seekBarLogoCornerRadius.value = logoCornerRadius * 100f
        binding.tvLogoCornerRadiusValue.text = "${(logoCornerRadius * 100).toInt()}%"
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
                binding.spinnerFormat.setText(it.localizedNameWithEnglish(requireContext()), false)
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
                    AdvancedBarcodeGenerator.ModuleShape.DEFAULT -> R.id.chipModuleSquare
                    AdvancedBarcodeGenerator.ModuleShape.CIRCLE -> R.id.chipModuleCircle
                    AdvancedBarcodeGenerator.ModuleShape.ROUNDED -> R.id.chipModuleRounded
                }
            )
            binding.seekBarModuleFillRatio.value = moduleFillRatio * 100f
            binding.tvModuleFillRatioValue.text = "${(moduleFillRatio * 100).toInt()}%"

            binding.chipGroupPositionPattern.check(
                when (positionPatternShape) {
                    AdvancedBarcodeGenerator.PositionPatternShape.DEFAULT -> R.id.chipPositionSquare
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

    /** 按当前形状与半径更新 logo 预览（无 logo 时隐藏）。 */
    private fun updateLogoPreview() {
        val logo = logoBitmap
        if (logo == null) {
            updateImagePreview(binding.ivLogoPreview, null)
            return
        }
        val masked = AdvancedBarcodeGenerator.maskLogoToShape(logo, logoShape, logoCornerRadius)
        updateImagePreview(binding.ivLogoPreview, masked)
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
        binding.etContent.inputType = when (selectedFormat) {
            BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
            BarcodeFormat.ITF, BarcodeFormat.ITF_14, BarcodeFormat.CODE_2_OF_5_STANDARD,
            BarcodeFormat.CODE_2_OF_5_MATRIX, BarcodeFormat.CODE_2_OF_5_INDUSTRIAL,
            BarcodeFormat.CODE_2_OF_5_IATA, BarcodeFormat.CODE_2_OF_5_DATALOGIC,
            BarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_LEITCODE,
            BarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE -> android.text.InputType.TYPE_CLASS_NUMBER
            else -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
    }

    private fun rememberFormat(format: BarcodeFormat) {
        val prefs = requireContext().getSharedPreferences("generate", 0)
        val names = prefs.getStringSet("recent_formats", emptySet()).orEmpty().toMutableList()
        names.remove(format.name)
        names.add(0, format.name)
        prefs.edit().putStringSet("recent_formats", names.take(5).toSet()).apply()
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
            safe { showSaveFormatDialog() }
        }

        binding.btnShare.setOnClickListener {
            safe { showShareFormatDialog() }
        }

        binding.btnClear.setOnClickListener {
            safe {
                binding.etContent.text?.clear()
                generateViewModel.invalidate()
                clearPreviewBitmap()
            }
        }

        binding.btnBatchGenerate.setOnClickListener {
            startActivity(Intent(requireContext(), BatchGenerateActivity::class.java))
        }

        binding.btnContentWizard.setOnClickListener {
            showContentWizard()
        }
    }

    // ==================== 结构化内容向导 ====================

    internal enum class WizardType { WIFI, CONTACT, CALENDAR, EMAIL, SMS, PHONE, GEO, URL }

    private fun showContentWizard() {
        val ctx = context ?: return
        val labels = arrayOf(
            getString(R.string.wizard_wifi),
            getString(R.string.wizard_contact),
            getString(R.string.wizard_calendar),
            getString(R.string.wizard_email),
            getString(R.string.wizard_sms),
            getString(R.string.wizard_phone),
            getString(R.string.wizard_geo),
            getString(R.string.wizard_url)
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.content_wizard))
            .setItems(labels) { dialog, which ->
                showWizardForm(WizardType.entries[which])
                dialog.dismiss()
            }
            .show()
    }

    internal data class WizardField(
        val key: String,
        val labelRes: Int,
        val inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
        val defaultValue: String = ""
    )

    private fun wizardTextForm(
        titleRes: Int,
        fields: List<WizardField>,
        build: (Map<String, String>) -> String
    ) {
        val ctx = context ?: return
        val density = resources.displayMetrics.density
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (20 * density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        val inputs = mutableMapOf<String, android.widget.EditText>()
        for (field in fields) {
            val edit = android.widget.EditText(ctx).apply {
                hint = getString(field.labelRes)
                inputType = field.inputType
                if (field.defaultValue.isNotEmpty()) setText(field.defaultValue)
            }
            inputs[field.key] = edit
            container.addView(edit)
        }
        val scroll = android.widget.ScrollView(ctx).apply { addView(container) }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(titleRes))
            .setView(scroll)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                val values = inputs.mapValues { it.value.text?.toString()?.trim() ?: "" }
                binding.etContent.setText(build(values))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showWizardForm(type: WizardType) {
        when (type) {
            WizardType.WIFI -> wizardTextForm(
                R.string.wizard_wifi,
                listOf(
                    WizardField("ssid", R.string.field_ssid),
                    WizardField("password", R.string.webdav_password),
                    WizardField("encryption", R.string.field_encryption, defaultValue = "WPA")
                )
            ) { v -> ContentBuilder.wifi(v["ssid"] ?: "", v["password"] ?: "", v["encryption"] ?: "WPA") }

            WizardType.CONTACT -> wizardTextForm(
                R.string.wizard_contact,
                listOf(
                    WizardField("name", R.string.field_name),
                    WizardField("phone", R.string.field_phone, android.text.InputType.TYPE_CLASS_PHONE),
                    WizardField("email", R.string.field_email, android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                    WizardField("organization", R.string.field_organization),
                    WizardField("address", R.string.field_address)
                )
            ) { v ->
                ContentBuilder.contactVCard(
                    v["name"] ?: "", v["phone"] ?: "", v["email"] ?: "",
                    v["organization"] ?: "", v["address"] ?: ""
                )
            }

            WizardType.EMAIL -> wizardTextForm(
                R.string.wizard_email,
                listOf(
                    WizardField("address", R.string.field_email, android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
                    WizardField("subject", R.string.field_subject),
                    WizardField("body", R.string.field_body)
                )
            ) { v -> ContentBuilder.email(v["address"] ?: "", v["subject"] ?: "", v["body"] ?: "") }

            WizardType.SMS -> wizardTextForm(
                R.string.wizard_sms,
                listOf(
                    WizardField("number", R.string.field_number, android.text.InputType.TYPE_CLASS_PHONE),
                    WizardField("message", R.string.field_message)
                )
            ) { v -> ContentBuilder.sms(v["number"] ?: "", v["message"] ?: "") }

            WizardType.PHONE -> wizardTextForm(
                R.string.wizard_phone,
                listOf(WizardField("number", R.string.field_number, android.text.InputType.TYPE_CLASS_PHONE))
            ) { v -> ContentBuilder.phone(v["number"] ?: "") }

            WizardType.GEO -> wizardTextForm(
                R.string.wizard_geo,
                listOf(
                    WizardField("latitude", R.string.field_latitude, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED),
                    WizardField("longitude", R.string.field_longitude, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED),
                    WizardField("query", R.string.field_query)
                )
            ) { v ->
                ContentBuilder.geo(
                    v["latitude"]?.toDoubleOrNull() ?: 0.0,
                    v["longitude"]?.toDoubleOrNull() ?: 0.0,
                    v["query"] ?: ""
                )
            }

            WizardType.URL -> wizardTextForm(
                R.string.wizard_url,
                listOf(WizardField("url", R.string.field_url, android.text.InputType.TYPE_TEXT_VARIATION_URI))
            ) { v -> ContentBuilder.url(v["url"] ?: "") }

            WizardType.CALENDAR -> showCalendarWizardForm()
        }
    }

    private var wizardStartMillis = 0L
    private var wizardEndMillis = 0L

    private fun showCalendarWizardForm() {
        val ctx = context ?: return
        val density = resources.displayMetrics.density
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (20 * density).toInt()
            setPadding(p, p / 2, p, 0)
        }

        val etTitle = android.widget.EditText(ctx).apply { hint = getString(R.string.field_title) }
        val etLocation = android.widget.EditText(ctx).apply { hint = getString(R.string.field_location) }
        val etDescription = android.widget.EditText(ctx).apply { hint = getString(R.string.field_description) }

        val now = java.util.Calendar.getInstance()
        wizardStartMillis = now.timeInMillis
        wizardEndMillis = now.timeInMillis + 60L * 60 * 1000

        val timeFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val btnStart = android.widget.Button(ctx)
        val btnEnd = android.widget.Button(ctx)
        fun refreshTimeButtons() {
            btnStart.text = getString(R.string.field_start_time) + ": " + timeFormat.format(java.util.Date(wizardStartMillis))
            btnEnd.text = getString(R.string.field_end_time) + ": " + timeFormat.format(java.util.Date(wizardEndMillis))
        }
        refreshTimeButtons()
        btnStart.setOnClickListener { pickDateTime(true) { refreshTimeButtons() } }
        btnEnd.setOnClickListener { pickDateTime(false) { refreshTimeButtons() } }

        val checkAllDay = android.widget.CheckBox(ctx).apply { text = getString(R.string.field_all_day) }

        container.addView(etTitle)
        container.addView(etLocation)
        container.addView(etDescription)
        container.addView(btnStart)
        container.addView(btnEnd)
        container.addView(checkAllDay)
        val scroll = android.widget.ScrollView(ctx).apply { addView(container) }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.wizard_calendar))
            .setView(scroll)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                binding.etContent.setText(
                    ContentBuilder.calendarEvent(
                        etTitle.text?.toString()?.trim() ?: "",
                        etLocation.text?.toString()?.trim() ?: "",
                        etDescription.text?.toString()?.trim() ?: "",
                        wizardStartMillis,
                        wizardEndMillis.coerceAtLeast(wizardStartMillis),
                        checkAllDay.isChecked
                    )
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun pickDateTime(isStart: Boolean, onPicked: () -> Unit) {
        val ctx = context ?: return
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = if (isStart) wizardStartMillis else wizardEndMillis
        android.app.DatePickerDialog(
            ctx,
            { _, year, month, day ->
                cal.set(year, month, day)
                android.app.TimePickerDialog(
                    ctx,
                    { _, hour, minute ->
                        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
                        cal.set(java.util.Calendar.MINUTE, minute)
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                        if (isStart) wizardStartMillis = cal.timeInMillis else wizardEndMillis = cal.timeInMillis
                        onPicked()
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun buildCurrentStyleConfig(): AdvancedBarcodeGenerator.StyleConfig {
        return selectedStyle.copy(
            cornerRadius = cornerRadius,
            logoBitmap = logoBitmap,
            logoScale = logoScale,
            logoShape = logoShape,
            logoCornerRadius = logoCornerRadius,
            foregroundBitmap = foregroundImageBitmap,
            backgroundBitmap = backgroundImageBitmap,
            moduleShape = moduleShape,
            moduleFillRatio = moduleFillRatio,
            positionPatternShape = positionPatternShape,
            gradientAngle = gradientAngle,
            gradientStops = if (gradientEnabled) gradientStops.toList() else emptyList()
        )
    }

    private fun recordHistory(request: GenerateRequest) {
        val sanitizedStyle = AdvancedBarcodeGenerator.sanitize(request.style, request.format)
        val styleJson = sanitizedStyle.toJson()
        lifecycleScope.launch {
            try {
                historyRepository.insertGenerate(
                    request.content, request.format.toHistoryType(), request.format.name, styleJson
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save history", e)
            }
        }
    }

    private fun generateBarcode() {
        val content = binding.etContent.text?.toString()?.trim()
        if (content.isNullOrEmpty()) {
            generateViewModel.invalidate()
            clearPreviewBitmap()
            return
        }
        val request = GenerateRequest(content, selectedFormat, buildCurrentStyleConfig())

        val validation = BarcodeGenerator.validateContent(content, selectedFormat)
        if (!validation.isValid) {
            generateViewModel.invalidate()
            clearPreviewBitmap()
            showGenerationWarning(validation.errorMessage ?: getString(R.string.invalid_content_for_format))
            return
        }
        generateViewModel.preview(request)
    }

    private fun currentRequest(): GenerateRequest? {
        val content = binding.etContent.text?.toString()?.trim().orEmpty()
        if (content.isEmpty()) return null
        val request = GenerateRequest(content, selectedFormat, buildCurrentStyleConfig())
        val validation = BarcodeGenerator.validateContent(content, selectedFormat)
        if (!validation.isValid) {
            Toast.makeText(context, validation.errorMessage ?: getString(R.string.invalid_content_for_format), Toast.LENGTH_SHORT).show()
            return null
        }
        return request
    }

    private fun clearPreviewBitmap() {
        binding.ivQRCode.setImageBitmap(null)
        currentBitmap?.takeUnless { it.isRecycled }?.recycle()
        currentBitmap = null
    }

    private fun showSaveFormatDialog() {
        val ctx = context ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_options, null)
        val rbFormatPng = dialogView.findViewById<android.widget.RadioButton>(R.id.rbFormatPng)
        val rbFormatJpeg = dialogView.findViewById<android.widget.RadioButton>(R.id.rbFormatJpeg)
        val rbFormatWebp = dialogView.findViewById<android.widget.RadioButton>(R.id.rbFormatWebp)
        val rbFormatSvg = dialogView.findViewById<android.widget.RadioButton>(R.id.rbFormatSvg)
        val rbSize512 = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSize512)
        val rbSize2048 = dialogView.findViewById<android.widget.RadioButton>(R.id.rbSize2048)
        val tvSizeLabel = dialogView.findViewById<android.widget.TextView>(R.id.tvSizeLabel)
        val rgSize = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgSize)

        // SVG 是矢量格式，尺寸选择无意义；选中时隐藏尺寸区
        dialogView.findViewById<android.widget.RadioGroup>(R.id.rgFormat)
            .setOnCheckedChangeListener { _, checkedId ->
                val sizeVisible = if (checkedId == R.id.rbFormatSvg) View.GONE else View.VISIBLE
                tvSizeLabel.visibility = sizeVisible
                rgSize.visibility = sizeVisible
            }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.save_format_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val size = when {
                    rbSize512.isChecked -> 512
                    rbSize2048.isChecked -> 2048
                    else -> 1024
                }
                when {
                    rbFormatSvg.isChecked -> saveBarcodeAsSvg()
                    rbFormatJpeg.isChecked ->
                        saveRasterBarcode(size, Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
                    rbFormatWebp.isChecked ->
                        saveRasterBarcode(size, webpCompressFormat(), "image/webp", "webp")
                    else -> saveRasterBarcode(size, Bitmap.CompressFormat.PNG, "image/png", "png")
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun webpCompressFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private var pendingSvg: String? = null

    private val saveSvgLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        val ctx = context ?: return@registerForActivityResult
        val svg = pendingSvg
        pendingSvg = null
        if (uri == null || svg == null) return@registerForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(svg.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(ctx, getString(R.string.svg_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBarcodeAsSvg() {
        val ctx = context ?: return
        val request = currentRequest() ?: run {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val style = request.style
            val config = SvgQRCodeGenerator.SvgConfig(
                foregroundColor = String.format("#%06X", 0xFFFFFF and style.foregroundColor),
                backgroundColor = String.format("#%06X", 0xFFFFFF and style.backgroundColor)
            )
            pendingSvg = SvgQRCodeGenerator.generateSVG(request.content, request.format, config)
            recordHistory(request)
            saveSvgLauncher.launch(SvgQRCodeGenerator.generateFileName(request.content, request.format))
        } catch (e: Exception) {
            pendingSvg = null
            Toast.makeText(ctx, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRasterBarcode(
        size: Int,
        compressFormat: Bitmap.CompressFormat,
        mimeType: String,
        extension: String
    ) {
        val ctx = context ?: return
        val request = currentRequest() ?: run {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }
        val exportId = generateViewModel.beginExport()
        lifecycleScope.launch {
            val bitmap = generateOutputBitmap(request, size) ?: run {
                generateViewModel.failExport(exportId, null)
                return@launch
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val prefix = request.format.name.lowercase().replace("_", "")
            val fileName = "${prefix}_$timeStamp.$extension"
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                        }
                        ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { uri ->
                            ctx.contentResolver.openOutputStream(uri)?.use { bitmap.compress(compressFormat, 100, it) }
                        }?.toString()
                    } else {
                        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fileName)
                        FileOutputStream(file).use { bitmap.compress(compressFormat, 100, it) }
                        file.absolutePath
                    }
                }
                bitmap.recycle()
                recordHistory(request)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Toast.makeText(ctx, getString(R.string.saved_to_gallery, fileName), Toast.LENGTH_SHORT).show()
                } else if (savedPath != null) {
                    Toast.makeText(ctx, getString(R.string.saved_to, savedPath), Toast.LENGTH_SHORT).show()
                }
                generateViewModel.completeExport(exportId)
            } catch (e: Exception) {
                if (!bitmap.isRecycled) bitmap.recycle()
                generateViewModel.failExport(exportId, e.message)
                Toast.makeText(ctx, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showShareFormatDialog() {
        val ctx = context ?: return
        val items = arrayOf(
            getString(R.string.share_option_barcode_image),
            getString(R.string.share_option_card)
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.share))
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> shareBarcode()
                    1 -> shareBarcodeCard()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun shareBarcodeCard() {
        val ctx = context ?: return
        val request = currentRequest() ?: run {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }
        val exportId = generateViewModel.beginExport()
        lifecycleScope.launch {
            val bitmap = generateOutputBitmap(request) ?: run {
                generateViewModel.failExport(exportId, null)
                return@launch
            }
            val uri = withContext(Dispatchers.IO) {
                ShareTemplateGenerator.generateShareImage(ctx, bitmap, request.content, request.format.toHistoryType())
            }
            bitmap.recycle()
            if (uri == null) {
                generateViewModel.failExport(exportId, null)
                Toast.makeText(ctx, getString(R.string.failed_to_save, getString(R.string.unknown_error)), Toast.LENGTH_SHORT).show()
                return@launch
            }
            recordHistory(request)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_qr)))
            generateViewModel.completeExport(exportId)
        }
    }

    private fun shareBarcode() {
        val ctx = context ?: return
        val request = currentRequest() ?: run {
            Toast.makeText(ctx, getString(R.string.please_generate_qr_first), Toast.LENGTH_SHORT).show()
            return
        }
        val exportId = generateViewModel.beginExport()
        lifecycleScope.launch {
            val bitmap = generateOutputBitmap(request) ?: run {
                generateViewModel.failExport(exportId, null)
                return@launch
            }
            try {
                val file = withContext(Dispatchers.IO) {
                    val cachePath = File(ctx.cacheDir, "images")
                    cachePath.mkdirs()
                    File(cachePath, "barcode.png").also { output ->
                        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                bitmap.recycle()

                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    file
                )

                recordHistory(request)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_qr)))
                generateViewModel.completeExport(exportId)
            } catch (e: Exception) {
                if (!bitmap.isRecycled) bitmap.recycle()
                generateViewModel.failExport(exportId, e.message)
                Toast.makeText(ctx, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun generateOutputBitmap(request: GenerateRequest, size: Int = OUTPUT_SIZE): Bitmap? {
        val result = withContext(Dispatchers.Default) {
            runCatching {
                AdvancedBarcodeGenerator.generateStyled(
                    request.content, request.format, size, size,
                    AdvancedBarcodeGenerator.sanitize(request.style, request.format)
                )
            }
        }
        return result.getOrElse {
            Log.e(TAG, "Output generation failed", it)
            Toast.makeText(context, getString(R.string.failed_to_generate, it.message), Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun validateGeneratedBarcode(request: GenerateRequest, bitmap: Bitmap) {
        validationJob?.cancel()
        val ctx = context ?: return
        validationJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val results = QRCodeScanner.scanSync(ctx, bitmap)
                val warning = when {
                    results.isEmpty() -> getString(R.string.warning_barcode_not_scannable)
                    !results.any { matchResult(request.content, request.format, it) } -> {
                        val scanned = results.firstOrNull()?.let { resultTextForFormat(request.format, it) } ?: ""
                        getString(R.string.warning_barcode_content_mismatch, request.content, scanned)
                    }
                    else -> null
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null && currentBitmap === bitmap && currentRequest() == request) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        validationJob?.cancel()
        binding.ivQRCode.setImageBitmap(null)
        _binding = null
    }
}
