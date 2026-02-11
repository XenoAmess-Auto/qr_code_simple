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
    
    // 本地黑名单 - 常见恶意域名
    private val BLACKLISTED_DOMAINS = setOf(
        "phishing.com", "malware.net", "virus.org",
        "suspicious.site", "dangerous.link"
    )
    
    // 本地可疑关键词
    private val SUSPICIOUS_KEYWORDS = listOf(
        "login", "verify", "account", "password", "credential",
        "security", "update", "confirm", "authenticate"
    )

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
                "无法解析域名"
            )

            // 1. 检查本地黑名单
            if (isBlacklisted(domain)) {
                return SecurityCheckResult(
                    false,
                    RiskLevel.HIGH,
                    "危险链接",
                    "该域名在黑名单中，可能存在安全风险"
                )
            }

            // 2. 检查 URL 可疑特征
            val suspiciousFeatures = checkSuspiciousFeatures(url, domain)
            if (suspiciousFeatures.isNotEmpty()) {
                return SecurityCheckResult(
                    false,
                    RiskLevel.MEDIUM,
                    "可疑链接",
                    suspiciousFeatures.joinToString("\n")
                )
            }

            // 3. 检查 HTTPS
            if (!url.startsWith("https://", ignoreCase = true)) {
                return SecurityCheckResult(
                    false,
                    RiskLevel.LOW,
                    "非安全链接",
                    "该链接未使用 HTTPS 加密传输"
                )
            }

            SecurityCheckResult(
                true,
                RiskLevel.SAFE,
                "链接安全",
                "未发现明显安全风险"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URL", e)
            SecurityCheckResult(
                false,
                RiskLevel.UNKNOWN,
                "检测失败",
                "无法完成安全检测: ${e.message}"
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
        return BLACKLISTED_DOMAINS.any { 
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
            features.add("使用 IP 地址而非域名")
        }
        
        // 检查 URL 长度
        if (url.length > 200) {
            features.add("URL 过长，可能隐藏真实地址")
        }
        
        // 检查可疑字符
        if (url.contains("@")) {
            features.add("包含 @ 符号，可能用于欺骗")
        }
        
        // 检查多重域名
        val domainCount = url.split("/").count { it.contains(".") }
        if (domainCount > 2) {
            features.add("URL 结构复杂，可能存在重定向")
        }
        
        // 检查可疑关键词组合
        val lowercaseUrl = url.lowercase()
        val keywordCount = SUSPICIOUS_KEYWORDS.count { lowercaseUrl.contains(it) }
        if (keywordCount >= 3) {
            features.add("包含多个敏感关键词，可能是钓鱼链接")
        }
        
        // 检查短链接服务
        val shortUrlServices = listOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly")
        if (shortUrlServices.any { domain.contains(it) }) {
            features.add("短链接服务，无法直接查看目标地址")
        }
        
        return features
    }

    /**
     * 获取安全提示文本
     */
    fun getSecurityTip(result: SecurityCheckResult): String {
        return when (result.riskLevel) {
            RiskLevel.SAFE -> "该链接看起来安全，但仍建议谨慎访问"
            RiskLevel.LOW -> "建议确认链接来源后再访问"
            RiskLevel.MEDIUM -> "该链接存在可疑特征，请谨慎访问"
            RiskLevel.HIGH -> "强烈建议不要访问此链接！"
            RiskLevel.UNKNOWN -> "无法判断安全性，请谨慎处理"
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
