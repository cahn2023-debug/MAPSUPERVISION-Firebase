# Tab Nhap Lieu `DATA Hub`

Tai lieu nay tong hop tinh nang va luong du lieu hien co cua tab nhap lieu trong workspace, dong thoi map no voi huong kien truc P0-P11.

## Pham vi

- Tab `DATA` trong workspace, gan voi `DataHubRoute`, `DataHubScreen`, `DataHubViewModel` va `WorkspaceViewModel`.
- Hai che do chinh trong cung tab:
  - `Nhap thiet ke`
  - `Cap nhat thi cong`

## 1. Tinh nang chinh

### 1.1 Chuyen che do trong tab

- `Nhap thiet ke`: tap trung vao import file va quan ly file da nhap.
- `Cap nhat thi cong`: tap trung vao cap nhat khoi luong thuc te, note va task theo doi tuong.

### 1.2 Chon va tai file

- FAB mo file picker.
- 1 file:
  - `xlsx` / `xls` -> preview va mapping Excel.
  - `kml` / `kmz` / `geojson` / `json` -> preview va mapping non-Excel.
  - File con lai -> import truc tiep.
- Nhieu file:
  - Bo qua mapping dialog.
  - Di thang vao luong import lo.

### 1.3 Mapping Excel truoc khi ghi DB

- Doc danh sach sheet.
- Doc header va sample rows.
- AI goi y cot:
  - ma doi tuong
  - toa do
  - nha thau
  - so hien thi ban do
  - loai doi tuong
  - cot khoi luong
- Nguoi dung xac nhan mapping roi moi parse.

### 1.4 Mapping non-Excel truoc khi ghi DB

- Ap dung cho `kml`, `kmz`, `geojson`, `json`.
- Doc metadata va sample fields.
- AI goi y anh xa:
  - truong vi tri / toa do
  - nha thau
  - map number
  - loai doi tuong
  - truong khoi luong / chieu dai tuyen
- Nguoi dung xac nhan cac co `confirmed*` roi moi parse.

### 1.5 Quan ly file da nhap

- Hien thi danh sach `importedFiles`.
- Mo lai mapping khi bam dup vao file da nhap.
- Xoa file nhap.
- Gop file.
- Keo-tha de mo luong gop file.

### 1.6 Loc va tim kiem

- Tim kiem theo ma doi tuong.
- Loc theo nha thau.
- Loc theo loai doi tuong.
- Sap xep theo ma A-Z / Z-A.

### 1.7 Cap nhat khoi luong thi cong tai cho

- Nhap `actualQty` theo node va work item.
- Cap nhat ngay len state de UI phan hoi nhanh.
- Ghi DB debounce sau 450ms.

### 1.8 Note va task theo doi tuong

- Mo bottom sheet cua node / route.
- Load note va task theo `objectCode`.
- Them / xoa / cap nhat note.
- Them / xoa / doi trang thai task.
- AI co the:
  - tom tat note
  - goi y task

### 1.9 Mo doi tuong tren ban do

- Bam dup card node / route trong grid de focus len map.

## 2. Luong du lieu nhap thiet ke

### 2.1 1 file Excel

1. Nguoi dung chon `xlsx` / `xls`.
2. `DataHubScreen` goi `loadExcelPreview(uri, existingFileId?)`.
3. `ImportRepository.inspectExcel()` doc sheet, header, sample rows.
4. AI bo sung goi y mapping.
5. State tam luu vao `excelParserUi`.
6. Nguoi dung xac nhan mapping va bam parse.
7. `parseExcelToDesign()` goi `ImportRepository.importExcelWithMapping(...)`.
8. `UserFileImportService` copy file vao `imports/pending`, parse, roi chuyen sang `processed` hoac `failed`.
9. `commitExcelImportDraft()` ghi:
   - `imported_files`
   - `gis_node`
   - `gis_route`
   - hoac replace geometry neu remap file cu.
10. Cap nhat `WorkspaceState`, dashboard, map filter va phat event sync.

