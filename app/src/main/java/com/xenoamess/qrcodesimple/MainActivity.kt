package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabButtons: List<Button>

    private var pendingGenerateContent: String? = null
    private var pendingGenerateFormat: String? = null
    private var pendingGenerateStyleJson: String? = null

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private const val EXTRA_GENERATE_CONTENT = "generate_content"
        private const val EXTRA_GENERATE_FORMAT = "generate_format"
        private const val EXTRA_GENERATE_STYLE_JSON = "generate_style_json"

        fun navigateToGenerate(context: Context, content: String, format: String? = null, styleJson: String? = null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_GENERATE_CONTENT, content)
                putExtra(EXTRA_GENERATE_FORMAT, format)
                putExtra(EXTRA_GENERATE_STYLE_JSON, styleJson)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域（状态栏、导航栏、灵动岛等）
        setupEdgeToEdge()

        checkPermissions()
        setupViewPager()
        setupTabButtons()

        // 处理快捷方式跳转
        handleShortcutIntent()

        // 处理从详情页跳转回生成页的参数
        handleGenerateIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGenerateIntent(intent)
    }

    private fun handleGenerateIntent(intent: Intent?) {
        val content = intent?.getStringExtra(EXTRA_GENERATE_CONTENT) ?: return
        val format = intent.getStringExtra(EXTRA_GENERATE_FORMAT)
        val styleJson = intent.getStringExtra(EXTRA_GENERATE_STYLE_JSON)

        // 切换到生成 tab
        if (::binding.isInitialized) {
            binding.viewPager.setCurrentItem(2, true)
        }

        // 如果 GenerateFragment 已创建，直接加载；否则暂存待 fragment 创建后读取
        val fragment = supportFragmentManager.findFragmentByTag("f2") as? GenerateFragment
        if (fragment != null && fragment.isAdded) {
            fragment.loadFromHistory(content, format?.let { BarcodeFormat.fromString(it) }, styleJson)
        } else {
            pendingGenerateContent = content
            pendingGenerateFormat = format
            pendingGenerateStyleJson = styleJson
        }
    }

    private fun navigateToGenerateTab() {
        binding.viewPager.setCurrentItem(2, true)
    }

    internal fun consumePendingGenerate(): Triple<String?, String?, String?> {
        val content = pendingGenerateContent
        val format = pendingGenerateFormat
        val styleJson = pendingGenerateStyleJson
        pendingGenerateContent = null
        pendingGenerateFormat = null
        pendingGenerateStyleJson = null
        return Triple(content, format, styleJson)
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