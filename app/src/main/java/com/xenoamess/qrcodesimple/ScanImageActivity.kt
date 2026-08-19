package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityScanImageBinding

class ScanImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanImageBinding
    private val shareViewModel: ScanImageShareViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shared media is imported by the retained ViewModel; this host only consumes the result.
        if (handleShareIntent(intent)) {
            return
        }

        binding = ActivityScanImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
    }

    /**
     * 处理 ACTION_SEND / ACTION_SEND_MULTIPLE 分享意图。
     * @return true when this launch is a valid shared-media request.
     */
    private fun handleShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = getStreamUri(intent)
                if (uri != null) {
                    processSharedMedia(uri, intent.type, null)
                    true
                } else {
                    false
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getStreamUris(intent)
                if (!uris.isNullOrEmpty()) {
                    processSharedMedia(uris.first(), intent.type, uris.size)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun processSharedMedia(uri: Uri, mimeType: String?, sharedCount: Int?) {
        shareViewModel.result.observe(this) { result ->
            if (isFinishing || isDestroyed || !shareViewModel.consume(result)) return@observe
            when (result) {
                is ScanImageProcessor.SharedMediaResult.Ready -> routeSharedMedia(result)
                ScanImageProcessor.SharedMediaResult.TooLarge -> {
                    Toast.makeText(this, R.string.shared_media_too_large, Toast.LENGTH_LONG).show()
                    finish()
                }
                ScanImageProcessor.SharedMediaResult.Failed -> {
                    Toast.makeText(this, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        if (shareViewModel.start(uri, mimeType) && sharedCount != null && sharedCount > 1) {
            Toast.makeText(
                this,
                getString(R.string.shared_multiple_first_only, sharedCount),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun routeSharedMedia(result: ScanImageProcessor.SharedMediaResult.Ready) {
        val target = when (result.destination) {
            ScanImageProcessor.Destination.IMAGE -> ResultActivity::class.java
            ScanImageProcessor.Destination.VIDEO -> VideoScanActivity::class.java
        }
        val uriExtra = when (result.destination) {
            ScanImageProcessor.Destination.IMAGE -> ResultActivity.EXTRA_BITMAP_URI
            ScanImageProcessor.Destination.VIDEO -> VideoScanActivity.EXTRA_VIDEO_URI
        }
        try {
            startActivity(Intent(this, target).apply {
                putExtra(uriExtra, result.uri.toString())
                putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE, result.ownsTempFile)
                putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE, result.leaseToken)
            })
        } catch (_: RuntimeException) {
            ScanImageProcessor.deleteOwnedSharedMedia(
                applicationContext,
                result.uri,
                result.ownsTempFile,
                result.leaseToken
            )
            Toast.makeText(this, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show()
        } finally {
            finish()
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
