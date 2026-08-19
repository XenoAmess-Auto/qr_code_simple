package com.xenoamess.qrcodesimple

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
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
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * GenerateFragment 保存/分享用户场景（API 28 传统存储路径）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentSaveTest {

    private lateinit var scenario: FragmentScenario<GenerateFragment>

    @Before
    fun setup() {
        clearFileProviderCache()
        pendingSvgFiles().forEach(File::delete)
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        scenario = FragmentScenario.launchInContainer(GenerateFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    private fun clearFileProviderCache() {
        try {
            val field = Class.forName("androidx.core.content.FileProvider").getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as java.util.HashMap<*, *>).clear()
        } catch (_: Exception) {
        }
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun generateContent(text: String) {
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<TextInputEditText>(R.id.etContent).setText(text)
            fragment.requireView().findViewById<Button>(R.id.btnGenerate).performClick()
        }
        awaitPreview()
    }

    private fun awaitPreview() {
        repeat(100) {
            idleMain()
            var ready = false
            scenario.onFragment { ready = it.currentBitmap != null }
            if (ready) return
            Thread.sleep(25)
        }
        throw AssertionError("Preview did not complete")
    }

    private fun awaitExportCompletion(): GenerateExportState {
        repeat(200) {
            idleMain()
            var state: GenerateExportState? = null
            scenario.onFragment { state = it.exportState }
            when (val result = state) {
                is GenerateExportState.Completed, is GenerateExportState.Failed -> return result
                else -> Thread.sleep(25)
            }
        }
        throw AssertionError("Export did not complete")
    }

    private fun awaitHistory(content: String): Boolean {
        val repository = com.xenoamess.qrcodesimple.data.HistoryRepository(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        repeat(100) {
            idleMain()
            val items = kotlinx.coroutines.runBlocking { repository.allHistory.first() }
            if (items?.any { it.content == content } == true) return true
            Thread.sleep(25)
        }
        return false
    }

    @Test
    fun `save barcode writes png to pictures dir`() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        generateContent("save-test-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(awaitExportCompletion() is GenerateExportState.Completed)

        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val pngFiles = pictures.listFiles { f -> f.name.startsWith("qrcode_") && f.name.endsWith(".png") }
        assertNotNull(pngFiles)
        assertTrue(pngFiles!!.isNotEmpty())
        assertTrue(pngFiles.maxByOrNull { it.lastModified() }!!.length() > 0)
    }

    @Test
    fun `api28 raster save requests permission and resumes after recreation`() {
        generateContent("permission-recreate-content")
        val requestCode = requestDefaultRasterSave()

        scenario.onFragment { fragment ->
            val request = Shadows.shadowOf(fragment.requireActivity()).lastRequestedPermission
            assertNotNull(request)
            assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, request!!.requestedPermissions.single())
        }

        scenario.recreate()
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        scenario.onFragment { fragment ->
            (fragment.requireActivity() as ComponentActivity).activityResultRegistry.dispatchResult(requestCode, true)
        }

        assertTrue(awaitExportCompletion() is GenerateExportState.Completed)
    }

    @Test
    fun `api28 raster save denial shows explicit error`() {
        generateContent("permission-denied-content")
        val requestCode = requestDefaultRasterSave()
        ShadowToast.reset()

        scenario.onFragment { fragment ->
            (fragment.requireActivity() as ComponentActivity).activityResultRegistry.dispatchResult(requestCode, false)
        }
        idleMain()

        assertEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.storage_write_permission_denied),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    @Config(sdk = [29], application = QRCodeApp::class)
    fun `api29 raster save does not request legacy permission`() {
        generateContent("api29-no-permission-content")
        requestDefaultRasterSave()

        scenario.onFragment { fragment ->
            assertNull(Shadows.shadowOf(fragment.requireActivity()).lastRequestedPermission)
        }
    }

    @Test
    fun `save as svg launches create document`() {
        generateContent("svg-test-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.findViewById<android.widget.RadioButton>(R.id.rbFormatSvg)!!.isChecked = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
        }
    }

    @Test
    fun `pending svg is written after recreation`() {
        generateContent("svg-recreate-content")
        val requestCode = launchSvgSave()
        val destination = Uri.parse("content://test/svg-recreate")
        val output = ByteArrayOutputStream()
        Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        ).registerOutputStream(destination, output)

        scenario.recreate()
        dispatchDocumentResult(requestCode, destination)
        idleMain()

        assertTrue(output.toString(Charsets.UTF_8.name()).contains("<svg"))
        assertEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.svg_saved),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `null svg output stream does not report success`() {
        generateContent("svg-null-stream")
        val requestCode = launchSvgSave()
        val destination = Uri.parse("content://test/svg-null")
        Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        ).registerOutputStreamSupplier(destination) { null }
        ShadowToast.reset()

        dispatchDocumentResult(requestCode, destination)
        idleMain()

        assertTrue(ShadowToast.getTextOfLatestToast()?.contains("Failed to save") == true)
        assertTrue(pendingSvgFiles().isEmpty())
    }

    @Test
    fun `svg output exception does not report success or retain pending file`() {
        generateContent("svg-output-exception")
        val requestCode = launchSvgSave()
        val destination = Uri.parse("content://test/svg-exception")
        Shadows.shadowOf(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        ).registerOutputStreamSupplier(destination) { throw IOException("write failed") }
        ShadowToast.reset()

        dispatchDocumentResult(requestCode, destination)
        idleMain()

        assertTrue(ShadowToast.getTextOfLatestToast()?.contains("Failed to save") == true)
        assertTrue(pendingSvgFiles().isEmpty())
    }

    @Test
    fun `share barcode launches chooser`() {
        generateContent("share-test-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnShare).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, dialog.listView.adapter.getItemId(0))
        assertTrue(awaitExportCompletion() is GenerateExportState.Completed)

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertTrue(
                intent?.action == Intent.ACTION_CHOOSER || intent?.action == Intent.ACTION_SEND
            )
        }
    }

    private fun launchSvgSave(): Int {
        var requestCode = 0
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.findViewById<android.widget.RadioButton>(R.id.rbFormatSvg)!!.isChecked = true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()
        scenario.onFragment { fragment ->
            requestCode = Shadows.shadowOf(fragment.requireActivity())
                .nextStartedActivityForResult.requestCode
        }
        return requestCode
    }

    private fun requestDefaultRasterSave(): Int {
        var requestCode = 0
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        idleMain()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()
        scenario.onFragment { fragment ->
            requestCode = Shadows.shadowOf(fragment.requireActivity()).lastRequestedPermission?.requestCode ?: 0
        }
        return requestCode
    }

    private fun dispatchDocumentResult(requestCode: Int, destination: Uri) {
        scenario.onFragment { fragment ->
            (fragment.requireActivity() as ComponentActivity).activityResultRegistry.dispatchResult(
                requestCode,
                Activity.RESULT_OK,
                Intent().setData(destination)
            )
        }
    }

    private fun pendingSvgFiles(): List<File> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return File(context.cacheDir, "images").listFiles()
            ?.filter { it.name.startsWith("pending-svg-") }
            .orEmpty()
    }

    @Test
    fun `share card option launches chooser`() {
        generateContent("share-card-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnShare).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.listView.performItemClick(null, 1, 0)
        assertTrue(awaitExportCompletion() is GenerateExportState.Completed)

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertTrue(
                intent?.action == Intent.ACTION_CHOOSER || intent?.action == Intent.ACTION_SEND
            )
        }
    }

    @Test
    fun `content wizard wifi fills structured payload`() {
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnContentWizard).performClick()
        }
        idleMain()

        val typeDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(typeDialog)
        typeDialog.listView.performItemClick(typeDialog.listView.getChildAt(0), 0, 0)
        idleMain()

        val formDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(formDialog)
        val edits = mutableListOf<android.widget.EditText>()
        collectEditTexts(formDialog.window?.decorView as? android.view.ViewGroup ?: return, edits)
        assertTrue(edits.size >= 2)
        edits[0].setText("TestNet")
        edits[1].setText("pass123")
        formDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        scenario.onFragment { fragment ->
            val content = fragment.requireView()
                .findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etContent)
                .text?.toString() ?: ""
            assertTrue(content.startsWith("WIFI:"))
            assertTrue(content.contains("S:TestNet;"))
            assertTrue(content.contains("P:pass123;"))
        }
    }

    private fun collectEditTexts(root: android.view.ViewGroup, out: MutableList<android.widget.EditText>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.widget.EditText) out.add(child)
            if (child is android.view.ViewGroup) collectEditTexts(child, out)
        }
    }

    @Test
    fun `batch generate button launches BatchGenerateActivity`() {
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnBatchGenerate).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertEquals(BatchGenerateActivity::class.java.name, intent.component?.className)
        }
    }

    @Test
    fun `generate writes history record`() {
        generateContent("history-record-content")
        assertTrue(awaitHistory("history-record-content"))
    }
}
