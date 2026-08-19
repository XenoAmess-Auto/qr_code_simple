package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.appcompat.app.AlertDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BackupActivityTest {

    private lateinit var scenario: ActivityScenario<BackupActivity>

    @Before
    fun setup() {
        LocaleHelper.setLanguage(ApplicationProvider.getApplicationContext(), "system")
        scenario = ActivityScenario.launch(BackupActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun captureNextStartedActivity(): Intent? {
        var intent: Intent? = null
        scenario.onActivity { activity ->
            intent = Shadows.shadowOf(activity).nextStartedActivity
        }
        return intent
    }

    private fun waitUntil(timeoutMs: Long = 5000, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            if (predicate()) return true
            Thread.sleep(25)
        }
        idleMain()
        return predicate()
    }

    private fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

    private fun editTexts(dialog: AlertDialog): List<EditText> {
        val result = mutableListOf<EditText>()
        fun walk(view: android.view.View) {
            if (view is EditText) result += view
            if (view is android.view.ViewGroup) {
                (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
            }
        }
        dialog.window?.decorView?.let(::walk)
        return result
    }

    private fun prepareEncryptedExport(password: String = "pw123"): Intent {
        scenario.onActivity { activity ->
            activity.findViewById<android.widget.Button>(R.id.btnExportEncrypted).performClick()
        }
        idleMain()
        val dialog = latestDialog()
        val fields = editTexts(dialog)
        assertEquals(2, fields.size)
        fields[0].setText(password)
        fields[1].setText(password)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()
        return captureNextStartedActivity()!!
    }

    @Test
    fun exportJsonButtonLaunchesCreateDocumentIntent() {
        onView(withId(R.id.btnExportJson)).perform(click())
        idleMain()

        val intent = captureNextStartedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
        assertEquals("application/json", intent?.type)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val title = intent?.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(title)
        assertTrue(title!!.endsWith(".json"))
    }

    @Test
    fun exportCsvButtonLaunchesCreateDocumentIntent() {
        onView(withId(R.id.btnExportCsv)).perform(click())
        idleMain()

        val intent = captureNextStartedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
        assertEquals("text/csv", intent?.type)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val title = intent?.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(title)
        assertTrue(title!!.endsWith(".csv"))
    }

    @Test
    fun importButtonLaunchesOpenDocumentIntent() {
        // 布局在 ScrollView 中，Robolectric 下 Espresso scrollTo 不可靠，直接触发点击
        scenario.onActivity { activity ->
            activity.findViewById<android.widget.Button>(R.id.btnImport).performClick()
        }
        idleMain()

        val intent = captureNextStartedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent?.action)
        assertEquals("*/*", intent?.type)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val mimeTypes = intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        assertNotNull(mimeTypes)
        assertTrue(mimeTypes!!.contains("application/json"))
        assertTrue(mimeTypes.contains("text/csv"))
        assertTrue(mimeTypes.contains("application/octet-stream"))
        assertFalse(mimeTypes.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    @Test
    fun exportPasswordMismatchKeepsDialogOpenForCorrection() {
        scenario.onActivity { activity ->
            activity.findViewById<android.widget.Button>(R.id.btnExportEncrypted).performClick()
        }
        idleMain()

        val dialog = latestDialog()
        val fields = editTexts(dialog)
        fields[0].setText("first")
        fields[1].setText("second")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertTrue(dialog.isShowing)
        assertEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.backup_password_mismatch),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun resultOkWithoutUriClearsPendingEncryptedPassword() {
        val requestIntent = prepareEncryptedExport()

        scenario.onActivity { activity ->
            Shadows.shadowOf(activity).receiveResult(
                requestIntent,
                android.app.Activity.RESULT_OK,
                Intent()
            )
            val passwordField = BackupActivity::class.java
                .getDeclaredField("pendingExportPassword")
                .apply { isAccessible = true }
            val kindField = BackupActivity::class.java
                .getDeclaredField("pendingExportKind")
                .apply { isAccessible = true }
            assertNull(passwordField.get(activity))
            assertNull(kindField.get(activity))
        }
    }

    @Test
    fun recreatedEncryptedExportNeverWritesPlaintextWhenPasswordWasLost() {
        prepareEncryptedExport()
        scenario.recreate()
        idleMain()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "recreated_${System.nanoTime()}.json")

        scenario.onActivity { activity -> activity.consumePendingExport(Uri.fromFile(file)) }

        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast()?.toString()?.startsWith(
                context.getString(R.string.export_failed, "").substringBefore(":")
            ) == true
        })
        assertTrue(!file.exists() || file.length() == 0L || BackupCrypto.isEncrypted(file.readBytes()))
    }

    @Test
    fun nullOutputStreamReportsFailureInsteadOfSuccess() {
        val uri = Uri.parse("content://backup-test/null-output")
        scenario.onActivity { activity ->
            Shadows.shadowOf(activity.contentResolver).registerOutputStreamSupplier(uri) { null }
            activity.findViewById<android.widget.Button>(R.id.btnExportJson).performClick()
            activity.consumePendingExport(uri)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(waitUntil {
            val toast = ShadowToast.getTextOfLatestToast()?.toString().orEmpty()
            toast.startsWith(context.getString(R.string.export_failed, "").substringBefore(":"))
        })
    }

    @Test
    fun outputStreamExceptionReportsFailureInsteadOfSuccess() {
        val uri = Uri.parse("content://backup-test/throwing-output")
        scenario.onActivity { activity ->
            Shadows.shadowOf(activity.contentResolver).registerOutputStreamSupplier(uri) {
                throw FileNotFoundException("provider rejected output")
            }
            activity.findViewById<android.widget.Button>(R.id.btnExportCsv).performClick()
            activity.consumePendingExport(uri)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(waitUntil {
            val toast = ShadowToast.getTextOfLatestToast()?.toString().orEmpty()
            toast.startsWith(context.getString(R.string.export_failed, "").substringBefore(":"))
        })
    }
}
