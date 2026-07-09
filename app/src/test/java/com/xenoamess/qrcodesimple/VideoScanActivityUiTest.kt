package com.xenoamess.qrcodesimple

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.google.zxing.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.ui.result.QRResult
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
class VideoScanActivityUiTest {

    private var scenario: ActivityScenario<VideoScanActivity>? = null

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

    private fun launchWithUri(uri: String): ActivityScenario<VideoScanActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoScanActivity::class.java)
            .putExtra(VideoScanActivity.EXTRA_VIDEO_URI, uri)
        val launched = ActivityScenario.launch<VideoScanActivity>(intent)
        launched.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario = launched
        return launched
    }

    private fun injectResult(text: String) {
        scenario?.onActivity { activity ->
            val addResult = VideoScanActivity::class.java.getDeclaredMethod("addResult", QRResult::class.java)
            addResult.isAccessible = true
            addResult.invoke(
                activity,
                QRResult(text, false, QRCodeScanner.Library.ZXING, BarcodeFormat.QR_CODE)
            )
            val updateUI = VideoScanActivity::class.java.getDeclaredMethod("updateUI")
            updateUI.isAccessible = true
            updateUI.invoke(activity)
        }
        flushMainLooper()
        Thread.sleep(100)
        flushMainLooper()
    }

    private fun currentItemCount(): Int {
        var count = 0
        scenario?.onActivity { activity ->
            count = activity.findViewById<RecyclerView>(R.id.recyclerView).adapter?.itemCount ?: 0
        }
        return count
    }

    @Test
    fun resultListShownAfterInjectingResult() {
        launchWithUri(Uri.fromFile(java.io.File("/dev/null/nonexistent.mp4")).toString())
        injectResult("https://example.com")
        assertEquals(1, currentItemCount())
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        onView(withId(R.id.layoutButtons)).check(matches(isDisplayed()))
        onView(withId(R.id.tvSelectionCount)).check(matches(withText("Selected: 0/1")))
    }

    @Test
    fun selectAllAndDeselectAllUpdateCount() {
        launchWithUri(Uri.fromFile(java.io.File("/dev/null/nonexistent.mp4")).toString())
        injectResult("https://example.com")

        onView(withId(R.id.btnSelectAll)).perform(click())
        flushMainLooper()
        onView(withId(R.id.tvSelectionCount)).check(matches(withText("Selected: 1/1")))

        onView(withId(R.id.btnDeselectAll)).perform(click())
        flushMainLooper()
        onView(withId(R.id.tvSelectionCount)).check(matches(withText("Selected: 0/1")))
    }

    @Test
    fun copySelectedCopiesToClipboard() {
        launchWithUri(Uri.fromFile(java.io.File("/dev/null/nonexistent.mp4")).toString())
        injectResult("https://example.com")

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
        launchWithUri(Uri.fromFile(java.io.File("/dev/null/nonexistent.mp4")).toString())
        injectResult("https://example.com")

        onView(withId(R.id.btnSelectAll)).perform(click())
        flushMainLooper()

        var startedIntent: Intent? = null
        scenario?.onActivity { activity ->
            startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        }

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
        launchWithUri(Uri.fromFile(java.io.File("/dev/null/nonexistent.mp4")).toString())
        injectResult("https://example.com")

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
}
