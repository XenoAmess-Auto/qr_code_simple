package com.example.qrcodesimple

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.qrcodesimple.databinding.ActivityCameraScanBinding

class CameraScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanBinding

    companion object {
        private const val TAG = "CameraScanActivity"
        private const val REQUEST_CAMERA = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        } else {
            initCamera()
        }

        binding.btnStopScan.setOnClickListener {
            finish()
        }
    }

    private fun initCamera() {
        // Initialize WeChatQRCode if not already done
        if (!QRCodeApp.initWeChatQRCodeDetector(application)) {
            val errorMsg = QRCodeApp.initErrorMessage ?: "Unknown error"
            Toast.makeText(this, "QR library failed: $errorMsg", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // For simplicity, show a message about camera implementation
        // Full camera preview with real-time scanning requires additional setup
        Toast.makeText(this, "Camera scanning ready", Toast.LENGTH_SHORT).show()
        
        // Note: Full camera implementation would require:
        // 1. CameraX or Camera2 API integration
        // 2. Frame analysis with WeChatQRCodeDetector
        // 3. Surface for preview
        
        // Simplified implementation - just use image scanning for now
        Toast.makeText(this, "Please use 'Scan Image' for now", Toast.LENGTH_LONG).show()
        finish()
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
                    initCamera()
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
}
