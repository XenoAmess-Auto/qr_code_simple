package com.example.qrcodesimple

import android.app.Application
import android.util.Log
import com.king.wechat.qrcode.WeChatQRCodeDetector

class QRCodeApp : Application() {

    companion object {
        private const val TAG = "QRCodeApp"
        @Volatile
        var isWeChatQRCodeInitialized = false
            private set
        
        @Volatile
        var initErrorMessage: String? = null
            private set

        fun initWeChatQRCodeDetector(app: Application): Boolean {
            if (isWeChatQRCodeInitialized) return true

            return try {
                Log.d(TAG, "Starting WeChatQRCodeDetector initialization...")

                // 先加载 OpenCV native 库
                System.loadLibrary("opencv_java4")
                Log.d(TAG, "OpenCV native library loaded")

                WeChatQRCodeDetector.init(app)
                isWeChatQRCodeInitialized = true
                initErrorMessage = null
                Log.i(TAG, "WeChatQRCodeDetector initialized successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                initErrorMessage = "Native library load failed: ${e.message}"
                Log.e(TAG, "Native library load failed", e)
                false
            } catch (e: Exception) {
                initErrorMessage = "Initialization failed: ${e.message}"
                Log.e(TAG, "Failed to initialize WeChatQRCodeDetector", e)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 延迟初始化，放到实际使用时再进行
        // initWeChatQRCodeDetector(this)
    }
}
