package com.xenoamess.qrcodesimple

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdateMirrorsTest {

    @Test
    fun `github release asset expands to mirrors then direct`() {
        val url = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v1.0.0/app.apk"
        assertEquals(
            listOf(
                "https://ghfast.top/$url",
                "https://gh-proxy.com/$url",
                url
            ),
            UpdateMirrors.candidates(url)
        )
    }

    @Test
    fun `objects and raw hosts are proxyable`() {
        val objects = "https://objects.githubusercontent.com/some/asset"
        assertEquals(3, UpdateMirrors.candidates(objects).size)

        val raw = "https://raw.githubusercontent.com/XenoAmess-Auto/qr_code_simple/master/x.json"
        assertEquals(3, UpdateMirrors.candidates(raw).size)
    }

    @Test
    fun `api github io and http stay direct`() {
        listOf(
            "https://api.github.com/repos/x/y/releases/latest",
            "https://xenoamess-auto.github.io/qr_code_simple/beta/version.json",
            "http://127.0.0.1:8080/version.json",
            "http://github.com/insecure"
        ).forEach { url ->
            assertEquals(listOf(url), UpdateMirrors.candidates(url))
        }
    }

    @Test
    fun `invalid url returns itself`() {
        assertEquals(listOf("not a url"), UpdateMirrors.candidates("not a url"))
    }
}
