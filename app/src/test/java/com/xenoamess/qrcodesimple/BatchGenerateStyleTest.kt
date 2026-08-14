package com.xenoamess.qrcodesimple

import android.os.Looper
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** 批量生成样式对话框：打开、选择预设、应用后按钮标记。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchGenerateStyleTest {

    private var scenario: ActivityScenario<BatchGenerateActivity>? = null

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(BatchGenerateActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `style dialog opens with scheme donuts and logo row`() {
        scenario?.onActivity { activity ->
            activity.findViewById<Button>(R.id.btnBatchStyle).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        val decor = dialog.window?.decorView as? android.view.ViewGroup
        assertNotNull(decor)
        assertTrue(countViews(decor!!) > 5)
    }

    private fun countViews(root: android.view.ViewGroup): Int {
        var count = 0
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            count++
            if (child is android.view.ViewGroup) count += countViews(child)
        }
        return count
    }

    @Test
    fun `picking a scheme marks the style button active`() {
        scenario?.onActivity { activity ->
            activity.findViewById<Button>(R.id.btnBatchStyle).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val scroll = findHorizontalScroll(dialog.window?.decorView as? android.view.ViewGroup)
        assertNotNull(scroll)
        val row = scroll!!.getChildAt(0) as android.view.ViewGroup
        assertTrue(row.childCount > 1)
        row.getChildAt(1).performClick()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        scenario?.onActivity { activity ->
            assertTrue(activity.findViewById<Button>(R.id.btnBatchStyle).text.toString().endsWith("✓"))
        }
    }

    private fun findHorizontalScroll(root: android.view.ViewGroup?): android.widget.HorizontalScrollView? {
        root ?: return null
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.widget.HorizontalScrollView) return child
            if (child is android.view.ViewGroup) {
                val found = findHorizontalScroll(child)
                if (found != null) return found
            }
        }
        return null
    }
}
