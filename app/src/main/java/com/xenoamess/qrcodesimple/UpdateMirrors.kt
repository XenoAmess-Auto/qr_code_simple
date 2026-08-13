package com.xenoamess.qrcodesimple

import java.net.URI

/**
 * GitHub 下载加速：把 release/更新相关 URL 展开为「公共镜像前缀 + 原 URL」候选列表。
 *
 * 完整性不依赖镜像可信：下载产物必须经过既有的精确大小 + SHA-256 + 签名校验，
 * 镜像篡改的包校验不过直接弃用；镜像只影响可用性，不影响安全性。
 *
 * 仅 github.com / objects.githubusercontent.com / raw.githubusercontent.com 的
 * https URL 参与镜像展开；api.github.com、github.io（Beta Pages）、http 与本地
 * 地址（单测 HttpServer）一律直连。
 */
object UpdateMirrors {

    // 公共 GitHub 加速镜像（按优先级），失效时改这里即可
    val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/"
    )

    private val PROXYABLE_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "raw.githubusercontent.com"
    )

    /** 下载候选：[镜像1+url, 镜像2+url, 原url]；不可代理的 URL 原样返回单元素列表。 */
    fun candidates(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return listOf(url)
        if (uri.scheme != "https" || uri.host !in PROXYABLE_HOSTS) return listOf(url)
        return MIRROR_PREFIXES.map { it + url } + url
    }
}
