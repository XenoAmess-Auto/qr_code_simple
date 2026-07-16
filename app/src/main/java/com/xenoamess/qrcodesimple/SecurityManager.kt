package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Color
import android.util.Log
import java.net.URL

/**
 * 恶意链接检测管理器
 */
object SecurityManager {

    private const val TAG = "SecurityManager"

    /**
     * 当前生效的黑名单。默认使用代码内置兜底列表；
     * [init] 后会替换为 assets 内置或 filesDir 在线更新产物（取 version 更高者）。
     */
    @Volatile
    private var blacklist: SecurityBlacklist = SecurityBlacklist.fallback()

    /** 用于解析本地化字符串；未 init 时回退到英文硬编码（单元测试场景）。 */
    @Volatile
    private var appContext: Context? = null

    private fun str(resId: Int, fallback: String, vararg args: Any): String {
        val ctx = appContext ?: return if (args.isEmpty()) fallback else String.format(fallback, *args)
        return ctx.getString(resId, *args)
    }

    /**
     * 初始化黑名单：加载 assets 内置列表，若有更高版本的在线更新产物则覆盖。
     * 可重复调用（在线更新完成后用于热加载）。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        val bundled = SecurityBlacklist.loadBundled(context)
        val override = SecurityBlacklist.loadOverride(context)
        blacklist = when {
            override != null && override.version > bundled.version -> override
            else -> bundled
        }
    }

    /** 仅用于测试：重置为代码内置兜底列表并清除 Context。 */
    internal fun resetForTesting() {
        blacklist = SecurityBlacklist.fallback()
        appContext = null
    }

    /** 当前生效的黑名单版本。 */
    fun currentVersion(): Int = blacklist.version

    data class SecurityCheckResult(
        val isSafe: Boolean,
        val riskLevel: RiskLevel,
        val message: String,
        val details: String = ""
    )

    enum class RiskLevel {
        SAFE,       // 安全
        LOW,        // 低风险
        MEDIUM,     // 中风险
        HIGH,       // 高风险
        UNKNOWN     // 未知
    }

    /**
     * 检查 URL 安全性（公开方法）- 同步版本
     */
    fun checkUrl(url: String): SecurityCheckResult {
        return checkUrlSecurity(url)
    }

