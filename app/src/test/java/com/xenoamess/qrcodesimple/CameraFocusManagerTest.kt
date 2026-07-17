package com.xenoamess.qrcodesimple

import android.graphics.Rect
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ZoomState
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * CameraFocusManager 测试：通过动态代理伪造 CameraX 接口，
 * 验证对焦动作构造与缩放钳制逻辑。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class CameraFocusManagerTest {

    private class FakeCameraHolder(
        val minZoom: Float = 1f,
        val maxZoom: Float = 8f,
        var currentZoom: Float = 1f
    ) {
        var lastFocusAction: FocusMeteringAction? = null
        var cancelFocusCalled = false

        val cameraControl: CameraControl = Proxy.newProxyInstance(
            CameraControl::class.java.classLoader,
            arrayOf(CameraControl::class.java),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "startFocusAndMetering" -> {
                        lastFocusAction = args[0] as FocusMeteringAction
                        null
                    }
                    "setZoomRatio" -> {
                        currentZoom = args[0] as Float
                        null
                    }
                    "cancelFocusAndMetering" -> {
                        cancelFocusCalled = true
                        null
                    }
                    "enableTorch" -> null
                    else -> null
                }
            }
        ) as CameraControl

        private val zoomState: ZoomState = Proxy.newProxyInstance(
            ZoomState::class.java.classLoader,
            arrayOf(ZoomState::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "getZoomRatio" -> currentZoom
                    "getMinZoomRatio" -> minZoom
                    "getMaxZoomRatio" -> maxZoom
                    "getLinearZoom" -> 0f
                    else -> null
                }
            }
        ) as ZoomState

        private val cameraInfo: CameraInfo = Proxy.newProxyInstance(
            CameraInfo::class.java.classLoader,
            arrayOf(CameraInfo::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "getZoomState" -> MutableLiveData(zoomState)
                    else -> null
                }
            }
        ) as CameraInfo

        val camera: Camera = Proxy.newProxyInstance(
            Camera::class.java.classLoader,
            arrayOf(Camera::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "getCameraControl" -> cameraControl
                    "getCameraInfo" -> cameraInfo
                    else -> null
                }
            }
        ) as Camera
    }

    @Test
    fun `focusOnPoint starts focus and metering with AF flag`() {
        val holder = FakeCameraHolder()
        CameraFocusManager.focusOnPoint(holder.camera, 100f, 200f, 1000f, 2000f)

        val action = holder.lastFocusAction
        assertNotNull(action)
        assertTrue(action!!.meteringPointsAf.isNotEmpty())
    }

    @Test
    fun `setZoom clamps to camera zoom range`() {
        val holder = FakeCameraHolder(minZoom = 1f, maxZoom = 4f)

        CameraFocusManager.setZoom(holder.camera, 10f)
        assertEquals(4f, holder.currentZoom, 0.001f)

        CameraFocusManager.setZoom(holder.camera, 0.5f)
        assertEquals(1f, holder.currentZoom, 0.001f)

        CameraFocusManager.setZoom(holder.camera, 2f)
        assertEquals(2f, holder.currentZoom, 0.001f)
    }

    @Test
    fun `zoomIn and zoomOut respect bounds`() {
        val holder = FakeCameraHolder(minZoom = 1f, maxZoom = 3f)

        CameraFocusManager.setZoom(holder.camera, 2.5f)
        CameraFocusManager.zoomIn(holder.camera, 1f)
        assertEquals(3f, holder.currentZoom, 0.001f)

        CameraFocusManager.zoomOut(holder.camera, 5f)
        assertEquals(1f, holder.currentZoom, 0.001f)
    }

    @Test
    fun `adjustZoomForQRSize zooms in for small codes and resets for large`() {
        val holder = FakeCameraHolder(maxZoom = 8f)

        // 小码（占预览 1%）→ 2x
        CameraFocusManager.adjustZoomForQRSize(
            holder.camera, Rect(0, 0, 100, 100), 1000, 1000
        )
        assertEquals(2f, holder.currentZoom, 0.001f)

        // 中等码（占 6%）→ 1.5x
        CameraFocusManager.adjustZoomForQRSize(
            holder.camera, Rect(0, 0, 245, 245), 1000, 1000
        )
        assertEquals(1.5f, holder.currentZoom, 0.001f)

        // 大码（占 64%）→ 恢复 1x
        CameraFocusManager.adjustZoomForQRSize(
            holder.camera, Rect(0, 0, 800, 800), 1000, 1000
        )
        assertEquals(1f, holder.currentZoom, 0.001f)
    }

    @Test
    fun `adjustZoomForQRSize ignores null bounding box`() {
        val holder = FakeCameraHolder()
        CameraFocusManager.adjustZoomForQRSize(holder.camera, null, 1000, 1000)
        assertEquals(1f, holder.currentZoom, 0.001f)
    }

    @Test
    fun `resetFocus cancels metering and restores 1x`() {
        val holder = FakeCameraHolder()
        CameraFocusManager.setZoom(holder.camera, 3f)

        CameraFocusManager.resetFocus(holder.camera)
        assertTrue(holder.cancelFocusCalled)
        assertEquals(1f, holder.currentZoom, 0.001f)
    }

    @Test
    fun `resetFocus with null camera is a no-op`() {
        CameraFocusManager.resetFocus(null)
    }

    @Test
    fun `autoFocus with null camera returns immediately`() {
        kotlinx.coroutines.runBlocking {
            CameraFocusManager.autoFocus(null)
        }
    }
}
