package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.HistoryItem
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
import kotlin.test.assertNull
import kotlin.test.assertFalse

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

    @Test
    fun `privacy mode prevents insertScan insertGenerate and import`() = runBlocking {
        QRCodeApp.setPrivacyMode(context, true)
        try {
            repository.insertScan("private", HistoryType.QR_CODE)
            repository.insertGenerate("private", HistoryType.QR_CODE, "QR_CODE", null)
            repository.importHistoryItem(HistoryItem(content = "private", type = HistoryType.QR_CODE, isGenerated = false))

            val items = repository.allHistory.first()
            assertEquals(0, items.size)
        } finally {
            QRCodeApp.setPrivacyMode(context, false)
        }
    }

    @Test
    fun `insert direct returns negative in privacy mode`() = runBlocking {
        QRCodeApp.setPrivacyMode(context, true)
        try {
            val id = repository.insert(HistoryItem(content = "direct", type = HistoryType.QR_CODE, isGenerated = false))
            assertEquals(-1, id)
        } finally {
            QRCodeApp.setPrivacyMode(context, false)
        }
    }

    @Test
    fun `searchHistory filters by content`() = runBlocking {
        repository.insertScan("https://github.com", HistoryType.QR_CODE)
        repository.insertScan("https://example.com", HistoryType.QR_CODE)

        val results = repository.searchHistory("github").first()
        assertEquals(1, results.size)
        assertEquals("https://github.com", results[0].content)
    }

    @Test
    fun `getFavoriteHistory returns only favorites`() = runBlocking {
        repository.insertScan("scan", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()
        repository.updateFavorite(item.id, true)

        val favorites = repository.getFavoriteHistory().first()
        assertEquals(1, favorites.size)
        assertTrue(favorites[0].isFavorite)
    }

    @Test
    fun `addTags and removeTag update item tags`() = runBlocking {
        repository.insertScan("tagged", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()

        repository.addTags(item.id, "work,important")
        val withTags = repository.allHistory.first().single()
        assertTrue(withTags.tags?.contains("work") == true)
        assertTrue(withTags.tags?.contains("important") == true)

        repository.removeTag(item.id, "work")
        val withoutWork = repository.allHistory.first().single()
        assertNull(withoutWork.tags?.let { if (it.contains("work")) it else null })
    }

    @Test
    fun `setTags replaces all tags`() = runBlocking {
        repository.insertScan("tagged", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()

        repository.addTags(item.id, "a,b")
        repository.setTags(item.id, listOf("c", "d"))

        val updated = repository.allHistory.first().single()
        assertEquals("c,d", updated.tags)
    }

    @Test
    fun `getHistoryByType filters by type`() = runBlocking {
        repository.insertScan("qr", HistoryType.QR_CODE)
        repository.insertScan("bar", HistoryType.BARCODE)

        val qrItems = repository.getHistoryByType(HistoryType.QR_CODE).first()
        assertEquals(1, qrItems.size)
        assertEquals(HistoryType.QR_CODE, qrItems[0].type)
    }

    @Test
    fun `getHistoryByBarcodeFormat filters by format`() = runBlocking {
        repository.insertGenerate("123", HistoryType.BARCODE, "EAN_13", null)
        repository.insertGenerate("456", HistoryType.BARCODE, "CODE_128", null)

        val eanItems = repository.getHistoryByBarcodeFormat("EAN_13").first()
        assertEquals(1, eanItems.size)
        assertEquals("EAN_13", eanItems[0].barcodeFormat)
    }

    @Test
    fun `addNotes and updateNotes modify notes`() = runBlocking {
        repository.insertScan("note", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()

        repository.addNotes(item.id, "my note")
        val withNotes = repository.allHistory.first().single()
        assertEquals("my note", withNotes.notes)

        repository.updateNotes(item.id, "updated note")
        val updated = repository.allHistory.first().single()
        assertEquals("updated note", updated.notes)
    }

    @Test
    fun `deleteById removes item`() = runBlocking {
        repository.insertScan("delete", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()

        repository.deleteById(item.id)
        assertEquals(0, repository.allHistory.first().size)
    }

    @Test
    fun `updateContent changes content`() = runBlocking {
        repository.insertScan("old", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()

        repository.updateContent(item.id, "new")
        val updated = repository.allHistory.first().single()
        assertEquals("new", updated.content)
    }

    @Test
    fun `getAllTypes and getAllTags aggregate correctly`() = runBlocking {
        repository.insertScan("qr", HistoryType.QR_CODE)
        repository.insertScan("bar", HistoryType.BARCODE)
        val item = repository.allHistory.first().first { it.content == "qr" }
        repository.addTags(item.id, "a,b")

        val types = repository.getAllTypes()
        assertTrue(types.contains(HistoryType.QR_CODE))
        assertTrue(types.contains(HistoryType.BARCODE))

        val tags = repository.getAllTags()
        assertTrue(tags.contains("a"))
        assertTrue(tags.contains("b"))
    }

    @Test
    fun `getHistoryByTag filters by tag`() = runBlocking {
        repository.insertScan("tagged", HistoryType.QR_CODE)
        val item = repository.allHistory.first().single()
        repository.addTags(item.id, "important")

        val tagged = repository.getHistoryByTag("important").first()
        assertEquals(1, tagged.size)
    }
}
