package com.xenoamess.qrcodesimple.decoder.hanxin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.random.Random

/**
 * Decoder failure/rejection tests for Han Xin Code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinDecoderFailureTest {

    @Test
    fun decodeRandomNoiseBitmap_returnsNull() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val seed = System.currentTimeMillis()
        println("decodeRandomNoiseBitmap_returnsNull random seed = $seed")
        val random = Random(seed)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                bitmap.setPixel(x, y, if (random.nextBoolean()) Color.BLACK else Color.WHITE)
            }
        }
        assertNull(HanXinDecoder.decode(bitmap))
    }

    @Test
    fun decodeQrCodeBitmap_returnsNull() {
        val qrBitmap = BarcodeGenerator.generate(
            "QR only",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.QR_CODE, width = 400, height = 400)
        )
        assertNotNull(qrBitmap)
        assertNull(HanXinDecoder.decode(qrBitmap!!))
    }

    @Test
    fun decodeBlankBitmap_returnsNull() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        assertNull(HanXinDecoder.decode(bitmap))
    }

    @Test
    fun decodeDamagedFinderPatterns_returnsNull() {
        val result = HanXinEncoder.encode("TEST", width = 400, height = 400)!!
        val size = result.version * 2 + 21
        val bitmap = result.bitmap.copy(result.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        // Destroy the three distinct finder pattern corners (finder + separator + function info).
        paintModulesBlack(bitmap, 0, 0, 9, size)
        paintModulesBlack(bitmap, size - 9, 0, 9, size)
        paintModulesBlack(bitmap, 0, size - 9, 9, size)
        assertNull(HanXinDecoder.decode(bitmap))
    }

    private fun paintModulesBlack(bitmap: Bitmap, startMx: Int, startMy: Int, moduleCount: Int, size: Int) {
        val moduleWidth = bitmap.width / size
        val moduleHeight = bitmap.height / size
        for (mx in startMx until startMx + moduleCount) {
            for (my in startMy until startMy + moduleCount) {
                val startX = mx * moduleWidth
                val startY = my * moduleHeight
                for (x in startX until startX + moduleWidth) {
                    for (y in startY until startY + moduleHeight) {
                        if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                            bitmap.setPixel(x, y, Color.BLACK)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun decodeFunctionInfoCorruptionWithinBudget_recovers() {
        val content = "AB"
        val result = HanXinEncoder.encode(content, width = 400, height = 400)!!
        val size = result.version * 2 + 21
        val bitmap = result.bitmap.copy(result.bitmap.config ?: Bitmap.Config.ARGB_8888, true)

        // Flip one module in the top function-info row (row 8, column 1). The
        // function info is protected by its own RS(4) code and should recover.
        flipModule(bitmap, 1, 8, size)

        val decoded = HanXinDecoder.decode(bitmap)
        assertNotNull(decoded, "Should recover from single function-info module corruption")
        assertEquals(content, decoded.text)
    }

    @Test
    fun decodeFunctionInfoCorruptionBeyondBudget_returnsNull() {
        val result = HanXinEncoder.encode("AB", width = 400, height = 400)!!
        val size = result.version * 2 + 21
        val bitmap = result.bitmap.copy(result.bitmap.config ?: Bitmap.Config.ARGB_8888, true)

        // Flip many function-info modules to exceed the RS correction capability.
        for (x in 0..8) {
            flipModule(bitmap, x, 8, size)
            flipModule(bitmap, 8, x, size)
        }

        assertNull(HanXinDecoder.decode(bitmap))
    }

    @Test
    fun decodeAllGeneratedMasks() {
        val content = "Mask test"
        for (mask in 0..3) {
            val result = HanXinEncoder.encode(
                content,
                width = 400,
                height = 400,
                requestedMask = mask
            )!!
            assertEquals(mask, result.mask)
            val decoded = HanXinDecoder.decode(result.bitmap)
            assertNotNull(decoded, "Should decode mask $mask")
            assertEquals(content, decoded.text)
        }
    }

    private fun flipModule(bitmap: Bitmap, mx: Int, my: Int, size: Int) {
        val moduleWidth = bitmap.width / size
        val moduleHeight = bitmap.height / size
        val startX = mx * moduleWidth
        val startY = my * moduleHeight
        val centerColor = bitmap.getPixel(startX + moduleWidth / 2, startY + moduleHeight / 2)
        val invertedColor = if (centerColor == Color.BLACK) Color.WHITE else Color.BLACK
        for (x in startX until startX + moduleWidth) {
            for (y in startY until startY + moduleHeight) {
                bitmap.setPixel(x, y, invertedColor)
            }
        }
    }
}
