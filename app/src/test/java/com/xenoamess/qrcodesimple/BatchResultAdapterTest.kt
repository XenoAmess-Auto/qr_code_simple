package com.xenoamess.qrcodesimple

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.databinding.ItemBatchResultBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchResultAdapterTest {

    private lateinit var scenario: ActivityScenario<BatchResultActivity>

    @Before
    fun setup() {
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(),
            BatchResultActivity::class.java
        ).apply {
            putStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS, arrayListOf("test"))
            putExtra(BatchGenerateActivity.EXTRA_FORMAT, BarcodeFormat.QR_CODE.name)
        }
        scenario = ActivityScenario.launch(intent)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun createBitmap(): Bitmap {
        return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            setPixel(0, 0, android.graphics.Color.BLACK)
        }
    }

    @Test
    fun adapterRendersSuccessAndErrorItems() {
        val results = listOf(
            BatchResultActivity.BatchResult("success", createBitmap(), "file1"),
            BatchResultActivity.BatchResult("failed", null, "file2")
        )

        var adapter: BatchResultActivity.BatchResultAdapter? = null
        scenario.onActivity { activity ->
            adapter = activity.BatchResultAdapter(results) { _, _ -> }
        }
        assertNotNull(adapter)
        assertEquals(2, adapter!!.itemCount)

        val parent = LinearLayout(ApplicationProvider.getApplicationContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val holder = adapter!!.onCreateViewHolder(parent, 0)
        adapter!!.onBindViewHolder(holder, 0)
        idleMain()

        assertEquals("success", holder.binding.tvContent.text.toString())
        assertEquals("file1", holder.binding.tvFileName.text.toString())
        assertEquals(RecyclerView.VISIBLE, holder.binding.btnSave.visibility)
        assertEquals(RecyclerView.GONE, holder.binding.tvError.visibility)
        assertNotNull((holder.binding.ivBarcode.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap)

        adapter!!.onBindViewHolder(holder, 1)
        idleMain()
        assertEquals(RecyclerView.GONE, holder.binding.btnSave.visibility)
        assertEquals(RecyclerView.VISIBLE, holder.binding.tvError.visibility)
        assertEquals("Failed to generate", holder.binding.tvError.text.toString())
    }

    @Test
    fun saveClickInvokesCallback() {
        var callbackPosition = -1
        var callbackBitmap: Bitmap? = null
        val bitmap = createBitmap()
        val results = listOf(BatchResultActivity.BatchResult("save-me", bitmap, "save_file"))

        var adapter: BatchResultActivity.BatchResultAdapter? = null
        scenario.onActivity { activity ->
            adapter = activity.BatchResultAdapter(results) { pos, bmp ->
                callbackPosition = pos
                callbackBitmap = bmp
            }
        }

        val parent = LinearLayout(ApplicationProvider.getApplicationContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val holder = adapter!!.onCreateViewHolder(parent, 0)
        adapter!!.onBindViewHolder(holder, 0)
        holder.binding.btnSave.performClick()
        idleMain()

        assertEquals(0, callbackPosition)
        assertNotNull(callbackBitmap)
    }
}
