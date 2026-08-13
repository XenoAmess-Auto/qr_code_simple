package com.xenoamess.qrcodesimple

import android.util.Log

/**
 * 轻量网络重试工具：指数退避（500ms → 1s → 2s …），默认最多 3 次。
 * 只用于幂等的元数据 GET 请求；APK 等大文件下载不走这里。
 */
object NetworkUtils {

    const val DEFAULT_MAX_ATTEMPTS = 3
    const val DEFAULT_INITIAL_DELAY_MS = 500L

    /** 第 attempt 次（从 0 计）失败后的退避等待。 */
    fun sleepBackoff(attempt: Int, initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS) {
        val delay = initialDelayMs shl attempt.coerceAtMost(4)
        try {
            Thread.sleep(delay)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 运行 [block]，异常时按指数退避重试，全部失败后抛出最后一次异常。
     * [block] 内部返回 null 不触发重试（用于表达"请求成功但内容不可用"）。
     */
    fun <T> withRetry(
        tag: String,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        block: () -> T
    ): T {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                Log.w(tag, "network attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts - 1) {
                    sleepBackoff(attempt)
                }
            }
        }
        throw lastError ?: IllegalStateException("withRetry exhausted without running block")
    }
}
