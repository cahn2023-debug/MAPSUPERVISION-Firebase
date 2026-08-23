# Specification: Sửa lỗi hiển thị và lựa chọn danh sách dự án trên Cloud (Firebase Project Catalog)

## Overview

Tài liệu đặc tả yêu cầu và giải pháp xử lý lỗi màn hình "Dự án trên Firebase" (`FirebaseProjectCatalogScreen`) không có danh sách dự án để lựa chọn hoặc thiếu các nút hành động lựa chọn, tải về và mở dự án vào Workspace làm việc.

---

## Locked Decisions

- **D1 — Phạm vi & Màn hình mục tiêu:** Xử lý toàn diện màn hình `FirebaseProjectCatalogScreen.kt`, `FirebaseAccessViewModel.kt`, `FirebaseAccessRepositoryImpl.kt` trên Android, đồng thời cập nhật hàm tạo dự án `createProjectDocument` tại `webapp/lib/sync.ts` để đồng bộ dữ liệu `projectCatalog`.
- **D2 — Luồng chọn & mở dự án đã duyệt (Approved/Admin):** Khi người dùng nhấn nút "Mở dự án" / "Tải về & Mở" trên một dự án có trạng thái `APPROVED` (hoặc với quyền Admin):
  1. Kiểm tra dự án cục bộ trong Room DB SQLite (`ProjectRepository.get(id)`).
  2. Nếu chưa có: Tự động khởi tạo `ProjectEntity` cục bộ (ID, tên, slug, đường dẫn DB cục bộ) để sẵn sàng làm việc.
  3. Kích hoạt dự án làm `activeProjectId` qua `ActiveProjectRepository.setActive(id)`.
  4. Kích hoạt tiến trình đồng bộ dữ liệu đám mây ngầm (`FirebaseSyncRepository.sync(id)`).
  5. Tự động đóng màn hình Catalog và điều hướng vào không gian làm việc bản đồ (`WorkspaceAppShell`).
- **D3 — Đồng bộ 2 chiều dữ liệu Catalog & Cơ chế Fallback:**
  1. **Android (Admin auto-backfill & fallback):** Khi Admin tải danh mục dự án, nếu collection `projectCatalog` trên Firestore bị trống hoặc thiếu dự án so với collection `projects`, client Admin sẽ tự động đồng bộ (backfill) các bản ghi sang `projectCatalog` theo đúng Firestore Security Rules, đồng thời fallback nạp danh sách dự án từ `projects` để luôn có dữ liệu hiển thị ngay lập tức.
  2. **Webapp (Next.js):** Khi Admin tạo dự án trên Web (`createProjectDocument`), hệ thống tự động ghi đồng thời vào collection `projects/{id}` và collection `projectCatalog/{id}`.
  3. **UI Empty State:** Hiển thị thông báo và hướng dẫn thân thiện khi Cloud chưa có dự án nào, kèm nút "Tạo dự án mới" (cho Admin) và nút "Mở dữ liệu cục bộ" (để làm việc offline).

---

## System Decision Impact

- **Impact:** draft new
- **Acceptance gate:** Đạt toàn bộ test cases cho `FirebaseAccessRepositoryImplTest`, `FirebaseAccessViewModelTest`, `ProjectCatalogUiTest`, kiểm tra thao tác tạo/mở dự án Cloud thành công trên Android và Webapp.

---

## Requirements

### Functional Requirements

- **FR-1 — Danh mục Cloud đầy đủ (Catalog Discovery & Admin Fallback):**
  - Hàm `listProjectCatalog()` trong `FirebaseAccessRepositoryImpl.kt` hỗ trợ tự động phát hiện và đồng bộ dự án từ `projects` sang `projectCatalog` khi user có quyền Admin.
  - Người dùng thông thường luôn đọc được danh mục công khai từ `projectCatalog` theo đúng phân quyền Firestore.
- **FR-2 — Nút hành động lựa chọn & Mở dự án (`ProjectCatalogCard`):**
  - Dự án có trạng thái `APPROVED` hoặc tài khoản là `Admin`: Hiển thị nút "Mở dự án" (nếu đã có trên máy) hoặc "Tải về & Mở" (nếu là dự án mới trên Cloud).
  - Dự án `NOT_REQUESTED`: Hiển thị nút "Yêu cầu cấp quyền".
  - Dự án `PENDING`: Hiển thị badge "Đang chờ duyệt".
  - Dự án `REJECTED` / `REVOKED`: Hiển thị nút "Gửi lại yêu cầu".
- **FR-3 — Điều phối mở dự án trong `FirebaseAccessViewModel`:**
  - Bổ sung hàm `openOrDownloadProject(entry: FirebaseProjectCatalogEntry, onComplete: () -> Unit)` để kết nối giữa `ProjectRepository`, `ActiveProjectRepository`, và `FirebaseSyncRepository`.
  - Đảm bảo dự án được kích hoạt và trigger sync ngay khi người dùng chọn.
- **FR-4 — Tạo dự án nhanh trên Cloud cho Admin:**
  - Màn hình `FirebaseProjectCatalogScreen` cung cấp nút "Tạo dự án Cloud" cho Admin với dialog nhập Tên và Mã dự án, ghi đồng thời vào Firestore `projects` và `projectCatalog`.
- **FR-5 — Đồng bộ từ Webapp (`webapp/lib/sync.ts`):**
  - Cập nhật `createProjectDocument` để thêm batch write document vào `projectCatalog/{projectId}` với cấu trúc chuẩn `{ projectName, projectCode, updatedAtEpochMs, status: "ACTIVE" }`.
