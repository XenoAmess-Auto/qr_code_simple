package com.xenoamess.qrcodesimple

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.Executors

/**
 * WebDavClient 端到端：用 JDK 内置 HttpServer 模拟 WebDAV 服务端。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class WebDavClientTest {

    private lateinit var server: HttpServer
    private var port = 0

    @Volatile
    private var lastAuthHeader: String? = null

    @Volatile
    private var lastPutBody: ByteArray? = null

    @Volatile
    private var getResponse: ByteArray? = null

    @Volatile
    private var requireAuth = false

    @Volatile
    private var respond404 = false

    @Before
    fun setup() {
        lastAuthHeader = null
        lastPutBody = null
        getResponse = null
        requireAuth = false
        respond404 = false

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newSingleThreadExecutor()
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
        port = server.address.port
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        lastAuthHeader = exchange.requestHeaders.getFirst("Authorization")
        if (requireAuth && lastAuthHeader == null) {
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
            return
        }
        when (exchange.requestMethod) {
            "PUT" -> {
                lastPutBody = exchange.requestBody.readBytes()
                exchange.sendResponseHeaders(201, -1)
            }
            "GET" -> {
                if (respond404) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    val body = getResponse ?: "hello-webdav".toByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
            }
            else -> exchange.sendResponseHeaders(405, -1)
        }
        exchange.close()
    }

    private fun url() = "http://127.0.0.1:$port/backup.qrbk"

    @Test
    fun `upload puts bytes and returns success`() {
        val data = "payload-123".toByteArray()
        val result = WebDavClient.upload(url(), "", CharArray(0), data)

        assertEquals(WebDavClient.Result.SUCCESS, result)
        assertArrayEquals(data, lastPutBody)
        assertNull(lastAuthHeader)
    }

    @Test
    fun `upload sends basic auth when username given`() {
        val result = WebDavClient.upload(url(), "alice", "secret".toCharArray(), byteArrayOf(1))

        assertEquals(WebDavClient.Result.SUCCESS, result)
        val expected = "Basic " + Base64.getEncoder().encodeToString("alice:secret".toByteArray())
        assertEquals(expected, lastAuthHeader)
    }

    @Test
    fun `download returns body on 200`() {
        getResponse = "remote-bytes".toByteArray()
        val (result, data) = WebDavClient.download(url(), "", CharArray(0))

        assertEquals(WebDavClient.Result.SUCCESS, result)
        assertArrayEquals("remote-bytes".toByteArray(), data)
    }

    @Test
    fun `download maps 404 to NOT_FOUND`() {
        respond404 = true
        val (result, data) = WebDavClient.download(url(), "", CharArray(0))

        assertEquals(WebDavClient.Result.NOT_FOUND, result)
        assertNull(data)
    }

    @Test
    fun `auth failure maps to AUTH_FAILED`() {
        requireAuth = true
        val (result, _) = WebDavClient.download(url(), "", CharArray(0))

        assertEquals(WebDavClient.Result.AUTH_FAILED, result)
    }

    @Test
    fun `unreachable host maps to NETWORK_ERROR`() {
        server.stop(0)
        val result = WebDavClient.upload(url(), "", CharArray(0), byteArrayOf(1))
        assertEquals(WebDavClient.Result.NETWORK_ERROR, result)
    }

    @Test
    fun `sync manager roundtrips config`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        WebDavSyncManager.saveConfig(context, "https://dav.example.com/dav/", "bob", "pw123".toCharArray())

        val config = WebDavSyncManager.loadConfig(context)
        assertEquals("https://dav.example.com/dav/", config?.url)
        assertEquals("bob", config?.username)
        assertEquals("pw123", config?.password?.concatToString())
        assertEquals("https://dav.example.com/dav/qr-code-simple-backup.qrbk", config?.remoteFileUrl())
        assertTrue(WebDavSyncManager.hasConfig(context))

        WebDavSyncManager.clearConfig(context)
        assertNull(WebDavSyncManager.loadConfig(context))
    }
}
