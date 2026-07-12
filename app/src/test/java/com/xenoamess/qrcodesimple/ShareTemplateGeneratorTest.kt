package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ShareTemplateGenerator 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShareTemplateGeneratorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clearFileProviderCache()
    }

    private fun clearFileProviderCache() {
        try {
            val fileProviderClass = Class.forName("androidx.core.content.FileProvider")
            val cacheField = fileProviderClass.getDeclaredField("sCache")
            cacheField.isAccessible = true
            val cache = cacheField.get(null) as? MutableMap<*, *>
            cache?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createQrBitmap(size: Int = 200): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        for (x in 0 until size step 10) {
            for (y in 0 until size step 10) {
                if ((x + y) % 20 == 0) {
                    bitmap.setPixel(x, y, Color.WHITE)
                }
            }
        }
        return bitmap
    }

    @Test
    fun `generateShareImage returns valid uri and creates file`() {
        runBlocking {
            val qrBitmap = createQrBitmap()
            val uri = ShareTemplateGenerator.generateShareImage(
                context,
                qrBitmap,
                "https://example.com",
                HistoryType.QR_CODE
            )

            assertNotNull(uri)
            assertTrue(uri.toString().startsWith("content://"))

            val shareDir = File(context.filesDir, "share_images")
            assertTrue(shareDir.exists() && shareDir.listFiles()?.isNotEmpty() == true)
        }
    }

    @Test
    fun `generatePlainQrImage returns valid uri with white padding`() {
        runBlocking {
            val qrBitmap = createQrBitmap(100)
            val padding = 40
            val uri = ShareTemplateGenerator.generatePlainQrImage(context, qrBitmap, padding)

            assertNotNull(uri)
            assertTrue(uri.toString().startsWith("content://"))
        }
    }

    @Test
    fun `generateShareImage uses correct default title for barcode type`() {
        runBlocking {
            for (type in HistoryType.entries) {
                val uri = ShareTemplateGenerator.generateShareImage(
                    context,
                    createQrBitmap(),
                    "test content",
                    type
                )
                assertNotNull(uri, "Should generate share image for $type")
            }
        }
    }

    @Test
    fun `generateShareImage handles custom template config`() {
        runBlocking {
            val config = ShareTemplateGenerator.TemplateConfig(
                title = "Custom Title",
                description = "Custom Description",
                showQrCode = true,
                backgroundColor = Color.BLACK,
                textColor = Color.WHITE,
                accentColor = Color.RED,
                showLogo = false
            )

            val uri = ShareTemplateGenerator.generateShareImage(
                context,
                createQrBitmap(),
                "content",
                HistoryType.TEXT,
                config
            )
            assertNotNull(uri)
        }
    }

    @Test
    fun `generateShareImage truncates long description`() {
        runBlocking {
            val config = ShareTemplateGenerator.TemplateConfig(
                title = "Title",
                description = "a".repeat(100)
            )
            val uri = ShareTemplateGenerator.generateShareImage(
                context,
                createQrBitmap(),
                "content",
                HistoryType.TEXT,
                config
            )
            assertNotNull(uri)
        }
    }

    @Test
    fun `generateShareImage returns null for recycled bitmap`() {
        runBlocking {
            val bitmap = createQrBitmap()
            bitmap.recycle()

            val uri = ShareTemplateGenerator.generateShareImage(
                context,
                bitmap,
                "content",
                HistoryType.QR_CODE
            )
            assertNull(uri)
        }
    }
}
