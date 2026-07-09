# Ma tran module chi tiet

Tai lieu nay tong hop module cap build, vai tro chinh, mot so file tieu bieu va tin hieu test de team nho nhanh blast radius truoc khi sua code.

## 1. Tong quan nhanh

| Module | Vai tro | So file `.kt/.kts` | So test file |
| --- | --- | ---: | ---: |
| `app` | Android shell, navigation, orchestration | 97 | 37 |
| `core` | utility, logging, UI nen | 12 | 1 |
| `domain` | model, contract, use case | 95 | 4 |
| `data` | Room DB, DAO, migration, repo impl | 161 | 16 |
| `project` | project lifecycle UI | 4 | 1 |
| `gis` | GIS UI va state | 4 | 1 |
| `gis-maplibre` | bridge render MapLibre | 6 | 4 |
| `photo` | media pipeline, stamp, GPS | 15 | 4 |
| `timeline` | progress, daily log, timeline UI | 4 | 1 |
| `reporting` | snapshot, PDF, DOCX, export UI | 10 | 1 |
| `storage-core` | active project, sync, package, storage | 10 | 2 |
| `storage-crypto` | ma hoa storage | 3 | 1 |
| `storage-import` | parser va import pipeline | 13 | 2 |
| `ai-core` | AI facade va contract | 14 | 0 |
| `ai-agent` | orchestration AI | 11 | 8 |
| `ai-model` | model runner va capability | 19 | 0 |
| `ai-rag` | retrieval support | 2 | 0 |
| `ai-prompt` | prompt, parser, normalizer | 8 | 0 |
| `buildSrc` | custom Gradle task | 3 | 1 |

## 2. Module theo nhom

### 2.1 `app`

- Muc dich: ghep tat ca module thanh ung dung Android hoan chinh.
- File tieu bieu:
  - `MapSupervisionApplication.kt`
  - `MainActivity.kt`
  - `WorkspaceAppShell.kt`
  - `WorkspaceViewModel.kt`
  - `DataHubRoute.kt`
  - `ProgressHubRoute.kt`
- Diem can nho:
  - blast radius lon nhat vi chua state va navigation chung
  - xu ly share intent, widget, worker va AI bridge

### 2.2 `core`

- Muc dich: utility va UI foundation dung chung.
- File tieu bieu:
  - `AppLogger.kt`
  - `AppResult.kt`
  - `AppExceptions.kt`
  - `DispatcherProvider.kt`

### 2.3 `domain`

- Muc dich: model nghiep vu, repository contract, use case.
- Repo contract tieu bieu:
  - `ProjectRepository.kt`
  - `ImportRepository.kt`
  - `PhotoRepository.kt`
  - `ProjectSyncRepository.kt`
  - `LocalLlmRepository.kt`
- Ghi chu:
  - day la lop "ngon ngu chung" cua toan he thong
  - khong nen dua Android framework detail vao day

### 2.4 `data`

- Muc dich: hien thuc persistence va migration.
- File tieu bieu:
  - `MapSupervisionDatabase.kt`
  - `ProjectScopedDatabaseProvider.kt`
  - `ProjectStorageMigrationService.kt`
  - `ProjectBridgeNormalization.kt`
  - `DataModule.kt`
- Ghi chu:
  - la module lon nhat ve source
  - xu ly Room, bridge shared DB/scoped DB, import lifecycle va repository implementation

### 2.5 `project`

- Muc dich: quan ly danh sach project va cac thao tac vong doi.
- File tieu bieu:
  - `ProjectScreen.kt`
  - `ProjectViewModel.kt`

### 2.6 `gis`

- Muc dich: logic va UI ban do doc lap renderer cu the.
- File tieu bieu:
  - `GisScreen.kt`
  - `GisViewModel.kt`

### 2.7 `gis-maplibre`

- Muc dich: renderer MapLibre.
- File tieu bieu:
  - `MapBridgeInstaller.kt`
- Ghi chu:
  - day la module bridge, can giu ranh gioi ro voi `gis`

### 2.8 `photo`

