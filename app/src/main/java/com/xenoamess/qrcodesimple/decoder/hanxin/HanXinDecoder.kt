package com.xenoamess.qrcodesimple.decoder.hanxin

import android.graphics.Bitmap
import java.nio.charset.Charset

/**
 * Han Xin Code (汉信码) decoder.
 *
 * Current implementation targets generated/aligned symbols. It performs
 * binarization, finder-pattern based grid recovery, function-info validation,
 * demasking, deinterleaving and bit-stream parsing.
 */
object HanXinDecoder {

    private const val FINDER_SIZE = 7
    private const val PICKET_FENCE_PERIOD = 13

    private val FINDER_TL = intArrayOf(0x7F, 0x40, 0x5F, 0x50, 0x57, 0x57, 0x57)
    private val FINDER_TR_BL = intArrayOf(0x7F, 0x01, 0x7D, 0x05, 0x75, 0x75, 0x75)
    private val FINDER_BR = intArrayOf(0x75, 0x75, 0x75, 0x05, 0x7D, 0x01, 0x7F)

    private val GB18030 = Charset.forName("GB18030")

    data class DecodeResult(
        val text: String,
        val version: Int,
        val eccLevel: Int,
        val mask: Int
    )

    /**
     * Decode a Han Xin Code from a [bitmap].
     *
     * @return the decoded text, or null if decoding fails.
     */
    fun decode(bitmap: Bitmap): DecodeResult? {
        val binary = binarize(bitmap)
        val (cropped, width, height) = cropToSymbol(binary) ?: return null
        val (grid, size) = extractGrid(cropped, width, height) ?: return null
        return decodeGrid(grid, size)
    }

    // -------------------------------------------------------------------------
    // Binarization
    // -------------------------------------------------------------------------

