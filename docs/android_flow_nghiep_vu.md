# Flow nghiep vu Android

## 1. Flow khoi dong app

1. `MapSupervisionApplication` khoi tao logger, MapLibre bridge, Coil, WorkManager.
2. `MainActivity.onCreate()` parse share payload neu app duoc mo bang `SEND` hoac `SEND_MULTIPLE`.
3. `StartupPermissionWrapper` kiem tra location va camera.
4. Sau khi du quyen, app render `WorkspaceAppShell`.
5. `WorkspaceAppShell` tao/cap `WorkspaceViewModel`, `ProjectViewModel`, `ReportingViewModel`, `GemmaChatViewModel`.

## 2. Flow chon va kich hoat project

1. `ProjectViewModel.refresh()` lay danh sach project.
2. Neu chua co `activeProjectId` va co project ton tai, app tu chon project dau tien.
3. `WorkspaceViewModel.observeWorkspace()` lang nghe `activeProjectRepository.activeProjectId`.
4. Khi project thay doi:
   - kiem tra migration neu can
   - subscribe `ObserveWorkspaceSnapshotUseCase`
   - nap lai state workspace

## 3. Flow tao project moi

1. Nguoi dung nhap ten project.
2. `ProjectViewModel.createProject(name, customPath?)` validate `name`.
3. `ProjectRepository.create(...)` tao project metadata.
4. `ActiveProjectRepository.setActive(created.id)` mo project vua tao.
5. `refresh()` nap lai danh sach.

Luu y:

- Neu tao thanh cong nhung set active that bai, UI van hien message thong bao.

## 4. Flow import thiet ke

## 4.1 Luong file don gian

1. UI goi `WorkspaceViewModel.importDesignFiles(uris)`.
2. Voi tung file, `ImportRepository.importFile(projectId, uri)` parse theo extension.
3. `UserFileImportService` dua file qua `pending -> processed/failed`.
4. Tao `ImportedFile`, upsert nodes/routes/progress lien quan.
5. Phat `ProjectSyncEvent`.

## 4.2 Luong Excel co preview/mapping

1. `loadExcelPreview(uri, existingFileId?)`.
2. `ImportRepository.inspectExcel(...)` doc sheet, header, sample rows.
3. AI goi y mapping.
4. Nguoi dung xac nhan.
5. `parseExcelToDesign()` goi `importExcelWithMapping(...)`.
6. Draft duoc commit vao DB va cap nhat dashboard/map.

## 4.3 Luong KML/KMZ/GeoJSON/JSON

1. `loadNonExcelPreview(uri, existingFileId?)`.
2. `inspectNonExcelFields(...)`.
3. AI goi y mapping field.
4. Nguoi dung xac nhan field bat buoc.
5. `parseNonExcelToDesign()` goi `importNonExcelWithMapping(...)`.
6. App ghi geometry vao DB, co the replace geometry cua file cu khi remap.

## 4.4 Hanh vi sau import

Sau import thanh cong, he thong thuong se:

- touch project
- cap nhat imported files
- cap nhat state map
- cap nhat dashboard
- phat `ProjectSyncEvent`

## 5. Flow xem va thao tac tren ban do

1. `WorkspaceAppShell` render route `map`.
2. `MapHubScreen` nhan:
   - `designNodes`
   - `designRoutes`
   - `mapUi`
   - material progress
   - filter contractor/material
3. Nguoi dung co the:
   - chon node
   - chon route
   - tim kiem
   - loc nha thau
   - loc loai vat tu
   - doi base map
   - bat/tat do khoang cach
   - zoom
   - my location
4. `WorkspaceViewModel` xu ly va dua lai state cho UI.

## 6. Flow cap nhat tien do va nhat ky

### 6.1 Node progress

1. Nguoi dung them/cap nhat `planned`, `actual`.
2. `TimelineViewModel.addProgress(...)` hoac action tu workspace ghi `NodeProgress`.
3. `remain` va `delayed` duoc tinh lai.
4. `ObserveTimelineUseCase` combine progress + logs + photo count.

### 6.2 Daily log

