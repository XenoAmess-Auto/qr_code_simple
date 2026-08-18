package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.cardview.widget.CardView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
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
            val navigation = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.mainNavigation)
            index = listOf(
                R.id.navRealtime,
                R.id.navImage,
                R.id.navGenerate,
                R.id.navHistory,
                R.id.navAbout
            ).indexOf(navigation.selectedItemId)
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
    fun systemBarInsetsAreNotAppliedTwiceToBottomNavigation() {
        scenario.onActivity { activity ->
            val navigation = activity.findViewById<View>(R.id.mainNavigation)
            val initialNavigationPadding = navigation.paddingBottom
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 96))
                .build()

            ViewCompat.dispatchApplyWindowInsets(navigation, insets)

            assertEquals(initialNavigationPadding, navigation.paddingBottom)
        }
    }

    @Test
    fun clickTabButtonsSwitchPages() {
        val tabIds = listOf(
            R.id.navRealtime,
            R.id.navImage,
            R.id.navGenerate,
            R.id.navHistory,
            R.id.navAbout
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

    /**
     * 在 ViewPager2 环境下验证叉号点击是否真正生效。
     * Espresso 的 click() 走真实 dispatchTouchEvent 链路:
     * DecorView → ContentView → ViewPager2 → RecyclerView → CameraScanFragment → resultCard → btnCloseResult
     */
    @Test
    fun closeResultButtonWorksInsideViewPager2() {
        waitForPager()

        // 显示结果卡片
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
            assertNotNull("CameraScanFragment 应存在于 ViewPager2 position 0", fragment)
            fragment?.showResult(
                QRCodeScanner.ScanResult("https://viewpager-test.com", QRCodeScanner.Library.ZXING)
            )
        }
        flushMainLooper()

        // 验证卡片已显示
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
            val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
            assertEquals("前置条件:卡片应已显示", View.VISIBLE, card?.visibility)
        }

        // 用 Espresso click() 走真实事件分发链路点击叉号
        onView(withId(R.id.btnCloseResult)).perform(click())
        flushMainLooper()

        // 验证卡片已隐藏
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
            val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
            assertEquals("ViewPager2 内点击叉号后卡片应隐藏", View.GONE, card?.visibility)
        }
    }
}
