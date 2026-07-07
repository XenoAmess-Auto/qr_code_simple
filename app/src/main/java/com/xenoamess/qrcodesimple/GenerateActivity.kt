package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xenoamess.qrcodesimple.databinding.ActivityGenerateBinding

class GenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenerateBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
