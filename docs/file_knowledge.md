# Cau truc ma nguon va he thong module

Tai lieu nay mo ta codebase `MapSupervision` theo goc nhin ma nguon, layering va trach nhiem tung khoi lon. Muc tieu la giup team nhanh chong xac dinh "sua o dau" va "anh huong den dau".

## 1. Cong nghe va to chuc tong quat

- Nen tang: Android multi-module.
- Ngon ngu chinh: Kotlin.
- UI: Jetpack Compose.
- DI: Hilt.
- Persistence: Room.
- Background work: WorkManager.
- Map renderer: MapLibre.
- AI stack: local/cloud hybrid qua nhom module `ai-*`.

## 2. Ban do ma nguon cap cao

### 2.1 `app`

Khoi ghep chinh cua ung dung:

- application/activity
- navigation shell
- workspace state
- route cho cac tab
- widget va worker
- bridge AI, import, reporting

File can nho:

- `app/.../MapSupervisionApplication.kt`
- `app/.../MainActivity.kt`
- `app/.../WorkspaceAppShell.kt`
- `app/.../WorkspaceViewModel.kt`

### 2.2 `core`

Khoi utility va nen dung chung:

- logging
- exception/result
- dispatcher/coroutine helper
- UI component va theme co ban

### 2.3 `domain`

Noi chua "ngon ngu nghiep vu chung":

- model du an, GIS, progress, photo, report, AI
- repository contract
- use case tong hop snapshot
- service contract de UI/dong bo goi xuong lop duoi

### 2.4 `data`

Noi hien thuc lop du lieu:

- `MapSupervisionDatabase`
- DAO va entity
- repository implementation
- migration/service cho project storage
- logic bridge giua shared DB va project-scoped DB

### 2.5 `project`, `gis`, `photo`, `timeline`, `reporting`

Day la cac feature module huong man hinh:

- `project`: lifecycle project
- `gis`: map UI/state
- `photo`: camera, gallery, stamp, GPS
- `timeline`: progress va nhat ky
- `reporting`: snapshot va export bao cao

### 2.6 `storage-core`, `storage-crypto`, `storage-import`

Khoi ha tang luu tru:

- quan ly root storage theo project
- package/import/export project
- ma hoa payload
- parser tep thiet ke va tai lieu

### 2.7 `ai-core`, `ai-agent`, `ai-model`, `ai-rag`, `ai-prompt`

Khoi AI duoc tach theo vai tro:

- contract va facade
- orchestration
- engine/model runtime
- retrieval support
- prompt va parser

## 3. Layering thuc te trong code

1. `app` va feature module nhan input tu nguoi dung.
2. ViewModel goi contract/use case trong `domain`.
3. `data` va `storage-*` xu ly DB, file, import/export.
4. `ProjectSyncRepository` phat event de cac feature cap nhat.
5. Ket qua quay lai UI qua state flow/snapshot.

## 4. Hai thanh phan co anh huong he thong

### 4.1 `WorkspaceViewModel`

Day la diem orchestration lon nhat cua app:

- gop state map, progress, import, photo, AI, report
- dieu phoi action lien tab
- phat effect mo file export/snackbar

Khi sua file nay can luon nghi den impact toi `map`, `data`, `progress`, `reports`, `materials`.

### 4.2 `ProjectScopedDatabaseProvider`

Day la diem nhay cam nhat cua persistence:

- mo DB rieng theo project
- chuan hoa `projectDbPath`
- seed/hydrate cac bang cot loi
- bridge du lieu giua shared DB va scoped DB

Khi sua import, migration, backup hoac reporting, day la file can doc lai dau tien.

## 5. Mot so cum file can doc theo bai toan

### 5.1 Sua import thiet ke

- `app/.../DataHubRoute.kt`
- `app/.../WorkspaceImport*.kt`
- `storage-import/...`
- `domain/.../ImportRepository.kt`
- `data/...` cac repository/import lifecycle lien quan

### 5.2 Sua map/GIS

- `app/.../MapHubScreen.kt`
- `gis/.../GisScreen.kt`
- `gis/.../GisViewModel.kt`
- `gis-maplibre/.../MapBridgeInstaller.kt`

### 5.3 Sua photo/media

- `photo/.../PhotoViewModel.kt`
- `photo/.../PhotoPipelineService.kt`
- `app/.../CameraOverlay.kt`
- `app/.../ShareIntentParser.kt`

### 5.4 Sua reporting

- `reporting/.../ReportingViewModel.kt`
- `reporting/.../PdfReportGenerator.kt`
- `reporting/.../DocxReportGenerator.kt`
- `storage-core/.../ProjectPackageService.kt`

### 5.5 Sua AI

- `ai-core/...`
- `ai-agent/...`
- `ai-model/...`
- `ai-rag/...`
- `ai-prompt/...`
- cac bridge AI trong `app`

## 6. Diem canh bao ky thuat

- Codebase da duoc tach module nhung state van hoi tu manh o `app`.
- AI stack tach ro theo module nhung test hien chua deu.
- `data` la module lon va nhieu logic nhay cam nhat ve consistency.
- Release gate khong chi kiem tra test ma con ep `lint`, `assembleDebug` va `enforceModuleBoundaries`.

## 7. Tai lieu nen mo kem

- `tong_quan_kien_truc_toan_du_an.md`
- `module_matrix_chi_tiet.md`
- `android_cau_truc_module_va_du_lieu.md`
