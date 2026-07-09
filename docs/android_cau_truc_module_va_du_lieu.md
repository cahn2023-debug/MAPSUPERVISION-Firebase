# Cau truc module va du lieu Android

## 1. Danh sach module trong workspace Android

Theo `settings.gradle.kts`, cac module chinh hien tai la:

- `app`
- `core`
- `domain`
- `data`
- `project`
- `gis`
- `gis-maplibre`
- `photo`
- `timeline`
- `reporting`
- `storage-core`
- `storage-crypto`
- `storage-import`
- `ai-core`
- `ai-agent`
- `ai-model`
- `ai-rag`
- `ai-prompt`

## 2. Vai tro tung module

### 2.1 `app`

Module app Android goc, chua:

- `Application`, `Activity`, manifest.
- Navigation shell Compose.
- Workspace orchestration.
- Widget.
- WorkManager worker.
- AI bridge, sync manager, theming.

Day la noi ghep cac feature module thanh trai nghiem nguoi dung hoan chinh.

### 2.2 `core`

Chua helper co tinh nen:

- `AppResult`
- exception/logging
- cac utility chung

### 2.3 `domain`

Noi khai bao:

- domain model: `Project`, `GisNode`, `GisRoute`, `TimelineSnapshot`, `ReportDraft`, `SitePhoto`, ...
- repository contracts
- use cases nhu `ObserveTimelineUseCase`, `ObserveWorkspaceSnapshotUseCase`
- service contracts nhu `IPhotoPipelineService`, `IPhotoLocationProvider`

### 2.4 `data`

Noi chua persistence va implementation:

- `MapSupervisionDatabase`
- DAO
- entity
- repository implementation
- scoped DB provider
- import lifecycle repository
- migration va sync logic

### 2.5 `project`

Feature quan ly project:

- tao/chuyen/clone/archive
- import/export project
- theo doi project active
- doi path luu du lieu

### 2.6 `gis` va `gis-maplibre`

- `gis`: model/logic map, label field, domain du lieu GIS.
- `gis-maplibre`: cau noi render map voi MapLibre.

### 2.7 `photo`

Xu ly anh/video hien truong:

- tao file output
- watermark/stamp
- gallery import
- geotag
- quality review bang AI

### 2.8 `timeline`

Tong hop:

- `NodeProgress`
- `DailyLog`
- AI summary va issue highlights

### 2.9 `reporting`

Tong hop snapshot va xuat:

- `PDF`
- `DOCX`
- report draft bang AI

### 2.10 `storage-*`

- `storage-core`: storage abstraction va event lien quan project.
- `storage-crypto`: xu ly storage can bao mat.
- `storage-import`: import pipeline, parse va luu file import.

### 2.11 `ai-*`

- `ai-core`: contract input/output cho AI.
- `ai-agent`: orchestration/agent behavior.
- `ai-model`: model runner, downloader, state store.
- `ai-rag`: retrieval va support data AI.
- `ai-prompt`: prompt/template cho task AI.

## 3. Cau truc state trong workspace

### 3.1 `WorkspaceDataState`

State du lieu lon nhat cua man hinh workspace, gom:

- `activeProjectId`
- `importedFiles`
- `designNodes`
- `designRoutes`
- `constructionProgress`
- `dashboard`
- `selectedNodePhotos`
- `projectPhotos`
- `workVolumeRows`
- `workVolumeProgress`
- `dailyLogs`
- `workCategories`
- `selectedObjectNotes`
- `selectedObjectTasks`
- `materialHandovers`
- `materialDeclarations`
- `workPlans`
- `projectTasks`
- `aiNoteSummary`
- `aiTaskSuggestions`

### 3.2 `WorkspaceUiState`

State dieu huong/UI:

- tab dang chon
- layout mode
- co dang mo report preview hay khong
- `previewNodeCode`

### 3.3 Share state

`PendingSharedImport` gom:

- `IncomingSharePayload`
- `SharedMediaDraft`

Phuc vu luong nhan anh/video tu app ben ngoai roi gan vao node/route trong project.

## 4. Persistence model

## 4.1 Room database

`data/src/main/java/com/mapsupervision/data/db/MapSupervisionDatabase.kt`

DB luu cac nhom du lieu chinh:

- project
- imported file
- gis node/route
- node progress
- work volume progress
- daily log
- note
- task
- photo
- report draft
- material handover/declaration
- work plan
- import session/version/conflict/audit

## 4.2 Project-scoped database

`ProjectScopedDatabaseProvider` la thanh phan rat quan trong.

No thuc hien:

- Xac dinh project co dang dung `PROJECT_DB` hay khong.
- Mo file DB rieng theo `project.projectDbPath`.
- Neu can, copy du lieu tu legacy/shared DB sang scoped DB.
- Bat `PRAGMA foreign_keys = ON`.
- Hydrate bang cot loi nhu imported files, nodes, routes, progress, daily log, work categories, work plan.
- Duy tri cleanup job de dong DB nhan roi.

## 4.3 Y nghia nghiep vu

Loi ich cua `project-scoped DB`:

- Tach biet du lieu giua cac du an.
- De backup/export tung du an.
- Giam rui ro query cheo du an.
- Tao duong cho local-first/offline-first.

Gia phai quan ly:

- Migration phuc tap hon.
- Can dong bo bridge trong giai doan chuyen doi.
- De sinh loi neu repository nao van doc shared DB sai thoi diem.

## 5. Repository contracts can nho

### 5.1 `ProjectRepository`

Cung cap:

- `create`
- `list`
- `clone`
- `archive`
- `importProject`
- `clearProject`
- `touch`
- `updateStoragePath`

### 5.2 `ImportRepository`

Cung cap 5 diem vao chinh:

- `importFile`
- `inspectExcel`
- `inspectNonExcelFields`
- `importNonExcelWithMapping`
- `importExcelWithMapping`

Day la contract trung tam cho `DATA Hub`.

### 5.3 `ProjectSyncRepository`

Phat `ProjectSyncEvent(projectId, reason, updatedAtEpochMs)` qua `SharedFlow`.

## 6. Storage vat ly

App luu file import theo pipeline:

- `imports/pending`
- `imports/processed`
- `imports/failed`

Anh/video va artifact export cung duoc dat theo root storage cua project thong qua `ProjectStorageManager` va `IPhotoPipelineService`.

## 7. Data flow lien module

Flow tong quat:

1. UI trong `app` goi action/ViewModel.
2. ViewModel goi repository contract/use case trong `domain`.
3. `data`/`storage-*` xu ly DB, file, migration, import.
4. Ket qua duoc dua ve state.
5. Neu du lieu thay doi theo project, `ProjectSyncRepository` phat event cho cac feature khac refresh.

## 8. Khu vuc de sinh bug cao

- Chuyen doi giua shared DB va scoped DB.
- Import remap file da ton tai va replace geometry.
- Dong bo state giua `WorkspaceViewModel` va cac ViewModel feature.
- Share intent media khi project active chua san sang.
- Export report/package trong luc project vua thay doi.
