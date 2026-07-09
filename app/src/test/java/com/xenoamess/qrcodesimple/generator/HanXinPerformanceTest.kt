package com.xenoamess.qrcodesimple.generator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.decoder.hanxin.HanXinDecoder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

/**
 * Simple informational micro-benchmarks for Han Xin Code encoding and decoding.
 *
 * These are not JMH-grade benchmarks; they exist to print rough throughput
 * numbers under Robolectric. They do not enforce fixed wall-clock thresholds
 * so that slower CI runners or noisy environments do not cause spurious
 * failures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinPerformanceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `encode short ASCII content timing`() {
        val content = "Hello Han Xin"
        val duration = measureNanos(warmup = 5, iterations = 20) {
            BarcodeGenerator.generate(
                content,
                BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
            )
        }
        println("Encode short ASCII: ${duration / 1_000_000} ms")
    }

    @Test
    fun `decode short ASCII content timing`() {
        val bitmap = BarcodeGenerator.generate(
            "Hello Han Xin",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
        )
        assertNotNull(bitmap)

        val duration = measureNanos(warmup = 3, iterations = 10) {
            HanXinDecoder.decode(bitmap!!)
        }
        println("Decode short ASCII: ${duration / 1_000_000} ms")
    }

    @Test
    fun `encode Chinese content timing`() {
        val content = "汉信码测试"
        val duration = measureNanos(warmup = 3, iterations = 10) {
            BarcodeGenerator.generate(
                content,
                BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
            )
        }
        println("Encode Chinese: ${duration / 1_000_000} ms")
    }

    @Test
    fun `decode Chinese content timing`() {
        val bitmap = BarcodeGenerator.generate(
            "汉信码测试",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
        )
        assertNotNull(bitmap)

        val duration = measureNanos(warmup = 3, iterations = 10) {
            HanXinDecoder.decode(bitmap!!)
        }
        println("Decode Chinese: ${duration / 1_000_000} ms")
    }

    @Test
    fun `encode long content timing`() {
        val content = "a".repeat(500)
        val duration = measureNanos(warmup = 2, iterations = 5) {
            BarcodeGenerator.generate(
                content,
                BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
            )
        }
        println("Encode 500 chars: ${duration / 1_000_000} ms")
    }

    @Test
    fun `decode long content timing`() {
        val content = "a".repeat(500)
        val bitmap = BarcodeGenerator.generate(
            content,
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
        )
        assertNotNull(bitmap)

        val duration = measureNanos(warmup = 2, iterations = 5) {
            HanXinDecoder.decode(bitmap!!)
        }
        println("Decode 500 chars: ${duration / 1_000_000} ms")
    }

    private inline fun measureNanos(warmup: Int, iterations: Int, block: () -> Unit): Long {
        repeat(warmup) { block() }
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val end = System.nanoTime()
        return (end - start) / iterations
    }
}
