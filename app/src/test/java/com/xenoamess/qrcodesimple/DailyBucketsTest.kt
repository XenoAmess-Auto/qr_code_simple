package com.xenoamess.qrcodesimple

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DailyBucketsTest {

    private fun atDay(daysAgo: Int, now: Long, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        return cal.timeInMillis
    }

    @Test
    fun `empty input yields all zeros`() {
        val result = DailyBuckets.bucketize(emptyList(), 7, System.currentTimeMillis())
        assertEquals(7, result.size)
        assertArrayEquals(IntArray(7), result)
    }

    @Test
    fun `timestamps land in correct day buckets`() {
        val now = System.currentTimeMillis()
        val timestamps = listOf(
            atDay(0, now),
            atDay(0, now, hour = 1),
            atDay(1, now),
            atDay(13, now),
            atDay(20, now)
        )
        val result = DailyBuckets.bucketize(timestamps, 14, now)
        assertEquals(14, result.size)
        assertEquals(2, result[13])
        assertEquals(1, result[12])
        assertEquals(1, result[0])
        assertEquals(4, result.sum())
    }

    @Test
    fun `timestamps older than window are ignored`() {
        val now = System.currentTimeMillis()
        val result = DailyBuckets.bucketize(listOf(atDay(100, now)), 14, now)
        assertArrayEquals(IntArray(14), result)
    }

    @Test
    fun `day boundary crossing is respected`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 30)
        val now = cal.timeInMillis

        val justBeforeMidnight = now - 31L * 60 * 1000
        val result = DailyBuckets.bucketize(listOf(justBeforeMidnight, now), 2, now)
        assertEquals(1, result[0])
        assertEquals(1, result[1])
    }
}
