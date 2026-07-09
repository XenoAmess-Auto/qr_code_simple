package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnAttach
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityBatchGenerateBinding
import kotlinx.coroutines.launch

/**
 * 批量生成条码 Activity
 */
class BatchGenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchGenerateBinding
    internal var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { importFromFile(it) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.batch_generate)

        setupFormatSelector()
        setupButtons()
    }

    private var pendingFormatBeforeFocus: BarcodeFormat? = null

    private fun setupFormatSelector() {
        val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
        val adapter = BarcodeFormatAdapter(this, formats)
        binding.spinnerFormat.setAdapter(adapter)
        binding.spinnerFormat.threshold = 0

        binding.spinnerFormat.doOnAttach {
            val existingFocusListener = binding.spinnerFormat.onFocusChangeListener
            binding.spinnerFormat.setOnFocusChangeListener { v, hasFocus ->
                existingFocusListener?.onFocusChange(v, hasFocus)
                if (hasFocus) {
                    pendingFormatBeforeFocus = selectedFormat
                    binding.spinnerFormat.setText("", false)
                    adapter.resetFilter()
                    binding.spinnerFormat.showDropDown()
                } else {
                    val text = binding.spinnerFormat.text?.toString()?.trim() ?: ""
                    val matched = formats.find {
                        it.localizedName(this@BatchGenerateActivity).equals(text, ignoreCase = true) ||
                            it.name.equals(text, ignoreCase = true)
                    }
                    selectedFormat = matched ?: pendingFormatBeforeFocus ?: selectedFormat
                    pendingFormatBeforeFocus = null
                    binding.spinnerFormat.setText(selectedFormat.localizedName(this@BatchGenerateActivity), false)
                    adapter.resetFilter()
                }
            }
        }

        binding.spinnerFormat.setOnItemClickListener { _, _, position, _ ->
            val format = adapter.getItem(position) ?: return@setOnItemClickListener
            selectedFormat = format
            pendingFormatBeforeFocus = null
            binding.spinnerFormat.setText(format.localizedName(this), false)
            adapter.resetFilter()
        }

        binding.spinnerFormat.setText(selectedFormat.localizedName(this), false)
    }

    private fun setupButtons() {
        binding.btnImportCsv.text = getString(R.string.import_csv_excel)
        binding.btnImportCsv.setOnClickListener {
            pickFileLauncher.launch(arrayOf(
                "text/csv",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel"
            ))
        }

        binding.btnDownloadTemplate.setOnClickListener {
            downloadTemplate()
        }

        binding.btnGenerate.setOnClickListener {
            generateBatch()
        }

        binding.btnClear.setOnClickListener {
            binding.etContent.text?.clear()
        }
    }

    private fun importFromFile(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            val result = when (getFileExtension(uri)) {
                "xlsx", "xls" -> BatchGenerator.parseExcel(this@BatchGenerateActivity, uri)
                else -> BatchGenerator.parseCsv(this@BatchGenerateActivity, uri)
            }
            
            binding.progressBar.visibility = View.GONE

            if (result.errors.isNotEmpty()) {
                Toast.makeText(
                    this@BatchGenerateActivity,
                    "Import completed with ${result.errors.size} errors",
                    Toast.LENGTH_LONG
                ).show()
            }

            if (result.items.isNotEmpty()) {
                val previewText = result.items.joinToString("\n") { it.content }
                binding.etContent.setText(previewText)
                binding.etContent.setSelection(binding.etContent.text?.length ?: 0)
                Toast.makeText(
                    this@BatchGenerateActivity,
                    "Imported ${result.items.size} items",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@BatchGenerateActivity,
                    "No valid items found",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getFileExtension(uri: Uri): String {
        return contentResolver.getType(uri)?.let { mime ->
            when (mime) {
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                "application/vnd.ms-excel" -> "xls"
                else -> "csv"
            }
        } ?: uri.path?.substringAfterLast('.', "") ?: "csv"
    }

    private fun downloadTemplate() {
        val template = BatchGenerator.generateTemplate()
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "batch_template.csv")
        }
        
        saveTemplateLauncher.launch(intent)
    }

    private val saveTemplateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(BatchGenerator.generateTemplate().toByteArray())
                    }
                    Toast.makeText(this, "Template saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateBatch() {
        val text = binding.etContent.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter content", Toast.LENGTH_SHORT).show()
            return
        }

        val items = BatchGenerator.parseSimpleBatch(text, selectedFormat)
        if (items.isEmpty()) {
            Toast.makeText(this, "No valid content found", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, BatchResultActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_CONTENTS, ArrayList(items.map { it.content }))
            putExtra(EXTRA_FORMAT, selectedFormat.name)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_CONTENTS = "contents"
        const val EXTRA_FORMAT = "format"
    }
}
