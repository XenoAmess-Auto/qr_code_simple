package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Environment
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.first
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
import java.io.File

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
        idleMain()
        Thread.sleep(300)
        idleMain()
    }

    @Test
    fun `save barcode writes png to pictures dir`() {
        generateContent("save-test-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<Button>(R.id.btnSave).performClick()
        }
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.listView.performItemClick(null, 0, 0)
        idleMain()

        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val pngFiles = pictures.listFiles { f -> f.name.startsWith("qrcode_") && f.name.endsWith(".png") }
        assertNotNull(pngFiles)
        assertTrue(pngFiles!!.isNotEmpty())
        assertTrue(pngFiles.maxByOrNull { it.lastModified() }!!.length() > 0)
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
        dialog.listView.performItemClick(null, 1, 0)
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
        }
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
        idleMain()
        Thread.sleep(300)
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertTrue(
                intent?.action == Intent.ACTION_CHOOSER || intent?.action == Intent.ACTION_SEND
            )
        }
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
        idleMain()
        Thread.sleep(500)
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertTrue(
                intent?.action == Intent.ACTION_CHOOSER || intent?.action == Intent.ACTION_SEND
            )
        }
    }

    @Test
    fun `generate writes history record`() {
        generateContent("history-record-content")

        val repository = com.xenoamess.qrcodesimple.data.HistoryRepository(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        val items = kotlinx.coroutines.runBlocking {
            repository.allHistory.first()
        }
        assertTrue(items?.any { it.content == "history-record-content" } == true)
    }
}
