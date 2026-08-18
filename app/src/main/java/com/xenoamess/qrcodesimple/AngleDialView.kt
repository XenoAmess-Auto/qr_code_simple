package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * 圆形角度旋钮：触摸拖动改变角度，0° 在右侧，顺时针递增。
 */
class AngleDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val primaryColor = MaterialColors.getColor(
        context,
        androidx.appcompat.R.attr.colorPrimary,
        context.getColor(R.color.app_primary)
    )
    private val outlineColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOutline,
        context.getColor(R.color.app_outline)
    )

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = outlineColor
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = primaryColor
        strokeCap = Paint.Cap.ROUND
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }

    private val center = PointF()
    private var radius = 0f

    init {
        isFocusable = true
        isClickable = true
        updateStateDescription()
    }

    var angle: Float = 0f
        set(value) {
            val normalized = ((value % 360f) + 360f) % 360f
            field = normalized
            invalidate()
            updateStateDescription()
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
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ClickableViewAccessibility：本视图只有拖拽语义，无点击语义；performClick 透传不做事
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
            0f,
            360f,
            angle
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        return when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                angle += ACCESSIBILITY_STEP_DEGREES
                true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                angle -= ACCESSIBILITY_STEP_DEGREES
                true
            }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    private fun updateStateDescription() {
        ViewCompat.setStateDescription(this, "${angle.roundToInt()}°")
    }

    private companion object {
        const val ACCESSIBILITY_STEP_DEGREES = 5f
    }
}
