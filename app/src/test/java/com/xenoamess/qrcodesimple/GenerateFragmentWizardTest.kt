package com.xenoamess.qrcodesimple

import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** 结构化内容向导 UI：各类型表单的打开-填写-应用闭环。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentWizardTest {

    private lateinit var scenario: FragmentScenario<GenerateFragment>

    @Before
    fun setup() {
        scenario = FragmentScenario.launchInContainer(GenerateFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun openWizardType(index: Int): AlertDialog {
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnContentWizard).performClick()
        }
        idleMain()
        val typeDialog = ShadowDialog.getLatestDialog() as AlertDialog
        typeDialog.listView.performItemClick(typeDialog.listView.getChildAt(index), index, index.toLong())
        idleMain()
        val form = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(form)
        return form
    }

    private fun fillAndApply(form: AlertDialog, values: Map<Int, String>) {
        val edits = mutableListOf<android.widget.EditText>()
        collectEdits(form.window?.decorView as? android.view.ViewGroup, edits)
        values.forEach { (index, text) -> edits[index].setText(text) }
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()
    }

    private fun collectEdits(root: android.view.ViewGroup?, out: MutableList<android.widget.EditText>) {
        root ?: return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.widget.EditText) out.add(child)
            if (child is android.view.ViewGroup) collectEdits(child, out)
        }
    }

    private fun currentContent(): String {
        var text = ""
        scenario.onFragment { fragment ->
            text = fragment.requireView()
                .findViewById<TextInputEditText>(R.id.etContent)
                .text?.toString() ?: ""
        }
        return text
    }

    @Test
    fun `wizard contact form builds vcard`() {
        val form = openWizardType(1)
        fillAndApply(form, mapOf(0 to "Ada", 1 to "+123", 2 to "ada@x.com", 3 to "Org", 4 to "Addr"))
        val content = currentContent()
        assertTrue(content.contains("BEGIN:VCARD"))
        assertTrue(content.contains("FN:Ada"))
        assertTrue(content.contains("TEL:+123"))
    }

    @Test
    fun `wizard email form builds mailto`() {
        val form = openWizardType(3)
        fillAndApply(form, mapOf(0 to "hi@x.com", 1 to "Hi", 2 to "Body"))
        assertTrue(currentContent().startsWith("mailto:hi@x.com?"))
    }

    @Test
    fun `wizard sms form builds sms payload`() {
        val form = openWizardType(4)
        fillAndApply(form, mapOf(0 to "+8613800138000", 1 to "OK"))
        assertTrue(currentContent().startsWith("sms:+8613800138000"))
    }

    @Test
    fun `wizard phone form builds tel payload`() {
        val form = openWizardType(5)
        fillAndApply(form, mapOf(0 to "+8613800138000"))
        assertTrue(currentContent().startsWith("tel:+8613800138000"))
    }

    @Test
    fun `wizard geo form builds geo payload`() {
        val form = openWizardType(6)
        fillAndApply(form, mapOf(0 to "31.23", 1 to "121.47", 2 to "Shanghai"))
        assertTrue(currentContent().startsWith("geo:31.23,121.47?q=Shanghai"))
    }

    @Test
    fun `wizard url form normalizes scheme`() {
        val form = openWizardType(7)
        fillAndApply(form, mapOf(0 to "example.com"))
        assertTrue(currentContent().startsWith("https://example.com"))
    }

    @Test
    fun `wizard calendar form builds vevent with defaults`() {
        val form = openWizardType(2)
        // 时间按钮保持默认（现在起一小时），只填标题
        fillAndApply(form, mapOf(0 to "Team Sync"))
        val content = currentContent()
        assertTrue(content.contains("BEGIN:VEVENT"))
        assertTrue(content.contains("SUMMARY:Team Sync"))
        assertTrue(content.contains("DTSTART:"))
        assertTrue(content.contains("DTEND:"))
    }
}
