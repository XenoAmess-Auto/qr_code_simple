@file:Suppress("DEPRECATION")

package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.runBlocking
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
class HistoryDetailActivityTest {

    private lateinit var repository: HistoryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = HistoryRepository(context)
        runBlocking { repository.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { repository.deleteAll() }
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun insertItem(): Long {
        val item = HistoryItem(
            content = "https://example.com",
            type = HistoryType.QR_CODE,
            isGenerated = false,
            barcodeFormat = "QR_CODE",
            notes = "Test note",
            tags = "test, sample",
            isFavorite = false
        )
        return kotlinx.coroutines.runBlocking { repository.insert(item) }
    }

    private fun launchActivity(itemId: Long): ActivityScenario<HistoryDetailActivity> {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            HistoryDetailActivity::class.java
        ).apply {
            putExtra(HistoryDetailActivity.EXTRA_ITEM_ID, itemId)
        }
        return ActivityScenario.launch<HistoryDetailActivity>(intent)
    }

    private fun resumeScenario(scenario: ActivityScenario<HistoryDetailActivity>) {
        scenario.moveToState(Lifecycle.State.RESUMED)
        flushMainLooper()
    }

    @Test
    fun invalidItemIdFinishesActivity() {
        val scenario = launchActivity(-1)
        flushMainLooper()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        scenario.close()
    }

    @Test
    fun validItemIdBindsFields() {
        val insertedId = insertItem()
        val scenario = launchActivity(insertedId)
        resumeScenario(scenario)

        assertTrue(
            "Content should be bound to the view",
            waitFor { getViewText(scenario, R.id.tvContent) == "https://example.com" }
        )

        scenario.onActivity { activity ->
            assertEquals("https://example.com", activity.findViewById<android.widget.TextView>(R.id.tvContent).text.toString())
            assertNotNull(activity.findViewById<android.widget.TextView>(R.id.tvType).text.toString())
            assertNotNull(activity.findViewById<android.widget.TextView>(R.id.tvTime).text.toString())
            assertEquals(View.VISIBLE, activity.findViewById<android.view.ViewGroup>(R.id.chipGroupTags).visibility)
            assertEquals(View.VISIBLE, activity.findViewById<android.widget.TextView>(R.id.tvNotes).visibility)
            assertEquals("Test note", activity.findViewById<android.widget.TextView>(R.id.tvNotes).text.toString())
            assertNotNull(activity.findViewById<ImageView>(R.id.ivBarcode).drawable)
        }
        scenario.close()
    }

    @Test
    fun shareButtonStartsSendIntent() {
        val insertedId = insertItem()
        val scenario = launchActivity(insertedId)
        resumeScenario(scenario)

        assertTrue(
            "Content should be bound before sharing",
            waitFor { getViewText(scenario, R.id.tvContent) == "https://example.com" }
        )

        clickSafely(R.id.btnShare)
        flushMainLooper()

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, dialog.listView.adapter.getItemId(0))
        flushMainLooper()

        scenario.onActivity { activity ->
            val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
            assertNotNull(startedIntent)
            assertEquals(Intent.ACTION_CHOOSER, startedIntent?.action)
            val sharedIntent = startedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertEquals("https://example.com", sharedIntent?.getStringExtra(Intent.EXTRA_TEXT))
        }
        scenario.close()
    }


    /**
     * Runs Espresso scrollTo+click with retries. Async data binding can leave the target
     * momentarily non-clickable in CI; retrying here removes the flaky PerformException
     * instead of masking the underlying assertion failures.
     */
    private fun clickSafely(viewId: Int, maxMs: Long = 3000) {
        assertTrue(
            "View $viewId never became clickable",
            waitFor(maxMs = maxMs) {
                try {
                    onView(withId(viewId)).perform(scrollTo(), click())
                    true
                } catch (_: Throwable) {
                    flushMainLooper()
                    false
                }
            }
        )
    }

    private fun waitFor(maxMs: Long = 1000, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            if (condition()) return true
            flushMainLooper()
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun toggleFavoriteUpdatesButton() {
        val insertedId = insertItem()
        val scenario = launchActivity(insertedId)
        resumeScenario(scenario)

        val beforeText = getFavoriteButtonText(scenario)
        clickSafely(R.id.btnToggleFavorite)
        flushMainLooper()

        assertTrue(
            "Favorite button text should change after toggle",
            waitFor { getFavoriteButtonText(scenario) != beforeText }
        )
        scenario.close()
    }

    private fun getFavoriteButtonText(scenario: ActivityScenario<HistoryDetailActivity>): String {
        return getViewText(scenario, R.id.btnToggleFavorite)
    }

    private fun getViewText(scenario: ActivityScenario<HistoryDetailActivity>, viewId: Int): String {
        var text = ""
        scenario.onActivity { activity ->
            text = activity.findViewById<android.widget.TextView>(viewId).text.toString()
        }
        return text
    }

    @Test
    fun openGenerateStartsMainActivity() {
        val insertedId = insertItem()
        val scenario = launchActivity(insertedId)
        resumeScenario(scenario)

        onView(withId(R.id.btnOpenGenerate)).perform(scrollTo(), click())
        flushMainLooper()

        scenario.onActivity { activity ->
            val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
            assertNotNull(startedIntent)
            assertEquals("com.xenoamess.qrcodesimple.MainActivity", startedIntent?.component?.className)
            assertEquals("https://example.com", startedIntent?.getStringExtra("generate_content"))
        }
        scenario.close()
    }
}
