package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.xenoamess.qrcodesimple.databinding.ActivityMainBinding

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabButtons: List<Button>

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabButtons()
        setupViewPager()
        checkPermissions()
        handleShortcutIntent()
    }

    private fun setupTabButtons() {
        tabButtons = listOf(
            binding.btnTabRealtime,
            binding.btnTabImage,
            binding.btnTabGenerate,
            binding.btnTabHistory,
            binding.btnTabAbout
        )

        tabButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                binding.viewPager.setCurrentItem(index, true)
                updateTabSelection(index)
            }
        }
    }

    private fun updateTabSelection(selectedIndex: Int) {
        tabButtons.forEachIndexed { index, button ->
            button.alpha = if (index == selectedIndex) 1.0f else 0.6f
        }
    }

    private fun handleShortcutIntent() {
        val data = intent.data
        if (data != null && data.toString() == "history") {
            binding.viewPager.setCurrentItem(3, false)
            updateTabSelection(3)
        } else {
            updateTabSelection(0)
        }
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabSelection(position)
            }
        })
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permissions_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
