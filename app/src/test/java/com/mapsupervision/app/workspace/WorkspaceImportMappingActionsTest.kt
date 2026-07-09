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
}
