# Architecture & Module Boundaries Memory

## 18-Module Clean Architecture
The codebase strictly enforces clean architecture across 18 Gradle modules:
- `:app` is the single application aggregator depending on all modules.
- `:core` has `emptySet()` dependencies and holds cross-cutting utilities (AppResult, DispatchersProvider, Logging).
- `:domain` depends solely on `:core` and defines pure Kotlin domain models, interfaces, and business use-cases.
- `:data` implements repositories, Room SQLite database, and network access.
- Sub-feature modules (`:gis`, `:gis-maplibre`, `:photo`, `:timeline`, `:project`, `:reporting`, `:storage-core`, `:storage-crypto`, `:storage-import`, `:ai-core`, `:ai-agent`, `:ai-model`, `:ai-prompt`, `:ai-rag`) keep cohesion high and coupling minimal.

## Boundary Enforcement
- The root `build.gradle.kts` declares `allowedProjectDependencies`.
- Run `./gradlew enforceModuleBoundaries` to verify compliance before committing.
