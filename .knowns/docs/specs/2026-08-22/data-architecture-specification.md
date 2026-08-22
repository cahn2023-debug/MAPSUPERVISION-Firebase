---
title: MapSupervision Data Architecture & Schema Specification
description: Complete specification of Room SQLite v48 (28 entities), ProjectScopedDatabaseProvider pattern, Event Outbox sync, and Firestore NoSQL collections.
tags: [spec, database, room, firestore, schema, storage, approved]
---

# MapSupervision Data Architecture & Schema Specification

> **Status:** APPROVED  
> **Date:** 2026-08-22  
> **Version:** 1.0  
> **Authors:** Database Architect, Senior Backend Specialist, Mobile Engineer  
> **Target Module:** `:data`, `:domain`, `:storage-core`, `:storage-crypto`  
> **Database Engine:** Room SQLite (v48) + Cloud Firestore NoSQL

---

## 1. Data Layer Overview

MapSupervision utilizes a hybrid local-first persistence architecture:
1. **Local Relational Core (Room SQLite v48):** Serves as the primary operational database on device, optimized for high-performance spatial queries, indexing, and offline transactions.
2. **Project-Scoped Database Isolation:** Each supervision project is isolated into its own independent SQLite database file (`MapSupervision_<projectId>.db`) managed dynamically by `ProjectScopedDatabaseProvider`.
3. **Transactional Outbox Sync (`event_outbox`):** Guarantees atomicity and reliable background synchronization with remote Cloud Firestore and Google Drive / Firebase Storage.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Android Local Storage                           │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    ProjectScopedDatabaseProvider                      │  │
│  │                                                                       │  │
│  │   ┌──────────────────────────┐     ┌──────────────────────────┐       │  │
│  │   │ Project A Database (v48) │     │ Project B Database (v48) │  ...  │  │
│  │   │  • 28 Room Tables        │     │  • 28 Room Tables        │       │  │
│  │   │  • Event Outbox          │     │  • Event Outbox          │       │  │
│  │   └────────────┬─────────────┘     └────────────┬─────────────┘       │  │
│  └────────────────┼────────────────────────────────┼─────────────────────┘  │
│                   │                                │                        │
│                   └───────────────┬────────────────┘                        │
│                                   ▼                                         │
│                      ┌────────────────────────┐                             │
│                      │  DomainEventOutboxBus  │                             │
│                      └────────────┬───────────┘                             │
└───────────────────────────────────┼─────────────────────────────────────────┘
                                    │ (WorkManager Async Sync)
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Cloud Infrastructure                             │
│                                                                             │
│  ┌───────────────────────────────────┐  ┌────────────────────────────────┐  │
│  │      Cloud Firestore (NoSQL)      │  │  Firebase Storage / Drive Storage│
│  │ • /users/{uid}                    │  │ • High-resolution field photos │  │
│  │ • /projects/{id}                  │  │ • PDF / DOCX inspection dossiers│ │
│  │ • /projects/{id}/projectMembers   │  │ • Offline vector MBTiles packages│ │
│  │ • /projects/{id}/{collectionName} │  └────────────────────────────────┘  │
│  └───────────────────────────────────┘                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Complete Room Database Schema (v48) - 28 Entities

The local database `MapSupervisionDatabase` contains 28 strongly-typed entities registered in `@Database(version = 48)`:

### Entity Group 1: Workspace & Core Entities
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `ProjectEntity` | `projects` | `id: String` | Root supervision project entity | `name`, `slug`, `storageMode`, `projectDbPath`, `projectCode`, `mediaStorageProvider`, `mediaStorageFolderId`, `isArchived`, `isDeleted` |
| `NoteEntity` | `notes` | `id: String` | Field engineering notes | `projectId` (FK -> `projects`), `title`, `content`, `colorHex`, `nodeId`, `routeId`, `isPinned`, `updatedAtEpochMs` |
| `TaskEntity` | `tasks` | `id: String` | Action items and work orders | `projectId` (FK -> `projects`), `title`, `description`, `status` (`TODO`, `IN_PROGRESS`, `DONE`), `priority`, `dueDateEpochMs`, `assignee` |
| `WorkCategoryEntity` | `work_categories` | `id: String` | Work item taxonomy | `projectId` (FK -> `projects`), `name`, `code`, `displayOrder` |
| `WorkPlanEntity` | `work_plans` | `id: String` | Master construction schedule | `projectId` (FK -> `projects`), `name`, `startDateEpochMs`, `endDateEpochMs`, `targetVolume`, `unit`, `status` |

