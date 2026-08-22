---
title: MapSupervision Full Features & Workflows Specification
description: Comprehensive feature and workflow specification covering 5 workspace hubs, CameraX watermark HUD, GIS MapLibre engine, Gemma AI assistant, and cloud sync.
tags: [spec, features, ui, gis, ai, camera, reporting, approved]
---

# MapSupervision Full Features & Workflows Specification

> **Status:** APPROVED  
> **Date:** 2026-08-22  
> **Version:** 1.0  
> **Authors:** Senior Mobile Architect, Product Lead, GIS Specialist  
> **Target Module:** `:app`, `:project`, `:gis`, `:gis-maplibre`, `:photo`, `:timeline`, `:reporting`, `:storage-import`, `:ai-agent`

---

## 1. Feature Architecture Overview

MapSupervision integrates 8 core functional subsystems into a unified Android workspace shell (`WorkspaceAppShell.kt`):

```
                                  ┌────────────────────────────────────────┐
                                  │           WorkspaceAppShell            │
                                  └───────────────────┬────────────────────┘
                                                      │
         ┌──────────────────┬─────────────────────────┼─────────────────────────┬──────────────────┐
         │                  │                         │                         │                  │
  ┌──────▼──────┐    ┌──────▼──────┐           ┌──────▼──────┐           ┌──────▼──────┐    ┌──────▼──────┐
  │   MapHub    │    │ ProgressHub │           │   DataHub   │           │MaterialsHub │    │ ReportsHub  │
  │ (GIS/Layers)│    │(Diary/Logs) │           │(Import/Sync)│           │ (Handover)  │    │ (PDF/DOCX)  │
  └─────────────┘    └─────────────┘           └─────────────┘           └─────────────┘    └─────────────┘
         ▲                  ▲                         ▲                         ▲                  ▲
         └──────────────────┴─────────────────────────┼─────────────────────────┴──────────────────┘
                                                      │
                       ┌──────────────────────────────┴──────────────────────────────┐
                       │                   Global Shell Subsystems                   │
                       ├──────────────────────────────┬──────────────────────────────┤
                       │  • Camera & Watermark Engine │  • Gemma AI Assistant Sheet  │
                       │  • Multi-Project Switcher    │  • Firebase Auth & Sync Bus  │
                       └──────────────────────────────┴──────────────────────────────┘
```

---

## Locked Decisions

- D1: 5-Hub navigation paradigm (MapHub, ProgressHub, DataHub, MaterialsHub, ReportsHub) managed by WorkspaceAppShell.
- D2: CameraX watermark HUD with anti-fraud GPS verification and DirectCaptureSaveDeduper deduplication.
- D3: Multi-engine AI orchestration (Cloud Gemini + On-Device Gemma/LiteRT) with Vietnamese NLP parser.

## System Decision Impact

- Impact: none — preserves and validates all existing feature capabilities.

---

## 2. Core Workspace Tabs

### 2.1 MapHub (GIS & Spatial Inspection Engine)
- **Primary Files:** `MapHubScreen.kt`, `MapHubDetails.kt`, `MapBridgeInstaller.kt`, `WorkspaceMapProgressActions.kt`
- **Core Capabilities:**
  - **MapLibre Engine Rendering:** Displays vector tile layers (.mbtiles) and offline raster tile packages with hardware acceleration.
  - **Dynamic Layer Rendering:** Real-time rendering of `GisNode` (points) and `GisRoute` (multiline strings) with status-dependent visual styling.
  - **Contractor Color Coding:** User-customizable color palettes per contractor (`onContractorColorChanged`, `onToggleContractorVisibility`).
  - **Interactive Selection & Inspection:**
    - Single-tap selection on map nodes opens an inspection drawer showing: node code, contractor, coordinates (WGS84 / VN2000), work volume, signal status (`NodeSignalStatus`), IP address, subnet, and linked field photos.
    - Single-tap on routes displays length, fiber core count (`fiberCoreCount`), connection details, and start/end nodes.
  - **Distance Measurement Tool:** Interactive polygon and polyline geodesic measurement on the map canvas (`onMapToggleMeasure`, `updateMeasureDistance`).
  - **Real-time GPS Tracking:** My Location centering, heading compass, and movement breadcrumbs.

