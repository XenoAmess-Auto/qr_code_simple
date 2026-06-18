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
import kotlin.test.assertTrue

/**
 * Simple micro-benchmarks for Han Xin Code encoding and decoding.
 *
 * These are not JMH-grade benchmarks; they exist to catch gross regressions
 * in the encode/decode pipeline and to give a rough idea of throughput under
 * Robolectric.
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
    fun `encode short ASCII content within reasonable time`() {
        val content = "Hello Han Xin"
        val duration = measureNanos(warmup = 5, iterations = 20) {
            BarcodeGenerator.generate(
                content,
                BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
            )
        }
        println("Encode short ASCII: ${duration / 1_000_000} ms")
        assertTrue(duration < 500_000_000, "Encoding should take less than 500 ms")
    }

    @Test
    fun `decode short ASCII content within reasonable time`() {
        val bitmap = BarcodeGenerator.generate(
            "Hello Han Xin",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
        )
        assertNotNull(bitmap)

        val duration = measureNanos(warmup = 3, iterations = 10) {
            HanXinDecoder.decode(bitmap!!)
        }
        println("Decode short ASCII: ${duration / 1_000_000} ms")
        assertTrue(duration < 500_000_000, "Decoding should take less than 500 ms")
    }

    @Test
    fun `encode Chinese content within reasonable time`() {
        val content = "汉信码测试"
        val duration = measureNanos(warmup = 3, iterations = 10) {
            BarcodeGenerator.generate(
                content,
                BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
            )
        }
        println("Encode Chinese: ${duration / 1_000_000} ms")
        assertTrue(duration < 500_000_000, "Encoding should take less than 500 ms")
    }

    @Test
    fun `decode Chinese content within reasonable time`() {
        val bitmap = BarcodeGenerator.generate(
            "汉信码测试",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 600, height = 600)
        )
        assertNotNull(bitmap)

        val duration = measureNanos(warmup = 3, iterations = 10) {
            HanXinDecoder.decode(bitmap!!)
        }
        println("Decode Chinese: ${duration / 1_000_000} ms")
        assertTrue(duration < 500_000_000, "Decoding should take less than 500 ms")
    }

    @Test
    fun `encode long content within reasonable time`() {
        val content = "a".repeat(500)
        val duration = measureNanos(warmup = 2, iterations = 5) {
            BarcodeGenerator.generate(
                content,
                BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
            )
        }
        println("Encode 500 chars: ${duration / 1_000_000} ms")
        assertTrue(duration < 2_000_000_000, "Long encoding should take less than 2 s")
    }

    @Test
    fun `decode long content within reasonable time`() {
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
        assertTrue(duration < 2_000_000_000, "Long decoding should take less than 2 s")
    }

    private inline fun measureNanos(warmup: Int, iterations: Int, block: () -> Unit): Long {
        repeat(warmup) { block() }
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val end = System.nanoTime()
        return (end - start) / iterations
    }
}
