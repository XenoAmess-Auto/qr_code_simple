package com.xenoamess.qrcodesimple

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * 历史页平板双栏（sw600dp）测试：点击列表项时详情应嵌入右侧面板，
 * 而不是启动 HistoryDetailActivity。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "sw600dp", application = QRCodeApp::class)
class HistoryTwoPaneTest {

    private lateinit var repository: HistoryRepository
    private var scenario: FragmentScenario<HistoryFragment>? = null

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = HistoryRepository(context)
        AppLockManager.init(context)
        AppLockManager.clearPin()
        AppLockManager.setBiometricEnabled(false)
        runBlocking {
            repository.deleteAll()
            repository.insertScan("https://example.com/two-pane", HistoryType.QR_CODE)
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking { repository.deleteAll() }
        AppLockManager.clearPin()
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun waitForDiff() {
        Thread.sleep(300)
        flushMainLooper()
    }

    private fun launchFragment(): FragmentScenario<HistoryFragment> {
        val scenario = launchFragmentInContainer<HistoryFragment>(themeResId = R.style.Theme_QRCodeSimple)
        scenario.moveToState(Lifecycle.State.RESUMED)
        this.scenario = scenario
        waitForDiff()
        return scenario
    }

    @Test
    fun `sw600dp layout has detail pane container`() {
        launchFragment()
        scenario?.onFragment { fragment ->
            assertNotNull(fragment.requireView().findViewById(R.id.detailPaneContainer))
        }
    }

    @Test
    fun `clicking item embeds detail fragment in pane instead of launching activity`() {
        launchFragment()
        scenario?.onFragment { fragment ->
            val rv = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            assertTrue("list should have items before click", (rv.adapter?.itemCount ?: 0) > 0)
            val holder = rv.findViewHolderForAdapterPosition(0)
            assertNotNull("first item should be laid out", holder)
            holder!!.itemView.performClick()
        }
        waitForDiff()

        scenario?.onFragment { fragment ->
            fragment.childFragmentManager.executePendingTransactions()
            // 详情 Fragment 已嵌入右侧面板
            val embedded = fragment.childFragmentManager.findFragmentById(R.id.detailPaneContainer)
            assertNotNull(embedded)
            assertTrue(embedded is HistoryDetailFragment)
            // 没有启动独立的 HistoryDetailActivity
            assertNull(
                Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            )
        }
    }

    @Test
    fun `foreground timeout hides embedded detail without an emission`() {
        AppLockManager.setPin("1234")
        AppLockManager.recordUnlock()
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("app_lock", android.content.Context.MODE_PRIVATE)
            .edit()
            .putLong("last_unlocked", System.currentTimeMillis() - 5 * 60 * 1000 + 1_000)
            .commit()
        launchFragment()
        scenario?.onFragment { fragment ->
            val rv = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            rv.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
            fragment.childFragmentManager.executePendingTransactions()
        }

        Thread.sleep(1_100)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1_001, TimeUnit.MILLISECONDS)
        flushMainLooper()

        scenario?.onFragment { fragment ->
            assertEquals(
                android.view.View.GONE,
                fragment.requireView().findViewById<android.view.View>(R.id.detailPaneContainer).visibility
            )
            val embedded = fragment.childFragmentManager.findFragmentById(R.id.detailPaneContainer)!!
            assertEquals(android.view.View.GONE, embedded.requireView().visibility)
        }

        AppLockManager.recordUnlock()
        waitForDiff()
        scenario?.onFragment { fragment ->
            assertEquals(
                android.view.View.VISIBLE,
                fragment.requireView().findViewById<android.view.View>(R.id.detailPaneContainer).visibility
            )
            val embedded = fragment.childFragmentManager.findFragmentById(R.id.detailPaneContainer)!!
            assertEquals(android.view.View.VISIBLE, embedded.requireView().visibility)
            assertEquals(
                "https://example.com/two-pane",
                embedded.requireView().findViewById<android.widget.TextView>(R.id.tvContent).text.toString()
            )
        }
    }
}
