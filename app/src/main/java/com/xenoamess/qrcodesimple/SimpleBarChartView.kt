package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 近 N 天扫码次数柱状图。轻量自绘，无第三方图表依赖。
 * 通过 [setData] 传入每天计数；柱子颜色跟随 colorPrimary。
 */
class SimpleBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var counts: IntArray = IntArray(0)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x40808080
        strokeWidth = 2f
    }
    private val barRect = RectF()

    init {
        val primary = com.google.android.material.color.MaterialColors.getColor(
            context, androidx.appcompat.R.attr.colorPrimary, "SimpleBarChartView"
        )
        barPaint.color = primary
    }

    fun setData(dailyCounts: IntArray) {
        counts = dailyCounts.copyOf()
        val values = dailyCounts.mapIndexed { index, count -> "${index + 1}: $count" }
        contentDescription = buildString {
            append(context.getString(R.string.stats_title))
            append(": ")
            append(values.joinToString(", "))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = counts.size
        if (n == 0 || width == 0 || height == 0) return

        val paddingH = paddingLeft + paddingRight
        val paddingV = paddingTop + paddingBottom
        val usableW = width - paddingH
        val usableH = height - paddingV
        if (usableW <= 0 || usableH <= 0) return

        val maxCount = counts.max().coerceAtLeast(1)
        val slotW = usableW.toFloat() / n
        val barW = slotW * 0.62f
        val baselineY = paddingTop + usableH.toFloat()

        canvas.drawLine(paddingLeft.toFloat(), baselineY, (width - paddingRight).toFloat(), baselineY, basePaint)

        for (i in 0 until n) {
            if (counts[i] <= 0) continue
            val barH = usableH * (counts[i].toFloat() / maxCount)
            val left = paddingLeft + i * slotW + (slotW - barW) / 2
            barRect.set(left, baselineY - barH, left + barW, baselineY)
            canvas.drawRoundRect(barRect, barW * 0.2f, barW * 0.2f, barPaint)
        }
    }
}
