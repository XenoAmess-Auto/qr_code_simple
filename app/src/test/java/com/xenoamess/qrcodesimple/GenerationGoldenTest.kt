package com.xenoamess.qrcodesimple

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * 生成结果金样测试：固定输入的 SVG 输出哈希必须保持稳定。
 *
 * 目的：ZXing / OkapiBarcode / 样式渲染等依赖升级或生成逻辑改动时，
 * 若生成图案发生静默变化（即便仍可回扫），本测试会失败以强制人工确认。
 *
 * 若变更属于预期（如纠错等级策略调整），重新计算并更新 GOLDEN 值，
 * 同时在提交信息中说明原因。
 */
@RunWith(AndroidJUnit4::class)
class GenerationGoldenTest {

    private data class GoldenCase(
        val format: BarcodeFormat,
        val content: String,
        val expectedSha256: String
    )

    private val cases = listOf(
        GoldenCase(
            BarcodeFormat.QR_CODE, "GOLDEN-TEST-CONTENT",
            "34eb239d07fd1b5007c13b678bb91cde6acdf4dd48382e0d8726ac4c77148d7c"
        ),
        GoldenCase(
            BarcodeFormat.EAN_13, "5901234123457",
            "b600afa1e23b1ecbece0c29cead6fa51ed627e008a1d44c9967397c6f124bed8"
        ),
        GoldenCase(
            BarcodeFormat.CODE_128, "GOLDEN123",
            "da6a402371e2728d5493b46ffc3f8499d7bbdf4ed17186eef81fb30d78775d5d"
        ),
        GoldenCase(
            BarcodeFormat.DATA_MATRIX, "GOLDEN-DM",
            "32ada41ddedcd3ed2f3bb30fd4a54ea77d94afdf7f7881069cd87a8fc5416ead"
        )
    )

    private fun sha256(s: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `svg output hashes stay stable`() {
        for (case in cases) {
            val svg = SvgQRCodeGenerator.generateSVG(case.content, case.format)
            assertEquals(
                "Golden hash mismatch for ${case.format} — generation output changed silently?",
                case.expectedSha256,
                sha256(svg)
            )
        }
    }
}
