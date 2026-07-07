package com.xenoamess.qrcodesimple

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityScanImageBinding

class ScanImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityScanImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
    }
}
