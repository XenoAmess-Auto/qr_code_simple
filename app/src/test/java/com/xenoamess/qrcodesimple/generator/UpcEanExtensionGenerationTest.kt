package com.xenoamess.qrcodesimple.generator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.google.zxing.ResultMetadataType
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * UPC/EAN Extension 生成测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpcEanExtensionGenerationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun scanExtension(content: String): String? {
        val config = BarcodeGenerator.BarcodeConfig(
            format = BarcodeFormat.UPC_EAN_EXTENSION,
            width = 800,
            height = 400
        )
        val bitmap = BarcodeGenerator.generate(content, config)
        assertNotNull(bitmap, "Should generate UPC/EAN extension")
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Should scan extension")
        return results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
            ?: results.first().resultMetadata?.get(ResultMetadataType.UPC_EAN_EXTENSION) as? String
            ?: results.first().text
    }

    @Test
    fun `2-digit extension roundtrip`() {
        assertEquals("12", scanExtension("12"))
    }

    @Test
    fun `5-digit extension roundtrip`() {
        assertEquals("12345", scanExtension("12345"))
    }
}
