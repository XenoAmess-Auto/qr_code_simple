package com.xenoamess.qrcodesimple

import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ScanRegionMapper 坐标映射测试（视图坐标 -> 帧 bitmap 像素坐标）。
 */
@RunWith(AndroidJUnit4::class)
class ScanRegionMapperTest {

    @Test
    fun `rotation 0 without crop maps proportionally`() {
        // bitmap 1000x1000，view 500x500，无裁剪（scale=0.5）
        val rect = ScanRegionMapper.mapViewRectToBitmap(
            RectF(100f, 100f, 400f, 400f), 500, 500,
            1000, 1000, 0
        )!!
        assertEquals(200, rect.left)
        assertEquals(200, rect.top)
        assertEquals(800, rect.right)
        assertEquals(800, rect.bottom)
    }

    @Test
    fun `rotation 90 full view covers full bitmap`() {
        // bitmap 1280x720 旋转 90° 后显示为 720x1280；view 同比例 360x640
        val rect = ScanRegionMapper.mapViewRectToBitmap(
            RectF(0f, 0f, 360f, 640f), 360, 640,
            1280, 720, 90
        )!!
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1280, rect.right)
        assertEquals(720, rect.bottom)
    }

    @Test
    fun `rotation 90 center square maps with axis swap`() {
        // view 360x640 中心 100px 方块 -> 显示归一化 (0.361,0.422)-(0.639,0.578)
        // 旋转 90°：bitmap = (fy, 1-fx) -> x 0.422..0.578, y 0.361..0.639
        val rect = ScanRegionMapper.mapViewRectToBitmap(
            RectF(130f, 270f, 230f, 370f), 360, 640,
            1280, 720, 90
        )!!
        assertEquals(540, rect.left)
        assertEquals(260, rect.top)
        assertEquals(740, rect.right)
        assertEquals(460, rect.bottom)
    }

    @Test
    fun `fill center crop accounts for horizontal offset`() {
        // bitmap 1280x720 rot 0，view 1080x1920（竖屏）
        // scale = max(1080/1280, 1920/720) = 2.67，scaled 3413x1920，水平裁剪
        // 全视图矩形可见范围 x: 0.3417..0.6583，y: 0..1
        val rect = ScanRegionMapper.mapViewRectToBitmap(
            RectF(0f, 0f, 1080f, 1920f), 1080, 1920,
            1280, 720, 0
        )!!
        assertEquals(437, rect.left)
        assertEquals(0, rect.top)
        assertEquals(842, rect.right)
        assertEquals(720, rect.bottom)
    }

    @Test
    fun `rotation 270 maps with inverse axis swap`() {
        // bitmap 1000x2000 rot 270，view 200x400
        // 显示 2000x1000，scale 0.4，offsetX 300
        // 全视图矩形 -> 显示 x 0.375..0.625, y 0..1
        // 旋转 270°：bitmap = (1-fy, fx) -> x 0..1, y 0.375..0.625
        val rect = ScanRegionMapper.mapViewRectToBitmap(
            RectF(0f, 0f, 200f, 400f), 200, 400,
            1000, 2000, 270
        )!!
        assertEquals(0, rect.left)
        assertEquals(750, rect.top)
        assertEquals(1000, rect.right)
        assertEquals(1250, rect.bottom)
    }

    @Test
    fun `rotation 180 flips both axes`() {
        val rect = ScanRegionMapper.mapViewRectToBitmap(
            RectF(0f, 0f, 250f, 250f), 500, 500,
            1000, 1000, 180
        )!!
        assertEquals(500, rect.left)
        assertEquals(500, rect.top)
        assertEquals(1000, rect.right)
        assertEquals(1000, rect.bottom)
    }

    @Test
    fun `degenerate rect returns null`() {
        assertNull(
            ScanRegionMapper.mapViewRectToBitmap(
                RectF(100f, 100f, 100f, 200f), 500, 500,
                1000, 1000, 0
            )
        )
    }

    @Test
    fun `zero view size returns null`() {
        assertNull(
            ScanRegionMapper.mapViewRectToBitmap(
                RectF(0f, 0f, 100f, 100f), 0, 0,
                1000, 1000, 0
            )
        )
    }
}
