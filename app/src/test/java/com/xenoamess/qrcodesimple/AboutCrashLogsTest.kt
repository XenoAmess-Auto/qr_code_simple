package com.xenoamess.qrcodesimple

import android.os.Looper
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** About 页崩溃日志入口：空态、有日志态、清除。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class AboutCrashLogsTest {

    private lateinit var scenario: FragmentScenario<AboutFragment>
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        CrashLogger.clear(context)
        scenario = FragmentScenario.launchInContainer(AboutFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
        CrashLogger.clear(context)
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun clickCrashLogs() {
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<android.view.View>(R.id.btnCrashLogs).performClick()
        }
        idleMain()
    }

    @Test
    fun `empty state dialog shows empty message`() {
        clickCrashLogs()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(dialog)
        assertTrue(
            dialog.findViewById<android.widget.TextView>(android.R.id.message)?.text?.toString()
                == context.getString(R.string.crash_log_empty)
        )
    }

    @Test
    fun `clearing logs requires explicit confirmation`() {
        CrashLogger.write(context, Thread.currentThread(), IllegalStateException("test-crash-marker"))
        clickCrashLogs()

        val logsDialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotNull(logsDialog)
        val message = logsDialog.findViewById<android.widget.TextView>(android.R.id.message)?.text?.toString() ?: ""
        assertTrue(message.contains("test-crash-marker"))

        logsDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        idleMain()
        assertTrue(CrashLogger.listLogs(context).isNotEmpty())

        val confirmDialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertNotSame(logsDialog, confirmDialog)
        assertTrue(confirmDialog.isShowing)
        confirmDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertTrue(CrashLogger.listLogs(context).isEmpty())
    }

    @Test
    fun `share button launches text share`() {
        CrashLogger.write(context, Thread.currentThread(), IllegalStateException("share-marker"))
        clickCrashLogs()

        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertTrue(
                intent?.action == android.content.Intent.ACTION_CHOOSER ||
                    intent?.action == android.content.Intent.ACTION_SEND
            )
        }
    }
}
