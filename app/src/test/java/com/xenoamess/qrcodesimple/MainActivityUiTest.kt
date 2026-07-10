package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class MainActivityUiTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitForPager() {
        flushMainLooper()
        Thread.sleep(300)
        flushMainLooper()
    }

    private fun currentPage(): Int {
        var page = -1
        scenario.onActivity { activity ->
            page = activity.findViewById<ViewPager2>(R.id.viewPager).currentItem
        }
        return page
    }

    private fun selectedTabIndex(): Int {
        var index = -1
        scenario.onActivity { activity ->
            val buttons = listOf(
                R.id.btnTabRealtime,
                R.id.btnTabImage,
                R.id.btnTabGenerate,
                R.id.btnTabHistory,
                R.id.btnTabAbout
            ).map { activity.findViewById<Button>(it) }
            val selectedColor = ContextCompat.getColor(activity, R.color.cyan_500)
            index = buttons.indexOfFirst { it.currentTextColor == selectedColor }
        }
        return index
    }

    @Test
    fun defaultTabIsRealtime() {
        waitForPager()
        assertEquals(0, currentPage())
        assertEquals(0, selectedTabIndex())
    }

    @Test
    fun clickTabButtonsSwitchPages() {
        val tabIds = listOf(
            R.id.btnTabRealtime,
            R.id.btnTabImage,
            R.id.btnTabGenerate,
            R.id.btnTabHistory,
            R.id.btnTabAbout
        )

        for ((index, buttonId) in tabIds.withIndex()) {
            onView(withId(buttonId)).perform(click())
            waitForPager()
            assertEquals("Clicking tab $index should switch ViewPager", index, currentPage())
            assertEquals(index, selectedTabIndex())
        }
    }

    @Test
    fun deepLinkHistoryTab_selectsHistoryPage() {
        scenario.close()
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            data = Uri.parse("history")
        }
        scenario = ActivityScenario.launch<MainActivity>(intent)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitForPager()

        assertEquals(3, currentPage())
        assertEquals(3, selectedTabIndex())
    }

    @Test
    fun extraGenerateContentNavigatesToGenerateTab() {
        scenario.close()
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("generate_content", "https://example.com")
        }
        scenario = ActivityScenario.launch<MainActivity>(intent)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        waitForPager()

        assertEquals(2, currentPage())
        assertEquals(2, selectedTabIndex())
    }

    @Test
    fun onNewIntentWithGenerateContentNavigatesToGenerateTab() {
        waitForPager()
        assertEquals(0, currentPage())

        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra("generate_content", "https://example.com")
        }
        scenario.onActivity { activity ->
            val method = MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
            method.isAccessible = true
            method.invoke(activity, intent)
        }
        waitForPager()

        assertEquals(2, currentPage())
        assertEquals(2, selectedTabIndex())
    }
}
