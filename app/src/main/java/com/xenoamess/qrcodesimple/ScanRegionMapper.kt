package com.xenoamess.qrcodesimple

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max

/**
 * 扫描区域坐标映射：把 [ScanRegionView] 上的视图坐标矩形映射到
 * 相机帧 bitmap 的像素坐标。
 *
 * 需要处理两层变换：
 * 1. PreviewView（FILL_CENTER）对旋转后的图像做居中裁剪缩放；
 * 2. 相机帧 bitmap 相对显示方向存在 rotationDegrees（0/90/180/270）旋转。
 */
object ScanRegionMapper {

    /**
     * @param viewRect 视图坐标系中的选择区域（像素）
     * @param viewWidth / viewHeight 选择视图尺寸
     * @param bitmapWidth / bitmapHeight 相机帧 bitmap 尺寸（未旋转）
     * @param rotationDegrees 帧相对显示方向的旋转角（[ImageProxy.getImageInfo]）
     * @return bitmap 像素坐标矩形；映射结果无效（空区域）时返回 null
     */
    fun mapViewRectToBitmap(
        viewRect: RectF,
        viewWidth: Int,
        viewHeight: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        rotationDegrees: Int
    ): Rect? {
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return null

        // 旋转后（即用户看到的）图像尺寸
        val rotated = rotationDegrees == 90 || rotationDegrees == 270
        val displayW = (if (rotated) bitmapHeight else bitmapWidth).toFloat()
        val displayH = (if (rotated) bitmapWidth else bitmapHeight).toFloat()

        // FILL_CENTER：等比放大至铺满视图，居中裁剪
        val scale = max(viewWidth / displayW, viewHeight / displayH)
        val scaledW = displayW * scale
        val scaledH = displayH * scale
        val offsetX = (scaledW - viewWidth) / 2f
        val offsetY = (scaledH - viewHeight) / 2f

        // 视图坐标 -> 显示图像归一化坐标
        fun toDisplayFraction(vx: Float, vy: Float): Pair<Float, Float> {
            val fx = ((vx + offsetX) / scale / displayW).coerceIn(0f, 1f)
            val fy = ((vy + offsetY) / scale / displayH).coerceIn(0f, 1f)
            return fx to fy
        }

        // 显示图像归一化坐标 -> bitmap 归一化坐标
        fun toBitmapFraction(fx: Float, fy: Float): Pair<Float, Float> = when (rotationDegrees) {
            90 -> fy to (1f - fx)
            180 -> (1f - fx) to (1f - fy)
            270 -> (1f - fy) to fx
            else -> fx to fy
        }

        val (leftTopFx, leftTopFy) = toDisplayFraction(viewRect.left, viewRect.top)
        val (rightBottomFx, rightBottomFy) = toDisplayFraction(viewRect.right, viewRect.bottom)
        val (x1, y1) = toBitmapFraction(leftTopFx, leftTopFy)
        val (x2, y2) = toBitmapFraction(rightBottomFx, rightBottomFy)

        val left = (minOf(x1, x2) * bitmapWidth).toInt().coerceIn(0, bitmapWidth)
        val top = (minOf(y1, y2) * bitmapHeight).toInt().coerceIn(0, bitmapHeight)
        val right = (maxOf(x1, x2) * bitmapWidth).toInt().coerceIn(0, bitmapWidth)
        val bottom = (maxOf(y1, y2) * bitmapHeight).toInt().coerceIn(0, bitmapHeight)

        if (right - left <= 0 || bottom - top <= 0) return null
        return Rect(left, top, right, bottom)
    }
}
