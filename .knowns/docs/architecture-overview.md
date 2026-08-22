---
title: Architecture Overview
description: Layering, the 18-module Gradle graph, enforced dependency rules, and where Firebase/GIS/AI live in MapSupervision.
tags: [architecture, modules, clean-architecture, android]
---

# Architecture Overview

Verified against source on 2026-08-22. Module adjacency quotes `build.gradle.kts:19-37`; module list quotes `settings.gradle.kts:18-35`.

## Shape

Single-Activity Android app, Compose UI, Clean Architecture leaning MVI. Domain layer holds interfaces/use-case abstractions; implementations sit in feature/data modules. 19 ViewModels expose immutable `*UiState` classes; the workspace screen adds `WorkspaceAction`/`WorkspaceEffect` (MVI-style intent/effect pairs).

## Modules (18 total)

`:app :core :domain :data :project :gis :gis-maplibre :photo :timeline :reporting :storage-core :storage-crypto :storage-import :ai-core :ai-agent :ai-model :ai-rag :ai-prompt`

Dependency rules (enforced, see below):

| Module | May depend on |
|---|---|
| :app | all other modules (only module allowed to see everything, incl. `:storage-crypto`) |
| :core | nothing |
| :domain | :core |
| :data | :core, :domain, :storage-core, :storage-crypto, :ai-core, :ai-prompt, :ai-rag |
| :gis | :core, :domain |
| :gis-maplibre | :gis, :domain |
| :photo | :core, :domain, :ai-core, :storage-core |
| :project | :core, :domain, :storage-core |
| :reporting | :core, :domain, :ai-core, :storage-core |
| :storage-core | :core, :domain |
| :storage-crypto | :core |
| :storage-import | :core, :domain, :storage-core, :storage-crypto |
| :timeline | :core, :domain, :ai-core |
| :ai-core | :core, :domain |
| :ai-agent | :core, :domain, :ai-core, :ai-model, :ai-prompt, :ai-rag |
| :ai-model | :core, :domain, :ai-core, :ai-prompt, :storage-core |
| :ai-rag | :core, :domain, :ai-core, :ai-prompt |
| :ai-prompt | :core, :domain, :ai-core |

Key consequences worth memorizing:

- **Firebase is quarantined in `:data`** — exactly 3 Kotlin files import `com.google.firebase`, all under `data/src/main/java/com/mapsupervision/data/sync/`: `FirebaseRuntime.kt`, `FirebaseSyncRepositoryImpl.kt`, `FirebaseAccessRepositoryImpl.kt`. No other module may add the SDK (see @decision/firebase-in-data-only).
- **MapLibre is quarantined in `:gis-maplibre`** (`org.maplibre.gl:android-sdk:11.7.0`, `gis-maplibre/build.gradle.kts:33`). `:gis` is the abstract GIS contract layer; UI code depends on `:gis`, never on `:gis-maplibre` directly except `:app` wiring.
- **AI flows through `:ai-agent`**: the orchestrator composes `:ai-model` (engines), `:ai-prompt` (prompt assets), `:ai-rag` (retrieval). Feature modules only see `:ai-core` contracts.

## Enforcement

`EnforceModuleBoundariesTask` (in `buildSrc/src/main/kotlin/`) reads the same `allowedProjectDependencies` map declared in `build.gradle.kts:19` and fails builds on undeclared edges. A root-level aggregate `check` task is registered at `build.gradle.kts:61`. Adding a dependency = edit the map + declare in the module's `build.gradle.kts`, in that order.

## Toolchain

Gradle 8.13 wrapper · AGP 8.13.2 · Kotlin 2.2.21 · KSP 2.2.21-2.0.5 · Hilt 2.57.2 · Java 17 + desugaring. App module: compileSdk 36 / minSdk 24 / targetSdk 35 (`app/build.gradle.kts`). Full convention detail: @doc/build-conventions.

## Cross-cutting

- Result wrapping: `AppResult<T>` = Success/Error across layers.
- DI: Hilt `SingletonComponent`; provisioning split into `object *Module` (@Provides) and `abstract *BindModule` (@Binds).
- Persistence: Room `MapSupervisionDatabase` version 48 (`data/src/main/java/com/mapsupervision/data/db/MapSupervisionDatabase.kt:99`) — but instantiated per-project, see @doc/patterns/project-scoped-database.
- Background work: persisted event outbox → WorkManager → Firebase sync (@doc/guides/firebase-sync).
- Desktop/web companion: `webapp/` — Next.js 15 + React 19 + firebase-admin 13 + googleapis + maplibre-gl 5 (admin tooling incl. `bootstrap:admins`, `migrate:drive-media` scripts).
