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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal enum class PendingExportKind {
    JSON,
    CSV,
    XLSX,
    ENCRYPTED
}

/**
 * 备份与恢复界面
 */
class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding

    /** 格式可跨重建恢复；密码只保留在当前 Activity 实例内。 */
    private var pendingExportKind: PendingExportKind? = null
    private var pendingExportPassword: CharArray? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data.takeIf { result.resultCode == Activity.RESULT_OK }
        consumePendingExport(uri)
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
        pendingExportKind = savedInstanceState
            ?.getString(STATE_PENDING_EXPORT_KIND)
            ?.let { runCatching { PendingExportKind.valueOf(it) }.getOrNull() }
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            launchExport(PendingExportKind.JSON, intent)
        }

        // 导出 CSV
        binding.btnExportCsv.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, HistoryBackupManager.generateBackupFileName("csv"))
            }
            launchExport(PendingExportKind.CSV, intent)
        }

        // XLSX 是只读报表导出，不是可恢复的备份格式。
        binding.btnExportExcel.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_TITLE, HistoryBackupManager.generateBackupFileName("xlsx"))
            }
            launchExport(PendingExportKind.XLSX, intent)
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
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/json", "text/csv", "text/plain", "application/octet-stream")
                )
            }
            importLauncher.launch(intent)
        }

        setupWebdavViews()
    }

    private fun setupWebdavViews() {
        binding.etWebdavPassword.isSaveEnabled = false
        // 回填已保存的配置（密码不回填，留空表示沿用已存密码）
        val savedUrl = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("webdav_url", null)
        val savedUsername = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("webdav_username", null)
        binding.etWebdavUrl.setText(savedUrl ?: "")
        binding.etWebdavUsername.setText(savedUsername ?: "")

        binding.switchWebdavAutoUpload.isChecked = WebDavSyncManager.isAutoUploadEnabled(this)
        binding.switchWebdavAutoUpload.setOnCheckedChangeListener { _, isChecked ->
            WebDavSyncManager.setAutoUploadEnabled(this, isChecked)
        }
        refreshWebdavLastSync()

        binding.btnWebdavUpload.setOnClickListener { runWebdav(isUpload = true) }
        binding.btnWebdavDownload.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.webdav_download)
                .setMessage(R.string.webdav_restore_confirm)
                .setPositiveButton(R.string.confirm) { _, _ -> runWebdav(isUpload = false) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun refreshWebdavLastSync() {
        val lastSync = WebDavSyncManager.getLastSync(this)
        binding.tvWebdavLastSync.text = getString(
            R.string.webdav_last_sync,
            if (lastSync > 0) {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(lastSync))
            } else {
                getString(R.string.webdav_never_synced)
            }
        )
    }

    internal fun runWebdav(isUpload: Boolean) {
        val url = binding.etWebdavUrl.text?.toString()?.trim().orEmpty()
        val username = binding.etWebdavUsername.text?.toString()?.trim().orEmpty()
        val passwordInput = binding.etWebdavPassword.text
        val password = try {
            if (!passwordInput.isNullOrEmpty()) {
                CharArray(passwordInput.length) { passwordInput[it] }
            } else {
                WebDavSyncManager.loadConfig(this)?.password ?: CharArray(0)
            }
        } finally {
            passwordInput?.clear()
        }

        if (url.isEmpty() || password.isEmpty()) {
            password.fill('\u0000')
            Toast.makeText(this, getString(R.string.webdav_not_configured), Toast.LENGTH_SHORT).show()
            return
        }
        val candidate = WebDavSyncManager.Config(url, username, password)

        lifecycleScope.launch {
            val outcome = try {
                val result = if (isUpload) {
                    WebDavSyncManager.upload(this@BackupActivity, candidate)
                } else {
                    WebDavSyncManager.download(this@BackupActivity, candidate)
                }
                if (result == WebDavSyncManager.Outcome.SUCCESS) {
                    WebDavSyncManager.saveConfig(this@BackupActivity, url, username, password)
                }
                result
            } finally {
                password.fill('\u0000')
            }
            val messageRes = when (outcome) {
                WebDavSyncManager.Outcome.SUCCESS ->
                    if (isUpload) R.string.webdav_upload_success else R.string.webdav_restore_success
                WebDavSyncManager.Outcome.NOT_CONFIGURED -> R.string.webdav_not_configured
                WebDavSyncManager.Outcome.AUTH_FAILED -> R.string.webdav_auth_failed
                WebDavSyncManager.Outcome.NOT_FOUND -> R.string.webdav_not_found
                WebDavSyncManager.Outcome.TOO_LARGE,
                WebDavSyncManager.Outcome.NETWORK_ERROR -> R.string.webdav_network_error
                WebDavSyncManager.Outcome.DECRYPT_FAILED -> R.string.backup_decrypt_failed
            }
            Toast.makeText(this@BackupActivity, getString(messageRes), Toast.LENGTH_LONG).show()
            if (outcome == WebDavSyncManager.Outcome.SUCCESS) {
                refreshWebdavLastSync()
            }
        }.invokeOnCompletion { password.fill('\u0000') }
    }

    private fun launchExport(kind: PendingExportKind, intent: Intent, password: CharArray? = null) {
        clearPendingExport()
        pendingExportKind = kind
        pendingExportPassword = password
        exportLauncher.launch(intent)
    }

    internal fun consumePendingExport(uri: Uri?) {
        val kind = pendingExportKind
        val password = pendingExportPassword
        pendingExportKind = null
        pendingExportPassword = null

        if (uri == null) {
            password?.fill('\u0000')
            return
        }
        if (kind == null) {
            password?.fill('\u0000')
            showExportFailure(getString(R.string.unknown_error))
            return
        }
        if (kind == PendingExportKind.ENCRYPTED && password == null) {
            showExportFailure(getString(R.string.backup_password_required))
            return
        }
        exportData(uri, kind, password)
    }

    internal fun exportData(uri: Uri, kind: PendingExportKind, password: CharArray? = null) {
        lifecycleScope.launch {
            try {
                when (kind) {
                    PendingExportKind.ENCRYPTED -> {
                        val encryptionPassword = requireNotNull(password) {
                            getString(R.string.backup_password_required)
                        }
                        val data = HistoryBackupManager.exportEncryptedJson(
                            this@BackupActivity,
                            encryptionPassword
                        )
                        withContext(Dispatchers.IO) {
                            requireNotNull(contentResolver.openOutputStream(uri)) {
                                getString(R.string.unknown_error)
                            }.use { outputStream ->
                                outputStream.write(data)
                            }
                        }
                    }
                    PendingExportKind.XLSX -> {
                        val data = HistoryBackupManager.exportToXlsx(this@BackupActivity)
                        withContext(Dispatchers.IO) {
                            requireNotNull(contentResolver.openOutputStream(uri)) {
                                getString(R.string.unknown_error)
                            }.use { outputStream ->
                                outputStream.write(data)
                            }
                        }
                    }
                    PendingExportKind.JSON,
                    PendingExportKind.CSV -> {
                        val content = when (kind) {
                            PendingExportKind.JSON -> HistoryBackupManager.exportToJson(this@BackupActivity)
                            PendingExportKind.CSV -> HistoryBackupManager.exportToCsv(this@BackupActivity)
                            else -> error("Unexpected text export kind: $kind")
                        }
                        withContext(Dispatchers.IO) {
                            requireNotNull(contentResolver.openOutputStream(uri)) {
                                getString(R.string.unknown_error)
                            }.use { outputStream ->
                                OutputStreamWriter(outputStream).use { writer ->
                                    writer.write(content)
                                }
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
                showExportFailure(e.message)
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    private fun showExportFailure(message: String?) {
        Toast.makeText(
            this,
            getString(R.string.export_failed, message ?: getString(R.string.unknown_error)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearPendingExport() {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        pendingExportKind = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingExportKind?.let { outState.putString(STATE_PENDING_EXPORT_KIND, it.name) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        super.onDestroy()
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

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_encrypted))
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = inputPassword.text.toString()
            val confirm = inputConfirm.text.toString()
            when {
                password.isEmpty() ->
                    Toast.makeText(this, getString(R.string.backup_password_required), Toast.LENGTH_SHORT).show()
                password != confirm ->
                    Toast.makeText(this, getString(R.string.backup_password_mismatch), Toast.LENGTH_SHORT).show()
                else -> {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, HistoryBackupManager.generateBackupFileName("qrbak"))
                    }
                    launchExport(PendingExportKind.ENCRYPTED, intent, password.toCharArray())
                    dialog.dismiss()
                }
            }
        }
    }

    /**
     * 加密导入：读取到加密备份后弹密码框。
     */
    private fun showImportPasswordDialog(data: ByteArray) {
        val inputPassword = EditText(this).apply {
            hint = getString(R.string.backup_password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.export_encrypted))
            .setMessage(getString(R.string.backup_password_prompt_import))
            .setView(inputPassword)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
        val positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {
            val passwordText = inputPassword.text.toString()
            if (passwordText.isEmpty()) {
                Toast.makeText(this, getString(R.string.backup_password_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val password = passwordText.toCharArray()
            positiveButton.isEnabled = false
            lifecycleScope.launch {
                val result = try {
                    HistoryBackupManager.importEncrypted(this@BackupActivity, data, password)
                } finally {
                    password.fill('\u0000')
                }
                Toast.makeText(
                    this@BackupActivity,
                    if (result.success) result.message else getString(R.string.backup_decrypt_failed),
                    Toast.LENGTH_LONG
                ).show()
                if (result.success) {
                    dialog.dismiss()
                } else {
                    positiveButton.isEnabled = true
                }
            }
        }
    }

    internal fun importData(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    requireNotNull(contentResolver.openInputStream(uri)) {
                        getString(R.string.unknown_error)
                    }.use { inputStream ->
                        readCapped(inputStream, MAX_IMPORT_BYTES)
                    }
                }
                if (bytes == null) {
                    showImportFailure(getString(R.string.backup_import_too_large, MAX_IMPORT_MEBIBYTES))
                    return@launch
                }

                if (BackupCrypto.isEncrypted(bytes)) {
                    // 加密备份：弹密码框走解密导入
                    showImportPasswordDialog(bytes)
                    return@launch
                }

                val content = decodeUtf8(bytes)
                if (content == null) {
                    showImportFailure(getString(R.string.backup_import_unsupported))
                    return@launch
                }
                val normalizedContent = content.removePrefix("\uFEFF")
                val result = if (HistoryBackupManager.looksLikeJson(normalizedContent)) {
                    // JSON
                    HistoryBackupManager.importFromJson(this@BackupActivity, normalizedContent)
                } else if (HistoryBackupManager.looksLikeCsv(normalizedContent)) {
                    // CSV
                    HistoryBackupManager.importFromCsv(this@BackupActivity, normalizedContent)
                } else {
                    showImportFailure(getString(R.string.backup_import_unsupported))
                    return@launch
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
                showImportFailure(e.message)
            }
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun readCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                val singleByte = input.read()
                if (singleByte < 0) break
                total++
                if (total > maxBytes) return null
                output.write(singleByte)
                continue
            }
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun showImportFailure(message: String?) {
        Toast.makeText(
            this,
            getString(R.string.import_failed, message ?: getString(R.string.unknown_error)),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private companion object {
        const val STATE_PENDING_EXPORT_KIND = "pending_export_kind"
        const val MAX_IMPORT_MEBIBYTES = 8
        const val MAX_IMPORT_BYTES = MAX_IMPORT_MEBIBYTES * 1024 * 1024
    }
}
