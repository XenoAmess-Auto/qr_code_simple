package com.xenoamess.qrcodesimple

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 扫描线动画视图
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var scanLineY = 0f
    private var animator: ValueAnimator? = null
    private var viewWidth = 0
    private var viewHeight = 0
    
    // 扫描框大小
    var scannerRect: Rect? = null
        set(value) {
            field = value
            invalidate()
        }
    
    // 扫描线颜色
    var scanLineColor: Int = Color.parseColor("#00BCD4")
        set(value) {
            field = value
            scanLinePaint.color = value
            invalidate()
        }
    
    // 角标颜色
    var cornerColor: Int = Color.parseColor("#00BCD4")
        set(value) {
            field = value
            cornerPaint.color = value
            invalidate()
        }
    
    // 角标长度
    var cornerLength = 60
    
    // 角标宽度
    var cornerWidth = 8
    
    init {
        // 扫描线画笔
        scanLinePaint.apply {
            color = scanLineColor
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        
        // 角标画笔
        cornerPaint.apply {
            color = cornerColor
            strokeWidth = cornerWidth.toFloat()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        // 遮罩画笔
        paint.apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        
        // 默认扫描框为屏幕中央 70% 区域
        if (scannerRect == null) {
            val size = (Math.min(w, h) * 0.7).toInt()
            val left = (w - size) / 2
            val top = (h - size) / 2
            scannerRect = Rect(left, top, left + size, top + size)
        }
        
        startAnimation()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val rect = scannerRect ?: return
        
        // 绘制遮罩（扫描框外部）
        // 上部分
        canvas.drawRect(0f, 0f, viewWidth.toFloat(), rect.top.toFloat(), paint)
        // 下部分
        canvas.drawRect(0f, rect.bottom.toFloat(), viewWidth.toFloat(), viewHeight.toFloat(), paint)
        // 左部分
        canvas.drawRect(0f, rect.top.toFloat(), rect.left.toFloat(), rect.bottom.toFloat(), paint)
        // 右部分
        canvas.drawRect(rect.right.toFloat(), rect.top.toFloat(), viewWidth.toFloat(), rect.bottom.toFloat(), paint)
        
        // 绘制四角
        drawCorners(canvas, rect)
        
        // 绘制扫描线
        drawScanLine(canvas, rect)
    }
    
    private fun drawCorners(canvas: Canvas, rect: Rect) {
        val length = cornerLength.toFloat()
        val halfWidth = cornerWidth / 2f
        
        // 左上角
        canvas.drawLine(rect.left.toFloat(), rect.top + halfWidth, rect.left + length, rect.top + halfWidth, cornerPaint)
        canvas.drawLine(rect.left + halfWidth, rect.top.toFloat(), rect.left + halfWidth, rect.top + length, cornerPaint)
        
        // 右上角
        canvas.drawLine(rect.right - length, rect.top + halfWidth, rect.right.toFloat(), rect.top + halfWidth, cornerPaint)
        canvas.drawLine(rect.right - halfWidth, rect.top.toFloat(), rect.right - halfWidth, rect.top + length, cornerPaint)
        
        // 左下角
        canvas.drawLine(rect.left.toFloat(), rect.bottom - halfWidth, rect.left + length, rect.bottom - halfWidth, cornerPaint)
        canvas.drawLine(rect.left + halfWidth, rect.bottom - length, rect.left + halfWidth, rect.bottom.toFloat(), cornerPaint)
        
        // 右下角
        canvas.drawLine(rect.right - length, rect.bottom - halfWidth, rect.right.toFloat(), rect.bottom - halfWidth, cornerPaint)
        canvas.drawLine(rect.right - halfWidth, rect.bottom - length, rect.right - halfWidth, rect.bottom.toFloat(), cornerPaint)
    }
    
    private fun drawScanLine(canvas: Canvas, rect: Rect) {
        // 绘制扫描线
        val lineY = rect.top + scanLineY
        if (lineY >= rect.top && lineY <= rect.bottom) {
            // 扫描线光晕效果
            scanLinePaint.alpha = 255
            canvas.drawLine(rect.left.toFloat(), lineY, rect.right.toFloat(), lineY, scanLinePaint)
            
            // 渐变效果
            scanLinePaint.alpha = 128
            canvas.drawLine(rect.left.toFloat(), lineY - 4, rect.right.toFloat(), lineY - 4, scanLinePaint)
            canvas.drawLine(rect.left.toFloat(), lineY + 4, rect.right.toFloat(), lineY + 4, scanLinePaint)
        }
    }
    
    private fun startAnimation() {
        animator?.cancel()
        
        val rect = scannerRect ?: return
        val height = rect.height().toFloat()
        
        animator = ValueAnimator.ofFloat(0f, height).apply {
            duration = 2000 // 2秒一个循环
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                scanLineY = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }
}
