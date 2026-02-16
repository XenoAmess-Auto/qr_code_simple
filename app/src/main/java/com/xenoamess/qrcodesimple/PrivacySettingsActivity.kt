package com.xenoamess.qrcodesimple

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.databinding.ActivityPrivacySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 隐私设置 Activity
 */
class PrivacySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySettingsBinding
    private lateinit var historyRepository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.privacy_settings)

        historyRepository = HistoryRepository(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // 加载隐私模式状态
        val isPrivacyMode = QRCodeApp.isPrivacyMode(this)
        binding.switchPrivacyMode.isChecked = isPrivacyMode
        updatePrivacyModeUI(isPrivacyMode)
    }

    private fun setupListeners() {
        binding.switchPrivacyMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showEnablePrivacyModeDialog()
            } else {
                QRCodeApp.setPrivacyMode(this, false)
                updatePrivacyModeUI(false)
                Toast.makeText(this, "Privacy mode disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearAllHistory.setOnClickListener {
            showClearHistoryDialog()
        }
    }

    private fun showEnablePrivacyModeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Privacy Mode?")
            .setMessage("When privacy mode is enabled:\n\n" +
                    "• Scan results will NOT be saved to history\n" +
                    "• Existing history will be kept\n" +
                    "• You can disable this at any time\n\n" +
                    "Are you sure you want to enable privacy mode?")
            .setPositiveButton("Enable") { _, _ ->
                QRCodeApp.setPrivacyMode(this, true)
                updatePrivacyModeUI(true)
                Toast.makeText(this, "Privacy mode enabled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchPrivacyMode.isChecked = false
            }
            .setCancelable(false)
            .show()
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear All History?")
            .setMessage("This will permanently delete all scan and generate history. This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                historyRepository.deleteAll()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PrivacySettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PrivacySettingsActivity, "Failed to clear history: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePrivacyModeUI(isEnabled: Boolean) {
        if (isEnabled) {
            binding.tvPrivacyStatus.text = "Privacy mode is ON"
            binding.tvPrivacyStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.cardPrivacyInfo.visibility = View.VISIBLE
        } else {
            binding.tvPrivacyStatus.text = "Privacy mode is OFF"
            binding.tvPrivacyStatus.setTextColor(getColor(android.R.color.darker_gray))
            binding.cardPrivacyInfo.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
