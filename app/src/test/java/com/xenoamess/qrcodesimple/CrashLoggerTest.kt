package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class CrashLoggerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        CrashLogger.clear(context)
    }

    @After
    fun tearDown() {
        CrashLogger.clear(context)
    }

    @Test
    fun `write stores crash log with metadata and stacktrace`() {
        val file = CrashLogger.write(context, Thread.currentThread(), IllegalStateException("boom"))

        assertTrue(file.exists())
        val text = file.readText()
        assertTrue(text.contains("time: "))
        assertTrue(text.contains("git: "))
        assertTrue(text.contains("thread: "))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun `listLogs and readLatest reflect written logs`() {
        CrashLogger.write(context, Thread.currentThread(), RuntimeException("first"))
        Thread.sleep(5)
        CrashLogger.write(context, Thread.currentThread(), RuntimeException("second"))

        val logs = CrashLogger.listLogs(context)
        assertEquals(2, logs.size)
        val latest = CrashLogger.readLatest(context)
        assertNotNull(latest)
        assertTrue(latest!!.contains("second"))
    }

    @Test
    fun `clear removes all logs`() {
        CrashLogger.write(context, Thread.currentThread(), RuntimeException("x"))
        CrashLogger.clear(context)

        assertTrue(CrashLogger.listLogs(context).isEmpty())
        assertNull(CrashLogger.readLatest(context))
    }

    @Test
    fun `prune keeps at most ten logs`() {
        repeat(12) {
            CrashLogger.write(context, Thread.currentThread(), RuntimeException("crash-$it"))
            Thread.sleep(2)
        }

        assertEquals(10, CrashLogger.listLogs(context).size)
    }
}