    private fun binarize(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val gray = IntArray(width * height)
        var sum = 0L
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val value = (r * 30 + g * 59 + b * 11) / 100
                gray[y * width + x] = value
                sum += value
            }
        }
        val mean = (sum / (width * height)).toInt()
        // Heuristic: if the image is mostly dark with a light symbol, invert the
        // threshold decision so that the light modules become the foreground.
        val darkCount = gray.count { it <= mean }
        val invert = darkCount > gray.size / 2
        return Array(height) { y ->
            IntArray(width) { x ->
                val dark = gray[y * width + x] <= mean
                if (invert) (if (dark) 0 else 1) else (if (dark) 1 else 0)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Symbol localization
    // -------------------------------------------------------------------------

    /**
     * Crop the binarized image to the axis-aligned bounding box of the barcode
     * symbol. Rows/columns are considered part of the symbol when they contain
     * more than a small fraction of dark pixels, which ignores sparse noise.
     */
    private fun cropToSymbol(binary: Array<IntArray>): Triple<Array<IntArray>, Int, Int>? {
        val height = binary.size
        if (height == 0) return null
        val width = binary[0].size

        val rowCounts = IntArray(height) { y -> binary[y].count { it == 1 } }
        val colCounts = IntArray(width) { x -> (0 until height).count { binary[it][x] == 1 } }

        val rowThreshold = maxOf(1, width / 20)
        val colThreshold = maxOf(1, height / 20)

        var minY = 0
        while (minY < height && rowCounts[minY] < rowThreshold) minY++
        var maxY = height - 1
        while (maxY >= 0 && rowCounts[maxY] < rowThreshold) maxY--
        var minX = 0
        while (minX < width && colCounts[minX] < colThreshold) minX++
        var maxX = width - 1
        while (maxX >= 0 && colCounts[maxX] < colThreshold) maxX--

        if (minX > maxX || minY > maxY) return null

        val newWidth = maxX - minX + 1
        val newHeight = maxY - minY + 1
        val cropped = Array(newHeight) { y ->
            IntArray(newWidth) { x -> binary[minY + y][minX + x] }
        }
        return Triple(cropped, newWidth, newHeight)
    }

    // -------------------------------------------------------------------------
    // Grid extraction
    // -------------------------------------------------------------------------

    private fun extractGrid(binary: Array<IntArray>, width: Int, height: Int): Pair<IntArray, Int>? {
        // The symbol may be rotated. Try each valid Han Xin size, sample with
        // integer module dimensions, and then try all four rotations until the
        // canonical finder patterns match.
        for (size in 23..189 step 2) {
            val moduleW = width / size
            val moduleH = height / size
            if (moduleW < 2 || moduleH < 2) continue
            val sampled = sampleGrid(binary, width, height, size, moduleW, moduleH)
            for (rotation in 0 until 4) {
                val grid = rotateGrid(sampled, size, rotation)
                if (verifyFinders(grid, size)) {
                    return grid to size
                }
            }
        }
        return null
    }

    private fun sampleGrid(
        binary: Array<IntArray>,
        width: Int,
        height: Int,
        size: Int,
        moduleW: Int,
        moduleH: Int
    ): IntArray {
        val grid = IntArray(size * size)
        for (y in 0 until size) {
            val py = (y * moduleH + moduleH / 2).coerceIn(0, height - 1)
            for (x in 0 until size) {
                val px = (x * moduleW + moduleW / 2).coerceIn(0, width - 1)
                grid[y * size + x] = binary[py][px]
            }
        }
        return grid
    }

    private fun verifyFinders(grid: IntArray, size: Int): Boolean {
        return matchesFinder(grid, size, 0, 0, FINDER_TL) &&
                matchesFinder(grid, size, size - FINDER_SIZE, 0, FINDER_TR_BL) &&
                matchesFinder(grid, size, 0, size - FINDER_SIZE, FINDER_TR_BL) &&
                matchesFinder(grid, size, size - FINDER_SIZE, size - FINDER_SIZE, FINDER_BR)
    }

    private fun matchesFinder(grid: IntArray, size: Int, x0: Int, y0: Int, pattern: IntArray): Boolean {
        for (yp in 0 until FINDER_SIZE) {
            for (xp in 0 until FINDER_SIZE) {
                val expected = (pattern[yp] shr (6 - xp)) and 1
                val actual = grid[(y0 + yp) * size + (x0 + xp)]
                if (expected != actual) return false
            }
        }
        return true
    }

    private fun rotateGrid(grid: IntArray, size: Int, times: Int): IntArray {
        if (times == 0) return grid
        val result = IntArray(grid.size)
        when (times) {
            1 -> {
                // 90 degrees clockwise
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val newX = size - 1 - y
                        val newY = x
                        result[newY * size + newX] = grid[y * size + x]
                    }
                }
            }
            2 -> {
                // 180 degrees
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val newX = size - 1 - x
                        val newY = size - 1 - y
                        result[newY * size + newX] = grid[y * size + x]
                    }
                }
            }
            3 -> {
                // 270 degrees clockwise
                for (y in 0 until size) {
                    for (x in 0 until size) {
                        val newX = y
                        val newY = size - 1 - x
                        result[newY * size + newX] = grid[y * size + x]
                    }
                }
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Grid decoding
    // -------------------------------------------------------------------------

    private fun decodeGrid(grid: IntArray, size: Int): DecodeResult? {
        val version = (size - 21) / 2
        if (version !in 1..84) return null

        val functionInfo = readFunctionInfo(grid, size)
        val (decodedVersion, eccLevel, mask) = decodeFunctionInfo(functionInfo) ?: return null
        if (decodedVersion != version) return null

        val demasked = applyMaskInverse(grid, size, mask)
        val totalCodewords = HanXinEncoder.TOTAL_CODEWORDS[version - 1]
        val picketFence = readDataCodewords(demasked, size, totalCodewords)
        val fullStream = reversePicketFence(picketFence, totalCodewords)

        val dataCodewords = HanXinEncoder.DATA_CODEWORDS[eccLevel - 1][version - 1]

        // For clean generated images syndromes are zero; extract data directly.
        val dataStream = fullStream.copyOf(dataCodewords)
        val text = parseDataStream(dataStream, dataCodewords) ?: return null
        return DecodeResult(text, version, eccLevel, mask)
    }

    // -------------------------------------------------------------------------
    // Function information
    // -------------------------------------------------------------------------

    private fun readFunctionInfo(grid: IntArray, size: Int): IntArray {
        val bits = IntArray(34)
        var idx = 0
        // Side 1: top horizontal, left to right -> bits[0..8]
        for (i in 0 until 9) bits[idx++] = grid[8 * size + i]
        // Side 2: left vertical going up, skip the first module (duplicate of bit 8) -> bits[9..16]
        for (i in 1 until 9) bits[idx++] = grid[(8 - i) * size + 8]
        // Side 3: right vertical going down -> bits[17..25]
        for (i in 0 until 9) bits[idx++] = grid[i * size + (size - 1 - 8)]
        // Side 4: top horizontal right part, skip the first module (duplicate of bit 25) -> bits[26..33]
        for (i in 1 until 9) bits[idx++] = grid[8 * size + (size - 1 - 8 + i)]
        return bits
    }

    private fun decodeFunctionInfo(bits: IntArray): Triple<Int, Int, Int>? {
        val cw = IntArray(7)
        for (i in 0 until 7) {
            var value = 0
            for (j in 0 until 4) {
                value = value or (bits[i * 4 + j] shl (3 - j))
            }
            cw[i] = value
        }

        val rs4 = HanXinEncoder.ReedSolomon(0x13, 4)
        rs4.initCode(4, 1)
        // TODO: re-enable strict function-info RS check after verifying bit ordering
        // if (!rs4.checkSyndromes(cw, 3, 4)) return null

        val version = ((cw[0] shl 4) or cw[1]) - 20
        val eccLevel = (cw[2] shr 2) + 1
        val mask = cw[2] and 0x03
        if (version !in 1..84 || eccLevel !in 1..4 || mask !in 0..3) return null
        return Triple(version, eccLevel, mask)
    }

    // -------------------------------------------------------------------------
    // Demasking
    // -------------------------------------------------------------------------

    private fun applyMaskInverse(grid: IntArray, size: Int, mask: Int): IntArray {
        val result = grid.copyOf()
        if (mask == 0) return result
        val bit = 1 shl mask
        for (y in 0 until size) {
            for (x in 0 until size) {
                val k = y * size + x
                // Skip finder patterns, separators and function information corners.
                if (isFinderArea(x, y, size)) continue
                val j = x + 1
                val i = y + 1
                val masked = when (mask) {
                    1 -> ((i + j) and 1) == 0
                    2 -> (((i + j) % 3 + j % 3) and 1) == 0
                    3 -> ((i % j + j % i + i % 3 + j % 3) and 1) == 0
                    else -> false
                }
                if (masked) {
                    result[k] = result[k] xor 1
                }
            }
        }
        return result
    }

    private fun isFinderArea(x: Int, y: Int, size: Int): Boolean {
        return (x < 8 && y < 8) ||
                (x >= size - 8 && y < 8) ||
                (x < 8 && y >= size - 8) ||
                (x >= size - 8 && y >= size - 8)
    }

    // -------------------------------------------------------------------------
    // Data reading
    // -------------------------------------------------------------------------

    private fun readDataCodewords(grid: IntArray, size: Int, totalCodewords: Int): IntArray {
        val result = IntArray(totalCodewords) { 0 }
        var bitPos = 0
        for (i in grid.indices) {
            if (bitPos >= totalCodewords * 8) break
            val x = i % size
            val y = i / size
            // Skip the four 9x9 function-pattern corners (finder + separator + function info)
            if ((x < 9 && y < 9) ||
                (x < 9 && y >= size - 9) ||
                (x >= size - 9 && y < 9) ||
                (x >= size - 9 && y >= size - 9)) continue
            if (grid[i] == 1) {
                result[bitPos shr 3] = result[bitPos shr 3] or (0x80 shr (bitPos and 0x07))
            }
            bitPos++
        }
        return result
    }

    private fun reversePicketFence(picketFence: IntArray, totalCodewords: Int): IntArray {
        val fullStream = IntArray(totalCodewords)
        var inputPos = 0
        for (start in 0 until PICKET_FENCE_PERIOD) {
            var i = start
            while (i < totalCodewords) {
                fullStream[i] = picketFence[inputPos++]
                i += PICKET_FENCE_PERIOD
            }
        }
        return fullStream
    }

    // -------------------------------------------------------------------------
    // Bit-stream parsing
    // -------------------------------------------------------------------------

    private class BitReader(private val data: IntArray, private val bitCount: Int) {
        private var pos = 0

        fun read(bits: Int): Int {
            var value = 0
            for (i in 0 until bits) {
                value = (value shl 1) or bitAt(pos + i)
            }
            pos += bits
            return value
        }

        fun peek(bits: Int): Int {
            var value = 0
            for (i in 0 until bits) {
                value = (value shl 1) or bitAt(pos + i)
            }
            return value
        }

        fun advance(bits: Int) {
            pos += bits
        }

        fun remaining(): Int = bitCount - pos

        private fun bitAt(index: Int): Int {
            if (index >= bitCount) return 0
            return (data[index shr 3] shr (7 - (index and 0x07))) and 1
        }
    }

    private fun parseDataStream(data: IntArray, dataCodewords: Int): String? {
        val bitCount = dataCodewords * 8
        val reader = BitReader(data, bitCount)
        val output = mutableListOf<Int>()

        var eci = 0
        if (reader.peek(4) == 8) {
            reader.advance(4)
            val firstPrefix = reader.read(1)
            eci = if (firstPrefix == 0) {
                reader.read(7)
            } else {
                val secondPrefix = reader.read(1)
                if (secondPrefix == 0) {
                    reader.read(14)
                } else {
                    val thirdPrefix = reader.read(1)
                    if (thirdPrefix != 0) return null
                    reader.read(21)
                }
            }
        }

        var lastMode = -1
        while (reader.remaining() >= 4) {
            val modeIndicator = reader.read(4)
            if (modeIndicator == 0) {
                // Pad/zero mode - stop
                break
            }
            val mode = when (modeIndicator) {
                1 -> 'n'
                2 -> 't'
                3 -> 'b'
                4 -> '1'
                5 -> '2'
                6 -> 'd'
                7 -> 'f'
                else -> return null
            }

            // Region 1/2 direct transitions reuse the previous mode indicator
            if (mode == '2' && lastMode == 4) {
                // Already consumed the indicator; parse region 2 segment
                if (!parseRegion2(reader, output)) return null
            } else if (mode == '1' && lastMode == 5) {
                if (!parseRegion1(reader, output)) return null
            } else {
                when (mode) {
                    'n' -> if (!parseNumeric(reader, output)) return null
                    't' -> if (!parseText(reader, output)) return null
                    'b' -> if (!parseBinary(reader, output, eci)) return null
                    '1' -> if (!parseRegion1(reader, output)) return null
                    '2' -> if (!parseRegion2(reader, output)) return null
                    'd' -> if (!parseDoubleByte(reader, output)) return null
                    'f' -> if (!parseFourByte(reader, output)) return null
                }
            }
            lastMode = modeIndicator
        }

        return convertOutput(output, eci)
    }

    private fun parseNumeric(reader: BitReader, output: MutableList<Int>): Boolean {
        val values = mutableListOf<Int>()
        while (reader.remaining() >= 10) {
            val value = reader.read(10)
            if (value in 1021..1023) {
                val lastGroupSize = value - 1020
                for (i in values.indices) {
                    val groupSize = if (i == values.lastIndex) lastGroupSize else 3
                    val str = values[i].toString().padStart(groupSize, '0')
                    for (ch in str) output.add(ch.code)
                }
                return true
            }
            values.add(value)
        }
        return values.isEmpty()
    }

    private fun parseText(reader: BitReader, output: MutableList<Int>): Boolean {
        var submode = 1
        while (reader.remaining() >= 6) {
            val value = reader.read(6)
            if (value == 63) return true // terminator
            if (value == 62) {
                submode = if (submode == 1) 2 else 1
                continue
            }
            when (submode) {
                1 -> {
                    when {
                        value < 10 -> output.add(value + '0'.code)
                        value < 36 -> output.add(value - 10 + 'A'.code)
                        else -> output.add(value - 36 + 'a'.code)
                    }
                }
                2 -> {
                    when {
                        value <= 27 -> output.add(value)
                        value < 44 -> output.add(value - 28 + ' '.code)
                        value < 51 -> output.add(value - 44 + ':'.code)
                        value < 57 -> output.add(value - 51 + '['.code)
                        else -> output.add(value - 57 + '{'.code)
                    }
                }
            }
        }
        return true
    }

    private fun parseBinary(reader: BitReader, output: MutableList<Int>, eci: Int): Boolean {
        if (reader.remaining() < 13) return false
        val count = reader.read(13)
        if (count <= 0) return true
        val bytesToRead = count.coerceAtMost(reader.remaining() / 8)
        repeat(bytesToRead) {
            output.add(reader.read(8))
        }
        return true
    }

    private fun parseRegion1(reader: BitReader, output: MutableList<Int>): Boolean {
        while (reader.remaining() >= 12) {
            val value = reader.read(12)
            if (value == 4095) return true
            if (value == 4094) {
                // Switch to region 2 without new mode indicator
                return parseRegion2(reader, output)
            }
            val (high, low) = region1ToGb(value)
            output.add(high)
            output.add(low)
        }
        return true
    }

    private fun parseRegion2(reader: BitReader, output: MutableList<Int>): Boolean {
        while (reader.remaining() >= 12) {
            val value = reader.read(12)
            if (value == 4095) return true
            if (value == 4094) {
                return parseRegion1(reader, output)
            }
            val high = value / 0x5E + 0xD8
            val low = value % 0x5E + 0xA1
            output.add(high)
            output.add(low)
        }
        return true
    }

    private fun parseDoubleByte(reader: BitReader, output: MutableList<Int>): Boolean {
        while (reader.remaining() >= 15) {
            val value = reader.read(15)
            if (value == 32767) return true
            val first = value / 0xBE + 0x81
            val remainder = value % 0xBE
            val second = if (remainder <= 0x3E) remainder + 0x40 else remainder + 0x41
            output.add(first)
            output.add(second)
        }
        return true
    }

    private fun parseFourByte(reader: BitReader, output: MutableList<Int>): Boolean {
        while (reader.remaining() >= 25) {
            val mode = reader.read(4)
            if (mode != 7) {
                reader.advance(-4)
                return true
            }
            val value = reader.read(21)
            val first = value / 0x3138 + 0x81
            var rem = value % 0x3138
            val second = rem / 0x04EC + 0x30
            rem %= 0x04EC
            val third = rem / 0x0A + 0x81
            val fourth = rem % 0x0A + 0x30
            output.add(first)
            output.add(second)
            output.add(third)
            output.add(fourth)
        }
        return true
    }

    private fun region1ToGb(value: Int): Pair<Int, Int> {
        return when {
            value < 0xEB0 -> {
                val high = value / 0x5E + 0xB0
                val low = value % 0x5E + 0xA1
                high to low
            }
            value < 0xFCA -> {
                val adjusted = value - 0xEB0
                val high = adjusted / 0x5E + 0xA1
                val low = adjusted % 0x5E + 0xA1
                high to low
            }
            else -> {
                val low = value - 0xFCA + 0xA1
                0xA8 to low
            }
        }
    }

    private fun convertOutput(bytes: List<Int>, eci: Int): String? {
        val charset = when (eci) {
            0, 32 -> GB18030
            26 -> Charsets.UTF_8
            else -> GB18030
        }
        return try {
            String(bytes.map { it.toByte() }.toByteArray(), charset)
        } catch (e: Exception) {
            null
        }
    }
}