### 2.2 ProgressHub (Construction Diary & Milestone Tracking)
- **Primary Files:** `ProgressHubScreen.kt`, `ProgressHubViewModel.kt`, `ProgressHubRoute.kt`, `DiaryCalendarWidget.kt`
- **Core Capabilities:**
  - **Daily Construction Logs (`DailyLog`):**
    - Log entries record work item name, category, manpower headcount, work volume, unit, linked node/route, engineer note, and timestamp.
    - Weather Integration: Automatic weather and ambient temperature retrieval (`fetchWeatherAuto`) based on project GPS coordinates.
  - **Batch Work Planning (`WorkPlan`):** Create batch execution schedules across multiple nodes/routes with status tracking (`PLANNED`, `IN_PROGRESS`, `COMPLETED`, `DELAYED`).
  - **Node Progress Checklist (`NodeProgress`):** Check off physical installation steps per node (e.g., pole erection, cable pulling, splicing, testing).
  - **App Widget Integration (`DiaryCalendarWidget`):** Android Home Screen AppWidget displaying the active project's daily work schedule, completion progress, and quick capture actions.

### 2.3 DataHub (Multi-Format Spatial & Tabular Ingestion Hub)
- **Primary Files:** `DataHubScreen.kt`, `DataHubRoute.kt`, `ExcelMappingDialog.kt`, `NonExcelMappingDialog.kt`, `CombineFilesDialog.kt`, `UserFileImportService.kt`
- **Core Capabilities:**
  - **Supported File Formats:**
    - Excel: `.xlsx`, `.xls` (multi-sheet support with column mapping).
    - Spatial Formats: `.kml`, `.kmz`, `.geojson`, `.json`.
    - Document Attachments: `.docx`.
  - **Flexible Column & Schema Mapping:**
    - Interactive dialog allowing users to map arbitrary spreadsheet columns to required system fields (Node Code, Contractor, Latitude, Longitude, Work Volume, etc.).
    - Coordinate System Auto-Detection & Normalization: Converts VN-2000 (local projection zones) to standard WGS84 decimal degrees.
  - **Geometry Repair & Normalization:** Repairs self-intersecting polylines, removes duplicate vertices, and closes open linear rings (`repairImportedGeometry`).
  - **Conflict & Duplicate Resolution Policies:**
    - `OVERWRITE`: Replaces existing node/route data with incoming file.
    - `KEEP_EXISTING`: Skips duplicate codes.
    - `MERGE_ATTRIBUTES`: Merges new properties into existing records while preserving history.
    - `CREATE_VERSION`: Creates a versioned snapshot under `ImportVersionEntity`.
  - **Combine Files Wizard (`CombineFilesDialog`):** Merges multiple spatial or Excel files into a single unified design dataset.

### 2.4 MaterialsHub (Material Inventory & Handover Management)
- **Primary Files:** `MaterialsHubScreen.kt`, `WorkspaceMaterialActions.kt`, `MaterialDeclarationRepositoryImpl.kt`
- **Core Capabilities:**
  - **Material Declarations (`MaterialDeclaration`):** Register master materials, specifications, units (meters, units, drums), and allocated quotas.
  - **Material Handover Tracking (`MaterialHandover`):**
    - Record handovers between investor, general contractor, and subcontractors.
    - Capture recipient signatures, delivery notes, and batch numbers.
  - **Consumption & Variance Analytics:** Compares planned vs. installed material quantities per node/route, flagging losses or discrepancies.

### 2.5 ReportsHub (Automated Engineering Document Generation)
- **Primary Files:** `ReportingScreen.kt`, `ReportingViewModel.kt`, `PdfReportGenerator.kt`, `DocxReportGenerator.kt`, `ReportPreviewDialog.kt`
- **Core Capabilities:**
  - **Official PDF Inspection Reports:** Generates multi-page PDF reports containing project metadata, milestone summary, completion charts, node status tables, and watermarked photo grids.
  - **DOCX Acceptance Documents:** Generates editable Microsoft Word (.docx) handover and acceptance dossiers compliant with construction standards.
  - **Data Export:** Export tabular project data to Excel (.xlsx) and CSV for external ERP/GIS systems.
  - **In-App Document Preview:** Built-in PDF viewer dialog for immediate verification prior to sharing or printing.

