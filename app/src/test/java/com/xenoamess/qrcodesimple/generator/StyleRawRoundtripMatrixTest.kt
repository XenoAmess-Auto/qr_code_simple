package com.xenoamess.qrcodesimple.generator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.ResultMetadataType
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertNotNull

/**
 * 样式回扫摸底矩阵：把 moduleShape / moduleFillRatio / positionPatternShape
 * 不清洗，直接喂给所有格式，统计生成成功率和回扫通过率。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StyleRawRoundtripMatrixTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    data class MatrixResult(
        val format: BarcodeFormat,
        val moduleShape: AdvancedBarcodeGenerator.ModuleShape,
        val moduleFillRatio: Float,
        val positionPatternShape: AdvancedBarcodeGenerator.PositionPatternShape,
        val generateSuccess: Boolean,
        val scanPassCount: Int,
        val runs: Int,
        val error: String?
    )

    @Test(timeout = 30 * 60 * 1000)
    fun `raw style roundtrip matrix for all formats`() {
        val runs = 5
        val results = mutableListOf<MatrixResult>()
        val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }

        for (format in formats) {
            val content = BarcodeFormatTestFixtures.validContent(format)
            val expected = BarcodeFormatTestFixtures.expectedRoundtripText(format, content)

            for (shape in AdvancedBarcodeGenerator.ModuleShape.entries) {
                for (ratio in listOf(0.5f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f)) {
                    for (pattern in AdvancedBarcodeGenerator.PositionPatternShape.entries) {
                        val style = AdvancedBarcodeGenerator.StyleConfig(
                            moduleShape = shape,
                            moduleFillRatio = ratio,
                            positionPatternShape = pattern
                        )

                        var generateSuccess = false
                        var scanPassCount = 0
                        var error: String? = null

                        try {
                            if (format.isScannable) {
                                for (i in 0 until runs) {
                                    val bitmap = AdvancedBarcodeGenerator.generateStyled(
                                        content, format, 800, style
                                    )
                                    if (bitmap == null) {
                                        error = "generate returned null"
                                        break
                                    }
                                    generateSuccess = true
                                    val scanResults = QRCodeScanner.scanSync(context, bitmap)
                                    val actual = actualRoundtripText(format, scanResults)
                                    if (actual == expected) scanPassCount++
                                }
                            } else {
                                val bitmap = AdvancedBarcodeGenerator.generateStyled(
                                    content, format, 800, style
                                )
                                generateSuccess = bitmap != null
                                if (!generateSuccess) error = "generate returned null"
                            }
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        }

                        results.add(
                            MatrixResult(
                                format = format,
                                moduleShape = shape,
                                moduleFillRatio = ratio,
                                positionPatternShape = pattern,
                                generateSuccess = generateSuccess,
                                scanPassCount = scanPassCount,
                                runs = if (format.isScannable) runs else 1,
                                error = error
                            )
                        )
                    }
                }
            }
        }

        printSummary(results)
        printDetailed(results)

        assertNotNull(results, "Results should be collected")
    }

    private fun actualRoundtripText(format: BarcodeFormat, results: List<QRCodeScanner.ScanResult>): String? {
        val first = results.firstOrNull() ?: return null
        return when (format) {
            BarcodeFormat.UPC_EAN_EXTENSION -> {
                results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
                    ?: first.resultMetadata?.get(ResultMetadataType.UPC_EAN_EXTENSION) as? String
            }

            else -> first.text
        }
    }

    private val matrixOutputFile = File("/tmp/style-raw-roundtrip-matrix-output.md")

    private fun printlnToFile(line: String) {
        println(line)
        matrixOutputFile.appendText("$line\n")
    }

    private fun clearOutputFile() {
        matrixOutputFile.writeText("")
    }

    private fun printSummary(results: List<MatrixResult>) {
        clearOutputFile()
        printlnToFile("")
        printlnToFile("## 样式回扫摸底矩阵 - 汇总")
        printlnToFile("")
        printlnToFile("| 格式 | 可扫描 | 组合数 | 生成成功 | 回扫通过组合 | 备注 |")
        printlnToFile("|---|---|---|---|---|---|")
        val byFormat = results.groupBy { it.format }
        for (format in byFormat.keys.sortedBy { it.name }) {
            val combos = byFormat[format]!!
            val generateSuccess = combos.count { it.generateSuccess }
            val scanPass = combos.count { it.scanPassCount > 0 }
            val notes = if (!format.isScannable) "仅生成" else ""
            printlnToFile(
                "| ${format.name} | ${format.isScannable} | ${combos.size} | " +
                        "$generateSuccess / ${combos.size} | $scanPass / ${combos.size} | $notes |"
            )
        }
    }

    private fun printDetailed(results: List<MatrixResult>) {
        printlnToFile("")
        printlnToFile("## 样式回扫摸底矩阵 - 明细")
        printlnToFile("")
        printlnToFile(
            "| 格式 | moduleShape | moduleFillRatio | positionPatternShape | " +
                    "生成成功 | 回扫通过次数/5 | 通过 | 错误 |"
        )
        printlnToFile("|---|---|---|---|---|---|---|---|")
        for (r in results) {
            val pass = if (r.format.isScannable) (if (r.scanPassCount > 0) "是" else "否") else "-"
            val scanRate = if (r.format.isScannable) "${r.scanPassCount}/${r.runs}" else "-"
            printlnToFile(
                "| ${r.format.name} | ${r.moduleShape} | ${r.moduleFillRatio} | ${r.positionPatternShape} | " +
                        "${if (r.generateSuccess) "是" else "否"} | $scanRate | $pass | ${r.error ?: ""} |"
            )
        }
    }
}
