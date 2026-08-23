package com.mapsupervision.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabularMappingValidationTest {

    @Test
    fun test_missing_required_position_field() {
        val headers = listOf("STT", "ToaDo", "ChuDauTu")
        val rows = listOf(mapOf("STT" to "1", "ToaDo" to "10.7769, 106.7009", "ChuDauTu" to "Viettel"))

        val result = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "",
            coordinateColumn = "ToaDo",
            latitudeColumn = "",
            longitudeColumn = "",
            useTwoColumnCoordinates = false,
            allowPartialImport = false
        )

        assertFalse(result.canConfirm)
        assertTrue(result.requiredErrors.any { it.contains("vị trí", ignoreCase = true) })
    }

    @Test
    fun test_single_coordinate_column_valid() {
        val headers = listOf("ViTri", "ToaDo", "ChuDauTu")
        val rows = listOf(
            mapOf("ViTri" to "NODE_01", "ToaDo" to "10.7769, 106.7009", "ChuDauTu" to "Viettel"),
            mapOf("ViTri" to "NODE_02", "ToaDo" to "10.7800, 106.7050", "ChuDauTu" to "VNPT")
        )

        val result = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "ViTri",
            coordinateColumn = "ToaDo",
            latitudeColumn = "",
            longitudeColumn = "",
            useTwoColumnCoordinates = false,
            allowPartialImport = false
        )

        assertTrue(result.canConfirm)
        assertEquals(2, result.validRowCount)
        assertEquals(0, result.invalidRowCount)
    }

    @Test
    fun test_two_column_coordinates_with_invalid_rows_and_partial_import() {
        val headers = listOf("ViTri", "ViDo", "KinhDo")
        val rows = listOf(
            mapOf("ViTri" to "NODE_01", "ViDo" to "10.7769", "KinhDo" to "106.7009"),
            mapOf("ViTri" to "NODE_02", "ViDo" to "abc", "KinhDo" to "106.7050"), // Invalid lat
            mapOf("ViTri" to "NODE_03", "ViDo" to "10.7800", "KinhDo" to "xyz")  // Invalid lon
        )

        val strictResult = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "ViTri",
            coordinateColumn = "",
            latitudeColumn = "ViDo",
            longitudeColumn = "KinhDo",
            useTwoColumnCoordinates = true,
            allowPartialImport = false
        )

        assertFalse("Strict result must block confirmation when invalid rows exist", strictResult.canConfirm)
        assertEquals(1, strictResult.validRowCount)
        assertEquals(2, strictResult.invalidRowCount)

        val partialResult = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "ViTri",
            coordinateColumn = "",
            latitudeColumn = "ViDo",
            longitudeColumn = "KinhDo",
            useTwoColumnCoordinates = true,
            allowPartialImport = true
        )

        assertTrue("Partial import result must allow confirmation for valid rows", partialResult.canConfirm)
        assertEquals(1, partialResult.validRowCount)
        assertEquals(2, partialResult.invalidRowCount)
    }

    @Test
    fun test_business_key_suggestion_and_duplicate_count() {
        // File with duplicate codes but unique coordinates
        val headers = listOf("ViTri", "ViDo", "KinhDo")
        val rows = listOf(
            mapOf("ViTri" to "NODE_01", "ViDo" to "10.7769", "KinhDo" to "106.7009"),
            mapOf("ViTri" to "NODE_01", "ViDo" to "10.7800", "KinhDo" to "106.7050") // Same code, different coord
        )

        val resultWithCodeKey = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "ViTri",
            latitudeColumn = "ViDo",
            longitudeColumn = "KinhDo",
            useTwoColumnCoordinates = true,
            deduplicationKey = DuplicateBusinessKey.CODE
        )

        // Since code has duplicates but coords are unique, suggested key should be COORDINATES
        assertEquals(DuplicateBusinessKey.COORDINATES, resultWithCodeKey.suggestedBusinessKey)
        // With CODE selected, 1 duplicate should be detected
        assertEquals(1, resultWithCodeKey.duplicateRowCount)

        val resultWithCoordKey = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "ViTri",
            latitudeColumn = "ViDo",
            longitudeColumn = "KinhDo",
            useTwoColumnCoordinates = true,
            deduplicationKey = DuplicateBusinessKey.COORDINATES
        )
        // With COORDINATES selected, 0 duplicates
        assertEquals(0, resultWithCoordKey.duplicateRowCount)
    }

    @Test
    fun test_composite_business_key_deduplication() {
        val headers = listOf("ViTri", "ToaDo")
        val rows = listOf(
            mapOf("ViTri" to "NODE_01", "ToaDo" to "10.7769, 106.7009"),
            mapOf("ViTri" to "NODE_01", "ToaDo" to "10.7769, 106.7009") // Exact duplicate
        )

        val result = TabularMappingValidator.validate(
            headers = headers,
            rows = rows,
            positionColumn = "ViTri",
            coordinateColumn = "ToaDo",
            deduplicationKey = DuplicateBusinessKey.COMPOSITE_CODE_COORD
        )

        assertEquals(1, result.duplicateRowCount)
        assertTrue(result.canConfirm)
    }
}
