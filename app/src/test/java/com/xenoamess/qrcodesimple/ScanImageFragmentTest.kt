package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScanImageFragmentTest {

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

    @Test
    fun galleryButtonLaunchesPickImageIntent() {
        val intent = clickButtonAndCaptureIntent(R.id.btnGallery)
        assertNotNull(intent)
        assertEquals(Intent.ACTION_PICK, intent?.action)
    }

    @Test
    fun cameraButtonLaunchesImageCaptureIntent() {
        val intent = clickButtonAndCaptureIntent(R.id.btnCamera)
        assertNotNull(intent)
        assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, intent?.action)
        assertNotNull(intent?.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT))
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
