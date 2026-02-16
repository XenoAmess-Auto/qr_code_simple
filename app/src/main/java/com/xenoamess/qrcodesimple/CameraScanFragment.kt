package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.xenoamess.qrcodesimple.ContentParser.ParsedContent
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentCameraScanBinding
import kotlinx.coroutines.launch
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraScanFragment : Fragment() {

    private var _binding: FragmentCameraScanBinding? = null
    private val binding get() = _binding!!
    private var cameraExecutor: ExecutorService? = null
    private lateinit var historyRepository: HistoryRepository
    private lateinit var contentActionHandler: ContentActionHandler
    private var isProcessing = false
    private var lastScanTime = 0L
    private val scanInterval = 300L
    private var lastDetectedContent: String? = null
    private var isCameraStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private var camera: Camera? = null
    private var currentZoom = 1f
    private var isFlashOn = false
    private var isBackCamera = true
    private var maxZoom = 10f
    private var currentParsedContent: ParsedContent? = null

    companion object {
        private const val TAG = "CameraScanFragment"
        private const val MIN_ZOOM = 1f
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Permission granted, starting camera...")
            isCameraStarted = false
            startCameraInternal()
        } else {
            Toast.makeText(requireContext(), getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        historyRepository = HistoryRepository(requireContext())
        contentActionHandler = ContentActionHandler(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupButtons()
        setupZoomControls()
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCameraWithDelay()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (!isCameraStarted && 
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCameraWithDelay()
        }
    }
    
    private fun setupZoomControls() {
        binding.zoomSlider.apply {
            valueFrom = MIN_ZOOM
            valueTo = maxZoom
            value = currentZoom

            addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    currentZoom = value
                    camera?.cameraControl?.setZoomRatio(currentZoom)
                }
            })
        }
    }

    private fun updateZoomSlider() {
        binding.zoomSlider.value = currentZoom.coerceIn(MIN_ZOOM, maxZoom)
    }
    
    private fun setupButtons() {
        binding.btnCopyResult.setOnClickListener { copyResult() }
        binding.btnShareResult.setOnClickListener { shareResult() }
        binding.resultCard.setOnClickListener { copyResult() }
        binding.btnCloseResult.setOnClickListener { hideResult() }
        binding.btnSmartAction.setOnClickListener { onSmartActionClick() }
        
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
    }
    
    private fun onSmartActionClick() {
        val parsedContent = currentParsedContent ?: return
        when (parsedContent) {
            is ParsedContent.Wifi -> connectToWifi(parsedContent)
            is ParsedContent.Url -> openUrl(parsedContent.url)
            is ParsedContent.Phone -> makePhoneCall(parsedContent.number)
            is ParsedContent.Email -> sendEmail(parsedContent)
            else -> { }
        }
    }
    
    private fun connectToWifi(wifi: ParsedContent.Wifi) {
        contentActionHandler.getActionButtons("WIFI:T:${wifi.encryption};S:${wifi.ssid};P:${wifi.password};;")
            .firstOrNull()?.onClick?.invoke()
    }
    
    private fun openUrl(url: String) {
        contentActionHandler.getActionButtons(url)
            .firstOrNull()?.onClick?.invoke()
    }
    
    private fun makePhoneCall(number: String) {
        contentActionHandler.getActionButtons("tel:$number")
            .firstOrNull()?.onClick?.invoke()
    }
    
    private fun sendEmail(email: ParsedContent.Email) {
        val mailtoUri = buildString {
            append("mailto:${email.address}")
            if (email.subject.isNotEmpty() || email.body.isNotEmpty()) {
                append("?")
                val params = mutableListOf<String>()
                if (email.subject.isNotEmpty()) {
                    params.add("subject=${email.subject}")
                }
                if (email.body.isNotEmpty()) {
                    params.add("body=${email.body}")
                }
                append(params.joinToString("&"))
            }
        }
        contentActionHandler.getActionButtons(mailtoUri)
            .firstOrNull()?.onClick?.invoke()
    }
    
    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        camera?.cameraControl?.enableTorch(isFlashOn)
        binding.btnFlash.setImageResource(
            if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
        Toast.makeText(requireContext(), 
            if (isFlashOn) getString(R.string.flash_on) else getString(R.string.flash_off), 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun switchCamera() {
        isBackCamera = !isBackCamera
        isCameraStarted = false
        isFlashOn = false
        binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
        startCameraWithDelay()
        Toast.makeText(requireContext(), 
            if (isBackCamera) getString(R.string.back_camera) else getString(R.string.front_camera), 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun startCameraWithDelay() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isAdded && _binding != null) {
                startCameraInternal()
            }
        }, 500)
    }
    
    private fun startCameraInternal() {
        if (isCameraStarted || _binding == null || !isAdded) {
            return
        }
        
        Log.d(TAG, "Starting camera...")
        isCameraStarted = true
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                if (_binding == null || !isAdded) {
                    isCameraStarted = false
                    return@addListener
                }
                
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                
                val cameraSelector = if (isBackCamera) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                
                // 检查是否有前置摄像头
                if (!isBackCamera && !cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    Toast.makeText(requireContext(), getString(R.string.front_camera_not_available), Toast.LENGTH_SHORT).show()
                    isBackCamera = true
                    isCameraStarted = false
                    return@addListener
                }
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }
                
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                // 获取最大缩放级别
                camera?.cameraInfo?.zoomState?.value?.maxZoomRatio?.let {
                    maxZoom = maxOf(it, MIN_ZOOM + 1f)
                    binding.zoomSlider.valueTo = maxZoom
                }
                
                camera?.cameraControl?.setZoomRatio(currentZoom)
                updateZoomSlider()
                
                // 恢复闪光灯状态
                if (isFlashOn && isBackCamera) {
                    camera?.cameraControl?.enableTorch(true)
                }
                
                Log.d(TAG, "Camera started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                isCameraStarted = false
                handler.postDelayed({
                    if (isAdded && _binding != null) {
                        startCameraInternal()
                    }
                }, 1000)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun shareResult() {
        val text = binding.tvResult.text.toString()
        if (text.isNotBlank() && text != getString(R.string.scanning)) {
            startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, getString(R.string.share_qr_code_content)))
        }
    }

    private fun copyResult() {
        val text = binding.tvResult.text.toString()
        if (text.isNotBlank() && text != getString(R.string.scanning)) {
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("QR Code", text))
            Toast.makeText(requireContext(), getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideResult() {
        binding.resultCard.visibility = View.GONE
        currentParsedContent = null
    }

    private fun showResult(result: String) {
        if (!isAdded) return
        activity?.runOnUiThread {
            binding.tvResult.text = result
            binding.resultCard.visibility = View.VISIBLE
            
            // 解析内容并更新智能操作按钮
            updateSmartActionButton(result)
            
            if (result != lastDetectedContent && result.isNotBlank()) {
                lastDetectedContent = result
                lifecycleScope.launch {
                    try {
                        historyRepository.insertScan(result, HistoryType.QR_CODE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save history", e)
                    }
                }
            }
        }
    }
    
    private fun updateSmartActionButton(content: String) {
        currentParsedContent = ContentParser.parse(content)
        
        val (iconResId, label, visible) = when (currentParsedContent) {
            is ParsedContent.Wifi -> Triple(
                R.drawable.ic_wifi, 
                getString(R.string.action_connect_wifi), 
                true
            )
            is ParsedContent.Url -> Triple(
                R.drawable.ic_open_in_browser, 
                getString(R.string.action_open_url), 
                true
            )
            is ParsedContent.Phone -> Triple(
                R.drawable.ic_phone, 
                getString(R.string.action_call), 
                true
            )
            is ParsedContent.Email -> Triple(
                R.drawable.ic_email, 
                getString(R.string.action_send_email), 
                true
            )
            is ParsedContent.GeoLocation -> Triple(
                R.drawable.ic_location, 
                getString(R.string.action_open_map), 
                true
            )
            is ParsedContent.Contact -> Triple(
                R.drawable.ic_contact, 
                getString(R.string.action_add_contact), 
                true
            )
            is ParsedContent.CalendarEvent -> Triple(
                R.drawable.ic_calendar, 
                getString(R.string.action_add_event), 
                true
            )
            is ParsedContent.SMS -> Triple(
                R.drawable.ic_sms,
                getString(R.string.action_send_sms),
                true
            )
            is ParsedContent.Text -> Triple(0, "", false)
            null -> Triple(0, "", false)
        }
        
        if (visible) {
            binding.btnSmartAction.visibility = View.VISIBLE
            binding.btnSmartAction.text = label
            binding.btnSmartAction.setIconResource(iconResId)
            
            // 显示内容类型标签
            binding.tvContentType.visibility = View.VISIBLE
            binding.tvContentType.text = contentActionHandler.getContentTypeLabel(content)
        } else {
            binding.btnSmartAction.visibility = View.GONE
            binding.tvContentType.visibility = View.GONE
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        if (isProcessing || currentTime - lastScanTime < scanInterval) {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastScanTime = currentTime

        try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val mat = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
            mat.put(0, 0, nv21)

            val rgbMat = Mat()
            org.opencv.imgproc.Imgproc.cvtColor(mat, rgbMat, org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_NV21)

            val bitmap = Bitmap.createBitmap(rgbMat.cols(), rgbMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(rgbMat, bitmap)

            mat.release()
            rgbMat.release()

            val results = QRCodeScanner.scanSync(requireContext(), bitmap)
            if (results.isNotEmpty()) {
                showResult(results[0])
            }

            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            isProcessing = false
            imageProxy.close()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        cameraExecutor?.shutdown()
        camera = null
        _binding = null
    }
}
