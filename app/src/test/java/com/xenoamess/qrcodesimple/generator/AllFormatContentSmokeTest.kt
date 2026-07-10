package com.xenoamess.qrcodesimple.generator

import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.fail

/**
 * 使用统一内容 "11223344" 对所有格式做冒烟测试：
 * 如果校验通过，BarcodeGenerator 和 AdvancedBarcodeGenerator 都必须能生成成功。
 * 此测试可暴露类似 Korea Post 这样校验宽松但底层编码器有隐藏长度限制的问题。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AllFormatContentSmokeTest {

    @Test
    fun `generate 11223344 for all formats where validation passes`() {
        val content = "11223344"
        val failures = mutableListOf<String>()
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val validation = BarcodeGenerator.validateContent(content, format)
            if (!validation.isValid) {
                continue
            }

            try {
                val basicBitmap = BarcodeGenerator.generate(
                    content,
                    BarcodeGenerator.BarcodeConfig(format = format)
                )
                if (basicBitmap == null) {
                    failures.add("BarcodeGenerator returned null for $format")
                }

                val styledBitmap = AdvancedBarcodeGenerator.generateStyled(
                    content,
                    format,
                    800,
                    AdvancedBarcodeGenerator.StyleConfig()
                )
                if (styledBitmap == null) {
                    failures.add("AdvancedBarcodeGenerator returned null for $format")
                }
            } catch (e: Exception) {
                failures.add("$format threw ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }
}