### 2.2 1 file non-Excel

1. Nguoi dung chon `kml`, `kmz`, `geojson`, `json`.
2. `DataHubScreen` goi `loadNonExcelPreview(uri, existingFileId?)`.
3. `ImportRepository.inspectNonExcelFields()` doc metadata va sample rows.
4. AI goi y mapping.
5. State tam luu vao `importMappingUi`.
6. Nguoi dung xac nhan cac field bat buoc.
7. `parseNonExcelToDesign()` goi `ImportRepository.importNonExcelWithMapping(...)`.
8. `UserFileImportService` copy file vao `imports/pending`, parse, roi chuyen sang `processed` hoac `failed`.
9. `commitNonExcelImportDraft()` ghi `imported_files`, `gis_node`, `gis_route` hoac replace geometry neu remap.
10. Cap nhat state va phat event sync.

### 2.3 Import truc tiep nhieu file

1. Nguoi dung chon nhieu file hoac file khong di qua mapping dialog.
2. `WorkspaceViewModel.importDesignFiles()` xu ly tung file.
3. `ImportRepository.importFile(projectId, uri)` parse theo extension.
4. `UserFileImportService` luu file vao `pending -> processed / failed`.
5. Moi file tao `ImportedFile`, deduplicate node / route, batch upsert DB.
6. Cuoi dot cap nhat `importUi`, dashboard, map filter va event sync.

### 2.4 Deduplicate truoc khi ghi

- So khop theo:
  - ma doi tuong
  - ten gan dung
  - bucket toa do
  - tuyen start/end
- Co cham diem rui ro:
  - `high`
  - `medium`
  - `low`
- Ket qua duoc dung de sinh canh bao cho nguoi dung.

## 3. Luong cap nhat thi cong trong cung tab

### 3.1 Khoi luong thuc te

1. Nguoi dung nhap so vao cong khoi luong cua node va hang muc.
2. `updateWorkVolumeProgress(nodeCode, workName, progress)` cap nhat ngay state.
3. Sau 450ms khong go tiep:
   - `WorkVolumeProgressRepository.upsert(...)`
   - phat `material_progress_updated`

### 3.2 Note va task

1. Nguoi dung mo bottom sheet cua doi tuong.
2. `loadNotesAndTasks(objectCode)` doc tu `NoteRepository` va `TaskRepository`.
3. Moi thao tac them / xoa / cap nhat deu ghi xuong DB roi reload lai danh sach.

### 3.3 AI tren note

- `summarizeNotes(objectCode)`:
  - lay toan bo note hien co
  - goi `aiFacade`
  - tra ve tom tat
- `suggestTasks(objectCode)`:
  - lay note + task hien co
  - goi `aiFacade`
  - tra ve danh sach task goi y

## 4. Luong gop file va xoa file

### 4.1 Gop file

1. Nguoi dung chon 2 file de gop.
2. Tao 1 `ImportedFile` moi.
3. Chuyen `importedFileId` cua node / route sang file moi.
4. Ghi node / route merged vao DB.
5. Soft-delete 2 ban ghi `imported_files` cu.

### 4.2 Xoa file nhap

- `deleteImportedFile(fileId)` goi `ImportedFileRepository.deleteById(fileId)`.
- Hien tai chi xoa metadata file, khong tu dong xoa geometry lien quan.

## 5. Du lieu duoc luu o dau

### 5.1 State tam trong UI

- `DataHubViewModel` giu:
  - tab con dang chon
  - search text
  - filter nha thau
  - filter loai doi tuong
  - sort order
  - trang thai bottom sheet note / task
- `WorkspaceState` giu:
  - `importUi`
  - `excelParserUi`
  - `importMappingUi`
  - `importedFiles`
  - `designNodes`
  - `designRoutes`
  - `workVolumeRows`
  - `selectedObjectNotes`
  - `selectedObjectTasks`
  - du lieu AI tam thoi

### 5.2 Luu file vat ly

- File nhap di qua:
  - `imports/pending`
  - `imports/processed`
  - `imports/failed`
