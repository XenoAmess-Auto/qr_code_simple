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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 自定义线性码（Pharmacode / Plessey / MSI Plessey / Telepen）生成测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomLinearGenerationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun roundtrip(format: BarcodeFormat, content: String) {
        val config = BarcodeGenerator.BarcodeConfig(format = format, width = 800, height = 400)
        val bitmap = BarcodeGenerator.generate(content, config)
        assertNotNull(bitmap, "Should generate $format for $content")
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Should scan $format")
    }

    @Test
    fun `Pharmacode with small number`() = roundtrip(BarcodeFormat.PHARMACODE, "1234")

    @Test
    fun `Pharmacode with large number`() = roundtrip(BarcodeFormat.PHARMACODE, "131070")

    @Test
    fun `Plessey with alphanumeric`() = roundtrip(BarcodeFormat.PLESSEY, "AB12")

    @Test
    fun `MSI Plessey with digits`() = roundtrip(BarcodeFormat.MSI_PLESSEY, "1234")

    @Test
    fun `Telepen with uppercase letters`() = roundtrip(BarcodeFormat.TELEPEN, "TEST")

    @Test
    fun `Telepen with ascii digits`() = roundtrip(BarcodeFormat.TELEPEN, "123456")
}
