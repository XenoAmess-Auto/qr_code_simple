package com.xenoamess.qrcodesimple

import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.xenoamess.qrcodesimple.ui.result.QRResult
import com.xenoamess.qrcodesimple.ui.result.QRResultAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class QRResultAdapterTest : BaseAdapterTest() {

    private lateinit var activity: AppCompatActivity
    private lateinit var contentActionHandler: ContentActionHandler

    @Before
    override fun setup() {
        super.setup()
        activity = Robolectric.buildActivity(AppCompatActivity::class.java, Intent())
            .create()
            .start()
            .resume()
            .get()
        contentActionHandler = ContentActionHandler(activity)
    }

    private fun createRecyclerView(): RecyclerView {
        val recyclerView = RecyclerView(ContextThemeWrapper(activity, R.style.Theme_QRCodeSimple))
        recyclerView.layoutManager = LinearLayoutManager(activity)
        return recyclerView
    }

    private fun bindItem(
        item: QRResult,
        withContentActionHandler: Boolean = true,
        withLifecycleScope: Boolean = false,
        withOnEdit: Boolean = false,
        onItemChecked: (Int, Boolean) -> Unit = { _, _ -> }
    ): QRResultAdapter.ViewHolder {
        val adapter = QRResultAdapter(
            items = mutableListOf(item),
            onItemChecked = onItemChecked,
            contentActionHandler = if (withContentActionHandler) contentActionHandler else null,
            lifecycleScope = if (withLifecycleScope) activity.lifecycleScope else null,
            onEdit = if (withOnEdit) { _, _ -> } else null
        )
        val recyclerView = createRecyclerView()
        recyclerView.adapter = adapter
        val holder = adapter.createViewHolder(recyclerView, 0)
        adapter.onBindViewHolder(holder, 0)
        flushMainLooper()
        return holder
    }

    @Test
    fun textItemDisplaysTextTypeAndHidesLabel() {
        val holder = bindItem(QRResult("plain text"))
        val tvTypeLabel = holder.itemView.findViewById<TextView>(R.id.tvTypeLabel)
        val ivTypeIcon = holder.itemView.findViewById<View>(R.id.ivTypeIcon)
        assertEquals(View.GONE, tvTypeLabel.visibility)
        assertEquals(View.VISIBLE, ivTypeIcon.visibility)
    }

    @Test
    fun urlItemDisplaysUrlLabelAndSmartAction() {
        val holder = bindItem(QRResult("https://example.com"))
        val tvTypeLabel = holder.itemView.findViewById<TextView>(R.id.tvTypeLabel)
        val smartActions = holder.itemView.findViewById<ViewGroup>(R.id.layoutSmartActions)
        assertEquals(activity.getString(R.string.content_type_url), tvTypeLabel.text.toString())
        assertEquals(View.VISIBLE, smartActions.visibility)
        assertEquals(1, smartActions.childCount)
        val button = smartActions.getChildAt(0) as TextView
        assertEquals(activity.getString(R.string.action_open_url), button.text.toString())
    }

    @Test
    fun wifiItemDisplaysWifiSmartAction() {
        val holder = bindItem(QRResult("WIFI:T:WPA;S:test;P:pass;;"))
        val tvTypeLabel = holder.itemView.findViewById<TextView>(R.id.tvTypeLabel)
        val smartActions = holder.itemView.findViewById<ViewGroup>(R.id.layoutSmartActions)
        assertEquals(activity.getString(R.string.content_type_wifi), tvTypeLabel.text.toString())
        assertEquals(1, smartActions.childCount)
        val button = smartActions.getChildAt(0) as TextView
        assertEquals(activity.getString(R.string.action_connect_wifi), button.text.toString())
    }

    @Test
    fun contactItemDisplaysContactLabel() {
        val holder = bindItem(QRResult("MECARD:N:John Doe;TEL:123;;"))
        val tvTypeLabel = holder.itemView.findViewById<TextView>(R.id.tvTypeLabel)
        assertEquals(activity.getString(R.string.content_type_contact), tvTypeLabel.text.toString())
    }

    @Test
    fun libraryPrefixShownWhenContentActionHandlerProvided() {
        val holder = bindItem(QRResult("code", library = QRCodeScanner.Library.ZXING))
        val tvResult = holder.itemView.findViewById<TextView>(R.id.tvResult)
        assertTrue(tvResult.text.toString().startsWith("[ZXING]"))
    }

    @Test
    fun libraryPrefixHiddenWithoutContentActionHandler() {
        val holder = bindItem(QRResult("code", library = QRCodeScanner.Library.ZXING), withContentActionHandler = false)
        val tvResult = holder.itemView.findViewById<TextView>(R.id.tvResult)
        assertEquals("code", tvResult.text.toString())
    }

    @Test
    fun selectedItemChecksCheckbox() {
        val holder = bindItem(QRResult("selected", isSelected = true))
        val checkbox = holder.itemView.findViewById<CheckBox>(R.id.checkbox)
        assertTrue(checkbox.isChecked)
    }

    @Test
    fun unselectedItemUnchecksCheckbox() {
        val holder = bindItem(QRResult("unselected", isSelected = false))
        val checkbox = holder.itemView.findViewById<CheckBox>(R.id.checkbox)
        assertFalse(checkbox.isChecked)
    }

    @Test
    fun editButtonVisibleOnlyWithOnEdit() {
        val holderWithoutEdit = bindItem(QRResult("no edit"), withOnEdit = false)
        assertEquals(View.GONE, holderWithoutEdit.itemView.findViewById<View>(R.id.btnEdit).visibility)

        val holderWithEdit = bindItem(QRResult("with edit"), withOnEdit = true)
        assertEquals(View.VISIBLE, holderWithEdit.itemView.findViewById<View>(R.id.btnEdit).visibility)
    }

    @Test
    fun checkboxClickTriggersCallback() {
        val callbacks = mutableListOf<Pair<Int, Boolean>>()
        val holder = bindItem(QRResult("click me")) { position, checked ->
            callbacks.add(position to checked)
        }
        val checkbox = holder.itemView.findViewById<CheckBox>(R.id.checkbox)
        checkbox.performClick()
        flushMainLooper()
        assertEquals(1, callbacks.size)
        assertEquals(0 to true, callbacks[0])
    }

    @Test
    fun rootClickTogglesCheckbox() {
        val callbacks = mutableListOf<Pair<Int, Boolean>>()
        val holder = bindItem(QRResult("root click")) { position, checked ->
            callbacks.add(position to checked)
        }
        holder.itemView.performClick()
        flushMainLooper()
        assertEquals(1, callbacks.size)
        assertEquals(0 to true, callbacks[0])
    }

    @Test
    fun securityIndicatorVisibleForSafeUrl() {
        val holder = bindItem(QRResult("https://example.com"), withLifecycleScope = true)
        val layout = holder.itemView.findViewById<View>(R.id.layoutSecurityIndicator)
        val tvStatus = holder.itemView.findViewById<TextView>(R.id.tvSecurityStatus)
        assertEquals(View.VISIBLE, layout.visibility)
        assertEquals("Link looks safe", tvStatus.text.toString())
    }

    @Test
    fun securityIndicatorShowsHighRiskForBlacklistedUrl() {
        val holder = bindItem(QRResult("https://phishing.com/steal"), withLifecycleScope = true)
        flushMainLooper()
        val tvStatus = holder.itemView.findViewById<TextView>(R.id.tvSecurityStatus)
        assertEquals("Dangerous link", tvStatus.text.toString())
    }

    @Test
    fun securityIndicatorHiddenForNonUrl() {
        val holder = bindItem(QRResult("plain text"), withLifecycleScope = true)
        val layout = holder.itemView.findViewById<View>(R.id.layoutSecurityIndicator)
        assertEquals(View.GONE, layout.visibility)
    }

    @Test
    fun securityIndicatorHiddenWithoutLifecycleScope() {
        val holder = bindItem(QRResult("https://example.com"), withLifecycleScope = false)
        val layout = holder.itemView.findViewById<View>(R.id.layoutSecurityIndicator)
        assertEquals(View.GONE, layout.visibility)
    }
}
