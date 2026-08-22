---
title: Critical Patterns
description: Promoted must-know patterns and traps for any feature work in MapSupervision — read before touching sync, DB, AI, or module boundaries.
tags: [critical, promoted, learnings]
---

# Critical Patterns

Promoted per kn-extract rules: entries here apply to multiple future features, avoid substantial repeated effort, and stay concise. Full detail lives in the linked canonical doc.

## 1. Firebase stays in `:data` (3 files, that's it)

Only `FirebaseRuntime.kt`, `FirebaseSyncRepositoryImpl.kt`, `FirebaseAccessRepositoryImpl.kt` under `data/.../sync/` may import `com.google.firebase`. New backend capability → extend a `:domain` interface + implement in `:data/sync`. Canonical: @doc/guides/firebase-sync

## 2. Module boundaries are build-enforced

`EnforceModuleBoundariesTask` reads the `allowedProjectDependencies` map in root `build.gradle.kts:19` and fails the build on undeclared edges. Adding a dependency = edit map first, then the module. Crossing a quarantine (Firebase / MapLibre / vendor-AI-SDK) is an architecture decision. Canonical: @doc/architecture-overview

## 3. Room DB is per-project, version 48

Inject `ProjectScopedDatabaseProvider`, never a raw DAO singleton; workers must re-resolve on project switch; every migration runs once per project file. Destructive migrations are off by default. Canonical: @doc/patterns/project-scoped-database

## 4. Offline-first via outbox, not direct writes

Local mutations append to a persisted event outbox → WorkManager drains it to Firestore. Never write Firestore directly from UI/ViewModel code paths. Canonical: @doc/guides/firebase-sync

## 5. Serialized low-memory build is intentional

`gradle.properties` sets daemon=false, parallel=false, workers.max=1, -Xmx1536m for constrained hardware. Don't "fix" it. Iterate with targeted module tasks instead. Canonical: @doc/build-conventions

## 6. Errors cross layers as AppResult, not exceptions

Repositories/use-cases return `AppResult<T>` Success/Error; infra failures are mapped at data/storage edges. Orchestrator callers must handle engine-unavailable as an error outcome. Canonical: @doc/build-conventions + @doc/patterns/ai-engine-orchestration

## 7. Known version skews are recorded, not silent

Coroutines 1.7.3 (`:domain`) vs 1.8.1 (catalog); CameraX 1.4.0 (`:app`) vs 1.6.1 (catalog). Aligning them = deliberate task with boundary check + regression run. Canonical: @doc/build-conventions
