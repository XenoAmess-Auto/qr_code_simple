package com.xenoamess.qrcodesimple

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityCameraScanBinding

class CameraScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraScanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
