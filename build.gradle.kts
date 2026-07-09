plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("com.vanniktech.dependency.graph.generator") version "0.8.0" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}


import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test


val projectDependencyPattern = Regex("""project\("(:[^"]+)"\)""")
val allowedProjectDependencies = mapOf(
    ":app" to setOf(":core", ":domain", ":data", ":project", ":gis", ":gis-maplibre", ":photo", ":timeline", ":reporting", ":storage-core", ":storage-crypto", ":storage-import", ":ai-core", ":ai-agent", ":ai-model", ":ai-rag", ":ai-prompt"),
    ":core" to emptySet(),
    ":data" to setOf(":core", ":domain", ":storage-core", ":storage-crypto", ":ai-core", ":ai-prompt", ":ai-rag"),
    ":domain" to setOf(":core"),
    ":gis" to setOf(":core", ":domain"),
    ":gis-maplibre" to setOf(":gis", ":domain"),
    ":photo" to setOf(":core", ":domain", ":ai-core", ":storage-core"),
    ":project" to setOf(":core", ":domain", ":storage-core"),
    ":reporting" to setOf(":core", ":domain", ":ai-core", ":storage-core"),
    ":storage-core" to setOf(":core", ":domain"),
    ":storage-crypto" to setOf(":core"),
    ":storage-import" to setOf(":core", ":domain", ":storage-core", ":storage-crypto"),
    ":timeline" to setOf(":core", ":domain", ":ai-core"),
    ":ai-core" to setOf(":core", ":domain"),
    ":ai-agent" to setOf(":core", ":domain", ":ai-core", ":ai-model", ":ai-prompt", ":ai-rag"),
    ":ai-model" to setOf(":core", ":domain", ":ai-core", ":ai-prompt", ":storage-core"),
    ":ai-rag" to setOf(":core", ":domain", ":ai-core", ":ai-prompt"),
    ":ai-prompt" to setOf(":core", ":domain", ":ai-core")
)

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        exclude("**/byRounds/**")
        options.encoding = "UTF-8"
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        jvmArgs("-Xmx1536m", "-Djava.awt.headless=true")
    }
}

tasks.register<EnforceModuleBoundariesTask>("enforceModuleBoundaries") {
    group = "verification"
    description = "Fails if project modules depend on disallowed project modules."
    buildFiles.from(rootProject.subprojects.map { it.buildFile })
    allowedDependencies.putAll(allowedProjectDependencies.mapValues { (_, value) -> value.joinToString(",") })
}

tasks.register("check") {
    group = "verification"
    description = "Aggregates module verification tasks and module boundary checks."
}

gradle.projectsEvaluated {
    val moduleCheckTasks = subprojects.mapNotNull { it.tasks.findByName("check") }
    tasks.named("check").configure {
        dependsOn(moduleCheckTasks)
        dependsOn("enforceModuleBoundaries")
    }
}
