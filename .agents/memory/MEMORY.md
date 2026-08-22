# Project Memory Index (MapSupervision)

> Authoritative Memory layer linking Knowns memories and architectural invariants.

---

## 1. Core Architecture & Dependency Boundaries
- [Modules & Boundaries](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/architecture-and-modules.md) → 18 Gradle modules, whitelist rules, `enforceModuleBoundaries` verification.
- [Database & Multi-Project Isolation](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/database-and-storage.md) → Room v48 (28 entities/DAOs), `ProjectScopedDatabaseProvider`, WAL mode, 5-min cache eviction.

## 2. Media, AI & Cloud Sync Subsystems
- [CameraX & GPS Watermark](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/camera-and-media.md) → Real-time HUD, anti-fraud GPS verification (`isGpsMocked`), `DirectCaptureSaveDeduper`.
- [Hybrid AI Orchestrator & Local RAG](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/ai-and-rag-engine.md) → 6 execution engines, `LiteRtSafetyGate`, Gemma on-device model, Vietnamese NLP dictionary resolver.
- [Firebase Auth & Event Outbox Sync](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/cloud-and-sync.md) → Offline-first `event_outbox` table, WorkManager sync, Firestore RBAC rules.

## 3. Engineering Conventions & Build System
- [Build & Release Pipelines](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/build-and-release.md) → Low-memory build profile (`-Xmx1536m`, `maxParallelForks = 1`), Keystore signing, Firebase SHA fingerprint matching.
- [Project Conventions & SDD](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/.agents/memory/project-conventions.md) → Git branching, conventional commits, Spec-Driven Development (`kn-spec` → `kn-flow` → `kn-verify` → `kn-commit`).