    /**
     * 检查 URL 安全性
     */
    private fun checkUrlSecurity(url: String): SecurityCheckResult {
        return try {
            val domain = extractDomain(url) ?: return SecurityCheckResult(
                false,
                RiskLevel.UNKNOWN,
                str(R.string.security_cannot_parse_domain, "Cannot parse domain")
            )

            // 1. 检查本地黑名单
            if (isBlacklisted(domain)) {
                return SecurityCheckResult(
                    false,
                    RiskLevel.HIGH,
                    str(R.string.security_dangerous_link, "Dangerous link"),
                    str(R.string.security_domain_blacklisted, "This domain is blacklisted and may be unsafe")
                )
            }

            // 2. 检查 URL 可疑特征
            val suspiciousFeatures = checkSuspiciousFeatures(url, domain)
            if (suspiciousFeatures.isNotEmpty()) {
                return SecurityCheckResult(
                    false,
                    RiskLevel.MEDIUM,
                    str(R.string.security_suspicious_link, "Suspicious link"),
                    suspiciousFeatures.joinToString("\n")
                )
            }

            // 3. 检查 HTTPS
            if (!url.startsWith("https://", ignoreCase = true)) {
                return SecurityCheckResult(
                    false,
                    RiskLevel.LOW,
                    str(R.string.security_insecure_link, "Insecure link"),
                    str(R.string.security_no_https, "This link does not use HTTPS encryption")
                )
            }

            SecurityCheckResult(
                true,
                RiskLevel.SAFE,
                str(R.string.security_link_safe, "Link looks safe"),
                str(R.string.security_no_obvious_risk, "No obvious security risks found")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL", e)
            SecurityCheckResult(
                false,
                RiskLevel.UNKNOWN,
                str(R.string.security_check_failed, "Security check failed"),
                str(R.string.security_check_failed_detail, "Could not complete the security check: %1\$s", e.message ?: "")
            )
        }
    }

    /**
     * 提取域名
     */
    private fun extractDomain(url: String): String? {
        return try {
            val urlObj = URL(url)
            urlObj.host?.lowercase()
        } catch (e: Exception) {
            // 如果 URL 解析失败，尝试简单提取
            val cleaned = url.replace(Regex("^https?://"), "")
                .replace(Regex("^www\\."), "")
            cleaned.split("/").firstOrNull()?.lowercase()
        }
    }

    /**
     * 检查域名是否在黑名单中
     */
    private fun isBlacklisted(domain: String): Boolean {
        return blacklist.domains.any {
            domain == it || domain.endsWith(".$it")
        }
    }

    /**
     * 检查 URL 可疑特征
     */
    private fun checkSuspiciousFeatures(url: String, domain: String): List<String> {
        val features = mutableListOf<String>()
        
        // 检查 IP 地址（而非域名）
        if (domain.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            features.add(str(R.string.security_feature_ip, "Uses an IP address instead of a domain name"))
        }
        
        // 检查 URL 长度
        if (url.length > 200) {
            features.add(str(R.string.security_feature_long_url, "URL is very long and may hide its real destination"))
        }
        
        // 检查可疑字符
        if (url.contains("@")) {
            features.add(str(R.string.security_feature_at, "Contains an @ symbol, which can be used to deceive"))
        }
        
        // 检查多重域名
        val domainCount = url.split("/").count { it.contains(".") }
        if (domainCount > 2) {
            features.add(str(R.string.security_feature_complex_url, "URL structure is complex and may involve redirects"))
        }
        
        // 检查可疑关键词组合
        val lowercaseUrl = url.lowercase()
        val keywordCount = blacklist.suspiciousKeywords.count { lowercaseUrl.contains(it) }
        if (keywordCount >= 3) {
            features.add(str(R.string.security_feature_keywords, "Contains multiple sensitive keywords; possible phishing"))
        }

        // 检查短链接服务
        if (blacklist.shortUrlServices.any { domain.contains(it) }) {
            features.add(str(R.string.security_feature_short_url, "Shortened URL service; destination cannot be inspected directly"))
        }
        
        return features
    }

    /**
     * 获取安全提示文本
     */
    fun getSecurityTip(result: SecurityCheckResult): String {
        return when (result.riskLevel) {
            RiskLevel.SAFE -> str(R.string.security_tip_safe, "This link looks safe, but stay cautious")
            RiskLevel.LOW -> str(R.string.security_tip_low, "Confirm the source before visiting this link")
            RiskLevel.MEDIUM -> str(R.string.security_tip_medium, "This link has suspicious features; be careful")
            RiskLevel.HIGH -> str(R.string.security_tip_high, "Strongly recommend NOT visiting this link!")
            RiskLevel.UNKNOWN -> str(R.string.security_tip_unknown, "Cannot determine safety; handle with care")
        }
    }

    /**
     * 获取风险颜色
     */
    fun getRiskColor(riskLevel: RiskLevel): Int {
        return try {
            when (riskLevel) {
                RiskLevel.SAFE -> Color.parseColor("#4CAF50")
                RiskLevel.LOW -> Color.parseColor("#FFC107")
                RiskLevel.MEDIUM -> Color.parseColor("#FF9800")
                RiskLevel.HIGH -> Color.parseColor("#F44336")
                RiskLevel.UNKNOWN -> Color.parseColor("#9E9E9E")
            }
        } catch (e: Exception) {
            // 在单元测试环境中 Color.parseColor 可能不可用
            // 返回默认颜色值
            when (riskLevel) {
                RiskLevel.SAFE -> 0xFF4CAF50.toInt()
                RiskLevel.LOW -> 0xFFFFC107.toInt()
                RiskLevel.MEDIUM -> 0xFFFF9800.toInt()
                RiskLevel.HIGH -> 0xFFF44336.toInt()
                RiskLevel.UNKNOWN -> 0xFF9E9E9E.toInt()
            }
        }
    }
}
