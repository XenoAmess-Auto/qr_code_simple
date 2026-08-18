package com.xenoamess.qrcodesimple

import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionExporterTest {
    @Test
    fun `csv escapes quotes commas and newlines`() {
        val csv = ScanSessionExporter.csv(listOf(ScanSessionExporter.Row("a,\"b\"\nc", "QR_CODE", 1, false)))
        assertTrue(csv.contains("\"a,\"\"b\"\"\nc\""))
    }

    @Test
    fun `json preserves session fields`() {
        val json = ScanSessionExporter.json(listOf(ScanSessionExporter.Row("code", "MICRO_QR", 2, true)))
        assertTrue(json.contains("MICRO_QR"))
    }
}
