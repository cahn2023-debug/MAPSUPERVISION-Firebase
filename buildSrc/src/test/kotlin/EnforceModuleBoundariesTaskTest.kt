import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EnforceModuleBoundariesTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verify_noViolations_succeeds() {
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).build()
        val task = project.tasks.create("enforceModuleBoundaries", EnforceModuleBoundariesTask::class.java)

        val moduleDir = tempFolder.newFolder("app")
        val buildFile = File(moduleDir, "build.gradle.kts").apply {
            writeText("""
                dependencies {
                    implementation(project(":core"))
                }
            """.trimIndent())
        }

        task.buildFiles.from(buildFile)
        task.allowedDependencies.put(":app", ":core")

        task.verify() // Should not throw
    }

    @Test
    fun verify_withViolations_throwsException() {
        val project = ProjectBuilder.builder().withProjectDir(tempFolder.root).build()
        val task = project.tasks.create("enforceModuleBoundaries", EnforceModuleBoundariesTask::class.java)

        val moduleDir = tempFolder.newFolder("core")
        val buildFile = File(moduleDir, "build.gradle.kts").apply {
            writeText("""
                dependencies {
                    implementation(project(":app"))
                }
            """.trimIndent())
        }

        task.buildFiles.from(buildFile)
        task.allowedDependencies.put(":core", "")

        val exception = assertThrows(IllegalStateException::class.java) {
            task.verify()
        }

        assertTrue(exception.message!!.contains("must not depend on :app"))
    }
}
