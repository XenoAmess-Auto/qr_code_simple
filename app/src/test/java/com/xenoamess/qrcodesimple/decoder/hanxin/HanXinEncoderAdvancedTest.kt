package com.xenoamess.qrcodesimple.decoder.hanxin

import android.graphics.Color
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Advanced Han Xin encoder tests covering ECC levels, masks, ECI, version
 * boundaries, invalid input, four-byte GB18030 and text submode switching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinEncoderAdvancedTest {

    @Before
    fun setup() {
        // Obtain a context in case future tests need it; currently unused but
        // kept for consistency with other Han Xin test suites.
        ApplicationProvider.getApplicationContext<android.app.Application>()
    }

    @Test
    fun encodeWithExplicitEccLevels_roundtrips() {
        val content = "HX"
        for (ecc in 1..4) {
            val result = HanXinEncoder.encode(
                content,
                requestedEccLevel = ecc,
                requestedVersion = 1
            )
            assertNotNull(result, "Should encode with ECC level $ecc")
            assertEquals(ecc, result.eccLevel, "ECC level should match request")

            val decoded = HanXinDecoder.decode(result.bitmap)
            assertNotNull(decoded, "Should decode with ECC level $ecc")
            assertEquals(content, decoded.text)
        }
    }

    @Test
    fun encodeWithAllMasks_decodes() {
        val content = "Hello"
        for (mask in 0..3) {
            val result = HanXinEncoder.encode(
                content,
                requestedMask = mask,
                requestedVersion = 1
            )
            assertNotNull(result, "Should encode with mask $mask")
            assertEquals(mask, result.mask, "Mask should match request")

            val decoded = HanXinDecoder.decode(result.bitmap)
            assertNotNull(decoded, "Should decode mask $mask")
            assertEquals(content, decoded.text)
        }
    }

    @Test
    fun encodeUtf8Content_withEci_roundtrips() {
        // Emoji are not representable in GB18030, forcing UTF-8 ECI 26.
        val content = "こんにちは😀"
        val result = HanXinEncoder.encode(content)
        assertNotNull(result)

        val decoded = HanXinDecoder.decode(result.bitmap)
        assertNotNull(decoded)
        assertEquals(content, decoded.text)
    }

    @Test
    fun versionBoundary_fitsInVersion1() {
        val result = HanXinEncoder.encode(
            "A".repeat(20),
            requestedEccLevel = 1,
            requestedVersion = 1
        )
        assertNotNull(result)
        assertEquals(1, result.version)
    }

    @Test
    fun versionBoundary_overflowsToVersion2() {
        val result = HanXinEncoder.encode(
            "A".repeat(30),
            requestedEccLevel = 1
        )
        assertNotNull(result)
        assertTrue(result.version >= 2, "Expected version >= 2 but was ${result.version}")
    }

    @Test
    fun rejectContentExceedingMaxCapacity() {
        val result = HanXinEncoder.encode("A".repeat(5000))
        assertNull(result, "Content far beyond version-84 capacity should fail")
    }

    @Test
    fun encodeFourByteGb18030Content_roundtrips() {
        // U+20000 is in CJK Extension B and encoded as 4 bytes in GB18030.
        val content = "𠀀"
        val result = HanXinEncoder.encode(content)
        assertNotNull(result)

        val decoded = HanXinDecoder.decode(result.bitmap)
        assertNotNull(decoded)
        assertEquals(content, decoded.text)
    }

    @Test
    fun encodeTextSubmodeSwitchingContent_roundtrips() {
        // Lowercase letters and punctuation force switches between Text submodes.
        val content = "a!b?c.d"
        val result = HanXinEncoder.encode(content)
        assertNotNull(result)

        val decoded = HanXinDecoder.decode(result.bitmap)
        assertNotNull(decoded)
        assertEquals(content, decoded.text)
    }

    @Test
    fun encodeEmptyContent_returnsNull() {
        val result = HanXinEncoder.encode("")
        assertNull(result, "Empty content should not produce a Han Xin symbol")
    }
}
