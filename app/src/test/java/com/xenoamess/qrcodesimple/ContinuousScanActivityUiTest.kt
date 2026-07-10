package com.xenoamess.qrcodesimple

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ContinuousScanActivityUiTest {

    private var scenario: ActivityScenario<ContinuousScanActivity>? = null

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val historyRepository = HistoryRepository(context)
        runBlocking { historyRepository.deleteAll() }
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    private fun runBlocking(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launchActivity(): ActivityScenario<ContinuousScanActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ContinuousScanActivity::class.java)
        val launched = ActivityScenario.launch<ContinuousScanActivity>(intent)
        launched.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario = launched
        return launched
    }

    private fun setField(activity: ContinuousScanActivity, name: String, value: Any?) {
        val field = ContinuousScanActivity::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(activity, value)
    }

    private fun injectResult(activity: ContinuousScanActivity, text: String) {
        val method = ContinuousScanActivity::class.java.getDeclaredMethod(
            "handleScanResult",
            QRCodeScanner.ScanResult::class.java
        )
        method.isAccessible = true
        method.invoke(
            activity,
            QRCodeScanner.ScanResult(text, QRCodeScanner.Library.ZXING, BarcodeFormat.QR_CODE)
        )
    }

    private fun currentItemCount(): Int {
        var count = 0
        scenario?.onActivity { activity ->
            count = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter?.itemCount ?: 0
        }
        return count
    }

    private fun getFirstSavedVisibility(): Int {
        var visibility = View.GONE
        scenario?.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.recyclerView)
            val holder = recyclerView.findViewHolderForAdapterPosition(0)
            visibility = holder?.itemView?.findViewById<View>(R.id.ivSaved)?.visibility ?: View.GONE
        }
        return visibility
    }

    @Test
    fun activityLaunchesWithEmptyList() {
        launchActivity()
        flushMainLooper()
        assertEquals(0, currentItemCount())
        onView(withId(R.id.tvCount)).check(matches(withText("0 items")))
    }

    @Test
    fun addNewResultIncreasesList() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            setField(activity, "scanInterval", 0L)
            setField(activity, "isAutoSaveEnabled", false)
            injectResult(activity, "https://example.com")
        }
        flushMainLooper()
        assertEquals(1, currentItemCount())
        onView(withId(R.id.tvCount)).check(matches(withText("1 items")))
    }

    @Test
    fun addDuplicateResultDoesNotAddItem() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            setField(activity, "scanInterval", 0L)
            setField(activity, "isAutoSaveEnabled", false)
            injectResult(activity, "same")
            injectResult(activity, "same")
        }
        flushMainLooper()
        assertEquals(1, currentItemCount())
    }

    @Test
    fun clearAllEmptiesList() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            setField(activity, "scanInterval", 0L)
            setField(activity, "isAutoSaveEnabled", false)
            injectResult(activity, "a")
            injectResult(activity, "b")
        }
        flushMainLooper()
        assertEquals(2, currentItemCount())

        onView(withId(R.id.btnClearAll)).perform(click())
        flushMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        flushMainLooper()

        assertEquals(0, currentItemCount())
        onView(withId(R.id.tvCount)).check(matches(withText("0 items")))
    }

    @Test
    fun autoSaveShowsSavedIcon() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            setField(activity, "scanInterval", 0L)
            setField(activity, "isAutoSaveEnabled", true)
            injectResult(activity, "https://example.com")
        }
        flushMainLooper()
        Thread.sleep(300)
        flushMainLooper()
        assertEquals(View.VISIBLE, getFirstSavedVisibility())
    }

    @Test
    fun saveAllSavesUnsavedResults() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            setField(activity, "scanInterval", 0L)
            setField(activity, "isAutoSaveEnabled", false)
            injectResult(activity, "https://example.com")
        }
        flushMainLooper()
        assertEquals(View.GONE, getFirstSavedVisibility())

        onView(withId(R.id.btnSaveAll)).perform(click())
        val visible = waitUntilSavedVisible()
        assertTrue("Save icon should become visible after save all", visible)
    }

    private fun waitUntilSavedVisible(): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (getFirstSavedVisibility() == View.VISIBLE) return true
            Thread.sleep(100)
            flushMainLooper()
        }
        return false
    }

    @Test
    fun settingsDialogTogglesAutoSave() {
        launchActivity()
        flushMainLooper()
        onView(withId(R.id.btnSettings)).perform(click())
        flushMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        val listView = dialog.listView
        assertNotNull(listView)
        listView.performItemClick(listView.adapter.getView(1, null, listView), 1, listView.adapter.getItemId(1))
        flushMainLooper()

        scenario?.onActivity { activity ->
            val field = ContinuousScanActivity::class.java.getDeclaredField("isAutoSaveEnabled")
            field.isAccessible = true
            assertEquals(false, field.get(activity))
        }
    }
}
