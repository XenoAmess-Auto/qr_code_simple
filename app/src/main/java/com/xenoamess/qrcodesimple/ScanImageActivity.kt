package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityScanImageBinding

class ScanImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanImageBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 系统分享入口：图片/视频直接路由到扫描，不展示本页 UI
        if (handleShareIntent(intent)) {
            finish()
            return
        }

        binding = ActivityScanImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
    }

    /**
     * 处理 ACTION_SEND / ACTION_SEND_MULTIPLE 分享意图。
     * @return true 表示已接管（调用方应直接 finish）
     */
    private fun handleShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = getStreamUri(intent)
                if (uri != null) {
                    ScanImageProcessor.processMedia(this, uri, intent.type)
                    true
                } else {
                    false
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getStreamUris(intent)
                if (!uris.isNullOrEmpty()) {
                    if (uris.size > 1) {
                        Toast.makeText(
                            this,
                            getString(R.string.shared_multiple_first_only, uris.size),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    ScanImageProcessor.processMedia(this, uris.first(), intent.type)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun getStreamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun getStreamUris(intent: Intent): List<Uri>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
}
