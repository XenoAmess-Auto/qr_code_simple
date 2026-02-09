package com.example.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.qrcodesimple.databinding.ActivityResultBinding
import com.example.qrcodesimple.databinding.ItemQrResultBinding
import com.king.wechat.qrcode.WeChatQRCodeDetector
import org.opencv.core.Mat
import java.util.ArrayList

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var adapter: QRResultAdapter
    private val results = mutableListOf<QRResult>()
    private var processedBitmap: Bitmap? = null

    companion object {
        const val EXTRA_BITMAP_URI = "bitmap_uri"
    }

    data class QRResult(
        val text: String,
        var isSelected: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupButtons()

        val uriString = intent.getStringExtra(EXTRA_BITMAP_URI)
        if (uriString != null) {
            // 确保 WeChatQRCode 已初始化（启动时已预加载，这里做二次确认）
            if (!QRCodeApp.ensureInitialized(application)) {
                val errorMsg = QRCodeApp.initErrorMessage ?: "Unknown error"
                Toast.makeText(this, "QR library failed: $errorMsg", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            processImage(Uri.parse(uriString))
        } else {
            Toast.makeText(this, "No image provided", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = QRResultAdapter(results) { position, isSelected ->
            results[position].isSelected = isSelected
            updateSelectionCount()
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnCopySelected.setOnClickListener {
            copySelected()
        }

        binding.btnShareSelected.setOnClickListener {
            shareSelected()
        }

        binding.btnDeleteSelected.setOnClickListener {
            deleteSelected()
        }

        binding.btnSelectAll.setOnClickListener {
            selectAll(true)
        }

        binding.btnDeselectAll.setOnClickListener {
            selectAll(false)
        }
    }

    private fun processImage(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        Thread {
            try {
                val bitmap = loadBitmapFromUri(uri)
                if (bitmap == null) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val points = ArrayList<Mat>()
                val detectedResults = WeChatQRCodeDetector.detectAndDecode(bitmap, points)

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE

                    if (detectedResults.isEmpty()) {
                        binding.tvNoResults.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                        binding.layoutButtons.visibility = View.GONE
                    } else {
                        binding.tvNoResults.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.layoutButtons.visibility = View.VISIBLE
                        binding.ivProcessedImage.visibility = View.VISIBLE

                        // Draw rectangles on bitmap
                        processedBitmap = drawQRCodeRects(bitmap, points)
                        binding.ivProcessedImage.setImageBitmap(processedBitmap)

                        results.clear()
                        results.addAll(detectedResults.map { QRResult(it) })
                        adapter.notifyDataSetChanged()
                        updateSelectionCount()
                    }
                }

                // Release Mats
                points.forEach { it.release() }

            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun drawQRCodeRects(bitmap: Bitmap, points: List<Mat>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        points.forEach { mat ->
            if (mat.rows() >= 4 && mat.cols() >= 1) {
                val path = Path()
                val x0 = mat[0, 0][0].toFloat()
                val y0 = mat[0, 1][0].toFloat()
                path.moveTo(x0, y0)

                for (i in 1 until 4) {
                    val x = mat[i, 0][0].toFloat()
                    val y = mat[i, 1][0].toFloat()
                    path.lineTo(x, y)
                }
                path.lineTo(x0, y0)

                canvas.drawPath(path, paint)
            }
        }

        return mutableBitmap
    }

    private fun updateSelectionCount() {
        val count = results.count { it.isSelected }
        binding.tvSelectionCount.text = "Selected: $count/${results.size}"
    }

    private fun selectAll(select: Boolean) {
        results.forEach { it.isSelected = select }
        adapter.notifyDataSetChanged()
        updateSelectionCount()
    }

    private fun copySelected() {
        val selected = results.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            return
        }

        val text = selected.joinToString("\n") { it.text }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Codes", text))
        Toast.makeText(this, "Copied ${selected.size} item(s)", Toast.LENGTH_SHORT).show()
    }

    private fun shareSelected() {
        val selected = results.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            return
        }

        val text = selected.joinToString("\n") { it.text }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share QR Code content"))
    }

    private fun deleteSelected() {
        val selected = results.filter { it.isSelected }
        if (selected.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Selected")
            .setMessage("Delete ${selected.size} item(s)?")
            .setPositiveButton("Delete") { _, _ ->
                results.removeAll { it.isSelected }
                adapter.notifyDataSetChanged()
                updateSelectionCount()

                if (results.isEmpty()) {
                    binding.tvNoResults.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.layoutButtons.visibility = View.GONE
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_copy_all -> {
                copyAll()
                true
            }
            R.id.action_share_all -> {
                shareAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun copyAll() {
        if (results.isEmpty()) return
        val text = results.joinToString("\n") { it.text }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Codes", text))
        Toast.makeText(this, "Copied all ${results.size} item(s)", Toast.LENGTH_SHORT).show()
    }

    private fun shareAll() {
        if (results.isEmpty()) return
        val text = results.joinToString("\n") { it.text }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share all QR Code content"))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    inner class QRResultAdapter(
        private val items: List<QRResult>,
        private val onItemChecked: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<QRResultAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemQrResultBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemQrResultBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                tvResult.text = item.text
                checkbox.isChecked = item.isSelected

                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    onItemChecked(position, isChecked)
                }

                root.setOnClickListener {
                    checkbox.isChecked = !checkbox.isChecked
                }

                btnCopy.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("QR Code", item.text))
                    Toast.makeText(this@ResultActivity, "Copied", Toast.LENGTH_SHORT).show()
                }

                btnShare.setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.text)
                    }
                    startActivity(Intent.createChooser(intent, "Share"))
                }

                btnEdit.setOnClickListener {
                    showEditDialog(position, item.text)
                }
            }
        }

        override fun getItemCount() = items.size

        private fun showEditDialog(position: Int, currentText: String) {
            val editText = android.widget.EditText(this@ResultActivity).apply {
                setText(currentText)
            }

            AlertDialog.Builder(this@ResultActivity)
                .setTitle("Edit QR Code Content")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    results[position] = results[position].copy(text = editText.text.toString())
                    notifyItemChanged(position)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
