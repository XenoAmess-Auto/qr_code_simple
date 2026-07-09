package com.xenoamess.qrcodesimple

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.utils.test.TestDataFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class HistoryAdapterTest : BaseAdapterTest() {

    @Before
    override fun setup() {
        super.setup()
        context = ContextThemeWrapper(context, R.style.Theme_QRCodeSimple)
    }


    private fun createAdapterWithTracking(): Pair<HistoryAdapter, MutableList<HistoryItem>> {
        val clicked = mutableListOf<HistoryItem>()
        val adapter = HistoryAdapter(
            onItemClick = { clicked.add(it) },
            onEdit = { clicked.add(it) },
            onShare = { clicked.add(it) },
            onShareQR = { clicked.add(it) },
            onDelete = { clicked.add(it) },
            onFavorite = { clicked.add(it) },
            onAddNote = { clicked.add(it) }
        )
        return adapter to clicked
    }

    private fun bindFirstItem(adapter: HistoryAdapter, item: HistoryItem): RecyclerView.ViewHolder {
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.submitList(listOf(item))
        flushMainLooper()
        val holder = adapter.createViewHolder(recyclerView, 0)
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    @Test
    fun scannedQrItemDisplaysCorrectTypeLabel() {
        val (adapter, _) = createAdapterWithTracking()
        val item = TestDataFactory.historyItem(
            content = "https://example.com",
            type = HistoryType.QR_CODE,
            isGenerated = false
        )
        val holder = bindFirstItem(adapter, item)
        val tvContent = holder.itemView.findViewById<TextView>(R.id.tvContent)
        val tvType = holder.itemView.findViewById<TextView>(R.id.tvType)
        assertEquals("https://example.com", tvContent.text.toString())
        assertTrue(tvType.text.toString().contains(context.getString(R.string.type_scanned)))
        assertTrue(tvType.text.toString().contains(context.getString(R.string.type_qr_code)))
    }

    @Test
    fun generatedBarcodeItemDisplaysFormat() {
        val (adapter, _) = createAdapterWithTracking()
        val item = TestDataFactory.historyItem(
            content = "123456789012",
            type = HistoryType.BARCODE,
            isGenerated = true,
            barcodeFormat = "EAN_13"
        )
        val holder = bindFirstItem(adapter, item)
        val tvType = holder.itemView.findViewById<TextView>(R.id.tvType)
        assertTrue(tvType.text.toString().contains(context.getString(R.string.type_generated)))
        assertTrue(tvType.text.toString().contains("EAN_13"))
    }

    @Test
    fun generatedOnlyItemUsesBarcodeFormatLabel() {
        val (adapter, _) = createAdapterWithTracking()
        val item = TestDataFactory.historyItem(
            content = "GRID MATRIX CONTENT",
            type = HistoryType.GENERATED_ONLY,
            isGenerated = true,
            barcodeFormat = "GRID_MATRIX"
        )
        val holder = bindFirstItem(adapter, item)
        val tvType = holder.itemView.findViewById<TextView>(R.id.tvType)
        assertTrue(tvType.text.toString().contains("GRID_MATRIX"))
    }

    @Test
    fun favoriteIconVisibleForFavoriteItem() {
        val (adapter, _) = createAdapterWithTracking()
        val item = TestDataFactory.historyItem(isFavorite = true)
        val holder = bindFirstItem(adapter, item)
        assertEquals(
            View.VISIBLE,
            holder.itemView.findViewById<View>(R.id.ivFavorite).visibility
        )
    }

    @Test
    fun notesVisibleWhenPresent() {
        val (adapter, _) = createAdapterWithTracking()
        val item = TestDataFactory.historyItem(notes = "Important note")
        val holder = bindFirstItem(adapter, item)
        val tvNotes = holder.itemView.findViewById<TextView>(R.id.tvNotes)
        assertEquals(View.VISIBLE, tvNotes.visibility)
        assertEquals("Important note", tvNotes.text.toString())
    }

    @Test
    fun buttonsTriggerCorrectCallbacks() {
        val (adapter, clicked) = createAdapterWithTracking()
        val item = TestDataFactory.historyItem(content = "callback test")
        val holder = bindFirstItem(adapter, item)
        holder.itemView.findViewById<View>(R.id.btnEdit).performClick()
        holder.itemView.findViewById<View>(R.id.btnShare).performClick()
        holder.itemView.findViewById<View>(R.id.btnShareQR).performClick()
        holder.itemView.findViewById<View>(R.id.btnDelete).performClick()
        holder.itemView.findViewById<View>(R.id.btnFavorite).performClick()
        holder.itemView.findViewById<View>(R.id.btnNote).performClick()
        holder.itemView.performClick()
        assertEquals(7, clicked.size)
        clicked.forEach { assertEquals("callback test", it.content) }
    }

    @Test
    fun diffCallbackAreItemsTheSame() {
        val item1 = TestDataFactory.historyItem(id = 1, content = "a")
        val item2 = TestDataFactory.historyItem(id = 1, content = "b")
        val item3 = TestDataFactory.historyItem(id = 2, content = "a")
        val diffCallback = HistoryAdapter.DiffCallback()
        assertTrue(diffCallback.areItemsTheSame(item1, item2))
        assertFalse(diffCallback.areItemsTheSame(item1, item3))
        assertFalse(diffCallback.areContentsTheSame(item1, item2))
        assertTrue(diffCallback.areContentsTheSame(item1, item1))
    }
}
