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

        fun initWeChatQRCodeDetector(app: Application): Boolean {
            if (isWeChatQRCodeInitialized) return true
            return try {
                WeChatQRCodeDetector.init(app)
                isWeChatQRCodeInitialized = true
                Log.i(TAG, "WeChatQRCodeDetector initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WeChatQRCodeDetector", e)
                false
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library load failed", e)
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
