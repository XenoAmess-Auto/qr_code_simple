package com.xenoamess.qrcodesimple.generator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * GS1 DataBar（RSS-14 / RSS Expanded）生成测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Gs1DatabarGenerationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun roundtrip(format: BarcodeFormat, content: String, expected: String = content) {
        val config = BarcodeGenerator.BarcodeConfig(format = format, width = 800, height = 400)
        val bitmap = BarcodeGenerator.generate(content, config)
        assertNotNull(bitmap, "Should generate $format")
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Should scan $format")
        assertEquals(expected, results.first().text, "Scanned content should match")
    }

    @Test
    fun `RSS-14 roundtrip`() =
        roundtrip(BarcodeFormat.RSS_14, "1234567890123", "12345678901231")

    @Test
    fun `RSS Expanded with parentheses syntax`() =
        roundtrip(BarcodeFormat.RSS_EXPANDED, "(01)12345678901231")

    @Test
    fun `RSS Expanded with bracket syntax converts to parentheses`() =
        roundtrip(BarcodeFormat.RSS_EXPANDED, "[01]12345678901231", "(01)12345678901231")

    @Test
    fun `RSS Expanded with multiple AIs`() =
        roundtrip(BarcodeFormat.RSS_EXPANDED, "(01)12345678901231(10)ABC123")
}
