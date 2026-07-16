package com.xenoamess.qrcodesimple

import android.os.Looper
import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 相机扫描页框选模式开关测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class CameraScanRegionToggleTest {

    private lateinit var scenario: FragmentScenario<CameraScanFragment>

    @Before
    fun setup() {
        scenario = FragmentScenario.launchInContainer(CameraScanFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `region view hidden by default and overlay visible`() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            assertEquals(View.GONE, view.findViewById<ScanRegionView>(R.id.scanRegionView).visibility)
            assertEquals(View.VISIBLE, view.findViewById<ScannerOverlayView>(R.id.scannerOverlay).visibility)
        }
    }

    @Test
    fun `toggle button shows and hides region view`() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val regionView = view.findViewById<ScanRegionView>(R.id.scanRegionView)
            val button = view.findViewById<ImageButton>(R.id.btnScanRegion)

            button.performClick()
            idleMain()
            assertEquals(View.VISIBLE, regionView.visibility)

            button.performClick()
            idleMain()
            assertEquals(View.GONE, regionView.visibility)
        }
    }
}
