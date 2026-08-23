---
id: doc-f48b73dd56ca6eca0c419753f1b4d6f0
title: 'Build & Code Conventions'
description: Toolchain versions, the deliberately serialized low-memory build profile, Hilt/DI layout, AppResult error handling, naming rules, and known version skews.
createdAt: '2026-08-23T03:18:13.947Z'
updatedAt: '2026-08-23T03:18:13.935Z'
tags:
  - build
  - gradle
  - conventions
  - hilt
  - coroutines
---

# Build & Code Conventions

Verified against source on 2026-08-22.

## Toolchain (single source: `gradle/libs.versions.toml`)

Gradle 8.13 · AGP 8.13.2 · Kotlin 2.2.21 · KSP 2.2.21-2.0.5 · Hilt 2.57.2 · Room 2.7.1 · Coroutines 1.8.1 (catalog) · CameraX 1.6.1 (catalog) · Java 17 with coreLibraryDesugaring.

## Serialized low-memory build profile — DO NOT "optimize" away

`gradle.properties` intentionally disables parallelism for low-RAM machines:

```properties
org.gradle.parallel=false
org.gradle.workers.max=1
org.gradle.daemon=false
org.gradle.jvmargs=-Xmx1536m
testOptions.unitTests.maxParallelForks=1 (app)
```

Do not enable `parallel`, raise workers, or bump `-Xmx` without an explicit user request — this profile exists because builds run on constrained hardware. Builds are slow by design; prefer targeted module tasks (`:data:testDebugUnitTest`) over whole-project builds during iteration.

## Version catalog discipline

The `[plugins]` block of the catalog is empty and several modules hardcode versions directly in their `build.gradle.kts` (e.g. `ai-model/build.gradle.kts:37-59` pins coroutines 1.8.1, Hilt 2.57.2, WorkManager 2.9.1, LiteRT-LM, MediaPipe, TFLite inline). When touching a module build file, match its existing style first; migrate to the catalog only as a deliberate, user-approved cleanup.

## Known version skews (do not silently "fix")

- Coroutines: catalog = 1.8.1, but `domain/build.gradle.kts` pins 1.7.3.
- CameraX: catalog = 1.6.1, but `app/build.gradle.kts` uses 1.4.0.
These are recorded inconsistencies; aligning them is a task-worthy change (boundary check + regression), not a drive-by edit.

## Hilt layout

All modules use `SingletonComponent`. Two-file convention per feature area:

- `object FooModule { @Provides fun ... }` — third-party/platform constructions
- `abstract class FooBindModule { @Binds fun ... }` — interface→impl bindings

Follow it when adding providers; don't mix @Provides into the BindModule file or vice versa.

## Error handling

Domain exposes `AppResult<T>` (`Success`/`Error`) plus a project exception hierarchy. Repository/use-case boundaries return `AppResult`; do not throw across layer boundaries. Map infra failures at the `:data`/`:storage-*` edge before they leak upward.

## Naming & structure quick rules

- ViewModels expose one immutable `*UiState` data class via StateFlow (MVI lean); workspace additionally uses `WorkspaceAction` / `WorkspaceEffect`.
- Use cases are thin `operator fun invoke` classes — currently only ~8 exist; most screens call repositories through ViewModels directly. Don't force a use case where a direct ViewModel→repository call already matches local style.
- Room DB is version 48 and **per-project instantiated** (`ProjectScopedDatabaseProvider`) — schema migrations must account for multiple live DB files, see @doc/patterns/project-scoped-database.
- No DataStore anywhere; user/session prefs go through SharedPreferences stores.

## Module boundary checklist for any new dependency edge

1. Edit `allowedProjectDependencies` in root `build.gradle.kts` (the enforcement task reads THIS map).
2. Add `implementation(project(":x"))` to the consuming module.
3. Run the boundary check (`EnforceModuleBoundariesTask` fails fast on undeclared edges).
4. If the edge crosses a quarantine (Firebase beyond `:data`, MapLibre beyond `:gis-maplibre`), stop and discuss — that's an architecture decision, not a build edit.


## Windows release build entrypoint

- Use `scripts/build-release.ps1` for a quick signed APK build on Windows. It validates the four `RELEASE_*` properties and the keystore path from `local.properties` without printing secret values, then runs `:app:assembleRelease --no-daemon`.
- Keep the full release gate in `specs/2026-08-22/run_release_check.ps1` for test, lint, boundary, signature, and smoke-test verification before a formal release.
- Source context: @doc/specs/2026-08-22/project-structure-specification
