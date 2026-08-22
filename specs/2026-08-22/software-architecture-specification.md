# MapSupervision Software Architecture Specification

> **Status:** APPROVED  
> **Date:** 2026-08-22  
> **Version:** 1.0  
> **Authors:** Senior Software Architect, Mobile Specialist, System Engineer  
> **Target Platform:** Android (API 26 - Android 8.0 Oreo up to API 35 - Android 15), Kotlin 2.2.21, Jetpack Compose

---

## 1. Executive Summary & System Philosophy

**MapSupervision** is an enterprise-grade, local-first Android application purpose-built for on-site infrastructure supervision (fiber optic deployment, civil construction, electrical grids, telecom networks, and spatial utilities).

The software architecture is governed by four non-negotiable core principles:

1. **Local-First & Offline-First Autonomy:** The application functions with full fidelity in remote field environments without cellular or Wi-Fi connectivity. All transactional data is written to a local project-scoped SQLite/Room database and staged in an Event Outbox for background cloud synchronization.
2. **Project-Scoped Data Isolation:** To ensure strict multi-project boundary security and zero data contamination, each project operates on its own dedicated SQLite database file managed by `ProjectScopedDatabaseProvider`.
3. **Multi-Module Clean Architecture:** The codebase is decoupled into 18 Gradle modules with compile-time dependency enforcement (`enforceModuleBoundaries` Gradle verification task).
4. **Hybrid Edge & Cloud AI Orchestration:** Machine learning and generative AI tasks leverage an adaptive multi-engine orchestrator (`AiOrchestrator`) that dynamically routes between on-device LiteRT / Gemma models, ML Kit vision engines, and Cloud Gemini APIs with rule-based fallback safety.

---

## 2. 18-Module Dependency Graph & Responsibilities

The project is structured into 18 modules declared in `settings.gradle.kts` and verified by `build.gradle.kts`:

```
                                  ┌────────────────────────┐
                                  │         :app           │ (Application Shell & DI Root)
                                  └───────────┬────────────┘
                                              │
        ┌──────────────┬──────────────┬───────┴──────┬──────────────┬──────────────┐
        │              │              │              │              │              │
 ┌──────▼─────┐ ┌──────▼─────┐ ┌──────▼─────┐ ┌──────▼─────┐ ┌──────▼─────┐ ┌──────▼──────┐
 │  :project  │ │    :gis    │ │   :photo   │ │ :timeline  │ │:reporting  │ │ :ai-agent   │
 └──────┬─────┘ └──────┬─────┘ └──────┬─────┘ └──────┬─────┘ └──────┬─────┘ └──────┬──────┘
        │              │              │              │              │              │
        │       ┌──────▼──────┐       │              │              │       ┌──────▼──────┐
        │       │:gis-maplibre│       │              │              │       │  :ai-model  │
        │       └──────┬──────┘       │              │              │       └──────┬──────┘
        │              │              │              │              │              │
        │              └──────┬───────┴──────────────┴──────────────┘              │
        │                     │                                                    │
 ┌──────▼──────┐       ┌──────▼──────┐       ┌─────────────┐                ┌──────▼──────┐
 │:storage-core│◄──────┤    :data    ├──────►│:storage-imp │                │   :ai-rag   │
 └──────┬──────┘       └──────┬──────┘       └──────┬──────┘                └──────┬──────┘
        │                     │                     │                              │
 ┌──────▼──────┐              │                     │                       ┌──────▼──────┐
 │:storage-cryp│              │                     │                       │  :ai-prompt │
 └──────┬──────┘              │                     │                       └──────┬──────┘
        │                     │                     │                              │
        │              ┌──────▼──────┐              │                       ┌──────▼──────┐
        └─────────────►│   :domain   │◄─────────────┴──────────────────────►│  :ai-core   │
                       └──────┬──────┘                                      └──────┬──────┘
                              │                                                    │
                       ┌──────▼──────┐                                             │
                       │    :core    │◄────────────────────────────────────────────┘
                       └─────────────┘
```

### Module Responsibility Breakdown Matrix

