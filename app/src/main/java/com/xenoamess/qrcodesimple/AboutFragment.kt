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
        binding.tvVersion.text = getString(R.string.version_with_prefix, versionName)

        // 更新语言按钮显示当前语言
        updateLanguageButton()

        binding.btnGitHubProject.setOnClickListener {
            openUrl("https://github.com/XenoAmess-Auto/qr_code_simple")
        }

        binding.btnGitHubMaintainer.setOnClickListener {
            openUrl("https://github.com/XenoAmess")
        }

        binding.btnDonate.setOnClickListener {
            openUrl("https://ko-fi.com/xenoamess")
        }

        binding.btnLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.btnPrivacy.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacySettingsActivity::class.java))
        }
    }

    private fun updateLanguageButton() {
        val currentLang = LocaleHelper.getCurrentLanguageDisplayName(requireContext())
        binding.btnLanguage.text = "${getString(R.string.language)}: $currentLang"
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
                    LocaleHelper.setApplicationLocale(selectedLanguage.code)
                    updateLanguageButton()
                    showRestartDialog()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
}
