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
        // 应用语言设置
        LocaleHelper.applyLanguage(this)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupViewPager()
        setupTabButtons()

        // 处理快捷方式跳转
        handleShortcutIntent()
    }

    private fun handleShortcutIntent() {
        val data = intent.data
        if (data != null && data.toString() == "history") {
            // 跳转到历史记录页面（第4个 tab，索引3）
            binding.viewPager.setCurrentItem(3, false)
            updateTabSelection(3)
        }
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // 监听页面变化，更新按钮状态
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabSelection(position)
            }
        })
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

        // 默认选中第一个
        updateTabSelection(0)
    }

    private fun updateTabSelection(selectedIndex: Int) {
        tabButtons.forEachIndexed { index, button ->
            if (index == selectedIndex) {
                button.setTextColor(ContextCompat.getColor(this, R.color.cyan_500))
                button.paint.isFakeBoldText = true
            } else {
                button.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                button.paint.isFakeBoldText = false
            }
        }
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