| Module | Responsibility | Primary Classes / Artifacts | Allowed Project Dependencies |
| :--- | :--- | :--- | :--- |
| **`:core`** | Platform utilities, Coroutine dispatchers, error models, base logging, `AppResult<T>` wrapper. | `AppResult`, `AppLogger`, `DispatchersProvider`, `AppError` | *None (Self-contained root)* |
| **`:domain`** | Pure Kotlin domain models, repository interfaces, use case contracts, business invariants. | `Project`, `GisNode`, `GisRoute`, `SitePhoto`, `DailyLog`, `Task`, `IGisRepository` | `:core` |
| **`:data`** | Room DB (v48), DAOs, Entities, Firestore/Firebase sync, Outbox dispatcher, Repository implementations. | `MapSupervisionDatabase`, `ProjectScopedDatabaseProvider`, `FirebaseSyncRepositoryImpl`, `EventOutboxDao` | `:core`, `:domain`, `:storage-core`, `:storage-crypto`, `:ai-core`, `:ai-prompt`, `:ai-rag` |
| **`:project`** | Project workspace switching, creation, metadata management, project settings UI. | `ProjectViewModel`, `ProjectScreen`, `ProjectDialog` | `:core`, `:domain`, `:storage-core` |
| **`:gis`** | GIS contracts, spatial coordinate systems, style configurations, layer abstractions. | `GisStyleProvider`, `SpatialCoordinateConverter`, `GisMapState` | `:core`, `:domain` |
| **`:gis-maplibre`** | MapLibre SDK integration, vector tile rendering (.mbtiles), offline GeoJSON layers, route tracing. | `MapBridgeInstaller`, `MapLibreMapView`, `GeoJsonLayerRenderer` | `:gis`, `:domain` |
| **`:photo`** | CameraX integration, custom HUD overlay, GPS stamping, Exif embedding, thumbnailing, media gallery. | `CameraOverlay`, `PhotoViewModel`, `PhotoScreen`, `PhotoPipelineServiceImpl` | `:core`, `:domain`, `:ai-core`, `:storage-core` |
| **`:timeline`** | Construction timeline UI, daily diary calendar, progress logging, milestone feeds. | `TimelineViewModel`, `TimelineScreen`, `TimelineSnapshot` | `:core`, `:domain`, `:ai-core` |
| **`:reporting`** | Document generation: PDF export (iText/Canvas), DOCX inspection reports, CSV/Excel summaries. | `PdfReportGenerator`, `DocxReportGenerator`, `ReportingViewModel`, `ReportingScreen` | `:core`, `:domain`, `:ai-core`, `:storage-core` |
| **`:storage-core`** | Active project state management, directory structure resolution, file packaging, import/export IO. | `ActiveProjectRepositoryImpl`, `ProjectStorageManager`, `ProjectPackageService` | `:core`, `:domain` |
| **`:storage-crypto`** | Encrypted payload storage, SQLiteCipher integration, Android Keystore credential encryption. | `ProjectCryptoManager` | `:core` |
| **`:storage-import`** | Multi-format parsers: Excel (.xlsx/.xls), KML/KMZ, GeoJSON, Shapefiles, DOCX, geometry normalization. | `UserFileImportService`, `ExcelParsingHelpers`, `GeoJsonStreamingParser`, `GeometryNormalization` | `:core`, `:domain`, `:storage-core`, `:storage-crypto` |
| **`:ai-core`** | AI contracts, engine interfaces, tokenizers, prompt payloads, safety policies. | `AiEngine`, `AiContracts`, `AIFacade`, `LiteRtSafetyGate` | `:core`, `:domain` |
| **`:ai-agent`** | Multi-agent coordination, tool execution, summary aggregation, supervisory reasoning. | `AiOrchestrator`, `SummaryAggregator` | `:core`, `:domain`, `:ai-core`, `:ai-model`, `:ai-prompt`, `:ai-rag` |
| **`:ai-model`** | Concrete AI runtime engines: Cloud Gemini, Local LiteRT, MediaPipe Gemma, ML Kit, TFLite, Rule-based. | `CloudGeminiEngine`, `LocalLiteRtEngine`, `MediaPipeLlmEngine`, `MlKitVisionEngine`, `AIManager` | `:core`, `:domain`, `:ai-core`, `:ai-prompt`, `:storage-core` |
| **`:ai-rag`** | Local retrieval augmented generation, document embeddings, vector chunk matching. | `RagDocumentBuilder`, `TextEmbeddingEngine` | `:core`, `:domain`, `:ai-core`, `:ai-prompt` |
| **`:ai-prompt`** | Prompt builders, Vietnamese natural language parser, dictionary normalizers, action extractors. | `ChatActionParser`, `CanonicalTextNormalizer`, `ChatDictionaryResolver`, `DailyLogCanonicalizer` | `:core`, `:domain`, `:ai-core` |
| **`:app`** | Application entry point, Hilt dependency injection root, navigation graph, app shell, system widgets. | `MapSupervisionApplication`, `MainActivity`, `WorkspaceAppShell`, `DiaryCalendarWidget` | *All modules allowed* |

