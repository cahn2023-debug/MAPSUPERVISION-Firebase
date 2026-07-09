import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class EnforceModuleBoundariesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFiles: ConfigurableFileCollection

    @get:Input
    abstract val allowedDependencies: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val projectDependencyPattern = Regex("""project\("(:[^"]+)"\)""")
        val allowed = allowedDependencies.get().mapValues { (_, value) ->
            if (value.isBlank()) emptySet() else value.split(",").map(String::trim).filter(String::isNotEmpty).toSet()
        }

        val violations = mutableListOf<String>()
        buildFiles.files.sortedBy { it.absolutePath }.forEach { buildFile ->
            val modulePath = ":${buildFile.parentFile.name}"
            val allowedForModule = allowed[modulePath] ?: emptySet()
            buildFile.readLines().forEachIndexed { lineIndex, line ->
                projectDependencyPattern.findAll(line).forEach { match ->
                    val dependencyPath = match.groupValues[1]
                    if (dependencyPath != modulePath && dependencyPath !in allowedForModule) {
                        violations += "${buildFile.relativeTo(project.rootProject.projectDir)}:${lineIndex + 1} -> $modulePath must not depend on $dependencyPath"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Module boundary violations:")
                    violations.forEach { appendLine(it) }
                }.trimEnd()
            )
        }
    }
}