### Entity Group 2: GIS & Spatial Entities
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `GisNodeEntity` | `gis_node` | `id: String` | Spatial point assets (poles, manholes, cabinets, splice points) | `projectId` (FK), `code` (Unique with `projectId`), `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`, `importedFileId` (FK), `ipAddress`, `subnet`, `gateway`, `signalStatus` |
| `GisRouteEntity` | `gis_route` | `id: String` | Linear cable & conduit spans | `projectId` (FK), `code` (Unique with `projectId`), `contractor`, `startNodeCode`, `endNodeCode`, `points: List<Pair<Double, Double>>`, `designLength`, `fiberCoreCount`, `fiberConnection`, `startNodeId` (FK), `endNodeId` (FK) |

### Entity Group 3: Progress, Inspection & Daily Diary
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `DailyLogEntity` | `daily_log` | `id: String` | Master daily construction diary entry | `projectId` (FK), `workItem`, `manpower`, `note`, `weather`, `temperature`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `linkedWorkPlanId`, `nodeId` (FK), `routeId` (FK) |
| `DailyLogLineEntity` | `daily_log_lines` | `id: String` | Detailed sub-item volume lines per daily log | `dailyLogId` (FK -> `daily_log`), `subWorkItem`, `volume`, `unit`, `contractor` |
| `DailyLogNodeEntity` | `daily_log_nodes` | `id: String` | Many-to-many link between daily log and affected GIS nodes | `dailyLogId` (FK), `nodeId` (FK), `status` |
| `DailyLogPhotoEntity` | `daily_log_photos` | `id: String` | Many-to-many link between daily log and attached photos | `dailyLogId` (FK), `photoId` (FK -> `site_photos`) |
| `NodeProgressEntity` | `node_progress` | `id: String` | Physical completion milestone checklist per node | `projectId` (FK), `nodeId` (FK), `milestoneName`, `isCompleted`, `completedAtEpochMs`, `completedBy` |
| `MaterialProgressEntity` | `material_progress` | `id: String` | Installed material quantity tracking per route/node | `projectId` (FK), `targetId` (FK), `materialName`, `installedQuantity`, `unit`, `updatedAtEpochMs` |

### Entity Group 4: Materials & Supply Chain
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `MaterialDeclarationEntity` | `material_declaration` | `id: String` | Master bill of quantities & material definitions | `projectId` (FK), `materialCode`, `materialName`, `specifications`, `unit`, `plannedQuantity`, `supplier` |
| `MaterialHandoverEntity` | `material_handover` | `id: String` | Material handover log between stakeholders | `projectId` (FK), `handoverCode`, `sender`, `recipient`, `materialCode`, `quantity`, `handoverDateEpochMs`, `signatureUrl`, `notes` |

