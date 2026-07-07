package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityScanImageBinding

class ScanImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanImageBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
    }
}
