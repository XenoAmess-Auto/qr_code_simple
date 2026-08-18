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
    private var importedItems: List<BatchGenerator.BatchItem>? = null

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
                            it.displayName.equals(text, ignoreCase = true) ||
                            it.name.equals(text, ignoreCase = true)
                    }
                    selectedFormat = matched ?: pendingFormatBeforeFocus ?: selectedFormat
                    pendingFormatBeforeFocus = null
                    binding.spinnerFormat.setText(selectedFormat.localizedNameWithEnglish(this@BatchGenerateActivity), false)
                    adapter.resetFilter()
                }
            }
        }

        binding.spinnerFormat.setOnItemClickListener { _, _, position, _ ->
            val format = adapter.getItem(position) ?: return@setOnItemClickListener
            selectedFormat = format
            pendingFormatBeforeFocus = null
            binding.spinnerFormat.setText(format.localizedNameWithEnglish(this), false)
            adapter.resetFilter()
        }

        binding.spinnerFormat.setText(selectedFormat.localizedNameWithEnglish(this), false)
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
            importedItems = null
        }

        binding.btnBatchStyle.setOnClickListener {
            showBatchStyleDialog()
        }
    }

    internal var batchScheme: AdvancedBarcodeGenerator.StyleConfig? = null
    internal var batchLogo: android.graphics.Bitmap? = null

    private val pickBatchLogoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            batchLogo = decodeLogoBitmap(uri)
        } catch (e: Exception) {
            android.util.Log.e("BatchGenerate", "logo decode failed", e)
        }
        updateBatchStyleButton()
    }

    private fun decodeLogoBitmap(uri: Uri): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, bounds)
        }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxDim / sample > 512) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, opts)
        }
    }

    private fun updateBatchStyleButton() {
        val active = batchScheme != null || batchLogo != null
        binding.btnBatchStyle.text = getString(R.string.style) + if (active) " ✓" else ""
    }

    private fun createSchemeDonut(scheme: AdvancedBarcodeGenerator.StyleConfig, selected: Boolean): android.graphics.drawable.Drawable {
        val density = resources.displayMetrics.density
        val outer = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(scheme.backgroundColor)
            val primary = com.google.android.material.color.MaterialColors.getColor(
                this@BatchGenerateActivity,
                androidx.appcompat.R.attr.colorPrimary,
                "BatchGenerateActivity"
            )
            setStroke(
                (if (selected) 3 else 1) * density.toInt().coerceAtLeast(1),
                if (selected) primary else android.graphics.Color.LTGRAY
            )
        }
        val inner = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(scheme.gradientStops.firstOrNull()?.color ?: scheme.foregroundColor)
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(outer, inner)).apply {
            val inset = (12 * density).toInt()
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun showBatchStyleDialog() {
        val schemes = listOf(
            AdvancedBarcodeGenerator.ColorSchemes.CLASSIC,
            AdvancedBarcodeGenerator.ColorSchemes.BLUE,
            AdvancedBarcodeGenerator.ColorSchemes.GREEN,
            AdvancedBarcodeGenerator.ColorSchemes.RED,
            AdvancedBarcodeGenerator.ColorSchemes.PURPLE,
            AdvancedBarcodeGenerator.ColorSchemes.ORANGE,
            AdvancedBarcodeGenerator.ColorSchemes.CYAN,
            AdvancedBarcodeGenerator.ColorSchemes.DARK,
            AdvancedBarcodeGenerator.ColorSchemes.QQ
        )
        val density = resources.displayMetrics.density
        val donutSize = (48 * density).toInt()
        val margin = (8 * density).toInt()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
        }
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val scroll = android.widget.HorizontalScrollView(this).apply {
            addView(row)
        }
        root.addView(scroll)

        fun refreshDonuts() {
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                @Suppress("UNCHECKED_CAST")
                val scheme = child.tag as AdvancedBarcodeGenerator.StyleConfig
                child.background = createSchemeDonut(scheme, batchScheme == scheme)
            }
        }

        schemes.forEach { scheme ->
            val view = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(donutSize, donutSize).apply {
                    setMargins(margin, margin, margin, margin)
                }
                tag = scheme
                background = createSchemeDonut(scheme, batchScheme == scheme)
                setOnClickListener {
                    batchScheme = if (batchScheme == scheme) null else scheme
                    refreshDonuts()
                }
            }
            row.addView(view)
        }

        val btnLogo = android.widget.Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.logo) + if (batchLogo != null) " ✓" else ""
            setOnClickListener { pickBatchLogoLauncher.launch("image/*") }
        }
        val btnClearLogo = android.widget.Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.clear)
            setOnClickListener {
                batchLogo = null
                btnLogo.text = getString(R.string.logo)
                updateBatchStyleButton()
            }
        }
        val logoRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(btnLogo, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnClearLogo, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(logoRow)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.style))
            .setView(root)
            .setPositiveButton(getString(R.string.apply)) { _, _ -> updateBatchStyleButton() }
            .show()
    }

    internal fun importFromFile(uri: Uri) {
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
                    getString(R.string.batch_import_errors, result.errors.size),
                    Toast.LENGTH_LONG
                ).show()
            }

            if (result.items.isNotEmpty()) {
                importedItems = result.items
                val previewText = result.items.joinToString("\n") { it.content }
                binding.etContent.setText(previewText)
                binding.etContent.setSelection(binding.etContent.text?.length ?: 0)
                Toast.makeText(
                    this@BatchGenerateActivity,
                    getString(R.string.batch_items_imported, result.items.size),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@BatchGenerateActivity,
                    getString(R.string.batch_no_items),
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
                    Toast.makeText(this, getString(R.string.batch_template_saved), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.failed_to_save, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateBatch() {
        val text = binding.etContent.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.please_enter_content), Toast.LENGTH_SHORT).show()
            return
        }

        val items = importedItems ?: BatchGenerator.parseSimpleBatch(text, selectedFormat)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_valid_content), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, BatchResultActivity::class.java).apply {
            putExtra(EXTRA_BATCH_ITEMS_JSON, BatchGenerator.itemsToJson(items))
        }
        // 样式经 Intent 传递（styleJson + logo 落缓存文件），进程被杀重建后不丢
        if (batchScheme != null || batchLogo != null) {
            val style = (batchScheme ?: AdvancedBarcodeGenerator.StyleConfig()).copy(logoBitmap = null)
            intent.putExtra(EXTRA_STYLE_JSON, style.toJson())
            batchLogo?.let { logo ->
                runCatching {
                    val logoFile = java.io.File(cacheDir, BATCH_LOGO_FILE)
                    java.io.FileOutputStream(logoFile).use { out ->
                        logo.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    intent.putExtra(EXTRA_LOGO_PATH, logoFile.absolutePath)
                }
            }
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
        const val EXTRA_BATCH_ITEMS_JSON = "batch_items_json"
        const val EXTRA_STYLE_JSON = "style_json"
        const val EXTRA_LOGO_PATH = "logo_path"
        const val BATCH_LOGO_FILE = "batch_logo.png"
    }
}