### Entity Group 5: Field Media & Photo Pipeline
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `SitePhotoEntity` | `site_photos` | `id: String` | Field photographs with embedded metadata & watermark | `projectId` (FK), `objectCode`, `tagCodesCsv`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedNodeId` (FK), `matchedRouteId` (FK), `syncStatus`, `remoteUrl` |
| `PhotoTagEntity` | `photo_tags` | `id: String` | Normalized taxonomy tags for photo filtering | `photoId` (FK), `tagCategory`, `tagValue` |

### Entity Group 6: File Ingestion & Spatial Import Engine
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `ImportedFileEntity` | `imported_files` | `id: String` | Records uploaded source files (Excel, KML, GeoJSON, DOCX) | `projectId` (FK), `fileName`, `fileSizeBytes`, `mimeType`, `parsedNodeCount`, `parsedRouteCount`, `importStatus`, `uploadedAtEpochMs` |
| `ImportSessionEntity` | `import_sessions` | `id: String` | Multi-file batch import execution context | `projectId` (FK), `sessionName`, `status`, `startedAtEpochMs`, `completedAtEpochMs` |
| `ImportVersionEntity` | `import_versions` | `id: String` | Historical snapshots of imported design data | `projectId` (FK), `importedFileId` (FK), `versionNumber`, `snapshotJson`, `createdAtEpochMs` |
| `ImportConflictEntity` | `import_conflicts` | `id: String` | Detected duplicate or conflicting records during import | `sessionId` (FK), `entityType`, `conflictKey`, `incomingDataJson`, `existingDataJson`, `resolution` |
| `ImportAuditEntity` | `import_audits` | `id: String` | Validation logs, warnings, and repair traces | `importedFileId` (FK), `severity` (`INFO`, `WARN`, `ERROR`), `message`, `lineIndex` |

### Entity Group 7: AI Engine, Prompts & RAG
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `AiActionLogEntity` | `ai_action_logs` | `id: String` | Execution audit log of all AI inferences | `projectId` (FK), `engineType` (`GEMINI`, `GEMMA`, `RULE`), `promptTokenCount`, `outputTokenCount`, `latencyMs`, `status`, `createdAtEpochMs` |
| `AiDecisionCacheEntity` | `ai_decision_cache`| `id: String` | Semantic cache for repeated AI queries | `promptHash: String` (Unique Index), `responsePayloadJson`, `expiresAtEpochMs` |
| `ChatHistoryEntity` | `chat_history` | `id: String` | Conversational message thread with Gemma/Gemini | `projectId` (FK), `sender` (`USER`, `ASSISTANT`, `SYSTEM`), `content`, `timestampEpochMs`, `actionJson` |
| `RagDocumentEmbeddingEntity`| `rag_document_embeddings`| `id: String` | Vector embeddings for local RAG retrieval | `projectId` (FK), `documentChunk`, `vectorEmbeddingJson`, `sourceReference`, `createdAtEpochMs` |

### Entity Group 8: Outbox & Reporting
| Entity Class | Table Name | Primary Key | Description | Key Fields & Foreign Keys |
| :--- | :--- | :--- | :--- | :--- |
| `EventOutboxEntity` | `event_outbox` | `id: String` | Reliable event staging queue for cloud sync | `projectId`, `eventType`, `payloadJson`, `status` (`PENDING`, `PROCESSING`, `DONE`, `FAILED`), `availableAtEpochMs`, `createdAtEpochMs` |
| `ReportDraftEntity` | `report_drafts` | `id: String` | Saved report configuration templates and drafts | `projectId` (FK), `reportTitle`, `reportType` (`DAILY`, `ACCEPTANCE`, `DEFECT`), `templateConfigJson`, `updatedAtEpochMs` |

---

## 3. Project-Scoped Database Isolation Pattern

To guarantee that each infrastructure project maintains zero data leakage and optimal local query performance:

1. **Provider (`ProjectScopedDatabaseProvider`):** Maintains an internal pool of active `MapSupervisionDatabase` instances indexed by `projectId`.
2. **Dynamic Database Path Resolution:**
   ```
   /data/user/0/com.mapsupervision/files/projects/{projectId}/db/MapSupervision_{projectId}.db
   ```
3. **Lifecycle & Cache Eviction:** When user switches from Project A to Project B, Provider safely checkpoints Project A SQLite WAL logs, closes connections, and initializes Project B Room instance.
4. **Data Bridge & Hydration (`ProjectBridgeNormalization`):** When creating a new project, standard system dictionaries and taxonomies are automatically seeded into the new scoped database.

---

## 4. Cloud Firestore Data Model & Security

Firestore is structured hierarchically to mirror project boundaries:

```
/users/{uid}
  ├── uid: String
  ├── email: String
  ├── displayName: String
  ├── projectIds: List<String>
  ├── isDisabled: Boolean
  └── lastLoginAtEpochMs: Long

/projects/{projectId}
  ├── id: String
  ├── name: String
  ├── slug: String
  ├── projectCode: String
  ├── mediaStorageFolderId: String
  ├── isArchived: Boolean
  │
  ├── /projectMembers/{uid}
  │     ├── uid: String
  │     ├── role: String ("ADMIN" | "MEMBER" | "VIEWER")
  │     └── isActive: Boolean
  │
  ├── /gis_node/{nodeId}
  ├── /gis_route/{routeId}
  ├── /site_photos/{photoId}
  ├── /daily_log/{logId}
  ├── /material_progress/{id}
  └── /material_handover/{id}
```

### Security Gate Rule Enforcement (`firestore.rules`)
- **`isProjectMember(projectId)`:** Validates caller authentication and checks `/projects/{projectId}/projectMembers/{request.auth.uid}.isActive == true`.
- **`isAdmin()`:** Caller must possess Firebase Auth custom claim `admin == true`.
- Unauthorized access or cross-project reads are blocked at the Firestore engine level.

---

## 5. Local File System & Directory Structure

All binary assets are organized strictly per-project on internal private storage:

```
/data/user/0/com.mapsupervision/files/
└── projects/
    └── {projectId}/
        ├── db/
        │   ├── MapSupervision_{projectId}.db
        │   ├── MapSupervision_{projectId}.db-wal
        │   └── MapSupervision_{projectId}.db-shm
        ├── photos/
        │   ├── IMG_20260822_100100_VT01.jpg
        │   └── IMG_20260822_100100_VT01.jpg.meta
        ├── thumbnails/
        │   └── THUMB_IMG_20260822_100100_VT01.jpg
        ├── mbtiles/
        │   └── vietnam_offline_satellite.mbtiles
        ├── imports/
        │   └── design_drawing_rev3.kml
        └── exports/
            └── Inspection_Report_20260822.pdf
```
Sensitive files (inspection signatures and credential tokens) are encrypted with hardware-backed AES-GCM-256 via `ProjectCryptoManager`.
