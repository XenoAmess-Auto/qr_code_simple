package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.AppDatabase
import com.xenoamess.qrcodesimple.databinding.ActivityDatabaseSecurityBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 数据库安全设置界面
 */
class DatabaseSecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatabaseSecurityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDatabaseSecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.database_security)

        setupViews()
    }

    private fun setupViews() {
        // 显示加密状态
        binding.tvEncryptionStatus.text = getString(R.string.encryption_enabled)
        binding.tvEncryptionStatus.setTextColor(getColor(android.R.color.holo_green_dark))

        // 重置数据库按钮
        binding.btnResetDatabase.setOnClickListener {
            showResetConfirmDialog()
        }

        // 导出备份按钮
        binding.btnExportBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_database)
            .setMessage(R.string.reset_database_confirm)
            .setPositiveButton(android.R.string.ok) { _: android.content.DialogInterface, _: Int ->
                performDatabaseReset()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDatabaseReset() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppDatabase.resetDatabase(this@DatabaseSecurityActivity)
                }

                Toast.makeText(
                    this@DatabaseSecurityActivity,
                    R.string.database_reset_success,
                    Toast.LENGTH_LONG
                ).show()

                // 重启应用
                restartApp()
            } catch (e: Exception) {
                Toast.makeText(
                    this@DatabaseSecurityActivity,
                    getString(R.string.database_reset_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DatabaseSecurityActivity::class.java))
        }
    }
}
