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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.FragmentCameraScanBinding
import kotlinx.coroutines.launch
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraScanFragment : Fragment() {

    private var _binding: FragmentCameraScanBinding? = null
    private val binding get() = _binding!!
    private var cameraExecutor: ExecutorService? = null
    private lateinit var historyRepository: HistoryRepository
    private var isProcessing = false
    private var lastScanTime = 0L
    private val scanInterval = 300L
    private var lastDetectedContent: String? = null
    private var isCameraStarted = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "CameraScanFragment"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Permission granted, starting camera...")
            isCameraStarted = false
            startCameraInternal()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        historyRepository = HistoryRepository(requireContext())
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
        
        // 检查权限
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            // 延迟启动相机，确保 view 完全准备好
            startCameraWithDelay()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 如果相机还没启动，尝试启动
        if (!isCameraStarted && 
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCameraWithDelay()
        }
    }
    
    private fun startCameraWithDelay() {
        // 清除之前的延迟任务
        handler.removeCallbacksAndMessages(null)
        
        // 延迟启动，确保 PreviewView 准备好
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
                
                // 先解绑所有
                cameraProvider.unbindAll()
                
                // 构建 Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }
                
                // 构建 ImageAnalysis
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor!!) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }
                
                // 绑定到生命周期
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                
                Log.d(TAG, "Camera started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                isCameraStarted = false
                // 重试一次
                handler.postDelayed({
                    if (isAdded && _binding != null) {
                        startCameraInternal()
                    }
                }, 1000)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun setupButtons() {
        binding.btnCopyResult.setOnClickListener { copyResult() }
        binding.btnShareResult.setOnClickListener { shareResult() }
        binding.resultCard.setOnClickListener { copyResult() }
        binding.btnCloseResult.setOnClickListener { hideResult() }
    }

    private fun shareResult() {
        val text = binding.tvResult.text.toString()
        if (text.isNotBlank() && text != "Scanning...") {
            startActivity(Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share QR Code"))
        }
    }

    private fun copyResult() {
        val text = binding.tvResult.text.toString()
        if (text.isNotBlank() && text != "Scanning...") {
            (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("QR Code", text))
            Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideResult() {
        binding.resultCard.visibility = View.GONE
    }

    private fun showResult(result: String) {
        if (!isAdded) return
        activity?.runOnUiThread {
            binding.tvResult.text = result
            binding.resultCard.visibility = View.VISIBLE
            
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
        _binding = null
    }
}