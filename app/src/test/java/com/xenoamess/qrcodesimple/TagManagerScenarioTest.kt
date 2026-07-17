package com.xenoamess.qrcodesimple

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TagManager 标签 CRUD 与建议场景测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TagManagerScenarioTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("tag_manager", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        TagManager.init(context)
    }

    @Test
    fun `add and remove tags persist`() {
        TagManager.addTag("work")
        TagManager.addTag("personal")
        assertTrue(TagManager.getAllTags().containsAll(listOf("work", "personal")))

        TagManager.removeTag("work")
        assertTrue(!TagManager.getAllTags().contains("work"))

        // 重新 init 后应从持久层恢复
        TagManager.init(ApplicationProvider.getApplicationContext())
        assertTrue(TagManager.getAllTags().contains("personal"))
    }

    @Test
    fun `rename tag migrates name`() {
        TagManager.addTag("old-name")
        TagManager.renameTag("old-name", "new-name")

        val tags = TagManager.getAllTags()
        assertTrue(!tags.contains("old-name"))
        assertTrue(tags.contains("new-name"))
    }

    @Test
    fun `suggested tags prefix match and are sorted`() {
        TagManager.addTag("shopping")
        TagManager.addTag("work")
        TagManager.addTag("shipping")

        val suggestions = TagManager.getSuggestedTags("sh")
        assertTrue(suggestions.containsAll(listOf("shopping", "shipping")))
        assertTrue(!suggestions.contains("work"))
    }

    @Test
    fun `parseTags trims and dedups`() {
        val parsed = TagManager.parseTags(" work ,  personal ,work, ")
        assertEquals(listOf("work", "personal"), parsed)
        assertEquals("work,personal", TagManager.tagsToString(parsed))
    }
}
