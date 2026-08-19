package com.xenoamess.qrcodesimple

import android.Manifest
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = QRCodeApp::class)
class MainActivityPermissionTest {

    private lateinit var controller: ActivityController<MainActivity>

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(application).denyPermissions(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        controller = Robolectric.buildActivity(MainActivity::class.java).create()
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun firstCreateDoesNotRequestPermissions() {
        assertNull(Shadows.shadowOf(controller.get()).lastRequestedPermission)
    }

    @Test
    fun enteringRealtimeCameraRequestsOnlyCameraPermission() {
        controller.start().resume().visible()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val request = Shadows.shadowOf(controller.get()).lastRequestedPermission
        assertNotNull(request)
        assertArrayEquals(arrayOf(Manifest.permission.CAMERA), request?.requestedPermissions)
    }
}