- Duong dan duoc tao tu `ProjectStorageManager.projectRoot(...)`.
- `storedPath` cua file sau xu ly duoc luu vao DB.

### 5.3 Luu DB theo du an

- He thong ho tro project-scoped database qua `ProjectScopedDatabaseProvider`.
- File DB du an nam o `project.projectDbPath`.

### 5.4 Cac bang chinh

- `imported_files`
- `gis_node`
- `gis_route`
- `work_volume_progress`
- `note`
- `task`

## 6. Tam nhin kien truc P0-P11

- P0: Audit kien truc va dependency
- P1: Chuan hoa domain model + canonical feature
- P2: Chuan hoa database, UUID, FK, audit
- P3: Tach import engine thanh pipeline
- P4: Chuan hoa geometry va input data
- P5: Refactor state, memory, paging
- P6: Event-driven synchronization
- P7: Garbage collector, cascade delete, versioning
- P8: AI integration va conflict resolution
- P9: Hieu nang, spatial index, streaming, cache
- P10: Kiem thu va hardening production
- P11: Documentation, release checklist, verification gates

## 7. Checklist P7-P11 de khong bo sot

### P7 - Garbage collector, cascade delete, versioning, rollback

- [x] Co repository luu version / audit / conflict cho import lifecycle.
- [x] Co purge job theo `deletedAt` / `completedAt` de don rac du lieu cu.
- [x] Co rollback theo version cho import session.
- [x] Co dao cua imported file / node / route / progress / note / task de support GC.

### P8 - AI integration, conflict resolution engine, confidence routing

- [x] Co conflict policy co ban de route `low` / `medium` / `high`.
- [x] Co event outbox writer de chuan bi dong bo event.
- [x] Co hook ghi lifecycle khi import duoc commit.
- [x] Co AI decision routing day du theo moi nhanh import / mapping / merge.

### P9 - Performance: streaming parser, spatial index, cache, batch writes

- [x] Co cache va index noi suy tren workspace state va repository layer.
- [x] Co batch-style upsert / reload theo dot import va progress.
- [x] Co streaming parser end-to-end cho file lon va du lieu non-Excel.
- [x] Co spatial index / geospatial query toi uu cho loc map lon.

### P10 - Test suite: unit, integration, stress, recovery, migration

- [x] Co unit test cho helper import / material / conflict policy.
- [x] Co full `app:testDebugUnitTest` xanh.
- [x] Co full `data:testDebugUnitTest` xanh, bao gom legacy migration / provider / storage tests.
- [x] Co integration test cho import -> domain -> database -> map -> progress.
- [x] Co stress / recovery / migration test cho du lieu lon va rollback.

### P11 - Documentation, release checklist, verification gates

- [x] Co tai lieu tong hop tab nhap lieu trong `docs/`.
- [x] Co checklist phase de doi chieu truoc khi merge.
- [x] Co release gate / runbook cuoi cung trong `docs/release_gate_runbook.md`.

### Ghi chu xac nhan

- `:app:testDebugUnitTest` xanh sau khi hoan tat P8-P10.
- `:storage-import:testDebugUnitTest` xanh, gom ca parser GeoJSON streaming.
- Cac test moi cua `data` cho outbox dispatch va rollback/purge xanh khi chay theo nhom muc tieu.
- `:data:testDebugUnitTest` full module da xanh sau khi chinh legacy migration expectations len schema 42.
- Release gate va rollback runbook da duoc dong goi trong `docs/release_gate_runbook.md`.
- Release gate nay da duoc gan vao GitHub Actions trong `.github/workflows/android.yml`.

## 8. Ket luan ngan

DATA Hub dang di theo huong:

- Canonical data model: moi nguon du lieu ve cung mot model trung gian.
- Domain-driven: nghiep vu quan trong di qua service / repository ro rang.
- Event-driven: import, progress, material va task co the phat event de cac tab khac dong bo.
- Scalable: de mo rong sang module tuong lai ma khong doi core architecture.
