package com.xenoamess.qrcodesimple

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class NetworkUtilsTest {

    @Test
    fun `withRetry returns immediately on success`() {
        val calls = AtomicInteger(0)
        val result = NetworkUtils.withRetry("test") {
            calls.incrementAndGet()
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls.get())
    }

    @Test
    fun `withRetry retries until success`() {
        val calls = AtomicInteger(0)
        val result = NetworkUtils.withRetry("test", maxAttempts = 3) {
            if (calls.incrementAndGet() < 3) {
                throw java.io.IOException("flaky")
            }
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, calls.get())
    }

    @Test
    fun `withRetry throws last exception after exhaustion`() {
        val calls = AtomicInteger(0)
        val error = assertThrows(java.io.IOException::class.java) {
            NetworkUtils.withRetry<String>("test", maxAttempts = 2) {
                calls.incrementAndGet()
                throw java.io.IOException("always fails")
            }
        }
        assertEquals("always fails", error.message)
        assertEquals(2, calls.get())
    }

    @Test
    fun `sleepBackoff waits exponentially longer`() {
        val t0 = System.nanoTime()
        NetworkUtils.sleepBackoff(0, 50)
        NetworkUtils.sleepBackoff(1, 50)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("expected at least ~120ms, got ${elapsedMs}ms", elapsedMs >= 120)
    }
}
