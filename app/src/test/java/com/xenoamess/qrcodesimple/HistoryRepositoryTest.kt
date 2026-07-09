package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HistoryRepository 单元测试，覆盖历史记录去重逻辑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: HistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = HistoryRepository(context)
        runBlocking { repository.deleteAll() }
    }

    @Test
    fun `insertScan duplicate content updates timestamp and keeps one item`() = runBlocking {
        val content = "https://example.com"
        repository.insertScan(content, HistoryType.QR_CODE)
        val first = repository.allHistory.first().single()

        Thread.sleep(10)
        repository.insertScan(content, HistoryType.QR_CODE)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals(content, items[0].content)
        assertEquals(false, items[0].isGenerated)
        assertEquals(first.id, items[0].id)
        assertEquals(HistoryType.QR_CODE, items[0].type)
        assertNotNull(items[0].timestamp)
        assertTrue(items[0].timestamp > first.timestamp)
    }

    @Test
    fun `insertScan does not affect generated item with same content`() = runBlocking {
        val content = "https://example.com"
        repository.insertGenerate(content, HistoryType.QR_CODE, "QR_CODE", "{}")
        repository.insertScan(content, HistoryType.BARCODE)

        val items = repository.allHistory.first()
        assertEquals(2, items.size)
        val scanItem = items.find { !it.isGenerated }!!
        val genItem = items.find { it.isGenerated }!!
        assertEquals(content, scanItem.content)
        assertEquals(HistoryType.BARCODE, scanItem.type)
        assertEquals(content, genItem.content)
        assertEquals(HistoryType.QR_CODE, genItem.type)
        assertEquals("QR_CODE", genItem.barcodeFormat)
        assertEquals("{}", genItem.styleJson)
    }

    @Test
    fun `insertGenerate duplicate content updates fields and keeps one item`() = runBlocking {
        val content = "123456789012"
        repository.insertGenerate(content, HistoryType.BARCODE, "EAN_13", null)
        val first = repository.allHistory.first().single()

        Thread.sleep(10)
        repository.insertGenerate(content, HistoryType.BARCODE, "CODE_128", "{}")

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals(first.id, items[0].id)
        assertEquals("CODE_128", items[0].barcodeFormat)
        assertEquals("{}", items[0].styleJson)
        assertEquals(true, items[0].isGenerated)
        assertTrue(items[0].timestamp > first.timestamp)
    }

    @Test
    fun `insertGenerate does not affect scanned item with same content`() = runBlocking {
        val content = "https://example.com"
        repository.insertScan(content, HistoryType.QR_CODE)
        repository.insertGenerate(content, HistoryType.BARCODE, "CODE_128", "{}")

        val items = repository.allHistory.first()
        assertEquals(2, items.size)
        val scanItem = items.find { !it.isGenerated }!!
        val genItem = items.find { it.isGenerated }!!
        assertEquals(HistoryType.QR_CODE, scanItem.type)
        assertEquals(HistoryType.BARCODE, genItem.type)
        assertEquals("CODE_128", genItem.barcodeFormat)
    }

    @Test
    fun `insertScan and insertGenerate same content create two separate items`() = runBlocking {
        val content = "same content"
        repository.insertScan(content, HistoryType.TEXT)
        repository.insertGenerate(content, HistoryType.TEXT, null, null)

        val items = repository.allHistory.first()
        assertEquals(2, items.size)
        assertEquals(1, items.count { !it.isGenerated })
        assertEquals(1, items.count { it.isGenerated })
    }
}
