package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.databinding.ActivityBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 备份与恢复界面
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportData(uri)
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importData(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.backup_restore)

        setupViews()
    }

    private fun setupViews() {
        // 导出 JSON
        binding.btnExportJson.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, HistoryBackupManager.generateBackupFileName("json"))
            }
            exportLauncher.launch(intent)
        }

        // 导出 CSV
        binding.btnExportCsv.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, HistoryBackupManager.generateBackupFileName("csv"))
            }
            exportLauncher.launch(intent)
        }

        // 导入
        binding.btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/csv", "text/plain"))
            }
            importLauncher.launch(intent)
        }
    }

    private fun exportData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = if (uri.toString().endsWith(".csv")) {
                    HistoryBackupManager.exportToCsv(this@BackupActivity)
                } else {
                    HistoryBackupManager.exportToJson(this@BackupActivity)
                }

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(content)
                        }
                    }
                }

                Toast.makeText(
                    this@BackupActivity,
                    R.string.export_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@BackupActivity,
                    getString(R.string.export_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun importData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
                    } ?: ""
                }

                val result = if (content.trim().startsWith("[")) {
                    // CSV
                    HistoryBackupManager.importFromCsv(this@BackupActivity, content)
                } else {
                    // JSON
                    HistoryBackupManager.importFromJson(this@BackupActivity, content)
                }

                Toast.makeText(
                    this@BackupActivity,
                    if (result.success) result.message else getString(
                        R.string.import_failed,
                        result.message
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@BackupActivity,
                    getString(R.string.import_failed, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
