pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MapSupervision"
include(":app")
include(":core")
include(":domain")
include(":data")
include(":project")
include(":gis")
include(":gis-maplibre")
include(":photo")
include(":timeline")
include(":reporting")
include(":storage-core")
include(":storage-crypto")
include(":storage-import")
include(":ai-core")
include(":ai-agent")
include(":ai-model")
include(":ai-rag")
include(":ai-prompt")
