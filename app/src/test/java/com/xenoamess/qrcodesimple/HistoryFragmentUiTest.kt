package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.HistoryItem
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
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class HistoryFragmentUiTest {

    private lateinit var repository: HistoryRepository
    private var scenario: FragmentScenario<HistoryFragment>? = null

    @Before
    fun setup() {
        repository = HistoryRepository(ApplicationProvider.getApplicationContext())
        runBlocking { repository.deleteAll() }
        QRCodeApp.setHistoryStatsExpanded(ApplicationProvider.getApplicationContext(), false)
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking { repository.deleteAll() }
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun waitForDiff() {
        Thread.sleep(300)
        flushMainLooper()
    }

    private fun launchFragment(): FragmentScenario<HistoryFragment> {
        scenario?.close()
        val scenario = launchFragmentInContainer<HistoryFragment>(themeResId = R.style.Theme_QRCodeSimple)
        scenario.moveToState(Lifecycle.State.RESUMED)
        this.scenario = scenario
        waitForDiff()
        return scenario
    }

    private fun insertItems() {
        runBlocking {
            repository.insertScan("https://example.com", HistoryType.QR_CODE)
            repository.insertGenerate("123456789012", HistoryType.BARCODE, "EAN_13", null)
            repository.insertScan("plain text", HistoryType.TEXT)
        }
    }

    private fun currentList(scenario: FragmentScenario<HistoryFragment>): List<com.xenoamess.qrcodesimple.data.HistoryItem> {
        var list = emptyList<com.xenoamess.qrcodesimple.data.HistoryItem>()
        scenario.onFragment { fragment ->
            val recyclerView = fragment.view?.findViewById<RecyclerView>(R.id.recyclerView)
            val adapter = recyclerView?.adapter as? HistoryAdapter
            list = adapter?.currentList ?: emptyList()
        }
        return list
    }

    private fun waitForListSize(
        scenario: FragmentScenario<HistoryFragment>,
        expectedSize: Int,
        timeoutMs: Long = 3000
    ): List<com.xenoamess.qrcodesimple.data.HistoryItem> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var list = emptyList<com.xenoamess.qrcodesimple.data.HistoryItem>()
        while (System.currentTimeMillis() < deadline) {
            flushMainLooper()
            list = currentList(scenario)
            if (list.size == expectedSize) break
            Thread.sleep(50)
        }
        return list
    }

    @Test
    fun statsCardCollapsedByDefault() {
        launchFragment()
        scenario?.onFragment { fragment ->
            assertEquals(
                android.view.View.GONE,
                fragment.requireView().findViewById<android.view.View>(R.id.statsDetailContainer).visibility
            )
            assertEquals(
                android.view.View.VISIBLE,
                fragment.requireView().findViewById<android.view.View>(R.id.tvStatsSummary).visibility
            )
        }
    }

    @Test
    fun statsCardTogglesExpansionOnClick() {
        launchFragment()
        onView(withId(R.id.statsCard)).perform(click())
        waitForDiff()
        scenario?.onFragment { fragment ->
            assertEquals(
                android.view.View.VISIBLE,
                fragment.requireView().findViewById<android.view.View>(R.id.statsDetailContainer).visibility
            )
        }

        onView(withId(R.id.statsCard)).perform(click())
        waitForDiff()
        scenario?.onFragment { fragment ->
            assertEquals(
                android.view.View.GONE,
                fragment.requireView().findViewById<android.view.View>(R.id.statsDetailContainer).visibility
            )
        }
    }

    @Test
    fun statsExpansionStatePersistsAcrossLaunch() {
        QRCodeApp.setHistoryStatsExpanded(ApplicationProvider.getApplicationContext(), true)
        launchFragment()
        scenario?.onFragment { fragment ->
            assertEquals(
                android.view.View.VISIBLE,
                fragment.requireView().findViewById<android.view.View>(R.id.statsDetailContainer).visibility
            )
        }
    }

    @Test
    fun emptyStateShowsWhenNoHistory() {
        launchFragment()
        onView(withId(R.id.tvEmpty)).check(matches(isDisplayed()))
        onView(withId(R.id.recyclerView)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun listShowsWhenHistoryExists() {
        insertItems()
        val scenario = launchFragment()
        onView(withId(R.id.tvEmpty)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
        val list = currentList(scenario)
        assertEquals(3, list.size)
    }

    @Test
    fun filterScannedShowsOnlyScanned() {
        insertItems()
        val scenario = launchFragment()
        onView(withId(R.id.btnFilterScanned)).perform(click())
        waitForDiff()
        val list = waitForListSize(scenario, 2)
        assertEquals(2, list.size)
        assertTrue(list.all { !it.isGenerated })
    }

    @Test
    fun filterGeneratedShowsOnlyGenerated() {
        insertItems()
        val scenario = launchFragment()
        onView(withId(R.id.btnFilterGenerated)).perform(click())
        waitForDiff()
        val list = waitForListSize(scenario, 1)
        assertEquals(1, list.size)
        assertTrue(list.all { it.isGenerated })
    }

    private fun waitForFirstContent(
        scenario: FragmentScenario<HistoryFragment>,
        expectedContent: String,
        timeoutMs: Long = 5000
    ): List<com.xenoamess.qrcodesimple.data.HistoryItem> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var list = emptyList<com.xenoamess.qrcodesimple.data.HistoryItem>()
        while (System.currentTimeMillis() < deadline) {
            flushMainLooper()
            list = currentList(scenario)
            if (list.firstOrNull()?.content == expectedContent) break
            Thread.sleep(50)
        }
        return list
    }

    @Test
    fun sortToggleReversesOrder() {
        runBlocking {
            repository.insert(HistoryItem(content = "old", type = HistoryType.QR_CODE, timestamp = 1000L, isGenerated = false))
            repository.insert(HistoryItem(content = "new", type = HistoryType.QR_CODE, timestamp = 2000L, isGenerated = false))
        }
        val scenario = launchFragment()
        var list = waitForFirstContent(scenario, "new")
        assertEquals("new", list.first().content)

        onView(withId(R.id.btnSort)).perform(click())
        list = waitForFirstContent(scenario, "old")
        assertEquals("old", list.first().content)
    }

    @Test
    fun timeRangeFilterShowsOnlyRecent() {
        val now = System.currentTimeMillis()
        runBlocking {
            repository.insert(HistoryItem(content = "recent", type = HistoryType.QR_CODE, timestamp = now, isGenerated = false))
            repository.insert(
                HistoryItem(
                    content = "ancient",
                    type = HistoryType.QR_CODE,
                    timestamp = now - 40L * 24 * 60 * 60 * 1000,
                    isGenerated = false
                )
            )
        }
        val scenario = launchFragment()
        waitForListSize(scenario, 2)

        onView(withId(R.id.btnAdvancedFilter)).perform(click())
        waitForDiff()

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as android.app.AlertDialog
        assertNotNull(dialog)
        dialog.findViewById<android.widget.RadioButton>(R.id.rbTime7d).isChecked = true
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        waitForDiff()

        val list = waitForListSize(scenario, 1)
        assertEquals(1, list.size)
        assertEquals("recent", list.first().content)
    }

    @Test
    fun typeFilterShowsOnlyMatchingType() {
        insertItems()
        val scenario = launchFragment()
        waitForListSize(scenario, 3)

        onView(withId(R.id.btnAdvancedFilter)).perform(click())
        waitForDiff()

        val dialog = ShadowAlertDialog.getLatestAlertDialog() as android.app.AlertDialog
        assertNotNull(dialog)
        dialog.findViewById<android.widget.Button>(R.id.btnPickType).performClick()
        waitForDiff()

        val typeDialog = ShadowAlertDialog.getLatestAlertDialog() as android.app.AlertDialog
        assertNotNull(typeDialog)
        val listView = typeDialog.listView
        var textIndex = -1
        for (i in 0 until listView.adapter.count) {
            if (listView.adapter.getItem(i).toString().equals("TEXT", ignoreCase = true) ||
                listView.adapter.getItem(i).toString() == "纯文本" ||
                listView.adapter.getItem(i).toString() == "Text"
            ) {
                textIndex = i
                break
            }
        }
        assertTrue("TEXT type should be listed", textIndex >= 0)
        listView.performItemClick(listView.getChildAt(textIndex), textIndex, listView.adapter.getItemId(textIndex))
        waitForDiff()

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        waitForDiff()

        val list = waitForListSize(scenario, 1)
        assertEquals(1, list.size)
        assertEquals(HistoryType.TEXT, list.first().type)
    }

    @Test
    fun searchQueryFiltersList() {
        insertItems()
        val scenario = launchFragment()
        scenario.onFragment { fragment ->
            fragment.view?.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
                ?.setQuery("example", true)
        }
        waitForDiff()
        val list = waitForListSize(scenario, 1)
        assertEquals(1, list.size)
        assertTrue(list.first().content.contains("example"))
    }

    @Test
    fun tagChipFiltersList() {
        val itemId = runBlocking {
            repository.insertScan("tagged content", HistoryType.QR_CODE)
            repository.allHistory.first().first().id
        }
        runBlocking {
            repository.setTags(itemId, listOf("work"))
        }
        val scenario = launchFragment()
        waitForDiff()
        onView(withText("work")).perform(click())
        waitForDiff()
        val list = waitForListSize(scenario, 1)
        assertEquals(1, list.size)
        assertEquals("tagged content", list.first().content)
    }

    @Test
    fun clearAllButtonShowsConfirmDialog() {
        insertItems()
        launchFragment()
        onView(withId(R.id.btnClearAll)).perform(click())
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog!!.isShowing)
        val title = Shadows.shadowOf(dialog).title
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals(
            context.getString(R.string.clear_history),
            title.toString()
        )
    }
}
