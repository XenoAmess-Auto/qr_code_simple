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
 * MaxiCode 生成测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MaxiCodeGenerationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun roundtrip(content: String) {
        val config = BarcodeGenerator.BarcodeConfig(
            format = BarcodeFormat.MAXICODE,
            width = 512,
            height = 512
        )
        val bitmap = BarcodeGenerator.generate(content, config)
        assertNotNull(bitmap, "Should generate MaxiCode")
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Should scan MaxiCode")
    }

    @Test
    fun `MaxiCode mode 4 with UPS data`() = roundtrip(
        "[)>\u001E01\u001D961Z00004952\u001DUPSN\u001D410 E MAIN ST\u001DSTE\u001DROCHESTER\u001DNY\u001D"
    )

    @Test
    fun `MaxiCode mode 4 short address`() = roundtrip(
        "[)>\u001E01\u001D961Z12345678\u001DUPSN\u001D123 MAIN ST\u001DANYTOWN\u001DPA\u001D"
    )
}
