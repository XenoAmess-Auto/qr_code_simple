@file:Suppress("DEPRECATION")

package com.xenoamess.qrcodesimple

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScanImageFragmentTest {

    class ThrowingCameraActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.Theme_QRCodeSimple)
            super.onCreate(savedInstanceState)
            val container = FrameLayout(this).apply { id = View.generateViewId() }
            setContentView(container)
            supportFragmentManager.beginTransaction()
                .replace(container.id, ScanImageFragment())
                .commitNow()
        }

        override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
            throw ActivityNotFoundException("camera disappeared")
        }
    }

    private lateinit var scenario: FragmentScenario<ScanImageFragment>

    @Before
    fun setup() {
        clearFileProviderCache()
        scenario = FragmentScenario.launchInContainer(ScanImageFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    /**
     * FileProvider 把路径策略按 authority 缓存在静态 Map 中（跨测试类共享 JVM 时，
     * 先跑的测试会把沙盒路径冻结进缓存，导致本测试的 external-files 路径无法解析）。
     */
    private fun clearFileProviderCache() {
        val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun getNextStartedActivity(): Intent? {
        var intent: Intent? = null
        scenario.onFragment { fragment ->
            intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }
        return intent
    }

    private fun clickButtonAndCaptureIntent(buttonId: Int): Intent? {
        getNextStartedActivity()
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<View>(buttonId).performClick()
        }
        idleMain()
        return getNextStartedActivity()
    }

    private fun registerCameraHandler() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "test.camera"
                name = "test.camera.CaptureActivity"
            }
        }
        Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            resolveInfo
        )
    }

    private fun cameraFiles(): Set<String> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            ?.listFiles()
            .orEmpty()
            .map(File::getAbsolutePath)
            .toSet()
    }

    private fun receiveCameraResult(captureIntent: Intent, resultCode: Int) {
        scenario.onFragment { fragment ->
            Shadows.shadowOf(fragment.requireActivity()).receiveResult(captureIntent, resultCode, null)
        }
        idleMain()
    }

    @Test
    fun galleryButtonLaunchesPickImageIntent() {
        val intent = clickButtonAndCaptureIntent(R.id.btnGallery)
        assertNotNull(intent)
        assertEquals(Intent.ACTION_PICK, intent?.action)
    }

    @Test
    fun cameraButtonLaunchesImageCaptureIntent() {
        registerCameraHandler()
        val intent = clickButtonAndCaptureIntent(R.id.btnCamera)
        assertNotNull(intent)
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent?.action)
        val outputUri = intent?.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
        assertNotNull(outputUri)
        assertEquals(outputUri, intent?.clipData?.getItemAt(0)?.uri)
        assertTrue(intent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }

    @Test
    fun cameraButtonWithoutHandlerDoesNotLaunchOrLeaveTemporaryFile() {
        val filesBefore = cameraFiles()

        val intent = clickButtonAndCaptureIntent(R.id.btnCamera)

        assertNull(intent)
        assertEquals(filesBefore, cameraFiles())
    }

    @Test
    fun cameraLaunchExceptionDoesNotEscapeOrLeaveTemporaryFile() {
        registerCameraHandler()
        val filesBefore = cameraFiles()
        val controller = org.robolectric.Robolectric.buildActivity(ThrowingCameraActivity::class.java).setup()
        val fragment = controller.get().supportFragmentManager.fragments.single() as ScanImageFragment

        fragment.requireView().findViewById<View>(R.id.btnCamera).performClick()

        assertEquals(filesBefore, cameraFiles())
        controller.destroy()
    }

    @Test
    fun cancelledCameraCaptureDeletesTemporaryFile() {
        registerCameraHandler()
        val filesBefore = cameraFiles()
        val intent = clickButtonAndCaptureIntent(R.id.btnCamera)!!
        assertEquals(filesBefore.size + 1, cameraFiles().size)

        receiveCameraResult(intent, android.app.Activity.RESULT_CANCELED)

        assertEquals(filesBefore, cameraFiles())
    }

    @Test
    fun successfulCameraCaptureSurvivesFragmentRecreation() {
        registerCameraHandler()
        val intent = clickButtonAndCaptureIntent(R.id.btnCamera)!!
        val outputUri = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
        val pngBytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT
        )
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .contentResolver
            .openOutputStream(outputUri)!!
            .use { it.write(pngBytes) }

        scenario.recreate()
        idleMain()
        receiveCameraResult(intent, android.app.Activity.RESULT_OK)

        var startedIntent: Intent? = null
        val deadline = System.currentTimeMillis() + 5_000
        while (startedIntent == null && System.currentTimeMillis() < deadline) {
            idleMain()
            scenario.onFragment { fragment ->
                startedIntent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            }
            if (startedIntent == null) Thread.sleep(20)
        }
        assertNotNull(startedIntent)
        assertEquals(ResultActivity::class.java.name, startedIntent?.component?.className)
        assertEquals(outputUri.toString(), startedIntent?.getStringExtra(ResultActivity.EXTRA_BITMAP_URI))
        assertFalse(cameraFiles().isEmpty())
    }

    @Test
    fun fileButtonLaunchesOpenDocumentIntent() {
        val intent = clickButtonAndCaptureIntent(R.id.btnFile)
        assertNotNull(intent)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent?.action)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val mimeTypes = intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        assertNotNull(mimeTypes)
        assertTrue(mimeTypes!!.contains("image/*"))
        assertTrue(mimeTypes.contains("video/*"))
    }
}
