package com.xenoamess.qrcodesimple

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Bundle
import android.os.Parcel
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
import com.xenoamess.qrcodesimple.data.HistoryType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

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

    private fun getField(activity: ContinuousScanActivity, name: String): Any? {
        val field = ContinuousScanActivity::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(activity)
    }

    private fun saveInstanceState(activity: ContinuousScanActivity): Bundle = Bundle().also { state ->
        ContinuousScanActivity::class.java.getDeclaredMethod("onSaveInstanceState", Bundle::class.java).apply {
            isAccessible = true
            invoke(activity, state)
        }
    }

    private fun parcelSize(state: Bundle): Int = Parcel.obtain().let { parcel ->
        try {
            state.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
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
    fun collectionRejectsOversizedAndCumulativeOverflowBeforeSavingState() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            setField(activity, "scanInterval", 0L)
            setField(activity, "isAutoSaveEnabled", false)
            injectResult(activity, "x".repeat(ContinuousScanActivity.MAX_RESULT_CHARACTERS + 1))
        }
        assertEquals(0, currentItemCount())

        launched.onActivity { activity ->
            repeat(200) { index ->
                val prefix = "$index:"
                injectResult(
                    activity,
                    prefix + "x".repeat(ContinuousScanActivity.MAX_RESULT_CHARACTERS - prefix.length)
                )
            }
        }

        val acceptedCount = currentItemCount()
        assertTrue(acceptedCount in 1 until 200)
        launched.onActivity { activity ->
            val state = saveInstanceState(activity)
            assertNotNull(state.getString("continuous_cache_token"))
            assertTrue(parcelSize(state) < 16 * 1024)
        }
    }

    @Test
    fun invalidInjectedSessionIsRejectedBeforeJsonConstruction() {
        val launched = launchActivity()
        launched.onActivity { activity ->
            @Suppress("UNCHECKED_CAST")
            (getField(activity, "results") as MutableList<ContinuousScanActivity.ScanResult>) +=
                ContinuousScanActivity.ScanResult(
                    "x".repeat(ContinuousScanActivity.MAX_RESULT_CHARACTERS + 1)
                )

            val state = saveInstanceState(activity)

            assertEquals(null, state.getString("continuous_cache_token"))
            assertEquals(true, getField(activity, "stateRestoreFailed"))
            assertTrue(parcelSize(state) < 16 * 1024)
        }
    }

    @Test
    fun resultsAndPendingJsonAndXlsxExportsSurviveRecreation() {
        val launched = launchActivity()
        val scanResult = ContinuousScanActivity.ScanResult(
            content = "preserved",
            type = HistoryType.BARCODE,
            timestamp = 123456789L,
            isSaved = true,
            appFormat = com.xenoamess.qrcodesimple.data.BarcodeFormat.CODE_128
        )
        val exportRow = ScanSessionExporter.Row("preserved", "CODE_128", 123456789L, true)

        launched.onActivity { activity ->
            @Suppress("UNCHECKED_CAST")
            (getField(activity, "results") as MutableList<ContinuousScanActivity.ScanResult>).add(scanResult)
            setField(activity, "exportRows", listOf(exportRow))
            setField(activity, "exportKind", "json")
        }

        launched.recreate()
        launched.onActivity { activity ->
            @Suppress("UNCHECKED_CAST")
            assertEquals(listOf(scanResult), getField(activity, "results") as List<ContinuousScanActivity.ScanResult>)
            assertEquals(listOf(exportRow), getField(activity, "exportRows"))
            assertEquals("json", getField(activity, "exportKind"))
            setField(activity, "exportKind", "xlsx")
        }

        launched.recreate()
        launched.onActivity { activity ->
            assertEquals(listOf(exportRow), getField(activity, "exportRows"))
            assertEquals("xlsx", getField(activity, "exportKind"))
        }
    }

    @Test
    fun largeSessionUsesReplaceableCacheFileAndSurvivesColdRestore() {
        val launched = launchActivity()
        val marker = "continuous-large-marker"
        val largeResults = List(128) { index ->
            ContinuousScanActivity.ScanResult("$marker-$index-${"x".repeat(4096)}")
        }
        val largeRows = largeResults.map {
            ScanSessionExporter.Row(it.content, "QR_CODE", it.timestamp, false)
        }
        lateinit var savedState: Bundle
        lateinit var firstToken: String

        launched.onActivity { activity ->
            val emptyStateSize = parcelSize(saveInstanceState(activity))
            @Suppress("UNCHECKED_CAST")
            (getField(activity, "results") as MutableList<ContinuousScanActivity.ScanResult>).addAll(largeResults)
            setField(activity, "exportRows", largeRows)
            setField(activity, "exportKind", "json")
            val firstState = saveInstanceState(activity)
            firstToken = getField(activity, "stateCacheToken") as String

            savedState = saveInstanceState(activity)
            val secondToken = getField(activity, "stateCacheToken") as String
            assertNotEquals(firstToken, secondToken)
            assertFalse(PrivateStateFileStore.file(activity, "continuous-scan-state", firstToken).exists())
            assertTrue(PrivateStateFileStore.file(activity, "continuous-scan-state", secondToken).isFile)
            assertTrue(
                PrivateStateFileStore.file(activity, "continuous-scan-state", secondToken).absolutePath
                    .startsWith(activity.noBackupFilesDir.absolutePath)
            )
            assertTrue(parcelSize(savedState) - emptyStateSize < 4096)
            assertTrue(parcelSize(firstState) - emptyStateSize < 4096)
        }

        val controller = Robolectric.buildActivity(ContinuousScanActivity::class.java)
            .create(Bundle(savedState))
            .start()
            .resume()
            .visible()
        val restored = controller.get()
        @Suppress("UNCHECKED_CAST")
        assertEquals(largeResults, getField(restored, "results") as List<ContinuousScanActivity.ScanResult>)
        assertEquals(largeRows, getField(restored, "exportRows"))
        assertEquals("json", getField(restored, "exportKind"))
        controller.pause().stop().destroy()
    }

    @Test
    fun missingSessionCacheSafelyKeepsPendingExportFormat() {
        val launched = launchActivity()
        lateinit var savedState: Bundle
        launched.onActivity { activity ->
            @Suppress("UNCHECKED_CAST")
            (getField(activity, "results") as MutableList<ContinuousScanActivity.ScanResult>) +=
                ContinuousScanActivity.ScanResult("will-be-missing")
            setField(activity, "exportKind", "xlsx")
            savedState = saveInstanceState(activity)
            val token = getField(activity, "stateCacheToken") as String
            PrivateStateFileStore.file(activity, "continuous-scan-state", token).delete()
        }

        val controller = Robolectric.buildActivity(ContinuousScanActivity::class.java)
            .create(Bundle(savedState))
            .start()
            .resume()
            .visible()
        val restored = controller.get()
        @Suppress("UNCHECKED_CAST")
        assertTrue((getField(restored, "results") as List<ContinuousScanActivity.ScanResult>).isEmpty())
        assertEquals("xlsx", getField(restored, "exportKind"))
        assertEquals(true, getField(restored, "stateRestoreFailed"))
        restored.findViewById<View>(R.id.btnExport).performClick()
        assertEquals(
            restored.getString(R.string.continuous_state_unavailable),
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast().toString()
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun newResultAfterFailedRestoreStartsExportableSession() {
        val failedState = Bundle().apply {
            putString("continuous_cache_token", PrivateStateFileStore.newToken())
        }
        val controller = Robolectric.buildActivity(ContinuousScanActivity::class.java)
            .create(failedState)
            .start()
            .resume()
            .visible()
        val activity = controller.get()
        setField(activity, "scanInterval", 0L)
        setField(activity, "isAutoSaveEnabled", false)
        assertEquals(true, getField(activity, "stateRestoreFailed"))

        injectResult(activity, "new-session")
        activity.findViewById<View>(R.id.btnExport).performClick()

        assertEquals(false, getField(activity, "stateRestoreFailed"))
        assertTrue(ShadowDialog.getLatestDialog() is AlertDialog)
        controller.pause().stop().destroy()
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
