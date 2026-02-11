package com.xenoamess.qrcodesimple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TagManager 单元测试
 */
class TagManagerTest {

    @Test
    fun `parse tags from string`() {
        val tags = TagManager.parseTags("work, personal, urgent")
        assertEquals(3, tags.size)
        assertEquals("work", tags[0])
        assertEquals("personal", tags[1])
        assertEquals("urgent", tags[2])
    }

    @Test
    fun `parse empty tags returns empty list`() {
        val tags = TagManager.parseTags(null)
        assertEquals(0, tags.size)
    }

    @Test
    fun `parse blank tags returns empty list`() {
        val tags = TagManager.parseTags("   ")
        assertEquals(0, tags.size)
    }

    @Test
    fun `tags to string conversion`() {
        val tags = listOf("work", "personal", "urgent")
        val result = TagManager.tagsToString(tags)
        assertEquals("work,personal,urgent", result)
    }

    @Test
    fun `empty tags to string returns empty`() {
        val result = TagManager.tagsToString(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `parse tags with extra spaces`() {
        val tags = TagManager.parseTags("  work  ,  personal  ,  urgent  ")
        assertEquals(3, tags.size)
        assertEquals("work", tags[0])
        assertEquals("personal", tags[1])
        assertEquals("urgent", tags[2])
    }

    @Test
    fun `parse tags with empty entries`() {
        val tags = TagManager.parseTags("work,,personal")
        assertEquals(2, tags.size)
        assertEquals("work", tags[0])
        assertEquals("personal", tags[1])
    }
}
