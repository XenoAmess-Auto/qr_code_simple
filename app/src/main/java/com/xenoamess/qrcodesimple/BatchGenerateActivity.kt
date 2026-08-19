package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnAttach
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ActivityBatchGenerateBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 批量生成条码 Activity
 */
class BatchGenerateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchGenerateBinding
    internal var selectedFormat: BarcodeFormat = BarcodeFormat.QR_CODE
    private var importedItems: List<BatchGenerator.BatchItem>? = null
    private var restoredInputText: String? = null
    private var stateCacheToken: String? = null

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
        restoreInstanceState(savedInstanceState)
        binding = ActivityBatchGenerateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.etContent.isSaveEnabled = false
        binding.etContent.filters = binding.etContent.filters +
            InputFilter.LengthFilter(MAX_INPUT_CODE_UNITS) +
            InputFilter { source, start, end, dest, dstart, dend ->
                val replacement = source.subSequence(start, end).toString()
                val candidate = buildString(dest.length - (dend - dstart) + replacement.length) {
                    append(dest, 0, dstart)
                    append(replacement)
                    append(dest, dend, dest.length)
                }
                if (batchStateBytes(candidate, importedItems).size <= STATE_CACHE_MAX_BYTES) {
                    null
                } else {
                    binding.etContent.post { showBatchLimitError(BatchResultTransfer.Limit.TOTAL_BYTES) }
                    dest.subSequence(dstart, dend)
                }
            }
        restoredInputText?.let {
            binding.etContent.setText(it)
            binding.etContent.setSelection(it.length)
        }

        setupEdgeToEdge()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.batch_generate)

        setupFormatSelector()
        setupButtons()
        updateBatchStyleButton()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val token = persistBatchState()
        token?.let { outState.putString(STATE_CACHE_TOKEN, it) }
        outState.putString(STATE_SELECTED_FORMAT, selectedFormat.name)
        batchScheme?.let { outState.putString(STATE_BATCH_SCHEME, it.copy(logoBitmap = null).toJson()) }
        if (batchLogo != null && token != null) {
            if (persistBatchLogo(token)) {
                outState.putString(STATE_BATCH_LOGO_TOKEN, token)
            } else {
                showStateSaveFailure(getString(R.string.logo))
                if (PrivateStateFileStore.existingFile(
                        this,
                        STATE_CACHE_DIRECTORY,
                        token,
                        BATCH_STATE_LOGO_EXTENSION
                    ) != null
                ) {
                    outState.putString(STATE_BATCH_LOGO_TOKEN, token)
                }
            }
        }
    }

    private fun restoreInstanceState(state: Bundle?) {
        if (state == null) {
            cleanupStateCache()
            return
        }
        selectedFormat = state.getString(STATE_SELECTED_FORMAT)?.let { name ->
            BarcodeFormat.entries.find { it.name == name }
        } ?: BarcodeFormat.QR_CODE
        batchScheme = state.getString(STATE_BATCH_SCHEME)?.let(::styleConfigFromJson)
        val token = PrivateStateFileStore.validToken(state.getString(STATE_CACHE_TOKEN)) ?: run {
            cleanupStateCache()
            return
        }
        cleanupStateCache()
        runCatching {
            val root = JSONObject(
                PrivateStateFileStore.read(this, STATE_CACHE_DIRECTORY, token, STATE_CACHE_MAX_BYTES)
                    .toString(Charsets.UTF_8)
            )
            check(root.optInt("version") == 1)
            val itemArray = root.optJSONArray("importedItems")
            val restoredItems = itemArray?.let {
                BatchGenerator.itemsFromJson(it.toString()).also { items ->
                    check(items.size == it.length())
                    check(BatchResultTransfer.validate(items) == null)
                }
            }
            val text = if (root.has("inputText")) {
                root.getString("inputText")
            } else {
                checkNotNull(restoredItems).joinToString("\n") { it.content }
            }
            check(batchStateBytes(text, restoredItems).size <= STATE_CACHE_MAX_BYTES)
            restoredInputText = text
            importedItems = restoredItems
            stateCacheToken = token
            val logoToken = PrivateStateFileStore.validToken(state.getString(STATE_BATCH_LOGO_TOKEN))
            if (logoToken == token) {
                val logoFile = PrivateStateFileStore.existingFile(
                    this,
                    STATE_CACHE_DIRECTORY,
                    logoToken,
                    BATCH_STATE_LOGO_EXTENSION
                )
                batchLogo = logoFile?.let { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }
            }
        }.onFailure {
            PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, token)
            stateCacheToken = null
        }
        cleanupStateCache(stateCacheToken)
    }

    private fun persistBatchState(): String? {
        val inputText = binding.etContent.text?.toString().orEmpty()
        if (inputText.isEmpty() && importedItems == null && batchLogo == null) {
            discardStateCache()
            cleanupStateCache()
            return null
        }
        val bytes = batchStateBytes(inputText, importedItems)
        if (bytes.size > STATE_CACHE_MAX_BYTES) {
            showStateSaveFailure(getString(R.string.batch_limit_total_size, STATE_CACHE_MAX_BYTES / (1024 * 1024)))
            return stateCacheToken
        }
        val token = PrivateStateFileStore.newToken()
        return runCatching {
            PrivateStateFileStore.write(this, STATE_CACHE_DIRECTORY, bytes, STATE_CACHE_MAX_BYTES, token)
            val previousToken = stateCacheToken
            stateCacheToken = token
            previousToken?.takeIf { it != token }?.let { PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, it) }
            cleanupStateCache(token)
            token
        }.getOrElse { failure ->
            PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, token)
            showStateSaveFailure(failure.message ?: getString(R.string.unknown_error))
            stateCacheToken
        }
    }

    private fun showStateSaveFailure(reason: String) {
        Toast.makeText(this, getString(R.string.failed_to_save, reason), Toast.LENGTH_LONG).show()
    }

    private fun discardStateCache() {
        PrivateStateFileStore.delete(this, STATE_CACHE_DIRECTORY, stateCacheToken)
        stateCacheToken = null
    }

    private fun cleanupStateCache(activeToken: String? = null) {
        PrivateStateFileStore.cleanupExpired(this, STATE_CACHE_DIRECTORY, STATE_CACHE_MAX_AGE_MS, activeToken)
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
            discardStateCache()
        }

        binding.btnBatchStyle.setOnClickListener {
            showBatchStyleDialog()
        }
    }

    internal var batchScheme: AdvancedBarcodeGenerator.StyleConfig? = null
    internal var batchLogo: android.graphics.Bitmap? = null
    private var draftBatchScheme: AdvancedBarcodeGenerator.StyleConfig? = null
    private var draftBatchLogo: android.graphics.Bitmap? = null
    private var isEditingBatchStyle = false
    private var draftLogoButton: MaterialButton? = null

    private val pickBatchLogoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            if (isEditingBatchStyle) {
                draftBatchLogo = decodeLogoBitmap(uri)
                draftLogoButton?.text = getString(R.string.logo) + if (draftBatchLogo != null) " ✓" else ""
            }
        } catch (e: Exception) {
            android.util.Log.e("BatchGenerate", "logo decode failed", e)
        }
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

    private fun persistBatchLogo(token: String): Boolean {
        val logo = batchLogo ?: return false
        val output = ByteArrayOutputStream()
        return runCatching {
            check(logo.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            PrivateStateFileStore.write(
                this,
                STATE_CACHE_DIRECTORY,
                output.toByteArray(),
                BATCH_STATE_LOGO_MAX_BYTES,
                token,
                BATCH_STATE_LOGO_EXTENSION
            )
        }.isSuccess
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
                if (selected) primary else getColor(R.color.app_outline_variant)
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
        draftBatchScheme = batchScheme
        draftBatchLogo = batchLogo
        isEditingBatchStyle = true
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
                child.background = createSchemeDonut(scheme, draftBatchScheme == scheme)
            }
        }

        schemes.forEach { scheme ->
            val view = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(donutSize, donutSize).apply {
                    setMargins(margin, margin, margin, margin)
                }
                tag = scheme
                background = createSchemeDonut(scheme, draftBatchScheme == scheme)
                setOnClickListener {
                    draftBatchScheme = if (draftBatchScheme == scheme) null else scheme
                    refreshDonuts()
                }
            }
            row.addView(view)
        }

        val btnLogo = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.logo) + if (draftBatchLogo != null) " ✓" else ""
            setOnClickListener { pickBatchLogoLauncher.launch("image/*") }
        }
        draftLogoButton = btnLogo
        val btnClearLogo = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.clear)
            setOnClickListener {
                draftBatchLogo = null
                btnLogo.text = getString(R.string.logo)
            }
        }
        val logoRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(btnLogo, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnClearLogo, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(logoRow)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.style))
            .setView(root)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                batchScheme = draftBatchScheme
                batchLogo = draftBatchLogo
                if (batchLogo == null) {
                    PrivateStateFileStore.delete(
                        this,
                        STATE_CACHE_DIRECTORY,
                        stateCacheToken,
                        BATCH_STATE_LOGO_EXTENSION
                    )
                }
                updateBatchStyleButton()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnDismissListener {
                draftBatchScheme = null
                draftBatchLogo = null
                draftLogoButton = null
                isEditingBatchStyle = false
            }
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

            result.limitExceeded?.let { limit ->
                showBatchLimitError(limit)
                return@launch
            }

            if (result.errors.isNotEmpty()) {
                Toast.makeText(
                    this@BatchGenerateActivity,
                    getString(R.string.batch_import_errors, result.errors.size),
                    Toast.LENGTH_LONG
                ).show()
            }

            if (result.items.isNotEmpty()) {
                val previewText = result.items.joinToString("\n") { it.content }
                if (batchStateBytes(previewText, result.items).size > STATE_CACHE_MAX_BYTES) {
                    showBatchLimitError(BatchResultTransfer.Limit.TOTAL_BYTES)
                    return@launch
                }
                importedItems = result.items
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
                    val outputStream = contentResolver.openOutputStream(uri)
                        ?: error(getString(R.string.unknown_error))
                    outputStream.use {
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

        val items = BatchGenerator.resolveBatchInput(text, selectedFormat, importedItems)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_valid_content), Toast.LENGTH_SHORT).show()
            return
        }
        BatchResultTransfer.validate(items)?.let {
            showBatchLimitError(it)
            return
        }
        val styleJson = if (batchScheme != null || batchLogo != null) {
            (batchScheme ?: AdvancedBarcodeGenerator.StyleConfig()).copy(logoBitmap = null).toJson()
        } else {
            null
        }
        runCatching { BatchResultTransfer.createIntent(this, items, styleJson, batchLogo) }
            .onSuccess(::startActivity)
            .onFailure {
                Toast.makeText(this, R.string.batch_data_unavailable, Toast.LENGTH_LONG).show()
            }
    }

    private fun showBatchLimitError(limit: BatchResultTransfer.Limit) {
        val message = when (limit) {
            BatchResultTransfer.Limit.ITEM_COUNT -> getString(R.string.batch_limit_items, BatchResultTransfer.MAX_ITEMS)
            BatchResultTransfer.Limit.ITEM_LENGTH -> getString(R.string.batch_limit_item_length, BatchResultTransfer.MAX_ITEM_CHARACTERS)
            BatchResultTransfer.Limit.TOTAL_BYTES -> getString(
                R.string.batch_limit_total_size,
                BatchResultTransfer.MAX_SERIALIZED_BYTES / (1024 * 1024)
            )
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        if (isFinishing) {
            discardStateCache()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BATCH_TOKEN = "batch_token"
        private const val BATCH_STATE_LOGO_EXTENSION = "png"
        private const val BATCH_STATE_LOGO_MAX_BYTES = 4 * 1024 * 1024
        private const val STATE_CACHE_TOKEN = "batch_cache_token"
        private const val STATE_SELECTED_FORMAT = "batch_selected_format"
        private const val STATE_BATCH_SCHEME = "batch_scheme"
        private const val STATE_BATCH_LOGO_TOKEN = "batch_logo_token"
        private const val STATE_CACHE_DIRECTORY = "batch-generate-state"
        internal const val STATE_CACHE_MAX_BYTES = BatchResultTransfer.MAX_SERIALIZED_BYTES
        private const val MAX_INPUT_CODE_UNITS = STATE_CACHE_MAX_BYTES
        private const val STATE_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

        internal fun batchStateBytes(
            inputText: String,
            importedItems: List<BatchGenerator.BatchItem>?
        ): ByteArray = JSONObject().apply {
            put("version", 1)
            val activeImportedItems = importedItems?.takeIf {
                inputText == it.joinToString("\n") { item -> item.content }
            }
            if (activeImportedItems == null) {
                put("inputText", inputText)
            } else {
                put("importedItems", JSONArray(BatchGenerator.itemsToJson(activeImportedItems)))
            }
        }.toString().toByteArray(Charsets.UTF_8)
    }
}
