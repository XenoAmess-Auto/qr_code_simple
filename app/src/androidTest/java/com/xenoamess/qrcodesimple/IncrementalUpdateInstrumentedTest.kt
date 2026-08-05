package com.xenoamess.qrcodesimple

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 增量更新的真机验证（单测覆盖不到的真机风险）：
 * 1. libapkpatch.so 在 ART 上可加载、可打补丁（ApkDiffPatch ZiPat1 格式）
 * 2. ApplicationInfo.sourceDir 已安装 APK 在真机上确实可读可算 sha256
 * 3. IncrementalUpdater 用真底包跑通单跳链
 */
@RunWith(AndroidJUnit4::class)
class IncrementalUpdateInstrumentedTest {

    // targetContext = 被测 App（filesDir/已安装APK 路径用它）
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    // instrumentation context = 测试 APK（app/src/androidTest/assets 打进的是测试 APK）
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    private fun copyFixture(name: String, destDir: File) {
        testContext.assets.open("apkdiff-fixtures/$name").use { input ->
            File(destDir, name).writeBytes(input.readBytes())
        }
    }

    @Test
    fun installedApkIsReadableAndSha256Computable() {
        val apk = ApkPatcher.installedApkFile(context)
        assertNotNull("已安装 APK 必须可读（增量打底的前提）", apk)
        val sha = ApkPatcher.sha256(apk!!)
        assertTrue("sha256 必须是 64 位 hex: $sha", sha.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun apkdiffReplaysFixtureByteForByteOnArt() {
        // 预生成夹具（服务端 ZipDiff 产出）：old.apk + patch.bin → new.apk
        val dir = context.cacheDir
        val fixtureDir = File(context.filesDir, "apkdiff-fixtures").apply { mkdirs() }
        for (name in listOf("old.apk", "new.apk", "patch.bin")) {
            copyFixture(name, fixtureDir)
        }
        val oldFile = File(fixtureDir, "old.apk")
        val patchFile = File(fixtureDir, "patch.bin")
        val expected = File(fixtureDir, "new.apk")
        val outFile = File(dir, "apkdiff-out.apk")

        ApkPatcher.applyPatch(context, oldFile, patchFile, outFile)

        assertTrue(
            "ART 上 ApkDiffPatch 打补丁结果必须与目标字节一致",
            expected.readBytes().contentEquals(outFile.readBytes())
        )
        outFile.delete()
    }

    @Test
    fun realBaseRunsSingleHopChain() = runBlocking {
        val fixtureDir = File(context.filesDir, "apkdiff-fixtures").apply { mkdirs() }
        if (!File(fixtureDir, "old.apk").exists()) {
            for (name in listOf("old.apk", "patch.bin", "new.apk")) {
                copyFixture(name, fixtureDir)
            }
        }
        val baseFixture = File(fixtureDir, "old.apk")
        val patchBytes = File(fixtureDir, "patch.bin").readBytes()
        val resultBytes = File(fixtureDir, "new.apk").readBytes()

        fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        val chain = UpdateDecider.UpdateChain(
            fromApkSha256 = sha(baseFixture.readBytes()),
            totalSizeBytes = patchBytes.size.toLong(),
            hops = listOf(
                UpdateDecider.PatchHop(
                    toVersionCode = 999,
                    url = "https://test/p.patch",
                    sizeBytes = patchBytes.size.toLong(),
                    patchSha256 = sha(patchBytes),
                    resultSha256 = sha(resultBytes)
                )
            )
        )
        val updater = IncrementalUpdater(context)
        updater.installedApkProvider = { baseFixture }
        updater.downloader = { _, destination, _, _ ->
            destination.parentFile?.mkdirs()
            destination.writeBytes(patchBytes)
            true
        }

        val output = File(context.filesDir, "updates/qr-code-simple-9.9.9.apk")
        val result = updater.executeChain(
            chain,
            output,
            sha(resultBytes),
            resultBytes.size.toLong()
        ) {}
        assertNotNull("真机单跳链必须成功", result)
        assertTrue(resultBytes.contentEquals(result!!.readBytes()))
        result.delete()
        Unit
    }
}