- Muc dich: camera, gallery, geotag, stamp va media pipeline.
- File tieu bieu:
  - `PhotoViewModel.kt`
  - `PhotoPipelineService.kt`
  - `PhotoStampRenderer.kt`
  - `PhotoLocationProvider.kt`

### 2.9 `timeline`

- Muc dich: tong hop progress, daily log, AI summary theo du an.
- File tieu bieu:
  - `TimelineScreen.kt`
  - `TimelineViewModel.kt`

### 2.10 `reporting`

- Muc dich: snapshot va export bao cao.
- File tieu bieu:
  - `ReportingViewModel.kt`
  - `ReportingSnapshot.kt`
  - `PdfReportGenerator.kt`
  - `DocxReportGenerator.kt`
  - `ReportExportBuilder.kt`

### 2.11 `storage-core`

- Muc dich: storage root, package service, event sync, active project state.
- File tieu bieu:
  - `ProjectStorageManager.kt`
  - `ProjectPackageService.kt`
  - `ProjectSyncRepositoryImpl.kt`
  - `ActiveProjectRepositoryImpl.kt`

### 2.12 `storage-crypto`

- Muc dich: ma hoa payload storage.
- File tieu bieu:
  - `ProjectCryptoManager.kt`

### 2.13 `storage-import`

- Muc dich: parser va import helper cho tep thiet ke.
- File tieu bieu:
  - `ExcelParsingHelpers.kt`
  - `GeoJsonStreamingParser.kt`
  - `DocxParser.kt`
  - `ImportPipelineContracts.kt`

### 2.14 `ai-core`

- Muc dich: AI contract, execution policy, facade.
- File tieu bieu:
  - `AIFacade.kt`
  - `AiEngine.kt`
  - `AiExecutionPolicy.kt`
  - `LiteRtSafetyGate.kt`

### 2.15 `ai-agent`

- Muc dich: orchestration va ket hop cac thanh phan AI.
- File tieu bieu:
  - `AiOrchestrator.kt`
  - `SummaryAggregator.kt`

### 2.16 `ai-model`

- Muc dich: local/cloud engine, device capability, model download/use.
- File tieu bieu:
  - `AIManager.kt`
  - `AndroidDeviceCapabilityDetector.kt`
  - `CloudGeminiEngine.kt`
  - `LocalLiteRtEngine.kt`
  - `MediaPipeLlmEngine.kt`

### 2.17 `ai-rag`

- Muc dich: build tai lieu RAG va support retrieval context.
- File tieu bieu:
  - `RagDocumentBuilder.kt`

### 2.18 `ai-prompt`

- Muc dich: parser, resolver va normalizer cho lenh AI.
- File tieu bieu:
  - `ChatActionParser.kt`
  - `ChatDictionaryResolver.kt`
  - `DailyLogCanonicalizer.kt`
  - `CanonicalTextNormalizer.kt`

### 2.19 `buildSrc`

- Muc dich: custom Gradle task noi bo.
- File tieu bieu:
  - `EnforceModuleBoundariesTask.kt`
- Ghi chu:
  - task nay quet cac `build.gradle.kts` de chan phu thuoc sai ranh gioi module

## 3. Goc nhin dependency

- `app` la diem hoi tu cua toan bo feature.
- `data` la diem hoi tu cua persistence va storage service.
- `domain` la hop dong dung chung giua feature va data.
- `storage-core` va `ProjectSyncRepository` la truc chia se state xuyen module.
- `ai-model` la diem tich hop nhieu dependency nhe nhat ve giao dien nhung nang ve runtime AI.

## 4. Khu vuc can uu tien khi doc truoc khi sua

- Sua state workspace: doc `app`, `domain`, `data`, `storage-core`.
- Sua import thiet ke: doc `app/DataHub*`, `storage-import`, `data`, `domain`.
- Sua map/GIS: doc `gis`, `gis-maplibre`, `app/MapHub*`.
- Sua project storage: doc `storage-core`, `storage-crypto`, `data/ProjectScopedDatabaseProvider.kt`.
- Sua AI: doc `ai-core`, `ai-agent`, `ai-model`, `ai-rag`, `ai-prompt`, va cac bridge trong `app`.
