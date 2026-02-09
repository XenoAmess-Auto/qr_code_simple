package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.xenoamess.qrcodesimple.databinding.ActivityCameraScanBinding
import com.king.wechat.qrcode.WeChatQRCodeDetector
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanBinding
    private var imageAnalysis: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessing = false
    private var lastScanTime = 0L
    private val scanInterval = 500L // 扫描间隔 500ms

    companion object {
        private const val TAG = "CameraScanActivity"
        private const val REQUEST_CAMERA = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // 确保 WeChatQRCode 已初始化（启动时已预加载，这里做二次确认）
        if (!QRCodeApp.ensureInitialized(application)) {
            val errorMsg = QRCodeApp.initErrorMessage ?: "Unknown error"
            Toast.makeText(this, "QR library failed: $errorMsg", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 检查并请求相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else {
            startCamera()
        }

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnStopScan.setOnClickListener {
            finish()
        }

        binding.btnCopyResult.setOnClickListener {
            copyResult()
        }

        binding.resultCard.setOnClickListener {
            copyResult()
        }

        binding.btnCloseResult.setOnClickListener {
            hideResult()
        }
    }

    private fun copyResult() {
        val text = binding.tvResult.text.toString()
        if (text.isNotBlank() && text != "Scanning...") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", text))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideResult() {
        binding.resultCard.visibility = View.GONE
    }

    private fun showResult(result: String) {
        runOnUiThread {
            binding.tvResult.text = result
            binding.resultCard.visibility = View.VISIBLE
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 预览
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            // 图像分析
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            // 选择后置相机
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解绑所有用例
                cameraProvider.unbindAll()

                // 绑定用例
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // 控制扫描频率
        if (isProcessing || currentTime - lastScanTime < scanInterval) {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastScanTime = currentTime

        try {
            // 获取图像缓冲区
            val buffer = imageProxy.planes[0].buffer
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

            // 创建 Mat
            val mat = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
            mat.put(0, 0, nv21)

            // 转换为 RGB
            val rgbMat = Mat()
            org.opencv.imgproc.Imgproc.cvtColor(mat, rgbMat, org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_NV21)

            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(rgbMat.cols(), rgbMat.rows(), Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(rgbMat, bitmap)

            // 释放 Mat
            mat.release()
            rgbMat.release()

            // 检测二维码
            val points = ArrayList<Mat>()
            val results = WeChatQRCodeDetector.detectAndDecode(bitmap, points)

            if (results.isNotEmpty()) {
                val result = results[0]
                Log.d(TAG, "QR Code detected: $result")
                showResult(result)
            }

            // 释放资源
            points.forEach { it.release() }
            bitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            isProcessing = false
            imageProxy.close()
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startCamera()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
