package com.xenoamess.qrcodesimple.decoder.hanxin

import android.graphics.BitmapFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Decode real-world Han Xin Code images from `app/src/test/resources/hanxin/`.
 *
 * Each image must be listed in `expected-results.txt` as:
 *     filename.png=expected text
 *
 * If no expectations are configured, the suite passes vacuously so that the
 * build does not break when the directory only contains the README.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinDecoderExternalTest {

    @Test
    fun decodeExternalImages() {
        val loader = javaClass.classLoader ?: return
        val dirUrl = loader.getResource("hanxin") ?: return
        val dir = File(dirUrl.toURI())
        val expectationsFile = File(dir, "expected-results.txt")
        if (!expectationsFile.exists()) return

        val entries = expectationsFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val file = line.substring(0, separator).trim()
                val text = line.substring(separator + 1).trim()
                if (file.isEmpty() || text.isEmpty()) return@mapNotNull null
                file to text
            }

        if (entries.isEmpty()) return

        var checked = 0
        for ((fileName, expectedText) in entries) {
            val resourceUrl = loader.getResource("hanxin/$fileName")
                ?: throw IllegalStateException("Resource hanxin/$fileName not found")
            val bytes = File(resourceUrl.toURI()).readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(bitmap, "Failed to decode bitmap: $fileName")

            val result = HanXinDecoder.decode(bitmap)
            assertNotNull(result, "Decoder should recover $fileName")
            assertEquals(expectedText, result.text, "Decoded text mismatch for $fileName")
            checked++
        }

        println("Verified $checked external Han Xin image(s)")
    }
}
