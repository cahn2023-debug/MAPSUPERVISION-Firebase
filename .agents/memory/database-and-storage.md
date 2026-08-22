# Database & Multi-Project Storage Memory

## Room Schema v48 & 28 Registered Entities
- Database class: `MapSupervisionDatabase` with `@Database(version = 48, exportSchema = true)`.
- 28 Entities: `ProjectEntity`, `NodeProgressEntity`, `SitePhotoEntity`, `DailyLogEntity`, `DailyLogLineEntity`, `DailyLogNodeEntity`, `DailyLogPhotoEntity`, `GisNodeEntity`, `GisRouteEntity`, `ImportedFileEntity`, `ImportSessionEntity`, `ImportVersionEntity`, `ImportConflictEntity`, `ImportAuditEntity`, `EventOutboxEntity`, `MaterialProgressEntity`, `NoteEntity`, `PhotoTagEntity`, `TaskEntity`, `WorkCategoryEntity`, `AiDecisionCacheEntity`, `ChatHistoryEntity`, `ReportDraftEntity`, `AiActionLogEntity`, `WorkPlanEntity`, `MaterialHandoverEntity`, `MaterialDeclarationEntity`, `RagDocumentEmbeddingEntity`.

## Multi-Project Isolation Engine
- `ProjectScopedDatabaseProvider` instantiates separate SQLite database files at:
  `context.filesDir/projects/{projectId}/db/MapSupervision_{projectId}.db`.
- Features WAL journaling mode (`PRAGMA synchronous = NORMAL`, `PRAGMA foreign_keys = ON`, `PRAGMA temp_store = MEMORY`).
- Connections are cached in memory and automatically evicted after 5 minutes of idle time.
- Base dictionaries (work categories, status maps) are seeded dynamically via `ProjectBridgeNormalization`.
