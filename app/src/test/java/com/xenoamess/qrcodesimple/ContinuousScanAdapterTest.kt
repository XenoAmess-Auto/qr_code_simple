package com.xenoamess.qrcodesimple

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xenoamess.qrcodesimple.data.HistoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ContinuousScanAdapterTest : BaseAdapterTest() {

    @Before
    override fun setup() {
        super.setup()
        context = ContextThemeWrapper(context, R.style.Theme_QRCodeSimple)
    }

    private fun bindFirstItem(
        item: ContinuousScanActivity.ScanResult,
        onCopy: (Int) -> Unit = {},
        onShare: (Int) -> Unit = {},
        onDelete: (Int) -> Unit = {}
    ): RecyclerView.ViewHolder {
        val adapter = ContinuousScanAdapter(listOf(item), onCopy, onShare, onDelete)
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        flushMainLooper()
        val holder = adapter.createViewHolder(recyclerView, 0)
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    @Test
    fun contentDisplayed() {
        val item = ContinuousScanActivity.ScanResult("https://example.com")
        val holder = bindFirstItem(item)
        val tvContent = holder.itemView.findViewById<TextView>(R.id.tvContent)
        assertEquals("https://example.com", tvContent.text.toString())
    }

    @Test
    fun savedIconVisibleForSavedItem() {
        val item = ContinuousScanActivity.ScanResult("saved", isSaved = true)
        val holder = bindFirstItem(item)
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.ivSaved).visibility)
    }

    @Test
    fun savedIconHiddenForUnsavedItem() {
        val item = ContinuousScanActivity.ScanResult("unsaved", isSaved = false)
        val holder = bindFirstItem(item)
        assertEquals(View.GONE, holder.itemView.findViewById<View>(R.id.ivSaved).visibility)
    }

    @Test
    fun timestampFormattedCorrectly() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 14, 35, 42)
        }
        val item = ContinuousScanActivity.ScanResult("code", timestamp = calendar.timeInMillis)
        val holder = bindFirstItem(item)
        val tvTime = holder.itemView.findViewById<TextView>(R.id.tvTime)
        assertTrue(tvTime.text.toString().contains("14:35:42"))
    }

    @Test
    fun copyButtonTriggersCallback() {
        var callbackPosition: Int? = null
        val item = ContinuousScanActivity.ScanResult("copy me")
        val holder = bindFirstItem(item, onCopy = { callbackPosition = it })
        holder.itemView.findViewById<View>(R.id.btnCopy).performClick()
        assertEquals(0, callbackPosition)
    }

    @Test
    fun shareButtonTriggersCallback() {
        var callbackPosition: Int? = null
        val item = ContinuousScanActivity.ScanResult("share me")
        val holder = bindFirstItem(item, onShare = { callbackPosition = it })
        holder.itemView.findViewById<View>(R.id.btnShare).performClick()
        assertEquals(0, callbackPosition)
    }

    @Test
    fun deleteButtonTriggersCallback() {
        var callbackPosition: Int? = null
        val item = ContinuousScanActivity.ScanResult("delete me")
        val holder = bindFirstItem(item, onDelete = { callbackPosition = it })
        holder.itemView.findViewById<View>(R.id.btnDelete).performClick()
        assertEquals(0, callbackPosition)
    }
}
