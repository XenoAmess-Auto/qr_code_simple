package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ActivityContinuousScanBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 连续扫描模式 Activity
 */
class ContinuousScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContinuousScanBinding
    private lateinit var adapter: ContinuousScanAdapter
    private val results = mutableListOf<ScanResult>()
    private lateinit var historyRepository: HistoryRepository

    private var scanInterval = 500L
    private var isVibrationEnabled = true
    private var isAutoSaveEnabled = true
    private var lastScanTime = 0L
    private var exportRows: List<ScanSessionExporter.Row> = emptyList()
    private var exportKind = "csv"

    private val exportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    when (exportKind) {
                        "json" -> output.write(ScanSessionExporter.json(exportRows).toByteArray())
                        "xlsx" -> output.write(ScanSessionExporter.xlsx(exportRows))
                        else -> output.write(ScanSessionExporter.csv(exportRows).toByteArray())
                    }
                } ?: error("Unable to open export destination")
            }.onSuccess { withContext(Dispatchers.Main) { Toast.makeText(this@ContinuousScanActivity, R.string.export_complete, Toast.LENGTH_SHORT).show() } }
                .onFailure { withContext(Dispatchers.Main) { Toast.makeText(this@ContinuousScanActivity, getString(R.string.export_failed, it.message), Toast.LENGTH_SHORT).show() } }
        }
    }

    data class ScanResult(
        val content: String,
        val type: HistoryType = HistoryType.QR_CODE,
        val timestamp: Long = System.currentTimeMillis(),
        var isSaved: Boolean = false
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContinuousScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.continuous_scan)

        historyRepository = HistoryRepository(this)

        setupRecyclerView()
        setupButtons()
        loadSettings()
        setupCameraFragment()
        updateCount()
    }

    private fun setupCameraFragment() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? CameraScanFragment
        fragment?.setScanResultListener(object : CameraScanFragment.OnScanResultListener {
            override fun onScanResult(result: QRCodeScanner.ScanResult) {
                handleScanResult(result)
            }
            override fun shouldPlayFeedback(): Boolean = false
        })
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
        binding.btnClearAll.setOnClickListener {
            showClearConfirmDialog()
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnSaveAll.setOnClickListener {
            saveAllResults()
        }
        binding.btnExport.setOnClickListener { showExportDialog() }
    }

    private fun handleScanResult(result: QRCodeScanner.ScanResult) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < scanInterval && results.any { it.content == result.text && it.type == result.appFormat.toHistoryType() }) {
            return
        }
        lastScanTime = currentTime

        if (results.any { it.content == result.text && it.type == result.appFormat.toHistoryType() }) {
            return
        }

        val scanResult = ScanResult(
            content = result.text,
            type = result.appFormat.toHistoryType()
        )
        results.add(0, scanResult)
        adapter.notifyItemInserted(0)
        binding.recyclerView.scrollToPosition(0)
        updateCount()

        if (isVibrationEnabled) {
            ScanFeedback.play(this)
        }

        if (isAutoSaveEnabled) {
            saveResult(scanResult)
        }
    }

    private fun saveResult(result: ScanResult) {
        lifecycleScope.launch {
            try {
                historyRepository.insertScan(result.content, result.type)
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
                    historyRepository.insertScan(result.content, result.type)
                    result.isSaved = true
                    savedCount++
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            adapter.notifyDataSetChanged()
            Toast.makeText(this@ContinuousScanActivity,
                getString(R.string.saved_items, savedCount), Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyResult(position: Int) {
        val result = results.getOrNull(position) ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QR Code", result.content))
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
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
            .setMessage(getString(R.string.clear_results_confirm, results.size))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                results.clear()
                adapter.notifyDataSetChanged()
                updateCount()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            getString(R.string.scan_vibration),
            getString(R.string.scan_auto_save, getString(if (isAutoSaveEnabled) R.string.enabled else R.string.disabled)),
            getString(R.string.scan_interval, scanInterval)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scan_settings)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        isVibrationEnabled = !isVibrationEnabled
                        saveSettings()
                    }
                    1 -> {
                        isAutoSaveEnabled = !isAutoSaveEnabled
                        saveSettings()
                    }
                    2 -> showIntervalDialog()
                }
            }
            .show()
    }

    private fun showIntervalDialog() {
        val intervals = arrayOf("100 ms", "300 ms", "500 ms", "1000 ms", "2000 ms")
        val values = longArrayOf(100L, 300L, 500L, 1000L, 2000L)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scan_interval_title)
            .setItems(intervals) { _, which ->
                scanInterval = values[which]
                saveSettings()
            }
            .show()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("continuous_scan", Context.MODE_PRIVATE)
        scanInterval = prefs.getLong("scan_interval", 500L)
        isVibrationEnabled = prefs.getBoolean("vibration", true)
        isAutoSaveEnabled = prefs.getBoolean("auto_save", true)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("continuous_scan", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("scan_interval", scanInterval)
            putBoolean("vibration", isVibrationEnabled)
            putBoolean("auto_save", isAutoSaveEnabled)
            apply()
        }
    }

    private fun updateCount() {
        binding.tvCount.text = getString(R.string.items_count, results.size)
    }

    private fun showExportDialog() {
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.no_results_to_export, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.export_results)
            .setItems(arrayOf(getString(R.string.export_csv), getString(R.string.export_excel), getString(R.string.export_json))) { _, index ->
                exportKind = listOf("csv", "xlsx", "json")[index]
                exportRows = results.map { ScanSessionExporter.Row(it.content, it.type.name, it.timestamp, it.isSaved) }
                val mime = if (exportKind == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/${if (exportKind == "json") "json" else "csv"}"
                exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, "scan-session.$exportKind"))
            }.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
