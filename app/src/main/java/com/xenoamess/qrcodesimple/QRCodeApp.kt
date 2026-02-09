package com.xenoamess.qrcodesimple

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

        /**
         * 检查库是否已初始化，未初始化则尝试初始化
         * 用于 Activity 在需要时检查状态
         */
        fun ensureInitialized(app: Application): Boolean {
            return if (isWeChatQRCodeInitialized) {
                true
            } else {
                // 如果启动时初始化失败，尝试再次初始化
                initWeChatQRCodeDetector(app)
            }
        }

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
        // 应用启动时预加载 WeChatQRCode 库（可选，失败不影响其他库）
        val success = initWeChatQRCodeDetector(this)
        if (!success) {
            Log.w(TAG, "WeChatQRCode pre-initialization failed, will use ZXing/ML Kit as fallback. Error: $initErrorMessage")
        }
    }
}
