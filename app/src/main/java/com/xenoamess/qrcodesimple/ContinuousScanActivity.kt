package com.xenoamess.qrcodesimple

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ActivityContinuousScanBinding
import kotlinx.coroutines.launch

/**
 * 连续扫描模式 Activity
 */
class ContinuousScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContinuousScanBinding
    private lateinit var adapter: ContinuousScanAdapter
    private val results = mutableListOf<ScanResult>()
    private lateinit var historyRepository: HistoryRepository
    
    // 扫描间隔（毫秒）
    private var scanInterval = 500L
    private var isVibrationEnabled = true
    private var isSoundEnabled = true
    private var isAutoSaveEnabled = true
    
    data class ScanResult(
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        var isSaved: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityContinuousScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.continuous_scan)

        historyRepository = HistoryRepository(this)
        
        setupRecyclerView()
        setupButtons()
        loadSettings()
    }

    private fun setupRecyclerView() {
        adapter = ContinuousScanAdapter(
            results,
            onCopy = { position -> copyResult(position) },
            onShare = { position -> shareResult(position) },
            onDelete = { position -> deleteResult(position) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnStartScan.setOnClickListener {
            startContinuousScan()
        }

        binding.btnStopScan.setOnClickListener {
            stopContinuousScan()
        }

        binding.btnClearAll.setOnClickListener {
            showClearConfirmDialog()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnSaveAll.setOnClickListener {
            saveAllResults()
        }
    }

    private fun startContinuousScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }

        binding.btnStartScan.visibility = View.GONE
        binding.btnStopScan.visibility = View.VISIBLE
        binding.scannerOverlay.visibility = View.VISIBLE
        
        // 启动扫描
        startScanning()
    }

    private fun stopContinuousScan() {
        binding.btnStartScan.visibility = View.VISIBLE
        binding.btnStopScan.visibility = View.GONE
        binding.scannerOverlay.visibility = View.GONE
        
        // 停止扫描
        stopScanning()
    }

    private fun startScanning() {
        // 实际扫描逻辑在 CameraScanActivity 中实现
        // 这里简化处理，实际应该集成 CameraX 扫描
        Toast.makeText(this, "Continuous scan started", Toast.LENGTH_SHORT).show()
    }

    private fun stopScanning() {
        Toast.makeText(this, "Continuous scan stopped", Toast.LENGTH_SHORT).show()
    }

    /**
     * 处理扫描结果（由扫描器调用）
     */
    fun onScanResult(content: String) {
        // 检查是否已存在相同内容（去重）
        if (results.any { it.content == content }) {
            return
        }

        val result = ScanResult(content = content)
        results.add(0, result) // 添加到顶部
        adapter.notifyItemInserted(0)
        binding.recyclerView.scrollToPosition(0)

        // 更新计数
        updateCount()

        // 震动反馈
        if (isVibrationEnabled) {
            vibrate()
        }

        // 自动保存到历史
        if (isAutoSaveEnabled) {
            saveResult(result)
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    private fun saveResult(result: ScanResult) {
        lifecycleScope.launch {
            try {
                historyRepository.insertScan(result.content, HistoryType.QR_CODE)
                result.isSaved = true
                val position = results.indexOf(result)
                if (position >= 0) {
                    adapter.notifyItemChanged(position)
                }
            } catch (e: Exception) {
                // 保存失败不影响扫描
            }
        }
    }

    private fun saveAllResults() {
        lifecycleScope.launch {
            var savedCount = 0
            results.filter { !it.isSaved }.forEach { result ->
                try {
                    historyRepository.insertScan(result.content, HistoryType.QR_CODE)
                    result.isSaved = true
                    savedCount++
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            adapter.notifyDataSetChanged()
            Toast.makeText(this@ContinuousScanActivity, 
                "Saved $savedCount items", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyResult(position: Int) {
        val result = results.getOrNull(position) ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QR Code", result.content))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareResult(position: Int) {
        val result = results.getOrNull(position) ?: return
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, result.content)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun deleteResult(position: Int) {
        results.removeAt(position)
        adapter.notifyItemRemoved(position)
        updateCount()
    }

    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_all))
            .setMessage("Clear all ${results.size} results?")
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                results.clear()
                adapter.notifyDataSetChanged()
                updateCount()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Scan Settings")
            .setMessage("Settings will be implemented in future update")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("continuous_scan", Context.MODE_PRIVATE)
        scanInterval = prefs.getLong("scan_interval", 500L)
        isVibrationEnabled = prefs.getBoolean("vibration", true)
        isSoundEnabled = prefs.getBoolean("sound", true)
        isAutoSaveEnabled = prefs.getBoolean("auto_save", true)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("continuous_scan", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("scan_interval", scanInterval)
            putBoolean("vibration", isVibrationEnabled)
            putBoolean("sound", isSoundEnabled)
            putBoolean("auto_save", isAutoSaveEnabled)
            apply()
        }
    }

    private fun updateCount() {
        binding.tvCount.text = "${results.size} items"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val REQUEST_CAMERA = 100
    }
}
