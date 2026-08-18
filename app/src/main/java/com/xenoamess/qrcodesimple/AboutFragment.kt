package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.xenoamess.qrcodesimple.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    private val contentBinding get() = binding.aboutContent

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置版本号
        val versionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "0.1.1"
        }
        val gitHash = BuildConfig.GIT_HASH
        contentBinding.tvVersion.text = getString(R.string.version_with_prefix, versionName, gitHash)

        updatePreferenceValues()

        contentBinding.btnGitHubProject.setOnClickListener {
            openUrl("https://github.com/XenoAmess-Auto/qr_code_simple")
        }

        contentBinding.btnGitHubMaintainer.setOnClickListener {
            openUrl("https://github.com/XenoAmess")
        }

        contentBinding.btnDonate.setOnClickListener {
            openUrl("https://ko-fi.com/xenoamess")
        }

        contentBinding.btnLanguage.setOnClickListener {
            showLanguageDialog()
        }

        contentBinding.btnTheme.setOnClickListener {
            showThemeDialog()
        }

        contentBinding.btnPrivacy.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacySettingsActivity::class.java))
        }

        contentBinding.btnCheckUpdate.setOnClickListener {
            AppUpdateManager.checkManually(requireActivity())
        }

        contentBinding.btnCheckBetaUpdate.setOnClickListener {
            AppUpdateManager.checkBetaUpdate(requireActivity())
        }

        contentBinding.btnVersionHistory.setOnClickListener {
            showVersionHistory()
        }

        // F-Droid builds receive updates from the F-Droid client.
        if (BuildConfig.IS_FDROID) {
            contentBinding.updateSection.visibility = View.GONE
        }

        contentBinding.btnCrashLogs.setOnClickListener {
            showCrashLogsDialog()
        }

        contentBinding.switchAutoUpdate.isChecked = QRCodeApp.isAppUpdateAutoCheckEnabled(requireContext())
        contentBinding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            QRCodeApp.setAppUpdateAutoCheckEnabled(requireContext(), isChecked)
            if (isChecked) {
                AppUpdateManager.checkManually(requireActivity())
            }
        }
    }

    private fun updatePreferenceValues() {
        contentBinding.tvLanguageValue.text = LocaleHelper.getCurrentLanguageDisplayName(requireContext())
        contentBinding.tvThemeValue.text = when (QRCodeApp.getThemeMode(requireContext())) {
            QRCodeApp.THEME_MODE_LIGHT -> getString(R.string.theme_light)
            QRCodeApp.THEME_MODE_DARK -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
    }

    private fun showThemeDialog() {
        val modes = listOf(
            QRCodeApp.THEME_MODE_SYSTEM to getString(R.string.theme_system),
            QRCodeApp.THEME_MODE_LIGHT to getString(R.string.theme_light),
            QRCodeApp.THEME_MODE_DARK to getString(R.string.theme_dark)
        )
        val current = QRCodeApp.getThemeMode(requireContext())
        val selectedIndex = modes.indexOfFirst { it.first == current }.coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme_setting))
            .setSingleChoiceItems(modes.map { it.second }.toTypedArray(), selectedIndex) { dialog, which ->
                val mode = modes[which].first
                if (mode != current) {
                    QRCodeApp.setThemeMode(requireContext(), mode)
                    QRCodeApp.applyThemeMode(requireContext())
                    // 主题切换立即生效，无需重启
                    requireActivity().recreate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = LocaleHelper.SUPPORTED_LANGUAGES
        val currentLang = LocaleHelper.getLanguage(requireContext())
        var selectedIndex = languages.indexOfFirst { it.code == currentLang }
        if (selectedIndex < 0) selectedIndex = 0

        val items = languages.map { it.displayName }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                if (selectedLanguage.code != currentLang) {
                    LocaleHelper.setLanguage(requireContext(), selectedLanguage.code)
                    updatePreferenceValues()
                    showRestartDialog()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showVersionHistory() {
        val history = readVersionHistory()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: getString(R.string.version_history_unavailable)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.version_history_title)
            .setMessage(history)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showCrashLogsDialog() {
        val ctx = requireContext()
        val logs = CrashLogger.listLogs(ctx)
        if (logs.isEmpty()) {
            AlertDialog.Builder(ctx)
                .setTitle(R.string.crash_logs)
                .setMessage(R.string.crash_log_empty)
                .setPositiveButton(R.string.close, null)
                .show()
            return
        }
        val content = CrashLogger.readLatest(ctx) ?: getString(R.string.crash_log_empty)
        val header = getString(R.string.crash_log_count, logs.size)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.crash_logs)
            .setMessage("$header\n\n$content")
            .setPositiveButton(R.string.share) { _, _ -> shareCrashLog(content) }
            .setNegativeButton(R.string.clear_logs) { _, _ ->
                CrashLogger.clear(ctx)
                Toast.makeText(ctx, getString(R.string.crash_log_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    private fun shareCrashLog(content: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun readVersionHistory(): String? {
        return versionHistoryLoaderForTesting?.invoke(requireContext()) ?: runCatching {
            requireContext().assets.open(CHANGELOG_ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.language))
            .setMessage(getString(R.string.language_changed))
            .setPositiveButton(getString(R.string.restart)) { _, _ ->
                restartApp()
            }
            .setNegativeButton(getString(R.string.later)) { _, _ ->
                Toast.makeText(requireContext(), getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun restartApp() {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
        requireActivity().finish()
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CHANGELOG_ASSET_NAME = "CHANGELOG.txt"

        /** Keeps the UI test hermetic while production always reads the packaged asset. */
        internal var versionHistoryLoaderForTesting: ((android.content.Context) -> String?)? = null
    }
}
