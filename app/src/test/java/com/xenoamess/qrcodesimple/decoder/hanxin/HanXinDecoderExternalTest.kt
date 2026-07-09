package com.xenoamess.qrcodesimple.decoder.hanxin

import android.graphics.BitmapFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Decode real-world Han Xin Code images from `app/src/test/resources/hanxin/`.
 *
 * Each image must be listed in `expected-results.txt` as:
 *     filename.png=expected text
 *
 * If the expected text is the literal string `FAIL`, the test asserts that the
 * decoder returns null for that image (used for samples that are not valid Han
 * Xin symbols).
 *
 * The test fails if the resource directory, the expectations file, or the
 * expectations list is missing, so that adding a sample without updating the
 * expectations file is caught immediately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinDecoderExternalTest {

    @Test
    fun decodeExternalImages() {
        val loader = javaClass.classLoader ?: fail("ClassLoader not available")
        val dirUrl = loader.getResource("hanxin")
            ?: fail("hanxin/ resource directory not found; add it under app/src/test/resources")
        val dir = File(dirUrl.toURI())
        val expectationsFile = File(dir, "expected-results.txt")
        if (!expectationsFile.exists()) {
            fail("expected-results.txt not found in hanxin/; create it to list sample images and expected texts")
        }

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

        if (entries.isEmpty()) {
            fail("expected-results.txt exists but contains no valid entries")
        }

        var checked = 0
        for ((fileName, expectedText) in entries) {
            val resourceUrl = loader.getResource("hanxin/$fileName")
                ?: throw IllegalStateException("Resource hanxin/$fileName not found")
            val bytes = File(resourceUrl.toURI()).readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(bitmap, "Failed to decode bitmap: $fileName")

            val result = HanXinDecoder.decode(bitmap)
            if (expectedText == "FAIL") {
                assertNull(result, "Decoder should fail for $fileName")
            } else {
                assertNotNull(result, "Decoder should recover $fileName")
                assertEquals(expectedText, result.text, "Decoded text mismatch for $fileName")
            }
            checked++
        }

        println("Verified $checked external Han Xin image(s)")
    }
}