- **FR-6 — Cải thiện UI/UX màn hình Catalog:**
  - Hiển thị danh sách dự án với card trực quan, badge trạng thái rõ ràng.
  - Hiển thị Loading indicator khi đang nạp, thông báo lỗi nếu mất kết nối.
  - Bổ sung Empty State hướng dẫn chi tiết khi chưa có dự án nào trên Cloud.

### Non-Functional Requirements

- **NFR-1 — Bảo mật & Phù hợp Firestore Rules:** Mọi thao tác đọc/ghi vào `projectCatalog` tuân thủ nghiêm ngặt schema cho phép trong `firestore.rules`.
- **NFR-2 — Kiến trúc phân tầng sạch:** Domain giữ interface sạch sẽ; Data xử lý logic Firestore & Room; App xử lý ViewModel State và Compose UI.
- **NFR-3 — Khả năng hoạt động Offline-first:** Nếu không có mạng hoặc chưa đăng nhập, người dùng vẫn có thể chọn "Mở dữ liệu cục bộ" để tiếp tục làm việc bình thường.

---

## Acceptance Criteria

- [x] **AC-1:** Khi đăng nhập tài khoản Admin, toàn bộ dự án hiện có trên Cloud Firestore được hiển thị đầy đủ trên màn hình "Dự án trên Firebase".
- [x] **AC-2:** Khi đăng nhập tài khoản thông thường, danh mục dự án Cloud hiển thị danh sách để người dùng có thể bấm "Yêu cầu cấp quyền".
- [x] **AC-3:** Khi bấm "Mở dự án" / "Tải về & Mở" trên một dự án đã được cấp quyền (hoặc với quyền Admin), hệ thống tự động khởi tạo dữ liệu cục bộ, kích hoạt Active Project, chạy sync và mở ra màn hình Workspace bản đồ.
- [x] **AC-4:** Khi Admin tạo dự án từ Webapp hoặc từ ứng dụng Android, bản ghi tương ứng tại collection `projectCatalog` được tự động tạo lập.
- [x] **AC-5:** Khi chưa có dự án nào trên Cloud, màn hình hiển thị Empty State rõ ràng kèm nút tạo mới (Admin) hoặc nút chuyển sang làm việc với dữ liệu cục bộ.
- [x] **AC-6:** Bộ kiểm thử tự động (Unit test) của `FirebaseAccessRepositoryImplTest`, `FirebaseAccessViewModelTest`, `ProjectCatalogUiTest` chạy thành công 100%.

---

## Scenarios

### Scenario 1: Admin đăng nhập và chọn mở dự án từ Cloud vào Workspace
**Given** Admin đăng nhập thành công vào ứng dụng Android, trên Cloud có sẵn dự án "Dự án Giám sát Tuyến Cáp A"  
**When** Màn hình "Dự án trên Firebase" xuất hiện  
**Then** Danh sách hiển thị card của "Dự án Giám sát Tuyến Cáp A" với nút "Mở dự án"  
**When** Admin nhấn "Mở dự án"  
**Then** Hệ thống kích hoạt dự án làm Active Project và chuyển thẳng vào màn hình bản đồ Workspace  

### Scenario 2: Người dùng thông thường yêu cầu quyền và mở dự án sau khi duyệt
**Given** Người dùng thường đăng nhập và thấy dự án "Tuyến Metro B" có trạng thái "Chưa yêu cầu"  
**When** Người dùng nhấn "Yêu cầu cấp quyền"  
**Then** Nút chuyển sang trạng thái "Đang gửi..." sau đó hiển thị badge "Đang chờ duyệt"  
**When** Admin phê duyệt yêu cầu cấp quyền  
**Then** Trạng thái dự án chuyển thành "Đã duyệt", xuất hiện nút "Tải về & Mở" cho phép người dùng mở dự án vào Workspace  

---

## Technical Notes

- File cần chỉnh sửa / bổ sung:
  1. `app/src/main/java/com/mapsupervision/app/FirebaseProjectCatalogScreen.kt`: Nâng cấp giao diện card dự án, thêm nút "Mở dự án", dialog tạo dự án cho Admin, và xử lý callback chọn dự án.
  2. `app/src/main/java/com/mapsupervision/app/auth/FirebaseAccessViewModel.kt`: Thêm hàm `openOrDownloadProject`, `createCloudProject`, inject các Repository liên quan.
  3. `app/src/main/java/com/mapsupervision/app/FirebaseAccessGate.kt`: Truyền callback mở dự án từ ViewModel tới CatalogScreen.
  4. `data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt`: Bổ sung logic admin fallback & auto-backfill giữa `projects` và `projectCatalog`.
  5. `webapp/lib/sync.ts`: Cập nhật `createProjectDocument` thêm batch write vào `projectCatalog`.
  6. Unit tests trong `:app`, `:data`, `:project` để bao phủ toàn bộ luồng mới.

---

## Task Links

- [cloud-proj-01] @task/cloud-proj-01: Đồng bộ 2 chiều dữ liệu Catalog trên Data Layer & Webapp Sync (done)
- [cloud-proj-02] @task/cloud-proj-02: ViewModel & Logic điều phối Mở / Tải về dự án Cloud & Tạo dự án Admin (done)
- [cloud-proj-03] @task/cloud-proj-03: Giao diện màn hình Danh mục Dự án Firebase (FirebaseProjectCatalogScreen) (done)
- [cloud-proj-04] @task/cloud-proj-04: Unit Tests & Kiểm thử tự động toàn diện (done)
- Full task list: @doc/specs/2026-08-23/cloud-project-selection-fix-tasks.md
