package com.mapsupervision.data.mlkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitScannerServiceParsingTest {

    @Test
    fun `parseMaterialData extracts name quantity and unit`() {
        val result = parseMaterialData(
            text = "Cap quang 120 m",
            lines = listOf("Cap quang 120 m")
        )

        assertTrue(result.success)
        assertEquals("Cap quang", result.materialName)
        assertEquals(120.0, result.quantity!!, 0.001)
        assertEquals("m", result.unit)
        assertTrue(result.error == null)
    }

    @Test
    fun `parseDailyLogData extracts work item manpower and note`() {
        val result = parseDailyLogData(
            text = "Cong viec: lap dat tu cap\nNhan cong: 12\nGhi chu: hoan thanh 80%",
            lines = listOf(
                "Cong viec: lap dat tu cap",
                "Nhan cong: 12",
                "Ghi chu: hoan thanh 80%"
            )
        )

        assertTrue(result.success)
        assertEquals("lap dat tu cap", result.workItem)
        assertEquals(12, result.manpower)
        assertEquals("hoan thanh 80%", result.note)
    }
}
