# Tong quan kien truc toan du an

## 1. Muc tieu cua workspace

`MapSupervision` la du an Android multi-module phuc vu giam sat cong trinh theo huong local-first. He thong uu tien:

- Lam viec theo `project active`.
- Nap du lieu thiet ke/GIS tu nhieu dinh dang.
- Quan ly node, route, tien do, nhat ky, vat tu, note va task.
- Ghi nhan media hien truong co GPS, stamp va lien ket doi tuong.
- Ho tro AI on-device/cloud cho tom tat, mapping va draft noi dung.
- Xuat bao cao va dong goi du an de backup/chia se.

## 2. To chuc codebase

Workspace duoc khai bao trong `settings.gradle.kts` voi 18 module:

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

Ngoai ra con co `buildSrc` de chua custom Gradle task.

## 3. Layering tong quat

### 3.1 Presentation va orchestration

- `app` la shell Android chinh, gom `MapSupervisionApplication`, `MainActivity`, `WorkspaceAppShell`, widget, worker, theming va navigation.
- `project`, `gis`, `photo`, `timeline`, `reporting` la cac feature module tap trung vao UI va logic cap man hinh.

### 3.2 Domain va contract

- `domain` chua model nghiep vu, repository contract, use case va service contract.
- `core` chua utility dung chung nhu logging, result, error, coroutine support va UI component nen.

### 3.3 Data, storage va import

- `data` chua Room database, DAO, repository implementation, migration va project-scoped database provider.
- `storage-core` chua active project state, sync event, package/export va quan ly thu muc luu tru.
- `storage-crypto` chua logic ma hoa khi can luu payload nhay cam.
- `storage-import` chua parser va import pipeline cho Excel, KML, KMZ, GeoJSON, JSON, DOCX.

### 3.4 AI stack

- `ai-core` chua contract va facade cap cao.
- `ai-agent` chua orchestration va tong hop ket qua AI.
- `ai-model` chua model engine, device capability, worker, cloud/local runner.
- `ai-rag` chua bo dung tai lieu cho retrieval.
- `ai-prompt` chua prompt, normalizer va parser cho lenh ngon ngu tu nhien.

## 4. Ranh gioi phu thuoc module

Repo dang ep ranh gioi module bang custom task `enforceModuleBoundaries`.

Nguyen tac thuc te:

- `app` duoc phep ghep nhieu module nhat.
- `core` khong duoc phu thuoc module noi bo nao khac.
- `domain` chi phu thuoc `core`.
- `data` khong duoc tham chieu nguoc lai cac feature UI module.
- `gis-maplibre` chi la lop render phu thuoc `gis` va `domain`.

Dieu nay giup repo giu layering ro rang va giam coupling giua UI, domain va persistence.

## 5. Luong du lieu tong quat

1. UI trong `app` hoac feature module phat action.
2. ViewModel goi use case/repository contract trong `domain`.
3. `data` va `storage-*` xu ly DB, file, migration, import/export.
4. `ProjectSyncRepository` phat su kien refresh cho cac man hinh lien quan.
5. Ket qua duoc day nguoc len `WorkspaceViewModel`, `ProjectViewModel`, `TimelineViewModel`, `ReportingViewModel`, `PhotoViewModel`.

## 6. Hai truc trung tam cua he thong

### 6.1 Project active

- `ActiveProjectRepository` quan ly `activeProjectId`.
- Gan nhu moi flow nghiep vu deu xoay quanh project dang active.
- Chuyen project active se lam nap lai snapshot, map, import state, progress, reporting va media.

### 6.2 Project-scoped database

- Moi project co the duoc gan file DB rieng.
- `ProjectScopedDatabaseProvider` mo scoped DB, seed du lieu, hydrate bang cot loi va bridge voi shared DB trong giai doan chuyen doi.
- Day la diem nhay cam nhat ve migration, import/export, backup va consistency.

## 7. Runtime Android

- `MapSupervisionApplication` khoi tao logger, map bridge, image loader va `WorkManager`.
- `MainActivity` la entry point Compose va xu ly share intent media.
- `WorkspaceAppShell` dieu huong 5 tab chinh: `map`, `progress`, `data`, `reports`, `materials`.
- App ho tro phone/tablet qua `COMPACT` va `EXPANDED`.

## 8. Chuc nang nghiep vu lon

### 8.1 Project va workspace

- Tao, chon, clone, archive, import/export project.
- Dong bo state theo project active.

### 8.2 GIS va DATA Hub

- Nap file thiet ke.
- Map field cho Excel va non-Excel.
- Hien thi node/route tren map, loc theo contractor va vat tu.

### 8.3 Progress va timeline

- Quan ly tien do thi cong.
- Ghi nhat ky, cong viec, ke hoach va dashboard.

### 8.4 Photo va media

- Chup anh/video, nhan media tu gallery hay share intent.
- Dong dau GPS, object code, thong tin hien truong.

### 8.5 Reporting

- Tao snapshot du lieu.
- Draft noi dung bang AI.
- Export `PDF`, `DOCX`, `ZIP`.

### 8.6 AI

- Mapping suggestion cho import.
- Note summary, task suggestion.
- Timeline summary va report draft.
- Chat/ngon ngu tu nhien dua tren context project.

## 9. Build va CI

- Root build dung Android Gradle Plugin `8.13.2`, Kotlin `2.2.21`, Java 17.
- CI GitHub Actions goi `scripts/release_gate.sh`.
- Gate thuc te chay:
  - `:app:testDebugUnitTest`
  - `:storage-import:testDebugUnitTest`
  - `:data:testDebugUnitTest`
  - `lint`
  - `assembleDebug`
  - `enforceModuleBoundaries`

## 10. Riem hot ky thuat

- Bridge giua shared DB va scoped DB.
- Import remap co the thay doi geometry va du lieu lien ket.
- `WorkspaceViewModel` la orchestration layer lon, de tao blast radius lien tab.
- AI stack phan tan qua nhieu module nhung test hien con thua o `ai-core`, `ai-model`, `ai-rag`, `ai-prompt`.
- Release gate hien duoc dinh nghia o cap repo, khong chi rieng DATA Hub.

## 11. Tai lieu nen doc tiep

- `module_matrix_chi_tiet.md`
- `android_kien_truc_tong_quan.md`
- `android_cau_truc_module_va_du_lieu.md`
- `build_kiem_thu_va_release.md`
