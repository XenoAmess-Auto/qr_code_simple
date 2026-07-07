package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Micro QR 生成专项测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MicroQrGenerationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun generateAndScan(format: BarcodeFormat, content: String): Bitmap? {
        val config = BarcodeGenerator.BarcodeConfig(format = format, width = 600, height = 600)
        return BarcodeGenerator.generate(content, config)
    }

    @Test
    fun `Micro QR with short content`() {
        val bitmap = generateAndScan(BarcodeFormat.MICRO_QR, "A")
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Micro QR should be scannable")
    }

    @Test
    fun `Micro QR with alphanumeric content`() {
        val bitmap = generateAndScan(BarcodeFormat.MICRO_QR, "ABC123")
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Micro QR should be scannable")
    }

    @Test
    fun `Micro QR with longer content still generates`() {
        val bitmap = generateAndScan(BarcodeFormat.MICRO_QR, "MICRO_DATA")
        assertNotNull(bitmap)
    }
}
