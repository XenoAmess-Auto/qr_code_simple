package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 圆形角度旋钮：触摸拖动改变角度，0° 在右侧，顺时针递增。
 */
class AngleDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#BDBDBD")
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#1976D2")
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1976D2")
    }

    private val center = PointF()
    private var radius = 0f

    var angle: Float = 0f
        set(value) {
            field = value
            invalidate()
            notifyAngle()
        }

    var onAngleChanged: ((Float) -> Unit)? = null

    private fun notifyAngle() {
        onAngleChanged?.invoke(angle)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        center.set(w / 2f, h / 2f)
        radius = min(w, h) / 2f - 16f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(center.x, center.y, radius, circlePaint)

        val rad = Math.toRadians(angle.toDouble())
        val endX = center.x + radius * cos(rad).toFloat()
        val endY = center.y + radius * sin(rad).toFloat()
        canvas.drawLine(center.x, center.y, endX, endY, indicatorPaint)
        canvas.drawCircle(endX, endY, 12f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - center.x
                val dy = event.y - center.y
                val rad = atan2(dy, dx)
                var degrees = Math.toDegrees(rad.toDouble()).toFloat()
                if (degrees < 0) degrees += 360f
                angle = degrees
                notifyAngle()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
