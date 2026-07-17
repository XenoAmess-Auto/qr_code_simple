package com.xenoamess.qrcodesimple

import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * HistoryDetailFragment 用户场景测试：编辑保存、收藏切换、删除确认。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class HistoryDetailScenarioTest {

    private lateinit var repository: HistoryRepository
    private var scenario: FragmentScenario<HistoryDetailFragment>? = null
    private var itemId: Long = -1

    @Before
    fun setup() {
        repository = HistoryRepository(ApplicationProvider.getApplicationContext())
        runBlocking {
            repository.deleteAll()
            repository.insertGenerate("detail-content", HistoryType.QR_CODE, "QR_CODE", null)
            itemId = repository.allHistory.first().first().id
        }
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

    private fun launch(): FragmentScenario<HistoryDetailFragment> {
        val args = Bundle().apply { putLong(HistoryDetailFragment.ARG_ITEM_ID, itemId) }
        val s = launchFragmentInContainer<HistoryDetailFragment>(args, R.style.Theme_QRCodeSimple)
        s.moveToState(Lifecycle.State.RESUMED)
        scenario = s
        // 等 Flow 收集并绑定
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            flush()
            var bound = false
            s.onFragment { fragment ->
                bound = fragment.requireView().findViewById<TextView>(R.id.tvContent).text.isNotEmpty()
            }
            if (bound) break
            Thread.sleep(50)
        }
        return s
    }

    @Test
    fun `detail shows content and barcode`() {
        launch()
        scenario?.onFragment { fragment ->
            assertEquals("detail-content", fragment.requireView().findViewById<TextView>(R.id.tvContent).text.toString())
            assertNotNull(fragment.requireView().findViewById<android.widget.ImageView>(R.id.ivBarcode).drawable)
        }
    }

    @Test
    fun `edit dialog saves new content`() {
        launch()
        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnEdit).performClick()
        }
        flush()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)

        var editText: EditText? = null
        fun walk(v: android.view.View) {
            if (v is EditText) editText = v
            if (v is android.view.ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        dialog.window?.decorView?.let { walk(it) }
        assertNotNull(editText)

        editText!!.setText("edited-content")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            flush()
            val current = runBlocking { repository.allHistory.first().first().content }
            if (current == "edited-content") break
            Thread.sleep(50)
        }
        assertEquals("edited-content", runBlocking { repository.allHistory.first().first().content })
    }

    @Test
    fun `toggle favorite updates repository`() {
        launch()
        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnToggleFavorite).performClick()
        }

        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            flush()
            if (runBlocking { repository.allHistory.first().first().isFavorite }) break
            Thread.sleep(50)
        }
        assertTrue(runBlocking { repository.allHistory.first().first().isFavorite })
    }

    @Test
    fun `delete confirm removes item`() {
        launch()
        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnDelete).performClick()
        }
        flush()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            flush()
            if (runBlocking { repository.allHistory.first().isEmpty() }) break
            Thread.sleep(50)
        }
        assertTrue(runBlocking { repository.allHistory.first().isEmpty() })
    }

    @Test
    fun `edit tags dialog persists tags`() {
        launch()
        scenario?.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnEditTags).performClick()
        }
        flush()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)

        var editText: EditText? = null
        fun walk(v: android.view.View) {
            if (v is EditText) editText = v
            if (v is android.view.ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        dialog.window?.decorView?.let { walk(it) }
        assertNotNull(editText)

        editText!!.setText("alpha,beta")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            flush()
            val tags = runBlocking { repository.allHistory.first().first().tags }
            if (tags == "alpha,beta") break
            Thread.sleep(50)
        }
        assertEquals("alpha,beta", runBlocking { repository.allHistory.first().first().tags })
    }
}
