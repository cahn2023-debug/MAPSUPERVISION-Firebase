package com.mapsupervision.app.workspace

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataHubScreenSmokeTest {
    @Test
    fun dataHubScreenDoesNotContainProjectMediaSection() {
        val candidatePaths = listOf(
            "src/main/java/com/mapsupervision/app/workspace/DataHubScreen.kt",
            "app/src/main/java/com/mapsupervision/app/workspace/DataHubScreen.kt"
        )
        val sourceFile = candidatePaths
            .asSequence()
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Could not locate DataHubScreen.kt")
        val source = sourceFile.readText()

        assertFalse(source.contains("ProjectMediaSection("))
        assertFalse(source.contains("Media dự án"))
        assertFalse(source.contains("Nhập media"))
    }
    @Test
    fun excelMappingDialogShowsProgressAndDisablesConfirmWhileImporting() {
        val sourceFile = listOf(
            "src/main/java/com/mapsupervision/app/workspace/ExcelMappingDialog.kt",
            "app/src/main/java/com/mapsupervision/app/workspace/ExcelMappingDialog.kt"
        ).map(::File).firstOrNull(File::exists)
            ?: error("Could not locate ExcelMappingDialog.kt")
        val source = sourceFile.readText()

        assertTrue(source.contains("enabled = !state.isLoading"))
        assertTrue(source.contains("if (state.isLoading) \"Đang nhập dữ liệu...\""))
        assertTrue(source.contains("decorFitsSystemWindows = true"))
        assertTrue(source.contains(".fillMaxHeight(0.9f)"))
    }
}
