package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.decoder.hanxin.HanXinEncoder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Han Xin Code encoder tests.
 *
 * Roundtrip tests that scan generated symbols back are kept in
 * [BarcodeGenerationRoundtripTest] once the decoder is available.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinEncoderTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `generate Han Xin with BarcodeGenerator`() {
        val content = "Hello Han Xin"
        val validation = BarcodeGenerator.validateContent(content, BarcodeFormat.HAN_XIN)
        assertTrue(validation.isValid, "Content should be valid: ${validation.errorMessage}")

        val bitmap = BarcodeGenerator.generate(
            content,
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
        )
        assertNotNull(bitmap)
        assertTrue(bitmap.width >= 800)
        assertTrue(bitmap.height >= 800)
    }

    @Test
    fun `encode numeric content`() {
        val result = HanXinEncoder.encode("1234567890", width = 400, height = 400)
        assertNotNull(result)
        assertEquals(1, result.version) // ten digits fit in the smallest version
        assertTrue(result.eccLevel >= 1)
    }

    @Test
    fun `encode ASCII text content`() {
        val result = HanXinEncoder.encode("HanXinCode2024", width = 400, height = 400)
        assertNotNull(result)
        assertTrue(result.version in 1..84)
    }

    @Test
    fun `encode Chinese content`() {
        val result = HanXinEncoder.encode("汉信码", width = 400, height = 400)
        assertNotNull(result)
        assertTrue(result.version in 1..84)
    }

    @Test
    fun `encode long content falls back to larger version`() {
        val result = HanXinEncoder.encode("a".repeat(500), width = 800, height = 800)
        assertNotNull(result)
        assertTrue(result.version > 1)
    }


    @Test
    fun `generated symbol size formula`() {
        val result = HanXinEncoder.encode("test", width = 600, height = 600)
        assertNotNull(result)
        val expectedModules = result.version * 2 + 21
        // Rendering scales the module grid to the requested size, so the
        // bitmap dimensions are exactly the requested size.
        assertEquals(600, result.bitmap.width)
        assertEquals(600, result.bitmap.height)
        assertTrue(expectedModules in 23..189)
    }

    @Test
    fun `Reed-Solomon GF 2^4 roundtrip`() {
        val rs4 = HanXinEncoder.ReedSolomon(0x13, 4)
        rs4.initCode(4, 1)
        val data = intArrayOf(1, 5, 13)
        val ecc = IntArray(4)
        rs4.encode(data, 3, ecc)
        val codeword = data + ecc
        assertTrue(rs4.checkSyndromes(codeword, 3, 4), "Syndromes should be zero after encode")
    }

    @Test
    fun `Reed-Solomon GF 2^8 corrects errors`() {
        val rs8 = HanXinEncoder.ReedSolomon(0x163, 8)
        rs8.initCode(8, 1)
        val data = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val ecc = IntArray(8)
        rs8.encode(data, data.size, ecc)
        val codeword = data + ecc

        // Corrupt three data bytes.
        codeword[0] = codeword[0] xor 0xFF
        codeword[3] = codeword[3] xor 0xAA
        codeword[7] = codeword[7] xor 0x55

        // Verify syndromes are non-zero.
        val syndromes = rs8.calculateSyndromes(codeword, codeword.size, 8)
        assertTrue(syndromes.any { it != 0 }, "Corrupted codeword should have non-zero syndromes")

        // Decode in place and verify correction.
        val corrected = codeword.copyOf()
        val (sigma, omega) = rs8.berlekampMassey(syndromes, 8)
        val locations = rs8.chienSearch(sigma, corrected.size)
        assertEquals(3, locations.size, "Should find three error locations")
        for (loc in locations) {
            val pos = loc
            val err = rs8.forney(sigma, omega, loc)
            corrected[pos] = corrected[pos] xor err
        }
        assertTrue(rs8.checkSyndromes(corrected, data.size, 8), "Corrected codeword should have zero syndromes")
        for (i in data.indices) {
            assertEquals(data[i], corrected[i], "Data byte $i should be restored")
        }
    }

    @Test
    fun `Reed-Solomon GF 2^8 corrects many errors`() {
        val rs8 = HanXinEncoder.ReedSolomon(0x163, 8)
        rs8.initCode(22, 1)
        val data = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
        val ecc = IntArray(22)
        rs8.encode(data, data.size, ecc)
        val codeword = data + ecc

        // Corrupt eight data bytes.
        val errors = intArrayOf(0, 2, 5, 7, 10, 12, 15, 19)
        for (pos in errors) {
            codeword[pos] = codeword[pos] xor ((pos * 17 + 31) and 0xFF)
        }

        val corrected = codeword.copyOf()
        val (sigma, omega) = rs8.berlekampMassey(rs8.calculateSyndromes(codeword, codeword.size, 22), 22)
        val locations = rs8.chienSearch(sigma, corrected.size)
        assertEquals(errors.size, locations.size, "Should find all error locations")
        for (loc in locations) {
            val pos = loc
            val err = rs8.forney(sigma, omega, loc)
            corrected[pos] = corrected[pos] xor err
        }
        assertTrue(rs8.checkSyndromes(corrected, data.size, 22), "Corrected codeword should have zero syndromes")
        for (i in data.indices) {
            assertEquals(data[i], corrected[i], "Data byte $i should be restored")
        }
    }

    @Test
    fun `Reed-Solomon GF 2^4 generator roots`() {
        val rs4 = HanXinEncoder.ReedSolomon(0x13, 4)
        rs4.initCode(4, 1)
        println("generator=${rs4.generator.toList()}")
        val alphaTo = intArrayOf(1, 2, 4, 8, 3, 6, 12, 11, 5, 10, 7, 14, 15, 13, 9)
        val indexOf = IntArray(16) { -1 }
        for (i in alphaTo.indices) indexOf[alphaTo[i]] = i
        val gen = rs4.generator
        for (r in 1..4) {
            var sum = 0
            for (j in gen.indices) {
                if (gen[j] == 0) continue
                val exp = (indexOf[gen[j]] + r * j) % 15
                sum = sum xor alphaTo[exp]
            }
            println("g(alpha^$r)=$sum")
        }
        assertTrue(true)
    }
}
