package com.xenoamess.qrcodesimple

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ResultActivityUiTest {

    private var scenario: ActivityScenario<ResultActivity>? = null

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

    private fun createQrImageUri(content: String = "https://example.com"): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = BarcodeGenerator.generate(
            content,
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.QR_CODE)
        ) ?: throw IllegalStateException("Failed to generate QR bitmap")

        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "test_qr_${content.hashCode()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file).toString()
    }

    private fun createBlankImageUri(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)

        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "blank.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file).toString()
    }

    private fun launchWithUri(uri: String): ActivityScenario<ResultActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ResultActivity::class.java)
            .putExtra(ResultActivity.EXTRA_BITMAP_URI, uri)
        val launched = ActivityScenario.launch<ResultActivity>(intent)
        launched.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario = launched
        return launched
    }

    private fun waitForResults(timeoutMs: Long = 10000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var count = 0
        while (System.currentTimeMillis() < deadline) {
            flushMainLooper()
            scenario?.onActivity { activity ->
                count = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter?.itemCount ?: 0
            }
            if (count > 0) break
            Thread.sleep(100)
        }
        return count
    }

    private fun waitForEmptyState(timeoutMs: Long = 10000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var visible = false
        while (System.currentTimeMillis() < deadline) {
            flushMainLooper()
            scenario?.onActivity { activity ->
                visible = activity.findViewById<View>(R.id.tvNoResults).visibility == View.VISIBLE
            }
            if (visible) break
            Thread.sleep(100)
        }
        return visible
    }

    private fun currentItemCount(): Int {
        var count = 0
        scenario?.onActivity { activity ->
            count = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter?.itemCount ?: 0
        }
        return count
    }

    @Test
    fun resultListShownAfterScan() {
        launchWithUri(createQrImageUri())
        assertTrue("Results should be detected", waitForResults() > 0)
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        onView(withId(R.id.layoutButtons)).check(matches(isDisplayed()))
        onView(withId(R.id.tvSelectionCount)).check(matches(withText("Selected: 0/1")))
    }

    @Test
    fun selectAllAndDeselectAllUpdateCount() {
        launchWithUri(createQrImageUri())
        assertTrue(waitForResults() > 0)

        onView(withId(R.id.btnSelectAll)).perform(click())
        flushMainLooper()
        onView(withId(R.id.tvSelectionCount)).check(matches(withText("Selected: 1/1")))

        onView(withId(R.id.btnDeselectAll)).perform(click())
        flushMainLooper()
        onView(withId(R.id.tvSelectionCount)).check(matches(withText("Selected: 0/1")))
    }

    @Test
    fun copySelectedCopiesToClipboard() {
        launchWithUri(createQrImageUri())
        assertTrue(waitForResults() > 0)

        onView(withId(R.id.btnSelectAll)).perform(click())
        flushMainLooper()
        onView(withId(R.id.btnCopySelected)).perform(click())
        flushMainLooper()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals("https://example.com", clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun shareSelectedStartsShareIntent() {
        launchWithUri(createQrImageUri())
        assertTrue(waitForResults() > 0)

        onView(withId(R.id.btnSelectAll)).perform(click())
        flushMainLooper()

        var startedIntent: Intent? = null
        scenario?.onActivity { activity ->
            startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        }
        // Pre-fetch is null before click; we just need to clear any stale intent.

        onView(withId(R.id.btnShareSelected)).perform(click())
        flushMainLooper()

        scenario?.onActivity { activity ->
            startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        }
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent?.action)
        val sharedIntent = startedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(sharedIntent)
        assertEquals("https://example.com", sharedIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun deleteSelectedRemovesItem() {
        launchWithUri(createQrImageUri())
        assertTrue(waitForResults() > 0)

        onView(withId(R.id.btnSelectAll)).perform(click())
        flushMainLooper()
        onView(withId(R.id.btnDeleteSelected)).perform(click())
        flushMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        flushMainLooper()

        assertEquals(0, currentItemCount())
        onView(withId(R.id.tvNoResults)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }

    @Test
    fun emptyStateShowsWhenNoResults() {
        launchWithUri(createBlankImageUri())
        assertTrue("Empty state should be shown", waitForEmptyState())
        onView(withId(R.id.tvNoResults)).check(matches(isDisplayed()))
    }
}
