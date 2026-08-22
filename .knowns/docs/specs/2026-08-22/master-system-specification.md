---
title: MapSupervision Master System Specification
description: Authoritative master technical specification unifying software architecture, full features, 28 Room database entities, and build conventions.
tags: [spec, master, architecture, features, database, build, approved]
---

# MapSupervision Master System Specification

> **Status:** APPROVED  
> **Date:** 2026-08-22  
> **Version:** 1.0  
> **Authors:** Senior Software Architect, Lead Engineer  
> **Tags:** `spec`, `master`, `architecture`, `features`, `database`, `build`, `approved`

---

## 1. Executive Summary

This Master Specification establishes the authoritative technical blueprint for the **MapSupervision** project. It consolidates all architectural layers, complete feature capabilities, database & storage schemas, and project engineering conventions into a unified Knowledge Base under Knowns.

---

## 2. Specification Suite Index

The full system documentation is decomposed into four comprehensive specifications:

```
                                  ┌──────────────────────────────────────────────┐
                                  │      Master System Specification (v1.0)      │
                                  └──────────────────────┬───────────────────────┘
                                                         │
         ┌───────────────────────────────┬───────────────┴───────────────┬───────────────────────────────┐
         │                               │                               │                               │
  ┌──────▼───────────────────────┐ ┌─────▼───────────────────────┐ ┌─────▼───────────────────────┐ ┌─────▼───────────────────────┐
  │ 1. Software Architecture     │ │ 2. Full Features & Workflows│ │ 3. Data Architecture        │ │ 4. Project Structure        │
  │ • 18-Module Clean Graph      │ │ • 5 Main Workspace Tabs     │ │ • 28 Room Entities (v48)    │ │ • AGP 8.13.2 / Kotlin 2.2.21│
  │ • Boundary Enforcement       │ │ • Camera & GPS Watermarking │ │ • Project-Scoped DB Pattern │ │ • Low-Memory Build Profiles │
  │ • Reactive StateFlow / UDF   │ │ • MapLibre GIS & Layering   │ │ • Transactional Outbox Sync │ │ • Quality Gates & Check     │
  │ • Hilt DI & AppResult<T>     │ │ • Multi-Format Ingestion    │ │ • Cloud Firestore Schema    │ │ • Signed APK Runbooks       │
  │ • Multi-Engine AI Pipeline   │ │ • Construction Diary & Hubs │ │ • AES-GCM Encrypted Storage │ │ • Knowns SDD Integration    │
  └──────────────────────────────┘ └─────────────────────────────┘ └─────────────────────────────┘ └─────────────────────────────┘
```

### Direct Document References

1. **Software Architecture Specification:**
   - Document: `@doc/specs/2026-08-22/software-architecture-specification`
   - Scope: 18-module graph, compilation boundaries, layering, dependency injection with Hilt, Coroutines/Flow, error contracts.

2. **Full Features & Workflows Specification:**
   - Document: `@doc/specs/2026-08-22/full-features-specification`
   - Scope: MapHub (GIS), ProgressHub (Diary/Logs), DataHub (Import), MaterialsHub (Handover), ReportsHub (PDF/DOCX), CameraX watermark HUD, Gemma on-device AI, Firebase auth & cloud sync.

3. **Data Architecture & Schema Specification:**
   - Document: `@doc/specs/2026-08-22/data-architecture-specification`
   - Scope: Complete 28 Room SQLite entities (v48), ProjectScopedDatabaseProvider isolation, Event Outbox pattern, Firestore collections, encrypted file storage layout.

4. **Project Structure & Build Conventions Specification:**
   - Document: `@doc/specs/2026-08-22/project-structure-specification`
   - Scope: Directory hierarchy, toolchain pinning, low-memory build optimization (`-Xmx1536m`), module boundary verification task, signed release pipelines.

---

## 3. High-Level System Architecture Summary

- **Platform:** Native Android (Kotlin 2.2.21, Jetpack Compose, Min SDK 26, Target SDK 35).
- **Core Database:** Local-first Room SQLite (v48) isolated per-project file (`MapSupervision_<projectId>.db`).
- **Cloud Backend:** Google Cloud / Firebase (Firestore NoSQL, Firebase Auth, Google Drive / Storage, Cloud WorkManager).
- **AI Core:** Hybrid Edge/Cloud (`AiOrchestrator`) coordinating MediaPipe Gemma, LiteRT, Cloud Gemini, ML Kit OCR, and local RAG.
- **GIS Engine:** MapLibre Native SDK with offline vector tiles (.mbtiles) and dynamic GeoJSON styling.

---

## 4. Acceptance & Verification Criteria

- [x] All 18 Gradle modules adhere to dependency whitelist rules enforced by `enforceModuleBoundaries`.
- [x] All 28 Room database entities match v48 schema definitions with non-destructive migrations.
- [x] Full functional coverage across all 5 workspace hubs, camera overlay, AI assistant, and export pipelines.
- [x] Strict data isolation maintained across projects via `ProjectScopedDatabaseProvider`.
- [x] All documentation validated and indexed within Knowns memory layer.

---

## 5. Task Links

All linked execution tasks have been completed and verified under `/kn-flow`:

- `@task-ole2i0` - `[mss-01] Verify 18-Module Clean Architecture & Boundary Whitelist` (Status: Done)
- `@task-baywan` - `[mss-02] Verify Room SQLite v48 Schema Integrity & 28 Entities / DAOs` (Status: Done)
- `@task-cur2hl` - `[mss-03] Validate ProjectScopedDatabaseProvider Multi-Project Isolation` (Status: Done)
- `@task-mn9jia` - `[mss-04] Audit Hilt DI & Coroutines AppResult Engine` (Status: Done)
- `@task-1st54w` - `[mss-05] Validate 5 Workspace Hubs & UDF State Flow` (Status: Done)
- `@task-ko9dx0` - `[mss-06] Verify CameraX Watermark HUD & Anti-Fraud GPS Stamping` (Status: Done)
- `@task-nd6e8j` - `[mss-07] Audit AI Multi-Engine Orchestrator & Local RAG Stack` (Status: Done)
- `@task-lxza27` - `[mss-08] Verify Firebase Auth & Event Outbox Cloud Sync Pipeline` (Status: Done)
- `@task-s9g76i` - `[mss-09] Verify Low-Memory Build Configuration & Quality Gates` (Status: Done)
- `@task-u9qrz7` - `[mss-10] Validate Signed Release APK Pipeline & SHA Fingerprint Match` (Status: Done)

