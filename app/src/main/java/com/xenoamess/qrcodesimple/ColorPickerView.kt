package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 色谱式颜色选取器：上方 SV（饱和度/明度）方格 + 中间 Hue 色相条 + 下方 Alpha 透明度条。
 * 通过 [currentColor] 读取当前选中颜色。
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x66000000
    }

    private var svBitmap: Bitmap? = null
    private var hueBitmap: Bitmap? = null
    private var alphaBitmap: Bitmap? = null

    private val svRect = RectF()
    private val hueRect = RectF()
    private val alphaRect = RectF()
    private val gap = 16f
    private val barHeight = 48f
    private val radius = 12f

    /** HSV 值：[0]=hue(0..360), [1]=sat(0..1), [2]=val(0..1) */
    private val hsv = floatArrayOf(0f, 1f, 1f)
    private var currentAlpha: Int = 255

    var onColorChanged: ((Int) -> Unit)? = null

    var currentColor: Int = Color.BLACK
        private set

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        currentAlpha = Color.alpha(color)
        rebuildSvBitmap()
        rebuildAlphaBitmap()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = 0f
        val top = 0f
        val right = w.toFloat()
        val svBottom = (h - barHeight * 2 - gap * 2).coerceAtLeast(0f)
        svRect.set(left, top, right, svBottom)
        hueRect.set(left, svBottom + gap, right, svBottom + gap + barHeight)
        alphaRect.set(left, hueRect.bottom + gap, right, hueRect.bottom + gap + barHeight)
        rebuildSvBitmap()
        rebuildHueBitmap()
        rebuildAlphaBitmap()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        svBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, svRect, svPaint)
        }
        // SV 选择圈
        val sx = svRect.left + hsv[1] * svRect.width()
        val sy = svRect.top + (1 - hsv[2]) * svRect.height()
        canvas.drawCircle(sx, sy, 12f, thumbShadowPaint)
        canvas.drawCircle(sx, sy, 10f, thumbPaint)

        hueBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, hueRect, huePaint)
        }
        // Hue 选择圈
        val hx = hueRect.left + (hsv[0] / 360f) * hueRect.width()
        canvas.drawCircle(hx, hueRect.centerY(), 14f, thumbShadowPaint)
        canvas.drawCircle(hx, hueRect.centerY(), 12f, thumbPaint)

        alphaBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, alphaRect, alphaPaint)
        }
        // Alpha 选择圈
        val ax = alphaRect.left + (currentAlpha / 255f) * alphaRect.width()
        canvas.drawCircle(ax, alphaRect.centerY(), 14f, thumbShadowPaint)
        canvas.drawCircle(ax, alphaRect.centerY(), 12f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                if (svRect.contains(x, y)) {
                    val s = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                    val v = 1f - ((y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
                    hsv[1] = s
                    hsv[2] = v
                    updateColor()
                    invalidate()
                    return true
                } else if (hueRect.contains(x, y)) {
                    val h = ((x - hueRect.left) / hueRect.width()).coerceIn(0f, 1f) * 360f
                    hsv[0] = h
                    rebuildSvBitmap()
                    rebuildAlphaBitmap()
                    updateColor()
                    invalidate()
                    return true
                } else if (alphaRect.contains(x, y)) {
                    currentAlpha = ((x - alphaRect.left) / alphaRect.width() * 255f).coerceIn(0f, 255f).toInt()
                    updateColor()
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateColor() {
        currentColor = Color.HSVToColor(currentAlpha, hsv)
        onColorChanged?.invoke(currentColor)
    }

    private fun rebuildSvBitmap() {
        if (svRect.width() <= 0 || svRect.height() <= 0) return
        val w = svRect.width().toInt()
        val h = svRect.height().toInt()
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val hueRgb = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        val white = Color.WHITE
        val black = Color.BLACK
        val canvas = Canvas(bmp)
        val satShader = LinearGradient(0f, 0f, w.toFloat(), 0f, white, hueRgb, Shader.TileMode.CLAMP)
        val satPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = satShader }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), satPaint)
        val valShader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.TRANSPARENT, black, Shader.TileMode.CLAMP)
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = valShader }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), valPaint)
        svBitmap?.recycle()
        svBitmap = bmp
    }

    private fun rebuildHueBitmap() {
        if (hueRect.width() <= 0) return
        val w = hueRect.width().toInt()
        val h = hueRect.height().toInt()
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colors = IntArray(12) { i ->
            Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))
        }
        val positions = FloatArray(12) { i -> i / 11f }
        val shader = LinearGradient(0f, 0f, w.toFloat(), 0f, colors, positions, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.shader = shader }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        hueBitmap?.recycle()
        hueBitmap = bmp
    }

    private fun rebuildAlphaBitmap() {
        if (alphaRect.width() <= 0) return
        val w = alphaRect.width().toInt()
        val h = alphaRect.height().toInt()
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val pureColor = Color.HSVToColor(hsv)
        val transparent = pureColor and 0x00FFFFFF
        val shader = LinearGradient(0f, 0f, w.toFloat(), 0f, transparent, pureColor, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.shader = shader }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        alphaBitmap?.recycle()
        alphaBitmap = bmp
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (desiredWidth * 1.15f).toInt().coerceAtLeast(360)
        setMeasuredDimension(
            resolveSizeAndState(desiredWidth, widthMeasureSpec, 0),
            resolveSizeAndState(desiredHeight, heightMeasureSpec, 0)
        )
    }
}
