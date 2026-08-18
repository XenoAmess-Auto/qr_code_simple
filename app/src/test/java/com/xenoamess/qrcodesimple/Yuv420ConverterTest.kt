package com.xenoamess.qrcodesimple

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

class Yuv420ConverterTest {
    @Test
    fun `uses row and pixel strides when reading planes`() {
        val y = ByteBuffer.wrap(byteArrayOf(16, 16, 0, 0, 16, 16, 0, 0))
        // Chroma samples live at offsets 0 and 2 because pixelStride is two.
        val u = ByteBuffer.wrap(byteArrayOf(128.toByte(), 0, 128.toByte(), 0))
        val v = ByteBuffer.wrap(byteArrayOf(128.toByte(), 0, 128.toByte(), 0))
        val pixels = Yuv420Converter.argbPixels(0, 0, 2, 2, listOf(
            Yuv420Converter.Plane(y, 4, 1),
            Yuv420Converter.Plane(u, 4, 2),
            Yuv420Converter.Plane(v, 4, 2)
        ))

        assertEquals(0xff000000.toInt(), pixels[3])
    }

    @Test
    fun `converts nominal white luminance to white`() {
        val y = ByteBuffer.wrap(byteArrayOf(235.toByte()))
        val u = ByteBuffer.wrap(byteArrayOf(128.toByte()))
        val v = ByteBuffer.wrap(byteArrayOf(128.toByte()))

        val pixels = Yuv420Converter.argbPixels(
            0,
            0,
            1,
            1,
            listOf(
                Yuv420Converter.Plane(y, 1, 1),
                Yuv420Converter.Plane(u, 1, 1),
                Yuv420Converter.Plane(v, 1, 1)
            )
        )

        assertEquals(0xffffffff.toInt(), pixels.single())
    }
}
