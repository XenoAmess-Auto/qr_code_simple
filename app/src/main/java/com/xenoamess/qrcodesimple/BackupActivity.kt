package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.databinding.ActivityBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

/**
 * 备份与恢复界面
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding

    /** 等待 SAF 返回期间暂存的加密导出密码；导出完成后立即清除。 */
    private var pendingExportPassword: CharArray? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                exportData(uri)
            }
        } else {
            pendingExportPassword = null
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
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

        // 导出加密备份
        binding.btnExportEncrypted.setOnClickListener {
            showExportPasswordDialog()
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

    internal fun exportData(uri: Uri) {
        val password = pendingExportPassword
        pendingExportPassword = null
        lifecycleScope.launch {
            try {
                if (password != null) {
                    // 加密备份：二进制写出
                    val data = HistoryBackupManager.exportEncryptedJson(this@BackupActivity, password)
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(data)
                        }
                    }
                } else {
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

    /**
     * 加密导出：先收集并确认密码，再拉起 SAF 建文件。
     */
    private fun showExportPasswordDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        val inputPassword = EditText(this).apply {
            hint = getString(R.string.backup_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val inputConfirm = EditText(this).apply {
            hint = getString(R.string.backup_password_confirm)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(inputPassword)
        layout.addView(inputConfirm)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_encrypted))
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = inputPassword.text.toString()
                val confirm = inputConfirm.text.toString()
                when {
                    password.isEmpty() ->
                        Toast.makeText(this, getString(R.string.backup_password_required), Toast.LENGTH_SHORT).show()
                    password != confirm ->
                        Toast.makeText(this, getString(R.string.backup_password_mismatch), Toast.LENGTH_SHORT).show()
                    else -> {
                        pendingExportPassword = password.toCharArray()
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_TITLE, HistoryBackupManager.generateBackupFileName("qrbak"))
                        }
                        exportLauncher.launch(intent)
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * 加密导入：读取到加密备份后弹密码框。
     */
    private fun showImportPasswordDialog(data: ByteArray) {
        val inputPassword = EditText(this).apply {
            hint = getString(R.string.backup_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_encrypted))
            .setMessage(getString(R.string.backup_password_prompt_import))
            .setView(inputPassword)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val password = inputPassword.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, getString(R.string.backup_password_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val result = HistoryBackupManager.importEncrypted(
                        this@BackupActivity, data, password.toCharArray()
                    )
                    Toast.makeText(
                        this@BackupActivity,
                        if (result.success) result.message else getString(R.string.backup_decrypt_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    internal fun importData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.readBytes()
                    } ?: ByteArray(0)
                }

                if (BackupCrypto.isEncrypted(bytes)) {
                    // 加密备份：弹密码框走解密导入
                    showImportPasswordDialog(bytes)
                    return@launch
                }

                val content = bytes.toString(Charsets.UTF_8)
                val result = if (HistoryBackupManager.looksLikeJson(content)) {
                    // JSON
                    HistoryBackupManager.importFromJson(this@BackupActivity, content)
                } else {
                    // CSV
                    HistoryBackupManager.importFromCsv(this@BackupActivity, content)
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
