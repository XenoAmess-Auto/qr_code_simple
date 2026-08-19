package com.xenoamess.qrcodesimple

import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ApkPatcher 补丁格式处理：
 * - "ZiPat1" → ApkDiffPatch（native，Robolectric 无 native → 必须抛受控 Exception 而非 Error）
 * - 未知格式 → IllegalArgumentException
 * 任何失败都必须以普通 Exception 形式冒出，供上层回退全量下载。
 * （真机打补丁路径由 IncrementalUpdateInstrumentedTest 用预生成 ZiPat1 夹具覆盖）
 */
@RunWith(RobolectricTestRunner::class)
class ApkPatcherTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "apkpatcher-test").apply { mkdirs() }
    }

    private fun writeFile(name: String, bytes: ByteArray): File =
        File(dir, name).apply { writeBytes(bytes) }

    @Test
    fun `ZiPat1 patch without native library throws controlled exception not Error`() {
        // Robolectric 加载不到 libapkpatch.so；必须把 UnsatisfiedLinkError 包成普通 Exception，
        // 否则上层 catch(Exception) 兜不住、直接崩溃
        val base = writeFile("zipdiff-base.bin", ByteArray(1024) { 1 })
        val patch = writeFile("zipdiff.patch", "ZiPat1&lzma-fake-patch-content".toByteArray())
        val out = File(dir, "zipdiff-out.bin")

        try {
            ApkPatcher.applyPatch(context, base, patch, out)
            org.junit.Assert.fail("应抛出受控异常")
        } catch (e: Exception) {
            assertTrue(
                "必须转成普通异常而非 Error 冒泡: ${e.javaClass.simpleName}",
                e is IllegalStateException
            )
        }
    }

    @Test
    fun `unknown format throws IllegalArgumentException`() {
        val base = writeFile("unk-base.bin", ByteArray(16))
        val patch = writeFile("unk.patch", "NOT-A-PATCH".toByteArray())
        val out = File(dir, "unk-out.bin")

        try {
            ApkPatcher.applyPatch(context, base, patch, out)
            org.junit.Assert.fail("应抛未知格式异常")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("unknown patch format"))
        }
    }

    @Test
    fun `empty file is treated as unknown format`() {
        val base = writeFile("empty-base.bin", ByteArray(16))
        val patch = writeFile("empty.patch", ByteArray(0))
        val out = File(dir, "empty-out.bin")

        try {
            ApkPatcher.applyPatch(context, base, patch, out)
            org.junit.Assert.fail("空补丁应抛异常")
        } catch (e: IllegalArgumentException) {
            assertEquals(true, e.message?.contains("unknown patch format"))
        }
    }

    @Test
    fun `concurrent native patches use isolated temporary files`() {
        val base = writeFile("parallel-base.bin", ByteArray(16))
        val patch = writeFile("parallel.patch", "ZiPat1&parallel".toByteArray())
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val tempPaths = Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newFixedThreadPool(2)
        val outputs = listOf(File(dir, "parallel-1.apk"), File(dir, "parallel-2.apk"))

        try {
            val futures = outputs.map { output ->
                executor.submit {
                    ApkPatcher.applyPatch(context, base, patch, output) { _, _, outputPath, _, tempPath, _ ->
                        tempPaths += tempPath
                        assertTrue(File(tempPath).isFile)
                        entered.countDown()
                        release.await(3, TimeUnit.SECONDS)
                        File(outputPath).writeBytes(byteArrayOf(1))
                        0
                    }
                }
            }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            release.countDown()
            futures.forEach { it.get(3, TimeUnit.SECONDS) }
        } finally {
            release.countDown()
            executor.shutdownNow()
        }

        assertEquals(2, tempPaths.size)
        assertNotEquals(tempPaths[0], tempPaths[1])
        assertTrue(outputs.all(File::isFile))
        assertTrue(tempPaths.all { !File(it).exists() })
    }

    @Test
    fun `native patch cancellation is preserved and temporary file is removed`() {
        val base = writeFile("cancel-base.bin", ByteArray(16))
        val patch = writeFile("cancel.patch", "ZiPat1&cancel".toByteArray())
        val output = File(dir, "cancel.apk")
        var tempPath: String? = null

        assertThrows(CancellationException::class.java) {
            ApkPatcher.applyPatch(context, base, patch, output) { _, _, _, _, temp, _ ->
                tempPath = temp
                throw CancellationException("cancelled")
            }
        }

        assertFalse(File(tempPath!!).exists())
    }
}