---

## 3. Layering & Architectural Patterns

### 3.1 Presentation Layer (Jetpack Compose)
- **Declarative UI:** 100% Jetpack Compose with Material 3 theming.
- **Unidirectional Data Flow (UDF):**
  - ViewModels expose immutable `StateFlow<UiState>`.
  - UI emits user actions (`WorkspaceAction`, `PhotoAction`, `ProjectAction`) to ViewModels.
  - One-time side-effects (navigation, toast, dialogs) are emitted via Kotlin `Channel<Effect>` and consumed as `Flow.collectLatest`.
- **Adaptive Layout:** Supports dynamic screen adaptation (`WorkspaceLayoutMode.COMPACT` vs `WorkspaceLayoutMode.EXPANDED`) for phone and tablet devices.

### 3.2 Domain Layer (Pure Kotlin)
- Contains zero Android framework dependencies (`android.*` imports are strictly forbidden).
- Enforces business logic and validation rules through Use Cases and Domain Services:
  - `IPhotoPipelineService`: Contract for media capture, timestamping, location stamping, and thumbnail generation.
  - `IPhotoLocationProvider`: Contract for GPS accuracy validation and mock-location detection.
  - `IActiveProjectRepository`: Source of truth for the currently active workspace.

### 3.3 Data & Storage Layer
- **Room Database:** High-performance persistence with 28 relational entities and 28 DAOs.
- **Project-Scoped Database Pattern:** Implements runtime switching of SQLite database files.
- **Transactional Outbox Pattern:** Guarantees eventual consistency between local SQLite mutations and remote Firebase Firestore collections.

---

## 4. Dependency Injection (Hilt) Architecture

Dagger-Hilt 2.57.2 is used for compile-time dependency injection across the entire module graph:

- **`@Singleton` Scope (`ApplicationComponent`):** Global services such as `DispatchersProvider`, `AppLogger`, `ProjectStorageManager`, `ActiveProjectRepository`, `FirebaseRuntime`, `AiOrchestrator`.
- **`@ViewModelScoped`:** ViewModels (`WorkspaceViewModel`, `ProjectViewModel`, `PhotoViewModel`, `ReportingViewModel`, `TimelineViewModel`, `GemmaChatViewModel`).
- **Dynamic Scoped Providers:** `ProjectScopedDatabaseProvider` provides dynamic access to the active project's `MapSupervisionDatabase` instance.

---

## 5. Asynchronous Concurrency & Error Model

### 5.1 Coroutine Dispatchers
Standardized through `DispatchersProvider`:
- `io`: Disk IO, Room transactions, network calls, file parsing.
- `default`: CPU-heavy tasks (geometry normalization, vector embedding, PDF rasterization, image processing).
- `main`: UI state updates and Jetpack Compose composition.

### 5.2 Error Handling (`AppResult<T>`)
Every repository method and usecase returns an explicit `AppResult<T>`:
```kotlin
sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Error(val exception: Throwable, val message: String? = null) : AppResult<Nothing>()
}
```
Checked exceptions are never propagated directly to the UI layer. ViewModels map `AppResult.Error` into localized UI error states.

---

## 6. Verification & Architectural Boundaries

Module integrity is guarded by the custom Gradle verification task:
```bash
./gradlew enforceModuleBoundaries
./gradlew check
```
Any illegal dependency across module boundaries immediately breaks the build.