1. Nguoi dung nhap work item, manpower, note.
2. `dailyLogRepository.add(...)` ghi DB.
3. Timeline/report/workspace refresh lai qua event hoac snapshot.

## 7. Flow note/task/AI tren doi tuong

1. Nguoi dung mo node/route detail.
2. `loadNotesAndTasks(objectCode)` nap note va task.
3. Co the:
   - them note
   - xoa note
   - them task
   - toggle task
4. `summarizeNotes(objectCode)` goi AI de tom tat note.
5. `suggestTasks(objectCode)` goi AI de de xuat task tiep theo.

## 8. Flow media: chup anh, import gallery, share intent

### 8.1 Chup anh/video tu app

1. `PhotoViewModel.createCaptureFile()` hoac `createCaptureVideoFile()`.
2. `IPhotoPipelineService` tao output file theo project storage ref.
3. Sau khi chup xong, `registerCapturedPhoto(...)`:
   - lay vi tri cuoi
   - watermark/stamp anh
   - luu `SitePhoto`
4. Workspace/report/photo module co the doc lai ngay.

### 8.2 Import tu gallery

1. UI goi `importMediaFromGallery(...)`.
2. Pipeline copy/normalize file vao storage project.
3. Gan node/route/engineer neu co.

### 8.3 Nhan media tu app ben ngoai

1. Android intent vao `MainActivity`.
2. `ShareIntentParser` loc image/video hop le.
3. Tao `IncomingSharePayload`.
4. `WorkspaceAppShell` day payload vao `PendingSharedImport`.
5. `ShareImportSheet` cho nguoi dung:
   - chon project
   - chon target `NODE` hoac `ROUTE`
   - chon object code
6. Xac nhan xong moi ghi media vao he thong.

## 9. Flow AI

## 9.1 Timeline AI

`TimelineViewModel.refresh()`:

1. lay `TimelineSnapshot`
2. map sang payload AI
3. goi `AIFacade.execute(TimelineSummaryPayload)`
4. dua ve `aiSummary` va `aiHighlights`

## 9.2 Reporting AI

`ReportingViewModel.requestReportDraft(projectId)`:

1. dam bao report snapshot hop le
2. tranh goi lai neu request giong nhau
3. goi AI tao draft
4. cache vao `ReportingSnapshot.aiDraft`

## 9.3 Workspace AI

`WorkspaceViewModel` con dong vai tro orchestration cho:

- AI note summary
- AI task suggestion
- chat/ngon ngu tu nhien
- import mapping suggestion

## 10. Flow xuat bao cao va dong goi du an

### 10.1 Bao cao PDF/DOCX

1. `ReportingViewModel.refreshReportData()` tai `ReportingSnapshot`.
2. Neu can, `requestReportDraft()` tao noi dung AI draft.
3. `exportPdf()` hoac `exportWord()` tao `exportContent`.
4. Generator module ghi file va tra ve duong dan.
5. `WorkspaceEffect.OpenExportedFile` co the mo file tu UI.

### 10.2 ZIP package

1. `ReportingViewModel.exportPackageZip()`.
2. Lay `slug` cua project active.
3. `ProjectPackageService.exportProjectZip(slug)`.
4. UI nhan file path de chia se/backup.

## 11. Flow import/export project

### 11.1 Export

`ProjectViewModel.exportProject(...)`:

1. Nap nodes, routes, notes, tasks, material progress, daily logs, imported files, photos, progress.
2. Dong goi thanh JSON lon kem metadata version.
3. Ghi file export.

### 11.2 Import

`ProjectViewModel.importProject(...)` co vai tro:

- doc file zip/json
- xu ly overwrite hoac create copy
- ghi lai metadata/project data
- phat event sync va refresh

## 12. Cac diem giao nhau de canh giac khi sua code

- Import xong map/report/timeline/photo deu co the can refresh.
- Chuyen project active se lam thay doi gan nhu toan bo state.
- Chup anh va import media co lien quan ca storage, DB, location va UI pending state.
- Bao cao dung du lieu tong hop tu nhieu module, nen bug du lieu co the den tu import, photo, progress hoac daily log.
