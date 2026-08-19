package com.xenoamess.qrcodesimple

import android.content.Context
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class HistoryFragmentUiTest {

    private lateinit var repository: HistoryRepository
    private var scenario: FragmentScenario<HistoryFragment>? = null

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = HistoryRepository(context)
        runBlocking { repository.deleteAll() }
        QRCodeApp.setHistoryStatsExpanded(context, false)
        AppLockManager.init(context)
        AppLockManager.clearPin()
        AppLockManager.setBiometricEnabled(false)
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

    @Test
    fun `recreating the view resumes history collection without touching stale binding`() {
        insertItems()
        val scenario = launchFragment()
        waitForListSize(scenario, 3)

        scenario.recreate()
        scenario.moveToState(Lifecycle.State.RESUMED)

        assertEquals(3, waitForListSize(scenario, 3).size)
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
    fun historyControlsUseCompactThreeColumnGrid() {
        launchFragment()
        scenario?.onFragment { fragment ->
            val grid = fragment.requireView().findViewById<android.widget.GridLayout>(R.id.filterTabLayout)
            assertEquals(3, grid.columnCount)
            assertEquals(6, grid.childCount)
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

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.findViewById<android.widget.RadioButton>(R.id.rbTime7d)!!.isChecked = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
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

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.findViewById<android.widget.Button>(R.id.btnPickType)!!.performClick()
        waitForDiff()

        val typeDialog = ShadowDialog.getLatestDialog() as AlertDialog
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

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
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
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        val title = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals(
            context.getString(R.string.clear_history),
            title?.toString()
        )
    }

    @Test
    fun emptyEditedContentKeepsDialogOpenAndCanBeCorrected() {
        runBlocking { repository.insertScan("editable-content", HistoryType.TEXT) }
        val scenario = launchFragment()
        waitForListSize(scenario, 1)
        scenario.onFragment { fragment ->
            val recyclerView = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            recyclerView.findViewHolderForAdapterPosition(0)!!.itemView
                .findViewById<android.view.View>(R.id.btnEdit).performClick()
        }
        flushMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val input = dialog.findAllEditTexts().single()
        input.setText("   ")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        flushMainLooper()

        assertTrue(dialog.isShowing)
        assertEquals(input.context.getString(R.string.please_enter_content), input.error?.toString())
        assertEquals("editable-content", runBlocking { repository.allHistory.first().single().content })

        input.setText("corrected-content")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline &&
            runBlocking { repository.allHistory.first().single().content } != "corrected-content"
        ) {
            flushMainLooper()
            Thread.sleep(50)
        }

        assertFalse(dialog.isShowing)
        assertEquals("corrected-content", runBlocking { repository.allHistory.first().single().content })
    }

    @Test
    fun incorrectPinKeepsUnlockDialogOpenAndCanBeCorrected() {
        AppLockManager.setPin("1234")
        AppLockManager.lock()
        launchFragment()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val input = dialog.findAllEditTexts().single()
        input.setText("0000")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        flushMainLooper()

        assertTrue(dialog.isShowing)
        assertFalse(AppLockManager.isUnlocked())

        input.setText("1234")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        flushMainLooper()

        assertFalse(dialog.isShowing)
        assertTrue(AppLockManager.isUnlocked())
    }

    @Test
    fun cancellingUnlockDialogLeavesReachableRetryEntry() {
        AppLockManager.setPin("1234")
        AppLockManager.lock()
        launchFragment()

        val firstDialog = ShadowDialog.getLatestDialog() as AlertDialog
        firstDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        flushMainLooper()

        onView(withId(R.id.tvEmpty)).check(matches(withText(R.string.unlock))).perform(click())
        flushMainLooper()

        val retryDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotSame(firstDialog, retryDialog)
        assertTrue(retryDialog.isShowing)
    }

    @Test
    fun repeatedResumeDoesNotStackUnlockDialogs() {
        AppLockManager.setPin("1234")
        AppLockManager.lock()
        val scenario = launchFragment()
        val firstDialog = ShadowDialog.getLatestDialog()

        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        flushMainLooper()

        assertEquals(firstDialog, ShadowDialog.getLatestDialog())
        assertEquals(1, ShadowDialog.getShownDialogs().count { it.isShowing })
    }

    @Test
    fun cancellingPinThenClickingFilterKeepsHistoryHiddenAndCannotClear() {
        insertItems()
        AppLockManager.setPin("1234")
        AppLockManager.lock()
        val scenario = launchFragment()

        val pinDialog = ShadowDialog.getLatestDialog() as AlertDialog
        pinDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        flushMainLooper()

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val filter = view.findViewById<android.view.View>(R.id.btnFilterScanned)
            val clear = view.findViewById<android.view.View>(R.id.btnClearAll)
            assertFalse(filter.isEnabled)
            filter.performClick()
            assertFalse(clear.isEnabled)
            assertEquals(android.view.View.GONE, clear.visibility)
            clear.performClick()
        }
        waitForDiff()

        assertFalse(pinDialog.isShowing)
        assertTrue(currentList(scenario).isEmpty())
        assertEquals(3, runBlocking { repository.allHistory.first().size })
        onView(withId(R.id.recyclerView)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun databaseAndQueryEmissionsStayHiddenAfterLockAndRecoverAfterUnlock() {
        runBlocking { repository.insertScan("initial", HistoryType.TEXT) }
        val scenario = launchFragment()
        assertEquals(1, waitForListSize(scenario, 1).size)

        AppLockManager.setPin("1234")
        AppLockManager.lock()
        runBlocking { repository.insertScan("emitted-while-locked", HistoryType.TEXT) }
        waitForListSize(scenario, 0)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
                .setQuery("emitted", true)
        }
        waitForDiff()

        assertTrue(currentList(scenario).isEmpty())
        onView(withId(R.id.recyclerView)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.tvEmpty)).check(matches(withText(R.string.unlock))).perform(click())
        flushMainLooper()

        val pinDialog = ShadowDialog.getLatestDialog() as AlertDialog
        pinDialog.findAllEditTexts().single().setText("1234")
        pinDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        val list = waitForListSize(scenario, 1)
        assertEquals("emitted-while-locked", list.single().content)
        scenario.onFragment { fragment ->
            assertTrue(fragment.requireView().findViewById<android.view.View>(R.id.btnFilterScanned).isEnabled)
        }
    }

    @Test
    fun foregroundTimeoutWithoutDatabaseEmissionHidesHistoryAndOpenContentDialogs() {
        runBlocking { repository.insertScan("timeout-content", HistoryType.TEXT) }
        AppLockManager.setPin("1234")
        AppLockManager.recordUnlock()
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("app_lock", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_unlocked", System.currentTimeMillis() - 5 * 60 * 1000 + 1_000)
            .commit()
        val scenario = launchFragment()
        waitForListSize(scenario, 1)

        scenario.onFragment { fragment ->
            val holder = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
                .findViewHolderForAdapterPosition(0)!!
            holder.itemView.findViewById<android.view.View>(R.id.btnNote).performClick()
        }
        flushMainLooper()
        val notesDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(notesDialog.isShowing)

        Thread.sleep(1_100)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1_001, TimeUnit.MILLISECONDS)
        flushMainLooper()

        assertFalse(notesDialog.isShowing)
        assertTrue(currentList(scenario).isEmpty())
        onView(withId(R.id.recyclerView)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.tvEmpty)).check(matches(withText(R.string.unlock)))
        assertEquals("timeout-content", runBlocking { repository.allHistory.first().single().content })
    }

    private fun AlertDialog.findAllEditTexts(): List<EditText> {
        val result = mutableListOf<EditText>()
        collectEditTexts(window?.decorView as? ViewGroup ?: return result, result)
        return result
    }

    private fun collectEditTexts(root: ViewGroup, out: MutableList<EditText>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is EditText) out.add(child)
            if (child is ViewGroup) collectEditTexts(child, out)
        }
    }
}
