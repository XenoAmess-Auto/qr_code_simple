package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Logo 形状裁剪（maskLogoToShape + StyleConfig 序列化/sanitize）测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class LogoShapeTest {

    private fun solidLogo(size: Int = 100, color: Int = Color.BLUE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private fun generateWithLogo(shape: AdvancedBarcodeGenerator.LogoShape, radius: Float): Bitmap {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            logoBitmap = solidLogo(),
            logoScale = 0.3f,
            logoShape = shape,
            logoCornerRadius = radius
        )
        return AdvancedBarcodeGenerator.generateStyled(
            "logo-shape-test", com.xenoamess.qrcodesimple.data.BarcodeFormat.QR_CODE,
            400, 400, style
        )!!
    }

    private fun isBlueish(pixel: Int): Boolean =
        Color.blue(pixel) > 200 && Color.red(pixel) < 100 && Color.green(pixel) < 100

    private fun cornerPixel(bitmap: Bitmap, style: AdvancedBarcodeGenerator.StyleConfig): Int {
        // logo 区中心到四角：取 logo 区左上角内 2px 处
        val logoSize = (bitmap.width * style.logoScale).toInt().coerceAtLeast(50)
        val left = (bitmap.width - logoSize) / 2
        val top = (bitmap.height - logoSize) / 2
        return bitmap.getPixel(left + 2, top + 2)
    }

    @Test
    fun `circle logo leaves corners as background`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(logoScale = 0.3f)
        val circle = generateWithLogo(AdvancedBarcodeGenerator.LogoShape.CIRCLE, 0.2f)
        val square = generateWithLogo(AdvancedBarcodeGenerator.LogoShape.SQUARE, 0.2f)

        // SQUARE 的角是 logo 蓝色；CIRCLE 的角被裁掉（不可能是蓝色）
        val squareCorner = cornerPixel(square, style)
        val circleCorner = cornerPixel(circle, style)
        assertTrue("square corner should be logo blue", isBlueish(squareCorner))
        assertTrue("circle corner should not be logo blue", !isBlueish(circleCorner))

        // 中心仍是 logo 蓝色
        assertTrue(isBlueish(circle.getPixel(200, 200)))
    }

    @Test
    fun `rounded rect radius controls corner transparency`() {
        val smallRadius = generateWithLogo(AdvancedBarcodeGenerator.LogoShape.ROUNDED_RECT, 0.05f)
        val largeRadius = generateWithLogo(AdvancedBarcodeGenerator.LogoShape.ROUNDED_RECT, 0.8f)

        val style = AdvancedBarcodeGenerator.StyleConfig(logoScale = 0.3f)
        // 大半径：角被裁掉（非红）；小半径：角接近方形（红）
        assertTrue(isBlueish(cornerPixel(smallRadius, style)))
        assertTrue(!isBlueish(cornerPixel(largeRadius, style)))
    }

    @Test
    fun `maskLogoToShape square returns original bitmap`() {
        val logo = solidLogo()
        val masked = AdvancedBarcodeGenerator.maskLogoToShape(logo, AdvancedBarcodeGenerator.LogoShape.SQUARE, 0.5f)
        assertTrue(masked === logo)
    }

    @Test
    fun `maskLogoToShape circle makes corners transparent`() {
        val masked = AdvancedBarcodeGenerator.maskLogoToShape(solidLogo(), AdvancedBarcodeGenerator.LogoShape.CIRCLE, 0f)
        assertEquals(0, Color.alpha(masked.getPixel(2, 2)))
        assertEquals(255, Color.alpha(masked.getPixel(50, 50)))
    }

    @Test
    fun `serialization roundtrip keeps logo shape and radius`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            logoScale = 0.35f,
            logoShape = AdvancedBarcodeGenerator.LogoShape.CIRCLE,
            logoCornerRadius = 0.66f
        )
        val restored = styleConfigFromJson(style.toJson())!!
        assertEquals(AdvancedBarcodeGenerator.LogoShape.CIRCLE, restored.logoShape)
        assertEquals(0.66f, restored.logoCornerRadius, 0.001f)
    }

    @Test
    fun `legacy json without logo shape defaults to square`() {
        val json = """{"foregroundColor":0,"backgroundColor":-1,"logoScale":0.3}"""
        val restored = styleConfigFromJson(json)!!
        assertEquals(AdvancedBarcodeGenerator.LogoShape.SQUARE, restored.logoShape)
        assertEquals(0.2f, restored.logoCornerRadius, 0.001f)
    }

    @Test
    fun `sanitize resets logo shape when logo unsupported`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            logoShape = AdvancedBarcodeGenerator.LogoShape.CIRCLE,
            logoCornerRadius = 0.9f
        )
        val caps = AdvancedBarcodeGenerator.FormatStyleCapabilities(logo = false)
        val sanitized = with(AdvancedBarcodeGenerator) { style.sanitized(caps) }
        assertEquals(AdvancedBarcodeGenerator.LogoShape.SQUARE, sanitized.logoShape)
        assertEquals(0.2f, sanitized.logoCornerRadius, 0.001f)
    }
}
