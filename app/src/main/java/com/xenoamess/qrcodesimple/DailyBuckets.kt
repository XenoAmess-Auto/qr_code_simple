package com.xenoamess.qrcodesimple

import java.util.Calendar

/** 把毫秒时间戳列表按自然日聚合为最近 [days] 天的计数（末位为今天）。 */
object DailyBuckets {

    fun bucketize(
        timestamps: List<Long>,
        days: Int,
        now: Long = System.currentTimeMillis()
    ): IntArray {
        require(days > 0)
        val todayStart = startOfDay(now)
        val result = IntArray(days)
        for (t in timestamps) {
            val dayStart = startOfDay(t)
            val diffDays = ((todayStart - dayStart) / 86_400_000L).toInt()
            val index = days - 1 - diffDays
            if (index in 0 until days) {
                result[index]++
            }
        }
        return result
    }

    private fun startOfDay(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
