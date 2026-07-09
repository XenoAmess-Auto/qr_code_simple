package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.zxing.common.BitMatrix

/**
 * 结构化条码布局元数据，用于样式渲染器精确控制每个模块/条杠的绘制。
 */
sealed class BarcodeLayout {

    /**
     * 2D 矩阵码布局：模块网格 + 功能图案区域。
     */
    data class GridLayout(
        val bitMatrix: BitMatrix,
        val moduleSize: Int,
        val positionPatterns: List<Rect> = emptyList(),
        val alignmentPatterns: List<Rect> = emptyList(),
        val timingPatterns: List<Rect> = emptyList(),
        val padding: Int = 0
    ) : BarcodeLayout()

    /**
     * 1D 线性条码布局：条杠运行段 + 模块宽度。
     */
    data class LinearLayout(
        val barRuns: List<BarRun>,
        val moduleWidth: Int,
        val width: Int = (barRuns.maxOfOrNull { it.right } ?: 0) + moduleWidth,
        val height: Int = barRuns.maxOfOrNull { it.bottom } ?: 0
    ) : BarcodeLayout() {

        data class BarRun(
            val left: Int,
            val top: Int,
            val right: Int,
            val bottom: Int,
            val kind: Kind = Kind.DATA
        ) {
            enum class Kind { DATA, GUARD }
        }
    }

    /**
     * MaxiCode 专用布局：六边形 + 目标圆。
     */
    data class MaxiCodeLayout(
        val hexagons: List<Hexagon>,
        val targets: List<Target>
    ) : BarcodeLayout() {

        data class Hexagon(
            val x: Float,
            val y: Float,
            val size: Float
        )

        data class Target(
            val cx: Float,
            val cy: Float,
            val radius: Float
        )
    }

    /**
     * 回退布局：没有可靠的结构化信息，渲染时直接对原始位图做像素级样式化。
     */
    data class Fallback(
        val bitmap: Bitmap
    ) : BarcodeLayout()
}

/**
 * 条码生成结果，同时包含渲染后的位图和结构化布局。
 */
data class BarcodeResult(
    val bitmap: Bitmap,
    val layout: BarcodeLayout
)
