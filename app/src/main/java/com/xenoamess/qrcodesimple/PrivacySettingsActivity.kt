package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.privacy_settings)

        historyRepository = HistoryRepository(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val isPrivacyMode = QRCodeApp.isPrivacyMode(this)
        binding.switchPrivacyMode.isChecked = isPrivacyMode
        updatePrivacyModeUI(isPrivacyMode)

        updateAppLockUI()
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

        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (AppLockManager.hasPin()) {
                    AppLockManager.setLockEnabled(true)
                    updateAppLockUI()
                    Toast.makeText(this, getString(R.string.app_lock_enabled), Toast.LENGTH_SHORT).show()
                } else {
                    showSetPinDialog { success ->
                        if (success) {
                            AppLockManager.setLockEnabled(true)
                        } else {
                            binding.switchAppLock.isChecked = false
                        }
                        updateAppLockUI()
                    }
                }
            } else {
                AppLockManager.setLockEnabled(false)
                updateAppLockUI()
                Toast.makeText(this, getString(R.string.app_lock_disabled), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangePin.setOnClickListener {
            showSetPinDialog { success ->
                if (success) {
                    Toast.makeText(this, getString(R.string.pin_set), Toast.LENGTH_SHORT).show()
                }
                updateAppLockUI()
            }
        }
    }

    private fun showSetPinDialog(onResult: (Boolean) -> Unit) {
        var resultDelivered = false
        val deliverResult: (Boolean) -> Unit = { success ->
            if (!resultDelivered) {
                resultDelivered = true
                onResult(success)
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val pinInput = EditText(this).apply {
            hint = getString(R.string.enter_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val confirmPinInput = EditText(this).apply {
            hint = getString(R.string.confirm_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        container.addView(pinInput)
        container.addView(confirmPinInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.set_pin))
            .setView(container)
            .setPositiveButton(getString(R.string.set_pin)) { _, _ ->
                val pin = pinInput.text?.toString()?.trim() ?: ""
                val confirmPin = confirmPinInput.text?.toString()?.trim() ?: ""

                if (pin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    deliverResult(false)
                    return@setPositiveButton
                }

                if (pin != confirmPin) {
                    Toast.makeText(this, getString(R.string.pin_mismatch), Toast.LENGTH_SHORT).show()
                    deliverResult(false)
                    return@setPositiveButton
                }

                AppLockManager.setPin(pin)
                deliverResult(true)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnDismissListener { deliverResult(false) }
            .show()
    }

    private fun updateAppLockUI() {
        val isEnabled = AppLockManager.isLockEnabled()
        binding.switchAppLock.isChecked = isEnabled
        binding.tvAppLockStatus.text = if (isEnabled) {
            getString(R.string.app_lock_enabled)
        } else {
            getString(R.string.app_lock_disabled)
        }
        binding.tvAppLockStatus.setTextColor(
            if (isEnabled) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray)
        )
        binding.btnChangePin.visibility = if (AppLockManager.hasPin()) View.VISIBLE else View.GONE
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
