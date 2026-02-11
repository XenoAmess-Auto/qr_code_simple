package com.xenoamess.qrcodesimple

import android.graphics.Rect
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 相机对焦管理器
 * 支持手动对焦和自动对焦
 */
object CameraFocusManager {

    private const val TAG = "CameraFocus"
    private const val AUTO_FOCUS_DELAY_MS = 2000L  // 自动对焦延迟
    private const val FOCUS_AREA_SIZE = 100  // 对焦区域大小

    private var lastFocusTime = 0L
    private var isAutoFocusing = false

    /**
     * 手动对焦（点击屏幕对焦）
     */
    fun focusOnPoint(
        camera: Camera,
        x: Float,
        y: Float,
        previewWidth: Float,
        previewHeight: Float
    ) {
        try {
            val factory = SurfaceOrientedMeteringPointFactory(previewWidth, previewHeight)
            val point = factory.createPoint(x, y, FOCUS_AREA_SIZE / previewWidth)

            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            camera.cameraControl.startFocusAndMetering(action)
            lastFocusTime = System.currentTimeMillis()

            Log.d(TAG, "Manual focus at ($x, $y)")
        } catch (e: Exception) {
            Log.e(TAG, "Manual focus failed", e)
        }
    }

    /**
     * 自动对焦
     * 定期触发对焦以确保清晰度
     */
    suspend fun autoFocus(camera: Camera?) {
        if (camera == null || isAutoFocusing) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFocusTime < AUTO_FOCUS_DELAY_MS) return

        isAutoFocusing = true

        try {
            withContext(Dispatchers.Main) {
                // 使用中心点对焦
                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                val centerPoint = factory.createPoint(0.5f, 0.5f)

                val action = FocusMeteringAction.Builder(centerPoint)
                    .setAutoCancelDuration(1, TimeUnit.SECONDS)
                    .build()

                camera.cameraControl.startFocusAndMetering(action)
                lastFocusTime = currentTime

                Log.d(TAG, "Auto focus triggered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto focus failed", e)
        } finally {
            isAutoFocusing = false
        }
    }

    /**
     * 设置连续自动对焦模式
     */
    fun enableContinuousFocus(camera: Camera) {
        try {
            // 设置连续自动对焦
            val cameraControl = camera.cameraControl
            cameraControl.setZoomRatio(1f)

            Log.d(TAG, "Continuous focus enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable continuous focus", e)
        }
    }

    /**
     * 根据二维码大小调整焦距
     * 当二维码较小时，适当增加缩放以辅助识别
     */
    fun adjustZoomForQRSize(
        camera: Camera,
        qrBoundingBox: Rect?,
        previewWidth: Int,
        previewHeight: Int
    ) {
        if (qrBoundingBox == null) return

        try {
            val qrWidth = qrBoundingBox.width()
            val qrHeight = qrBoundingBox.height()
            val qrArea = qrWidth * qrHeight
            val previewArea = previewWidth * previewHeight

            // 计算二维码占预览画面的比例
            val qrRatio = qrArea.toFloat() / previewArea

            Log.d(TAG, "QR code ratio: $qrRatio")

            // 如果二维码太小，适当增加缩放
            when {
                qrRatio < 0.05f -> setZoom(camera, 2f)
                qrRatio < 0.1f -> setZoom(camera, 1.5f)
                qrRatio > 0.5f -> setZoom(camera, 1f)  // 太大时恢复
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust zoom", e)
        }
    }

    /**
     * 设置缩放级别
     */
    fun setZoom(camera: Camera, zoomRatio: Float) {
        try {
            val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: return
            val minZoom = camera.cameraInfo.zoomState.value?.minZoomRatio ?: return

            val clampedZoom = zoomRatio.coerceIn(minZoom, maxZoom)
            camera.cameraControl.setZoomRatio(clampedZoom)

            Log.d(TAG, "Zoom set to $clampedZoom")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set zoom", e)
        }
    }

    /**
     * 增加缩放
     */
    fun zoomIn(camera: Camera, step: Float = 0.5f) {
        try {
            val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
            val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
            setZoom(camera, (currentZoom + step).coerceAtMost(maxZoom))
        } catch (e: Exception) {
            Log.e(TAG, "Zoom in failed", e)
        }
    }

    /**
     * 减小缩放
     */
    fun zoomOut(camera: Camera, step: Float = 0.5f) {
        try {
            val currentZoom = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
            val minZoom = camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
            setZoom(camera, (currentZoom - step).coerceAtLeast(minZoom))
        } catch (e: Exception) {
            Log.e(TAG, "Zoom out failed", e)
        }
    }

    /**
     * 重置对焦
     */
    fun resetFocus(camera: Camera?) {
        if (camera == null) return

        try {
            camera.cameraControl.cancelFocusAndMetering()
            setZoom(camera, 1f)
            lastFocusTime = 0L

            Log.d(TAG, "Focus reset")
        } catch (e: Exception) {
            Log.e(TAG, "Reset focus failed", e)
        }
    }
}
