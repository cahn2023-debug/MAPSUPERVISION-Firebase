# Tong hop du an MapSupervision - version 1.1

## 1. Thong tin phat hanh

- Ten ung dung: `MapSupervision`
- Application id: `com.mapsupervision`
- Version name: `1.1`
- Version code: `2`
- Nen tang: Android
- Min SDK: `24`
- Target SDK: `35`
- Compile SDK: `36`
- Java/Kotlin target: `17`

## 2. Du an nay la gi

`MapSupervision` la ung dung Android multi-module phuc vu giam sat cong trinh tren nen ban do so. He thong duoc thiet ke theo huong local-first, lam viec theo `project active`, cho phep nap du lieu thiet ke, theo doi tien do hien truong, ghi nhan media, su dung AI ho tro va xuat bao cao.

## 3. Cac khoi chuc nang lon

### 3.1 Project va workspace

- Tao, chon, clone, archive, import/export project.
- Theo doi `activeProjectId` de dong bo toan bo workspace.
- Ho tro du lieu theo `project-scoped database`.

### 3.2 DATA Hub va import

- Import `Excel`, `KML`, `KMZ`, `GeoJSON`, `JSON`, `DOCX`.
- Preview va mapping cot/field truoc khi commit.
- Quan ly file qua cac trang thai `pending`, `processed`, `failed`.
- Ho tro remap va repair geometry.

### 3.3 GIS va giam sat

- Hien thi node/route tren map.
- Loc theo contractor, vat tu, tim doi tuong.
- Theo doi signal/progress/moc thi cong tai doi tuong.

### 3.4 Progress, nhat ky va vat tu

- Cap nhat tien do thi cong.
- Quan ly daily log, work plan, task, note.
- Theo doi material declaration va material handover.

### 3.5 Media hien truong

- Chup anh/quay video trong app.
- Nhan media tu gallery hoac share intent Android.
- Gan GPS, object code, watermark/stamp va luu theo project.

### 3.6 AI

- Goi y mapping import.
- Tom tat timeline va note.
- Goi y task tiep theo.
- Draft bao cao va chat theo context project.

### 3.7 Reporting va package

- Xuat `PDF`.
- Xuat `DOCX`.
- Dong goi du an thanh `ZIP`.

## 4. Cau truc module

Workspace hien co 18 module source va 1 khoi `buildSrc`:

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
- `buildSrc`

Tom tat vai tro:

- `app`: shell Android, navigation, state workspace, widget, worker.
- `core`: utility, logging, result, UI nen.
- `domain`: model, repository contract, use case.
- `data`: Room DB, DAO, migration, repository implementation.
- `project/gis/photo/timeline/reporting`: cac feature module.
- `storage-*`: storage root, crypto, parser/import, package.
- `ai-*`: contract, orchestration, model runtime, retrieval, prompt.

## 5. Kien truc runtime va du lieu

### 5.1 Runtime Android

- `MapSupervisionApplication` khoi tao logger, map bridge, image loader va WorkManager.
- `MainActivity` la entry point Compose va xu ly share intent media.
- `WorkspaceAppShell` dieu huong 5 route chinh:
  - `map`
  - `progress`
  - `data`
  - `reports`
  - `materials`

### 5.2 Luong du lieu

1. UI/ViewModel trong `app` phat action.
2. `domain` dinh nghia contract va use case.
3. `data` va `storage-*` xu ly DB, file, import/export.
4. `ProjectSyncRepository` phat event de refresh cac feature.
5. State quay lai workspace va man hinh con.

### 5.3 Persistence

- Room la persistence chinh.
- Repo co ca shared DB va project-scoped DB.
- `ProjectScopedDatabaseProvider` la diem trung tam cho open DB, hydrate va bridge du lieu.

## 6. Build, CI va release

### 6.1 Build

```powershell
.\gradlew.bat :app:assembleRelease
```

### 6.2 Release gate thuc te

Script: `scripts/release_gate.sh`

Gate hien tai chay:

- `:app:testDebugUnitTest`
- `:storage-import:testDebugUnitTest`
- `:data:testDebugUnitTest`
- `lint`
- `assembleDebug`
- `enforceModuleBoundaries`

### 6.3 CI

GitHub Actions file:

- `.github/workflows/android.yml`

CI dang goi truc tiep `scripts/release_gate.sh`.

## 7. Hien trang ma nguon

So file `.kt/.kts` theo module lon:

- `data`: 161
- `app`: 97
- `domain`: 95
- `ai-model`: 19
- `photo`: 15

Tin hieu test hien tai:

- Nhieu test nhat nam o `app`, `data`, `ai-agent`, `gis-maplibre`, `photo`.
- Cac module `ai-core`, `ai-model`, `ai-rag`, `ai-prompt` hien chua co test rieng dang ke.

## 8. Rui ro ky thuat can ghi nho

- Bridge shared DB va project-scoped DB.
- Import remap/repair co the thay doi geometry da co.
- `WorkspaceViewModel` la orchestration layer lon, de tao tac dong lien tab.
- Reporting va package lay du lieu tong hop tu nhieu module nen de bi anh huong day chuyen.
- AI stack da tach module ro nhung test hien chua deu.

## 9. Artifact hien co trong `docs`

- `MapSupervision_v1.1_release.zip`
- `mapsupervision_logo.png`
- `database_tables.html`

## 10. Tai lieu tham chieu de doc tiep

- `README.md`
- `tong_quan_kien_truc_toan_du_an.md`
- `module_matrix_chi_tiet.md`
- `build_kiem_thu_va_release.md`
- `android_kien_truc_tong_quan.md`
- `android_cau_truc_module_va_du_lieu.md`