---

## 3. Global Subsystems & Cross-Cutting Capabilities

### 3.1 Field Photo & Camera System
- **Primary Files:** `CameraOverlay.kt`, `CameraOverlayState.kt`, `PhotoScreen.kt`, `PhotoViewModel.kt`, `DirectCaptureSaveDeduper.kt`
- **Workflow & Features:**
  - **CameraX High-Performance Pipeline:** Hardware-accelerated camera capture with real-time HUD (crosshair, level indicator, aspect ratio selector 4:3 / 16:9).
  - **Dynamic Watermark & Stamping Engine:**
    - Overlays project name, station/node code, route code, supervising engineer name.
    - Embeds WGS84 GPS Coordinates, altitude, location accuracy (± meters), and timestamp.
    - Injects weather status icon and temperature.
  - **Anti-Fraud GPS Verification:** Validates location provider and flags mock/spoofed GPS coordinates (`isGpsMocked`).
  - **Auto Spatial Matching:** Automatically identifies the closest `GisNode` within proximity and pre-populates metadata tags.
  - **Direct Capture Deduplication (`DirectCaptureSaveDeduper`):** Prevents duplicate image processing on rapid shutter taps.

### 3.2 AI Multi-Agent & Gemma Chat Assistant
- **Primary Files:** `GemmaChatSheet.kt`, `GemmaChatViewModel.kt`, `AiOrchestrator.kt`, `ChatActionParser.kt`, `SummaryAggregator.kt`
- **Workflow & Features:**
  - **Adaptive Multi-Engine Routing:**
    - `CloudGeminiEngine`: Used for high-complexity analytical reasoning, multi-document synthesis, and complex planning when online.
    - `MediaPipeLlmEngine` / `LocalLiteRtEngine`: On-device Gemma LLM running offline for local query answering, task suggestions, and report summarization.
    - `MlKitVisionEngine` & `TfliteVisionEngine`: On-device OCR and visual feature detection on construction photos.
    - `RuleBasedEngine`: Deterministic fallback guarantee when offline and low device memory.
  - **Vietnamese Natural Language Action Parser (`ChatActionParser`):** Interprets user voice/text commands (e.g., *"Thêm nhật ký hôm nay tổ anh Ba kéo 300m cáp tại cột VT05"*) and converts them into structured database transactions.
  - **Dictionary Normalizer (`ChatDictionaryResolver`):** Resolves colloquial slang, contractor nicknames, and abbreviated node codes to exact system IDs.
  - **Local RAG (`RagDocumentBuilder`):** Vector search over project specifications, engineering standards, and previous inspection notes.

### 3.3 Firebase Authentication, RBAC & Cloud Sync
- **Primary Files:** `FirebaseAccessGate.kt`, `FirebaseAccessViewModel.kt`, `FirebaseSyncRepositoryImpl.kt`, `FirebaseMediaUploadWorker.kt`
- **Workflow & Features:**
  - **Authentication:** Supports Firebase Email/Password and Google OAuth with SHA-1/SHA-256 fingerprint verification.
  - **Role-Based Access Control (RBAC):** Gated by Firestore Security Rules:
    - `Admin`: Full write access to project members, permissions, and project configuration.
    - `Project Member`: Read/write access to project nodes, routes, photos, logs, and materials.
    - `Viewer`: Read-only access to maps and generated reports.
  - **Transactional Outbox Sync:** Local mutations write to `event_outbox` table. `FirebaseMediaUploadWorker` processes pending events with exponential backoff retry.
  - **Media Cloud Upload (`DriveMediaUploadClient`):** Asynchronously uploads high-resolution field photos to Google Drive / Firebase Storage and stores remote URL references.

### 3.4 Multi-Project Workspace Switcher
- **Primary Files:** `ProjectViewModel.kt`, `ProjectScreen.kt`, `ProjectScopedDatabaseProvider.kt`
- **Workflow & Features:**
  - Instant workspace switching without app restart.
  - Complete data isolation: swapping active project closes current SQLite connection and dynamically opens the target project's dedicated database.
  - Project packaging (`.msdata` zip bundle) for offline backup, export, and peer-to-peer sharing.
