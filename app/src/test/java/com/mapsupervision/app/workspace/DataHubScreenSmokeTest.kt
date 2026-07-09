package com.mapsupervision.app.workspace

import java.io.File
import org.junit.Assert.assertFalse
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
}
