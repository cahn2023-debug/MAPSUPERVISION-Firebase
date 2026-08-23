package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceImportMappingActionsTest {

    @Test
    fun `buildImportedGeometryMessage reports matched existing map objects when nothing new is inserted`() {
        val message = buildImportedGeometryMessage(
            newNodeCount = 0,
            newRouteCount = 0,
            replacingExistingFile = false
        )

        assertTrue(message.contains("khớp"))
        assertTrue(message.contains("không tạo thêm đối tượng mới"))
    }

    @Test
    fun `buildImportedGeometryMessage reports update when replacing existing file`() {
        val message = buildImportedGeometryMessage(
            newNodeCount = 2,
            newRouteCount = 1,
            replacingExistingFile = true
        )

        assertEquals("Đã cập nhật dữ liệu: +2 node, +1 tuyến", message)
    }

    @Test
    fun `starting Excel import marks mapping state as loading`() {
        val state = ExcelParserUiState(showMappingDialog = true)

        val started = state.startExcelImport()

        assertTrue(started.isLoading)
        assertTrue(started.showMappingDialog)
        assertEquals("Đang parse Excel...", started.message)
    }

    @Test
    fun `starting Excel import twice does not reset an active import`() {
        val state = ExcelParserUiState(
            isLoading = true,
            message = "Đang xử lý dữ liệu lớn..."
        )

        assertEquals(state, state.startExcelImport())
    }

    @Test
    fun `ExcelParserUiState preserves allowPartialImport toggle state`() {
        val state = ExcelParserUiState(
            allowPartialImport = true,
            validRowCount = 251,
            invalidRowCount = 1,
            validationErrors = emptyList()
        )

        assertTrue(state.allowPartialImport)
        assertTrue(state.canConfirm)
        assertEquals(251, state.validRowCount)
        assertEquals(1, state.invalidRowCount)
    }
}
