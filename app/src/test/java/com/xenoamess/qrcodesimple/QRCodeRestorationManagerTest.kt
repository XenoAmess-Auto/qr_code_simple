package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * QRCodeRestorationManager 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QRCodeRestorationManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createTestBitmap(width: Int = 100, height: Int = 100, color: Int = Color.GRAY): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    @Test
    fun `restoreQRCode with default options returns original and processed variants`() {
        runBlocking {
            val bitmap = createTestBitmap()
            val results = QRCodeRestorationManager.restoreQRCode(bitmap)

            assertTrue(results.isNotEmpty(), "Should return at least original bitmap")
            assertEquals(bitmap, results.first(), "First result should be original bitmap")
            assertTrue(results.size > 1, "Should return processed variants")
        }
    }

    @Test
    fun `restoreQRCode with all options disabled returns only original`() {
        runBlocking {
            val bitmap = createTestBitmap()
            val options = QRCodeRestorationManager.RestorationOptions(
                tryGrayscale = false,
                tryContrastEnhancement = false,
                trySharpening = false,
                tryBinarization = false,
                tryScaling = false
            )
            val results = QRCodeRestorationManager.restoreQRCode(bitmap, options)

            assertEquals(1, results.size, "Should return only original when all options disabled")
            assertEquals(bitmap, results.first())
        }
    }

    @Test
    fun `quickPreprocess returns original grayscale and contrast`() {
        runBlocking {
            val bitmap = createTestBitmap()
            val results = QRCodeRestorationManager.quickPreprocess(bitmap)

            assertEquals(3, results.size, "Quick preprocess should return 3 variants")
            assertEquals(bitmap, results[0])
        }
    }

    @Test
    fun `grayscale transformation produces a distinct variant`() {
        runBlocking {
            val bitmap = createTestBitmap(10, 10, Color.RED)
            val results = QRCodeRestorationManager.restoreQRCode(
                bitmap,
                QRCodeRestorationManager.RestorationOptions(
                    tryGrayscale = true,
                    tryContrastEnhancement = false,
                    trySharpening = false,
                    tryBinarization = false,
                    tryScaling = false
                )
            )

            val gray = results.find { it != bitmap }
            assertNotNull(gray, "Should produce grayscale variant")
            assertEquals(bitmap.width, gray!!.width)
            assertEquals(bitmap.height, gray.height)
        }
    }

    @Test
    fun `binarization produces only black and white`() {
        runBlocking {
            val bitmap = createTestBitmap(20, 20, Color.GRAY)
            val results = QRCodeRestorationManager.restoreQRCode(
                bitmap,
                QRCodeRestorationManager.RestorationOptions(
                    tryGrayscale = false,
                    tryContrastEnhancement = false,
                    trySharpening = false,
                    tryBinarization = true,
                    tryScaling = false
                )
            )

            val binary = results.find { it != bitmap }
            assertNotNull(binary, "Should produce binary variant")

            for (x in 0 until binary!!.width step 5) {
                for (y in 0 until binary.height step 5) {
                    val pixel = binary.getPixel(x, y)
                    assertTrue(
                        pixel == Color.BLACK || pixel == Color.WHITE,
                        "Binarized image should only contain black or white pixels"
                    )
                }
            }
        }
    }

    @Test
    fun `scaling produces larger bitmaps`() {
        runBlocking {
            val bitmap = createTestBitmap(50, 50)
            val results = QRCodeRestorationManager.restoreQRCode(
                bitmap,
                QRCodeRestorationManager.RestorationOptions(
                    tryGrayscale = false,
                    tryContrastEnhancement = false,
                    trySharpening = false,
                    tryBinarization = false,
                    tryScaling = true
                )
            )

            val scaled = results.find { it != bitmap && (it.width > bitmap.width || it.height > bitmap.height) }
            assertNotNull(scaled, "Should produce scaled up variant")
            assertTrue(scaled!!.width > bitmap.width || scaled.height > bitmap.height)
        }
    }

    @Test
    fun `restoration handles invalid bitmap gracefully`() {
        runBlocking {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.recycle()

            val results = QRCodeRestorationManager.restoreQRCode(bitmap)
            assertTrue(results.isNotEmpty(), "Should return at least original even on invalid bitmap")
        }
    }

    @Test
    fun `distinct removes duplicate results`() {
        runBlocking {
            val bitmap = createTestBitmap(2, 2, Color.BLACK)
            val results = QRCodeRestorationManager.restoreQRCode(bitmap)

            val distinct = results.distinct()
            assertEquals(distinct.size, results.size, "Results should be distinct")
        }
    }
}
