package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.TabularMappingValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabularMappingValidationTest {

    private val sampleHeaders = listOf("Mã", "Vị trí", "Tọa độ", "Vĩ độ", "Kinh độ", "Nhà thầu", "Khối lượng")

    @Test
    fun test_missing_position_column_blocks_confirmation() {
        val rows = listOf(
            mapOf("Mã" to "N1", "Tọa độ" to "16.0, 108.0")
        )
        val result = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = null,
            coordinateColumn = "Tọa độ",
            useTwoColumnCoordinates = false
        )

        assertFalse(result.canConfirm)
        assertTrue(result.requiredErrors.any { it.contains("Tên đối tượng") || it.contains("Vị trí") })
    }

    @Test
    fun test_missing_single_coordinate_column_blocks_confirmation() {
        val rows = listOf(
            mapOf("Vị trí" to "N1", "Tọa độ" to "16.0, 108.0")
        )
        val result = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            coordinateColumn = null,
            useTwoColumnCoordinates = false
        )

        assertFalse(result.canConfirm)
        assertTrue(result.requiredErrors.any { it.contains("Tọa độ") })
    }

    @Test
    fun test_missing_two_column_coordinates_blocks_confirmation() {
        val rows = listOf(
            mapOf("Vị trí" to "N1", "Vĩ độ" to "16.0", "Kinh độ" to "108.0")
        )
        val resultMissingLat = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            latitudeColumn = null,
            longitudeColumn = "Kinh độ",
            useTwoColumnCoordinates = true
        )
        assertFalse(resultMissingLat.canConfirm)
        assertTrue(resultMissingLat.requiredErrors.any { it.contains("Vĩ độ") })

        val resultMissingLon = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            latitudeColumn = "Vĩ độ",
            longitudeColumn = null,
            useTwoColumnCoordinates = true
        )
        assertFalse(resultMissingLon.canConfirm)
        assertTrue(resultMissingLon.requiredErrors.any { it.contains("Kinh độ") })
    }

    @Test
    fun test_invalid_rows_block_confirmation_unless_partial_import_allowed() {
        val rows = listOf(
            mapOf("Vị trí" to "Node 1", "Tọa độ" to "16.0, 108.0"), // Valid
            mapOf("Vị trí" to "Node 2", "Tọa độ" to "invalid-gps-format"), // Invalid GPS
            mapOf("Vị trí" to "Node 3", "Tọa độ" to "150.0, 300.0"), // Out of bounds
            mapOf("Vị trí" to "", "Tọa độ" to "16.1, 108.1") // Blank position
        )

        val strictResult = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            coordinateColumn = "Tọa độ",
            useTwoColumnCoordinates = false,
            allowPartialImport = false
        )

        assertEquals(1, strictResult.validRowCount)
        assertEquals(3, strictResult.invalidRowCount)
        assertFalse("Strict mode should block confirmation when invalid rows exist", strictResult.canConfirm)

        val partialResult = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            coordinateColumn = "Tọa độ",
            useTwoColumnCoordinates = false,
            allowPartialImport = true
        )

        assertEquals(1, partialResult.validRowCount)
        assertEquals(3, partialResult.invalidRowCount)
        assertTrue("Partial import mode should allow confirmation when valid rows > 0", partialResult.canConfirm)
    }

    @Test
    fun test_all_valid_rows_enable_confirmation() {
        val rows = listOf(
            mapOf("Vị trí" to "Node 1", "Tọa độ" to "16.0, 108.0"),
            mapOf("Vị trí" to "Node 2", "Tọa độ" to "16.05; 108.20"),
            mapOf("Vị trí" to "Node 3", "Tọa độ" to "21.0285 105.8542")
        )

        val result = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            coordinateColumn = "Tọa độ",
            useTwoColumnCoordinates = false
        )

        assertEquals(3, result.validRowCount)
        assertEquals(0, result.invalidRowCount)
        assertTrue(result.canConfirm)
        assertTrue(result.requiredErrors.isEmpty())
    }

    @Test
    fun test_two_column_coordinates_validation() {
        val rows = listOf(
            mapOf("Vị trí" to "Node 1", "Vĩ độ" to "16.0678", "Kinh độ" to "108.2208"),
            mapOf("Vị trí" to "Node 2", "Vĩ độ" to "16.0712", "Kinh độ" to "108.2245"),
            mapOf("Vị trí" to "Node 3", "Vĩ độ" to "khong-phai-so", "Kinh độ" to "108.2245")
        )

        val result = TabularMappingValidator.validate(
            headers = sampleHeaders,
            rows = rows,
            positionColumn = "Vị trí",
            latitudeColumn = "Vĩ độ",
            longitudeColumn = "Kinh độ",
            useTwoColumnCoordinates = true,
            allowPartialImport = true
        )

        assertEquals(2, result.validRowCount)
        assertEquals(1, result.invalidRowCount)
        assertTrue(result.canConfirm)
    }
}
