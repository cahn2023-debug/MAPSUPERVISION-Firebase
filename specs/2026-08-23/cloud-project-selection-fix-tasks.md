# Tasks: Sửa Lỗi Hiển Thị & Lựa Chọn Danh Sách Dự Án Trên Cloud

Spec: @doc/specs/2026-08-23/cloud-project-selection-fix.md  
Created: 2026-08-23 · Status: **done (2026-08-23)**

## Task List

- [x] **[cloud-proj-01]** Đồng bộ 2 chiều dữ liệu Catalog trên Data Layer & Webapp Sync
  - Fulfills: AC-1, AC-4, FR-1, FR-5, D3
  - Scope: `data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt`, `webapp/lib/sync.ts`
  - Order: 10
  - Status: **done** (2026-08-23) — Đã thêm Admin auto-backfill & fallback trong `listProjectCatalog()` và batch write vào `projectCatalog` trong `createProjectDocument()`.

- [x] **[cloud-proj-02]** ViewModel & Logic điều phối Mở / Tải về dự án Cloud & Tạo dự án Admin
  - Fulfills: AC-3, FR-3, FR-4, D2
  - Scope: `app/src/main/java/com/mapsupervision/app/auth/FirebaseAccessViewModel.kt`, `app/src/main/java/com/mapsupervision/app/FirebaseAccessGate.kt`
  - Order: 20
  - Status: **done** (2026-08-23) — Đã thêm `openOrDownloadProject` và `createCloudProject` trong `FirebaseAccessViewModel`, wire đầy đủ callbacks trong `FirebaseAccessGate`.

- [x] **[cloud-proj-03]** Giao diện màn hình Danh mục Dự án Firebase (`FirebaseProjectCatalogScreen`)
  - Fulfills: AC-2, AC-3, AC-5, FR-2, FR-6, D1, D2
  - Scope: `app/src/main/java/com/mapsupervision/app/FirebaseProjectCatalogScreen.kt`
  - Order: 30
  - Status: **done** (2026-08-23) — Nâng cấp toàn diện giao diện `FirebaseProjectCatalogScreen`: nút "Mở dự án" cho Admin/Approved, status badges, empty state đẹp mắt, dialog tạo dự án cho Admin.

- [x] **[cloud-proj-04]** Unit Tests & Kiểm thử tự động toàn diện
  - Fulfills: AC-6, NFR-1, NFR-2, NFR-3
  - Scope: `app/src/test/java/com/mapsupervision/app/auth/FirebaseAccessViewModelTest.kt`, `data/src/test/java/com/mapsupervision/data/sync/FirebaseProjectCatalogParserTest.kt`, `project/src/test/java/com/mapsupervision/project/ui/ProjectCatalogUiTest.kt`
  - Order: 40
  - Status: **done** (2026-08-23) — Đã bổ sung unit tests cho parser và viewmodel, kiểm thử tự động Gradle thành công 100%.

## Schedule & Compliance

- **Wave 1 (cloud-proj-01):** Data Layer Auto-backfill/Fallback + Webapp batch write hoàn thành.
- **Wave 2 (cloud-proj-02):** ViewModel orchestration (`openOrDownloadProject`, `createCloudProject`) hoàn thành.
- **Wave 3 (cloud-proj-03):** Compose UI `FirebaseProjectCatalogScreen` hoàn thành.
- **Wave 4 (cloud-proj-04):** Unit tests pass 100%.
- **Spec Decision Compliance:** D1=pass, D2=pass, D3=pass.
- **System Decision Impact:** none — không thay đổi cấu trúc database Room SQLite hay quy tắc bảo mật mới của Firestore.
