package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityBatchGenerateBinding
import kotlinx.coroutines.launch

/**
 * 批量生成条码 Activity
 */
class BatchGenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchGenerateBinding
    private var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE

    private val pickCsvLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importFromCsv(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applyLanguage(this)
        super.onCreate(savedInstanceState)
        binding = ActivityBatchGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置沉浸式状态栏并处理安全区域
        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.batch_generate)

        setupFormatSpinner()
        setupButtons()
    }

    private fun setupFormatSpinner() {
        val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            formats.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFormat.adapter = adapter

        binding.spinnerFormat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFormat = formats[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnImportCsv.setOnClickListener {
            pickCsvLauncher.launch("text/csv")
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

    private fun importFromCsv(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            
            val result = BatchGenerator.parseCsv(this@BatchGenerateActivity, uri)
            
            binding.progressBar.visibility = View.GONE

            if (result.errors.isNotEmpty()) {
                Toast.makeText(
                    this@BatchGenerateActivity,
                    "Import completed with ${result.errors.size} errors",
                    Toast.LENGTH_LONG
                ).show()
            }

            if (result.items.isNotEmpty()) {
                // 显示预览
                val previewText = result.items.joinToString("\n") { it.content }
                binding.etContent.setText(previewText)
                Toast.makeText(
                    this@BatchGenerateActivity,
                    "Imported ${result.items.size} items",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@BatchGenerateActivity,
                    "No valid items found in CSV",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

        // 跳转到结果页面进行批量生成
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
