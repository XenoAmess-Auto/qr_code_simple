package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.Button
import android.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first
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
import org.robolectric.shadows.ShadowDialog

/**
 * HistoryFragment 用户场景测试：收藏筛选、搜索、标签筛选、清空、分享条码图。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class HistoryScenarioTest {

    private lateinit var repository: HistoryRepository
    private var scenario: FragmentScenario<HistoryFragment>? = null

    @Before
    fun setup() {
        clearFileProviderCache()
        repository = HistoryRepository(ApplicationProvider.getApplicationContext())
        runBlocking { repository.deleteAll() }
    }

    /** 见 ScanImageFragmentTest：FileProvider 静态路径缓存会在测试类间互相污染。 */
    private fun clearFileProviderCache() {
        val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking { repository.deleteAll() }
    }

    private fun flush() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitForDiff() {
        Thread.sleep(300)
        flush()
    }

    /** 轮询等待列表达到期望数量（Flow 收集 + DiffUtil 是异步的）。 */
    private fun waitForCount(expected: Int, timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            flush()
            var count = -1
            scenario?.onFragment { count = itemCount(it) }
            if (count == expected) return
            Thread.sleep(50)
        }
        flush()
    }

    private fun launch(): FragmentScenario<HistoryFragment> {
        scenario?.close()
        val s = launchFragmentInContainer<HistoryFragment>(themeResId = R.style.Theme_QRCodeSimple)
        s.moveToState(Lifecycle.State.RESUMED)
        scenario = s
        waitForDiff()
        return s
    }

    private fun itemCount(fragment: HistoryFragment): Int {
        val rv = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
        return rv.adapter?.itemCount ?: 0
    }

    @Test
    fun `favorite filter tab shows only favorites`() {
        runBlocking {
            repository.insertScan("plain-item", HistoryType.QR_CODE)
            repository.insertScan("fav-item", HistoryType.QR_CODE)
            repository.updateFavorite(
                repository.allHistory.first().first { it.content == "fav-item" }.id, true
            )
        }
        launch()

        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnFilterFavorite).performClick()
        }
        waitForCount(1)

        scenario?.onFragment { fragment ->
            assertEquals(1, itemCount(fragment))
        }
    }

    @Test
    fun `search query narrows the list`() {
        runBlocking {
            repository.insertScan("https://example.com", HistoryType.QR_CODE)
            repository.insertScan("totally different text", HistoryType.TEXT)
        }
        launch()

        scenario?.onFragment { fragment ->
            val sv = fragment.requireView().findViewById<SearchView>(R.id.searchView)
            sv.setIconified(false)
            sv.setQuery("example", false)
        }
        waitForCount(1)

        scenario?.onFragment { fragment ->
            assertEquals(1, itemCount(fragment))
        }
    }

    @Test
    fun `tag chips appear and filter by tag`() {
        runBlocking {
            repository.insertScan("tagged-item", HistoryType.QR_CODE)
            repository.setTags(
                repository.allHistory.first().first { it.content == "tagged-item" }.id,
                listOf("work")
            )
            repository.insertScan("untagged-item", HistoryType.TEXT)
        }
        launch()

        scenario?.onFragment { fragment ->
            val chipGroup = fragment.requireView().findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupTags)
            assertEquals(View.VISIBLE, chipGroup.visibility)
            // 找到 "work" 标签 chip 并选中
            var workChip: Chip? = null
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip
                if (chip?.text == "work") workChip = chip
            }
            assertNotNull(workChip)
            workChip!!.isChecked = true
        }
        waitForCount(1)

        scenario?.onFragment { fragment ->
            assertEquals(1, itemCount(fragment))
        }
    }

    @Test
    fun `clear all confirm empties history`() {
        runBlocking {
            repository.insertScan("item-1", HistoryType.QR_CODE)
            repository.insertScan("item-2", HistoryType.QR_CODE)
        }
        launch()

        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnClearAll).performClick()
        }
        flush()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline &&
            runBlocking { repository.allHistory.first().isNotEmpty() }) {
            flush()
            Thread.sleep(50)
        }

        assertEquals(0, runBlocking { repository.allHistory.first().size })
    }

    @Test
    fun `share qr from history launches image share chooser`() {
        runBlocking {
            repository.insertGenerate("share-qr-content", HistoryType.QR_CODE, "QR_CODE", null)
        }
        launch()

        scenario?.onFragment { fragment ->
            val rv = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            val holder = rv.findViewHolderForAdapterPosition(0)
            assertNotNull(holder)
            holder!!.itemView.findViewById<View>(R.id.btnShareQR).performClick()
        }
        // 等协程生成位图并拉起分享
        val deadline = System.currentTimeMillis() + 5000
        var chooser: Intent? = null
        while (System.currentTimeMillis() < deadline) {
            flush()
            scenario?.onFragment { fragment ->
                chooser = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            }
            if (chooser != null) break
            Thread.sleep(50)
        }

        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser?.action)
        val inner = chooser?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals("image/png", inner?.type)
        assertNotNull(inner?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
    }
}
