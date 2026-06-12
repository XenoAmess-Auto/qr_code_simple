package com.xenoamess.qrcodesimple.decoder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BarcodeScanUtils 的纯逻辑单元测试，不依赖 Android Bitmap。
 */
class BarcodeScanUtilsLogicTest {

    @Test
    fun `groupBars groups consecutive same values`() {
        val bars = listOf(true, true, false, true, false, false)
        val groups = BarcodeScanUtils.groupBars(bars)

        assertEquals(4, groups.size)
        assertEquals(true to 2, groups[0])
        assertEquals(false to 1, groups[1])
        assertEquals(true to 1, groups[2])
        assertEquals(false to 2, groups[3])
    }

    @Test
    fun `normalizeWidths converts to relative modules`() {
        val groups = listOf(
            true to 4,
            false to 2,
            true to 2,
            false to 6
        )
        val normalized = BarcodeScanUtils.normalizeWidths(groups)

        assertEquals(4, normalized.size)
        assertEquals(true to 2, normalized[0])
        assertEquals(false to 1, normalized[1])
        assertEquals(true to 1, normalized[2])
        assertEquals(false to 3, normalized[3])
    }

    @Test
    fun `normalizeWidths handles already normalized input`() {
        val groups = listOf(true to 1, false to 2, true to 1)
        val normalized = BarcodeScanUtils.normalizeWidths(groups)

        assertEquals(true to 1, normalized[0])
        assertEquals(false to 2, normalized[1])
        assertEquals(true to 1, normalized[2])
    }

    @Test
    fun `groupBars handles empty list`() {
        assertEquals(emptyList<Pair<Boolean, Int>>(), BarcodeScanUtils.groupBars(emptyList()))
    }
}
