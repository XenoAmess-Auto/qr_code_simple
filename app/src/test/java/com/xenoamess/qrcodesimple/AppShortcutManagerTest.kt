package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * AppShortcutManager 动态快捷方式测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class AppShortcutManagerTest {

    private lateinit var context: Context
    private lateinit var repository: HistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = HistoryRepository(context)
        runBlocking { repository.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { repository.deleteAll() }
        AppShortcutManager.removeAllDynamicShortcuts(context)
    }


    private fun dynamicShortcuts(): List<android.content.pm.ShortcutInfo> {
        val sm = context.getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
        return sm.dynamicShortcuts ?: emptyList()
    }

    @Test
    fun `updateDynamicShortcuts creates shortcuts for recent history`() = runBlocking {
        repository.insertScan("https://example.com/recent-item", HistoryType.QR_CODE)

        AppShortcutManager.updateDynamicShortcuts(context)

        val dynamic = dynamicShortcuts()
        assertTrue(dynamic.isNotEmpty())

        val first = dynamic.first()
        // 标签超长会被截断（15 字符 + 省略号），只断言前缀
        assertTrue(first.shortLabel.toString().startsWith("https://example"))
        // 深链必须走 GenerateActivity 的 ACTION_SEND 预填入口
        assertEquals(Intent.ACTION_SEND, first.intent?.action)
        assertEquals("text/plain", first.intent?.type)
        assertEquals(
            "https://example.com/recent-item",
            first.intent?.getStringExtra(Intent.EXTRA_TEXT)
        )
        assertEquals(GenerateActivity::class.java.name, first.intent?.component?.className)
    }

    @Test
    fun `updateDynamicShortcuts caps at two shortcuts`() = runBlocking {
        repeat(4) { i ->
            repository.insertScan("content-$i", HistoryType.QR_CODE)
        }

        AppShortcutManager.updateDynamicShortcuts(context)

        assertTrue(dynamicShortcuts().size <= 2)
    }

    @Test
    fun `long content label is truncated`() = runBlocking {
        repository.insertScan("a".repeat(50), HistoryType.TEXT)

        AppShortcutManager.updateDynamicShortcuts(context)

        assertTrue(dynamicShortcuts().first().shortLabel.toString().length <= 18)
    }

    @Test
    fun `updateDynamicShortcuts with empty history clears shortcuts`() = runBlocking {
        AppShortcutManager.updateDynamicShortcuts(context)
        assertTrue(dynamicShortcuts().isEmpty())
    }
}
