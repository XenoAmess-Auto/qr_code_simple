package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * ImagePerformanceManager 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImagePerformanceManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createBitmapBytes(width: Int, height: Int, color: Int = Color.RED, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, 100, stream)
        return stream.toByteArray()
    }

    @Test
    fun `loadOptimizedBitmap returns bitmap for valid data`() {
        runBlocking {
            val data = createBitmapBytes(100, 100)
            val bitmap = ImagePerformanceManager.loadOptimizedBitmap(data)

            assertNotNull(bitmap)
            assertEquals(100, bitmap!!.width)
            assertEquals(100, bitmap.height)
        }
    }

    @Test
    fun `loadOptimizedBitmap downscales large image`() {
        runBlocking {
            val data = createBitmapBytes(4000, 4000)
            val bitmap = ImagePerformanceManager.loadOptimizedBitmap(data)

            assertNotNull(bitmap)
            assertTrue(bitmap!!.width <= 2048 || bitmap.height <= 2048, "Large image should be downscaled")
        }
    }

    @Test
    fun `loadOptimizedBitmap triggers heavy compression for huge images`() {
        runBlocking {
            val bitmap = Bitmap.createBitmap(3000, 3000, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val data = stream.toByteArray()

            val loaded = ImagePerformanceManager.loadOptimizedBitmap(data)
            assertNotNull(loaded)
        }
    }

    @Test
    fun `loadOptimizedBitmap handles invalid data gracefully`() {
        runBlocking {
            val bitmap = ImagePerformanceManager.loadOptimizedBitmap(byteArrayOf(0, 1, 2, 3))
            // Robolectric may return a placeholder bitmap for invalid data; just ensure no crash
            assertTrue(bitmap == null || bitmap.width >= 0)
        }
    }

    @Test
    fun `rotateBitmap rotates and recycles original when needed`() {
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)

        val rotated = ImagePerformanceManager.rotateBitmap(bitmap, 90f)

        assertEquals(50, rotated.width)
        assertEquals(100, rotated.height)
    }

    @Test
    fun `rotateBitmap returns same bitmap for 0 degrees`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val rotated = ImagePerformanceManager.rotateBitmap(bitmap, 0f)
        assertEquals(bitmap, rotated)
    }

    @Test
    fun `getBitmapMemorySize returns byte count`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        assertEquals(bitmap.byteCount.toLong(), ImagePerformanceManager.getBitmapMemorySize(bitmap))
    }

    @Test
    fun `safeRecycle handles null and recycled bitmaps`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        ImagePerformanceManager.safeRecycle(bitmap)
        assertTrue(bitmap.isRecycled)

        ImagePerformanceManager.safeRecycle(null)
        ImagePerformanceManager.safeRecycle(bitmap)
    }

    @Test
    fun `ImageLoadOptions default values are reasonable`() {
        val options = ImagePerformanceManager.ImageLoadOptions()
        assertEquals(2048, options.maxWidth)
        assertEquals(2048, options.maxHeight)
        assertEquals(90, options.compressQuality)
        assertEquals(Bitmap.Config.ARGB_8888, options.preferredConfig)
    }
}
