package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * ScanRegionView 触摸状态机场景测试：移动选区、角点调整、外部重选、裁剪数学。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class ScanRegionTouchTest {

    private fun createView(): ScanRegionView {
        val view = ScanRegionView(ApplicationProvider.getApplicationContext())
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 400)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return view
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0)
    }

    private fun drag(view: ScanRegionView, x1: Float, y1: Float, x2: Float, y2: Float) {
        view.onTouchEvent(event(MotionEvent.ACTION_DOWN, x1, y1))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, (x1 + x2) / 2, (y1 + y2) / 2))
        view.onTouchEvent(event(MotionEvent.ACTION_MOVE, x2, y2))
        view.onTouchEvent(event(MotionEvent.ACTION_UP, x2, y2))
    }

    private fun selectRegion(view: ScanRegionView): RectF {
        drag(view, 100f, 100f, 300f, 300f)
        val rect = view.getSelectionRect()
        assertNotNull(rect)
        return rect!!
    }

    @Test
    fun dragInsideSelectionMovesIt() {
        val view = createView()
        val before = selectRegion(view)

        // 在选区内部按下并拖动 → 平移
        drag(view, 200f, 200f, 250f, 240f)
        val after = view.getSelectionRect()!!

        assertEquals(before.width(), after.width(), 0.5f)
        assertEquals(before.height(), after.height(), 0.5f)
        assertTrue(after.left > before.left)
        assertTrue(after.top > before.top)
    }

    @Test
    fun dragCornerResizesSelection() {
        val view = createView()
        selectRegion(view)

        // 拖动右下角向外扩
        drag(view, 300f, 300f, 360f, 350f)
        val resized = view.getSelectionRect()!!
        assertTrue(resized.right > 300f)
        assertTrue(resized.bottom > 300f)
    }

    @Test
    fun dragOutsideStartsNewSelection() {
        val view = createView()
        selectRegion(view)

        // 在选区外拖出新区域
        drag(view, 320f, 20f, 390f, 90f)
        val current = view.getSelectionRect()
        // 新区域太小（<100）会被清除 → null；或者替换为合法新区域
        if (current != null) {
            assertTrue(current.left >= 300f)
        }
    }

    @Test
    fun cropToSelectionClampsToBitmapBounds() {
        val view = createView()
        selectRegion(view) // 100..300

        val bitmap = Bitmap.createBitmap(250, 250, Bitmap.Config.ARGB_8888)
        val cropped = view.cropToSelection(bitmap)
        assertNotNull(cropped)
        // 选区右下超出位图边界 → 钳制
        assertTrue(cropped!!.width <= 150)
        assertTrue(cropped.height <= 150)
    }

    @Test
    fun cropToSelectionWithoutSelectionReturnsNull() {
        val view = createView()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        assertNull(view.cropToSelection(bitmap))
    }

    @Test
    fun getSelectionRectReturnsDefensiveCopy() {
        val view = createView()
        val rect = selectRegion(view)
        rect.left = -999f
        val fresh = view.getSelectionRect()!!
        assertTrue(fresh.left >= 0f)
    }
}
