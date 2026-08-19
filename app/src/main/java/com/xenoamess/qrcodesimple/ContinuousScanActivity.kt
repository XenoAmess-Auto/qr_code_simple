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
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.databinding.ActivityContinuousScanBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    private var stateCacheToken: String? = null
    private var stateRestoreFailed = false
    private var collectedResultBytes = 0L

    private val exportLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (stateRestoreFailed) {
            Toast.makeText(this, R.string.continuous_state_unavailable, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
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
        var isSaved: Boolean = false,
        val appFormat: BarcodeFormat = BarcodeFormat.UNKNOWN
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreInstanceState(savedInstanceState)
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
        if (stateRestoreFailed) {
            Toast.makeText(this, R.string.continuous_state_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        persistSessionState()?.let { outState.putString(STATE_CACHE_TOKEN, it) }
        if (stateRestoreFailed) outState.putBoolean(STATE_CACHE_UNAVAILABLE, true)
        outState.putString(STATE_EXPORT_KIND, exportKind)
    }

    private fun persistSessionState(): String? {
        if (results.isEmpty() && exportRows.isEmpty()) {
            discardStateCache()
            cleanupStateCache()
            return null
        }
        if (validateResults(results).first != null || validateExportRows(exportRows) != null) {
            discardStateCache()
            stateRestoreFailed = true
            return null
        }
        val bytes = JSONObject().apply {
            put("version", 1)
            put("results", JSONArray().apply {
                results.forEach { result ->
                    put(JSONObject().apply {
                        put("content", result.content)
                        put("type", result.type.name)
                        put("timestamp", result.timestamp)
                        put("saved", result.isSaved)
                        put("format", result.appFormat.name)
                    })
                }
            })
            put("exportRows", JSONArray().apply {
                exportRows.forEach { row ->
                    put(JSONObject().apply {
                        put("content", row.content)
                        put("format", row.format)
                        put("timestamp", row.timestamp)
                        put("saved", row.saved)
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > STATE_CACHE_MAX_BYTES) {
            discardStateCache()
            stateRestoreFailed = true
            return null
        }
        val token = PrivateStateFileStore.newToken()
        return runCatching {
            PrivateStateFileStore.write(this, STATE_CACHE_DIRECTORY, bytes, STATE_CACHE_MAX_BYTES, token)
            val previousToken = stateCacheToken
            stateCacheToken = token
            stateRestoreFailed = false
            previousToken?.takeIf { it != token }?.let { PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, it) }
            cleanupStateCache(token)
            token
        }.getOrElse {
            stateRestoreFailed = true
            null
        }
    }

    private fun restoreInstanceState(state: Bundle?) {
        if (state == null) {
            cleanupStateCache()
            return
        }
        stateRestoreFailed = state.getBoolean(STATE_CACHE_UNAVAILABLE, false)
        exportKind = state.getString(STATE_EXPORT_KIND)?.takeIf { it in EXPORT_KINDS } ?: "csv"
        val savedToken = state.getString(STATE_CACHE_TOKEN)
        val token = PrivateStateFileStore.validToken(savedToken) ?: run {
            stateRestoreFailed = stateRestoreFailed || savedToken != null
            cleanupStateCache()
            return
        }
        cleanupStateCache()
        runCatching {
            val root = JSONObject(
                PrivateStateFileStore.read(this, STATE_CACHE_DIRECTORY, token, STATE_CACHE_MAX_BYTES)
                    .toString(Charsets.UTF_8)
            )
            check(root.optInt("version") == 1)
            val resultArray = root.getJSONArray("results")
            check(resultArray.length() <= MAX_RESULTS)
            val restoredResults = buildList(resultArray.length()) {
                var restoredBytes = 0L
                for (index in 0 until resultArray.length()) {
                    val item = resultArray.getJSONObject(index)
                    val content = item.getString("content")
                    check(content.length <= MAX_RESULT_CHARACTERS)
                    restoredBytes += resultStorageBytes(content)
                    check(restoredBytes <= MAX_RESULT_TOTAL_BYTES)
                    add(
                        ScanResult(
                            content = content,
                            type = HistoryType.valueOf(item.getString("type")),
                            timestamp = item.getLong("timestamp"),
                            isSaved = item.getBoolean("saved"),
                            appFormat = BarcodeFormat.valueOf(item.getString("format"))
                        )
                    )
                }
            }
            val rowArray = root.getJSONArray("exportRows")
            check(rowArray.length() <= MAX_RESULTS)
            val restoredRows = buildList(rowArray.length()) {
                for (index in 0 until rowArray.length()) {
                    val item = rowArray.getJSONObject(index)
                    add(
                        ScanSessionExporter.Row(
                            item.getString("content"),
                            item.getString("format"),
                            item.getLong("timestamp"),
                            item.getBoolean("saved")
                        )
                    )
                }
            }
            check(validateExportRows(restoredRows) == null)
            results.addAll(restoredResults)
            exportRows = restoredRows
            collectedResultBytes = validateResults(restoredResults).second
            stateCacheToken = token
        }.onFailure {
            PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, token)
            stateCacheToken = null
            stateRestoreFailed = true
        }
        cleanupStateCache(stateCacheToken)
    }

    private fun discardStateCache() {
        PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, stateCacheToken)
        stateCacheToken = null
    }

    private fun cleanupStateCache(activeToken: String? = null) {
        PrivateStateFileStore.cleanupExpired(this, STATE_CACHE_DIRECTORY, STATE_CACHE_MAX_AGE_MS, activeToken)
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
        if (result.text.length > MAX_RESULT_CHARACTERS) {
            showCollectionLimitError(BatchResultTransfer.Limit.ITEM_LENGTH)
            return
        }
        if (results.any { it.content == result.text && it.appFormat == result.appFormat }) {
            return
        }
        if (currentTime - lastScanTime < scanInterval) {
            return
        }
        val resultBytes = resultStorageBytes(result.text)
        val limit = when {
            results.size >= MAX_RESULTS -> BatchResultTransfer.Limit.ITEM_COUNT
            collectedResultBytes + resultBytes > MAX_RESULT_TOTAL_BYTES -> BatchResultTransfer.Limit.TOTAL_BYTES
            else -> null
        }
        if (limit != null) {
            showCollectionLimitError(limit)
            return
        }
        lastScanTime = currentTime

        val scanResult = ScanResult(
            content = result.text,
            type = result.appFormat.toHistoryType(),
            appFormat = result.appFormat
        )
        results.add(0, scanResult)
        collectedResultBytes += resultBytes
        stateRestoreFailed = false
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
                historyRepository.insertScan(
                    result.content,
                    result.type,
                    result.appFormat.takeUnless { it == BarcodeFormat.UNKNOWN }?.name
                )
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
                    historyRepository.insertScan(
                        result.content,
                        result.type,
                        result.appFormat.takeUnless { it == BarcodeFormat.UNKNOWN }?.name
                    )
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
        val removed = results.getOrNull(position) ?: return
        results.removeAt(position)
        collectedResultBytes -= resultStorageBytes(removed.content)
        adapter.notifyItemRemoved(position)
        updateCount()
    }

    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_all))
            .setMessage(getString(R.string.clear_results_confirm, results.size))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                results.clear()
                collectedResultBytes = 0
                adapter.notifyDataSetChanged()
                updateCount()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            "${getString(R.string.scan_vibration)}: ${getString(if (isVibrationEnabled) R.string.enabled else R.string.disabled)}",
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

    private fun validateResults(values: List<ScanResult>): Pair<BatchResultTransfer.Limit?, Long> {
        if (values.size > MAX_RESULTS) return BatchResultTransfer.Limit.ITEM_COUNT to 0L
        var bytes = 0L
        for (result in values) {
            if (result.content.length > MAX_RESULT_CHARACTERS) {
                return BatchResultTransfer.Limit.ITEM_LENGTH to bytes
            }
            bytes += resultStorageBytes(result.content)
            if (bytes > MAX_RESULT_TOTAL_BYTES) return BatchResultTransfer.Limit.TOTAL_BYTES to bytes
        }
        return null to bytes
    }

    private fun validateExportRows(rows: List<ScanSessionExporter.Row>): BatchResultTransfer.Limit? {
        if (rows.size > MAX_RESULTS) return BatchResultTransfer.Limit.ITEM_COUNT
        var bytes = 0L
        for (row in rows) {
            if (row.content.length > MAX_RESULT_CHARACTERS || row.format.length > MAX_RESULT_CHARACTERS) {
                return BatchResultTransfer.Limit.ITEM_LENGTH
            }
            bytes += resultStorageBytes(row.content) + BatchResultTransfer.serializedStringBytes(row.format)
            if (bytes > MAX_RESULT_TOTAL_BYTES) return BatchResultTransfer.Limit.TOTAL_BYTES
        }
        return null
    }

    private fun showCollectionLimitError(limit: BatchResultTransfer.Limit) {
        val message = when (limit) {
            BatchResultTransfer.Limit.ITEM_COUNT -> getString(R.string.batch_limit_items, MAX_RESULTS)
            BatchResultTransfer.Limit.ITEM_LENGTH -> getString(R.string.batch_limit_item_length, MAX_RESULT_CHARACTERS)
            BatchResultTransfer.Limit.TOTAL_BYTES -> getString(
                R.string.batch_limit_total_size,
                MAX_RESULT_TOTAL_BYTES / (1024 * 1024)
            )
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showExportDialog() {
        if (results.isEmpty()) {
            Toast.makeText(
                this,
                if (stateRestoreFailed) R.string.continuous_state_unavailable else R.string.no_results_to_export,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.export_results)
            .setItems(arrayOf(getString(R.string.export_csv), getString(R.string.export_excel), getString(R.string.export_json))) { _, index ->
                exportKind = listOf("csv", "xlsx", "json")[index]
                exportRows = results.map {
                    val format = it.appFormat.takeUnless { value -> value == BarcodeFormat.UNKNOWN }?.name ?: it.type.name
                    ScanSessionExporter.Row(it.content, format, it.timestamp, it.isSaved)
                }
                val mime = if (exportKind == "xlsx") "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" else "text/${if (exportKind == "json") "json" else "csv"}"
                exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, "scan-session.$exportKind"))
            }.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        if (isFinishing) discardStateCache()
        super.onDestroy()
    }

    companion object {
        internal const val MAX_RESULTS = 500
        internal const val MAX_RESULT_CHARACTERS = 8_192
        internal const val MAX_RESULT_TOTAL_BYTES = 1024 * 1024
        private const val STATE_CACHE_TOKEN = "continuous_cache_token"
        private const val STATE_CACHE_UNAVAILABLE = "continuous_cache_unavailable"
        private const val STATE_EXPORT_KIND = "continuous_export_kind"
        private const val STATE_CACHE_DIRECTORY = "continuous-scan-state"
        private const val STATE_CACHE_MAX_BYTES = 4 * 1024 * 1024
        private const val STATE_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private val EXPORT_KINDS = setOf("csv", "xlsx", "json")

        private fun resultStorageBytes(content: String): Long =
            128L + BatchResultTransfer.serializedStringBytes(content)
    }
}
