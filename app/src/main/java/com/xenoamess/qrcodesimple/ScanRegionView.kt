package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import kotlin.math.min

/**
 * 扫描区域选择视图
 * 支持框选特定区域进行识别
 */
class ScanRegionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    interface OnRegionSelectedListener {
        fun onRegionSelected(rect: RectF)
        fun onRegionCleared()
    }

    private val holder: SurfaceHolder = getHolder().apply {
        addCallback(this@ScanRegionView)
        setFormat(PixelFormat.TRANSPARENT)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2000BCD4")
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00BCD4")
        style = Paint.Style.FILL
    }

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDragging = false
    private var isAdjusting = false
    private var activeCorner = Corner.NONE

    private var selectionRect = RectF()
    private var listener: OnRegionSelectedListener? = null

    private enum class Corner {
        NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private val cornerRadius = 20f
    private val touchThreshold = 40f

    init {
        setZOrderOnTop(true)
    }

    fun setOnRegionSelectedListener(listener: OnRegionSelectedListener) {
        this.listener = listener
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawSelection()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        drawSelection()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchDown(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchMove(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleTouchUp()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(x: Float, y: Float) {
        if (selectionRect.isEmpty) {
            // 开始新的选择
            startX = x
            startY = y
            endX = x
            endY = y
            isDragging = true
        } else {
            // 检查是否点击了角落（调整大小）
            activeCorner = getTouchedCorner(x, y)
            if (activeCorner != Corner.NONE) {
                isAdjusting = true
            } else if (selectionRect.contains(x, y)) {
                // 移动选择区域
                startX = x - selectionRect.left
                startY = y - selectionRect.top
                isDragging = true
            } else {
                // 开始新的选择
                clearSelection()
                startX = x
                startY = y
                endX = x
                endY = y
                isDragging = true
            }
        }
    }

    private fun handleTouchMove(x: Float, y: Float) {
        when {
            isDragging -> {
                endX = x
                endY = y
                updateSelectionRect()
                drawSelection()
            }
            isAdjusting -> {
                adjustSelection(x, y)
                drawSelection()
            }
        }
    }

    private fun handleTouchUp() {
        if (isDragging || isAdjusting) {
            isDragging = false
            isAdjusting = false
            activeCorner = Corner.NONE

            if (selectionRect.width() > 100 && selectionRect.height() > 100) {
                listener?.onRegionSelected(selectionRect)
            } else {
                clearSelection()
            }
        }
    }

    private fun updateSelectionRect() {
        selectionRect.set(
            min(startX, endX),
            min(startY, endY),
            max(startX, endX),
            max(startY, endY)
        )
    }

    private fun adjustSelection(x: Float, y: Float) {
        when (activeCorner) {
            Corner.TOP_LEFT -> {
                selectionRect.left = x
                selectionRect.top = y
            }
            Corner.TOP_RIGHT -> {
                selectionRect.right = x
                selectionRect.top = y
            }
            Corner.BOTTOM_LEFT -> {
                selectionRect.left = x
                selectionRect.bottom = y
            }
            Corner.BOTTOM_RIGHT -> {
                selectionRect.right = x
                selectionRect.bottom = y
            }
            else -> {}
        }
    }

    private fun getTouchedCorner(x: Float, y: Float): Corner {
        val corners = mapOf(
            Corner.TOP_LEFT to Pair(selectionRect.left, selectionRect.top),
            Corner.TOP_RIGHT to Pair(selectionRect.right, selectionRect.top),
            Corner.BOTTOM_LEFT to Pair(selectionRect.left, selectionRect.bottom),
            Corner.BOTTOM_RIGHT to Pair(selectionRect.right, selectionRect.bottom)
        )

        for ((corner, point) in corners) {
            val distance = kotlin.math.hypot(x - point.first, y - point.second)
            if (distance < touchThreshold) {
                return corner
            }
        }
        return Corner.NONE
    }

    private fun drawSelection() {
        val canvas = holder.lockCanvas() ?: return

        try {
            // 清除画布
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            if (!selectionRect.isEmpty) {
                // 绘制填充
                canvas.drawRect(selectionRect, fillPaint)

                // 绘制边框
                canvas.drawRect(selectionRect, paint)

                // 绘制角落标记
                drawCorners(canvas)

                // 绘制尺寸信息
                drawSizeInfo(canvas)
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawCorners(canvas: Canvas) {
        val corners = listOf(
            Pair(selectionRect.left, selectionRect.top),
            Pair(selectionRect.right, selectionRect.top),
            Pair(selectionRect.left, selectionRect.bottom),
            Pair(selectionRect.right, selectionRect.bottom)
        )

        for ((x, y) in corners) {
            canvas.drawCircle(x, y, cornerRadius, cornerPaint)
        }
    }

    private fun drawSizeInfo(canvas: Canvas) {
        val width = selectionRect.width().toInt()
        val height = selectionRect.height().toInt()
        val text = "${width}x${height}"

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80000000")
        }

        val textWidth = textPaint.measureText(text)
        val textX = selectionRect.centerX() - textWidth / 2
        val textY = selectionRect.top - 20

        canvas.drawRect(
            textX - 10,
            textY - 40,
            textX + textWidth + 10,
            textY + 10,
            bgPaint
        )
        canvas.drawText(text, textX, textY, textPaint)
    }

    fun getSelectionRect(): RectF? {
        return if (selectionRect.isEmpty) null else RectF(selectionRect)
    }

    fun clearSelection() {
        selectionRect.setEmpty()
        drawSelection()
        listener?.onRegionCleared()
    }

    fun hasSelection(): Boolean = !selectionRect.isEmpty

    /**
     * 裁剪图片到选择区域
     */
    fun cropToSelection(bitmap: android.graphics.Bitmap): android.graphics.Bitmap? {
        val rect = getSelectionRect() ?: return null

        val x = rect.left.toInt().coerceIn(0, bitmap.width)
        val y = rect.top.toInt().coerceIn(0, bitmap.height)
        val width = rect.width().toInt().coerceAtMost(bitmap.width - x)
        val height = rect.height().toInt().coerceAtMost(bitmap.height - y)

        if (width <= 0 || height <= 0) return null

        return android.graphics.Bitmap.createBitmap(bitmap, x, y, width, height)
    }
}
