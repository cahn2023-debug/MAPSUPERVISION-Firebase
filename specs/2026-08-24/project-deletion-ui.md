# Specification: Bổ sung nút & quy trình Xóa Project (Danger Zone) cho Android và Web (UI/UX Pro Max)

## Overview

Tài liệu đặc tả yêu cầu, thiết kế trải nghiệm người dùng (UI/UX Pro Max) và giải pháp kỹ thuật cho tính năng **Xóa Dự án (Project Deletion)** trên cả hai nền tảng Web Dashboard (Next.js) và ứng dụng di động Android (Jetpack Compose). Tính năng đảm bảo tính toàn vẹn dữ liệu, phân quyền bảo mật chặt chẽ (Admin/Owner), cảnh báo trực quan đa tầng (Danger Zone & Glassmorphic Modal) và dọn dẹp triệt để dữ liệu cả trên Cloud (Firestore) lẫn Local (Room SQLite + Local Storage Files).

---

## Locked Decisions

- **D1 — Quyền hạn & Phạm vi xóa (Hard Delete an toàn):**
  - Chỉ người dùng có vai trò **Admin** hoặc **Project Owner** mới có quyền xóa vĩnh viễn dự án.
  - Khi xác nhận xóa thành công:
    - **Cloud (Firebase):** Xóa document `projects/{projectId}`, bản ghi danh mục `projectCatalog/{projectId}`, và toàn bộ các subcollections con (`projectMembers`, `projectTasks`, `dailyLogs`, `projectLayers`, `photos`, ...).
    - **Local (Android SQLite/Storage):** Đóng kết nối `ProjectScopedDatabaseProvider`, giải phóng Room Database cục bộ, dọn sạch thư mục media/tiles của project qua `ProjectStorageManager.deleteProjectStorage`, và dọn dẹp các bản ghi tham chiếu trong cơ sở dữ liệu hệ thống.
  - Yêu cầu xác thực kép: Nhập chính xác Tên hoặc Mã dự án (`projectName`/`projectCode`) kết hợp mật khẩu xác thực lại (hoặc Firebase Re-authentication) để ngăn ngừa hoàn toàn rủi ro thao tác nhầm.

- **D2 — Vị trí hiển thị & Trải nghiệm UI/UX (UI/UX Pro Max Standards):**
  - **Web Dashboard:**
    - Thiết kế phân khu riêng biệt **"Vùng nguy hiểm / Danger Zone"** ở cuối tab Cài đặt dự án / Trang quản trị dự án.
    - Card Danger Zone sử dụng phong cách Glassmorphism hiện đại: viền chuyển sắc cảnh báo `border-rose-500/30`, nền tối chuyển sắc mờ (`backdrop-blur-md bg-rose-950/10`), biểu tượng cảnh báo phát sáng vi tế (`glow-red`).
    - Nút "Xóa dự án này" (`Delete Project`) với micro-interaction: hover glow, active scale (0.98), hiệu ứng chuyển trạng thái mượt mà.
    - Glassmorphic Danger Modal: Lớp overlay làm mờ nền 20px, hộp thoại cảnh báo nguy hiểm với checklist tác động (dữ liệu nào sẽ mất vĩnh viễn), ô nhập tên project để mở khóa nút bấm, và nút "Tôi hiểu hậu quả, xóa dự án này" với countdown/loading animation.
  - **Android Client (Jetpack Compose):**
    - Đặt nút hành động xóa trong màn hình chi tiết dự án / bảng quản trị dự án (`MapHubScreen` / Project Settings Sheet).
    - Áp dụng Material 3 + Custom Danger Theme (Container màu Rose/Crimson trầm, icon Alert, haptic feedback khi chạm).
    - Modal xác nhận 2 bước với giao diện trực quan, rõ ràng.

- **D3 — Xử lý dữ liệu ngoại tuyến chưa đồng bộ (Offline Outbox Sync):**
  - Trên thiết bị Android, trước khi xóa, hệ thống kiểm tra số lượng bản ghi/hình ảnh đang chờ đẩy lên Cloud trong bảng `event_outbox` / hàng đợi đồng bộ.
  - Nếu còn dữ liệu chưa sync, hiển thị cảnh báo màu vàng/đỏ ghi rõ: *"Còn X thay đổi / ảnh chưa được đồng bộ lên Cloud"*.
  - Bắt buộc người dùng phải tích chọn checkbox *"Tôi hiểu và chấp nhận hủy bỏ vĩnh viễn các thay đổi chưa đồng bộ này"* mới cho phép kích hoạt nút xóa.

---

## System Decision Impact

- **Impact:** draft new
- **Acceptance gate:** Đạt toàn bộ kiểm thử Unit Test / Integration Test cho các hàm xóa trên Web (`deleteProjectDocument`) và Android (`ProjectRepositoryImplTest`, `ProjectDeletionFlowTest`), kèm xác thực trực quan UI modal/danger zone trên cả Webapp và Android.

---

## Requirements

### Functional Requirements

- **FR-1 — Phân quyền & Điều kiện hiển thị:**
  - Chỉ hiển thị khu vực "Danger Zone / Xóa dự án" khi tài khoản hiện tại có quyền `Admin` hoặc `Owner` của dự án được chọn.
  - Với người dùng Viewer / Editor thông thường, ẩn hoàn toàn hoặc hiển thị trạng thái disabled kèm tooltip giải thích.
- **FR-2 — Xóa dự án trên Webapp (`webapp/lib/sync.ts` & `webapp/app/page.tsx`):**
  - Bổ sung hàm `deleteProjectDocument(db: Firestore, projectId: string): Promise<void>` thực hiện xóa theo batch/transaction:
    - Xóa `projects/{projectId}`
    - Xóa `projectCatalog/{projectId}`
    - Xóa các subcollections con liên quan: `projectMembers`, `projectTasks`, `dailyLogs`, `projectLayers`, `events`.
  - Trên UI Web (`page.tsx`), xây dựng Component `ProjectDangerZone` và `DeleteProjectModal` với validation:
    - Người dùng phải nhập khớp chuỗi `projectName` hoặc `projectCode`.
    - Gọi Firebase re-authenticate nếu cần hoặc xác nhận mật khẩu.
    - Sau khi xóa thành công: reset `selectedProjectId`, hiển thị thông báo Toast thành công, điều hướng về dự án khả dụng đầu tiên hoặc trạng thái Empty State.
- **FR-3 — Xóa dự án trên Android (`MapHubScreen.kt` & `ProjectRepositoryImpl.kt`):**
  - Tích hợp và hoàn thiện luồng `onDeleteProject` trong `MapHubScreen.kt` và `WorkspaceAppShell.kt`.
  - Cập nhật Dialog xác nhận xóa theo chuẩn UI/UX Pro Max:
    - Hiển thị số lượng sự kiện/ảnh chưa sync (nếu có).
    - Trường nhập tên/mã project xác thực.
    - Trường nhập mật khẩu xác thực lại qua `FirebaseAuth.reauthenticateWithCredential`.
    - Checkbox xác nhận mất dữ liệu ngoại tuyến.
  - Thực thi tuần tự: Xóa Cloud Firestore -> Dọn dẹp cục bộ qua `completeLocalDeletion` & `deleteProjectStorage` -> Chuyển active project sang dự án khác hoặc quay về màn hình Catalog.
- **FR-4 — Trạng thái phản hồi & Xử lý lỗi (Feedback & Resilience):**
  - Hiển thị trạng thái Loading (Spinner / Skeleton / Shimmer) trong suốt quá trình xóa.
  - Nếu quá trình xóa Cloud gặp lỗi phân quyền hoặc mất kết nối: Hiển thị thông báo lỗi chi tiết, không thực hiện xóa nhầm dữ liệu cục bộ khi chưa được xác nhận.

### Non-Functional Requirements

- **NFR-1 — An toàn dữ liệu tuyệt đối (Zero Data Leakage / Ghost Records):** Đảm bảo dọn dẹp sạch cả bảng catalog công khai lẫn tài nguyên lưu trữ, không để lại bản ghi rác hoặc orphan files.
- **NFR-2 — Thẩm mỹ UI/UX Pro Max:**
  - Hiệu ứng chuyển động mượt mà (Framer Motion trên Web, Compose AnimatedVisibility/Transition trên Android).
  - Màu sắc cảnh báo cao cấp: tông Rose/Red kết hợp nền tối (Dark Glassmorphism), độ tương phản cao đạt chuẩn WCAG AA.
  - Target touch tối thiểu 44px trên mobile, micro-interactions phản hồi xúc giác/âm thanh rõ ràng.
- **NFR-3 — Hiệu năng:** Thời gian phản hồi và dọn dẹp dưới 2 giây trên mạng thông thường.

---

## Acceptance Criteria

- [ ] **AC-1:** Chỉ tài khoản Admin/Owner mới nhìn thấy nút "Xóa dự án" và phân khu "Danger Zone" trên cả Web và Android.
- [ ] **AC-2:** Trên Webapp, nút Xóa nằm trong khu vực Danger Zone ở cuối phần Quản trị dự án với phong cách Glassmorphism 2.0. Khi bấm, mở Glassmorphic Confirmation Modal.
- [ ] **AC-3:** Nút xác nhận xóa trong Modal chỉ được kích hoạt (enabled) khi người dùng nhập chính xác 100% tên hoặc mã của dự án được chọn.
- [ ] **AC-4:** Trên Android, Dialog/BottomSheet xóa dự án hiển thị chính xác số lượng dữ liệu chưa sync và chỉ cho phép bấm xóa khi người dùng đã tích xác nhận.
- [ ] **AC-5:** Khi xác nhận xóa thành công, hệ thống xóa hoàn toàn bản ghi trên Firestore (`projects/{id}` và `projectCatalog/{id}`) và giải phóng Room DB + thư mục lưu trữ cục bộ trên Android.
- [ ] **AC-6:** Sau khi xóa, Webapp và Android tự động chuyển đổi sang dự án hợp lệ khác hoặc quay về màn hình danh sách dự án với thông báo thành công trực quan.
- [ ] **AC-7:** Bộ kiểm thử tự động (Unit test) cho logic xóa trên Web và Android vượt qua 100%.

---

## Scenarios

### Scenario 1: Admin xóa dự án trên Webapp
**Given** Admin đã đăng nhập vào Web Dashboard và chọn dự án "Dự án Nâng cấp Cáp Tuyến 1" (Mã: `PROJECT_CAP_01`)  
**When** Admin cuộn xuống khu vực "Danger Zone" và nhấn "Xóa dự án này"  
**Then** Một Glassmorphic Modal mở ra, liệt kê các cảnh báo mất dữ liệu và yêu cầu gõ lại "PROJECT_CAP_01"  
**When** Admin gõ đúng "PROJECT_CAP_01" và nhấn "Tôi hiểu rủi ro, tiến hành xóa"  
**Then** Nút chuyển sang trạng thái loading "Đang xóa dữ liệu...", hệ thống xóa document Firestore và Catalog, đóng modal, hiển thị Toast thành công và tự động chọn dự án kế tiếp  

### Scenario 2: Xóa dự án trên Android khi còn dữ liệu Outbox chưa sync
**Given** Admin đang mở dự án trên Android và có 3 ảnh chụp offline chưa kịp sync lên Cloud  
**When** Admin mở menu Cài đặt dự án và chọn "Xóa dự án"  
**Then** Dialog cảnh báo xuất hiện thông báo: "Cảnh báo: Có 3 mục chưa được đồng bộ lên Cloud!" kèm ô checkbox xác nhận và các trường nhập mã xác thực  
**When** Admin nhập đúng mã dự án, nhập mật khẩu và tích chọn "Tôi xác nhận hủy các thay đổi chưa đồng bộ", sau đó bấm "Xóa vĩnh viễn"  
**Then** Ứng dụng dọn dẹp Room SQLite, xóa thư mục media cục bộ, xóa bản ghi Cloud và chuyển về màn hình danh mục dự án  

---

## Technical Notes & Implementation Plan

### 1. Webapp (Next.js + Tailwind + Firestore)
- **`webapp/lib/sync.ts`**:
  - Viết hàm `deleteProjectDocument(firestore: Firestore, projectId: string): Promise<void>`.
  - Thực hiện xóa document `projects/{projectId}`, `projectCatalog/{projectId}` và các subcollections liên quan theo batch chunks.
- **`webapp/app/page.tsx`**:
  - Tạo Component `ProjectDangerZone` đặt ở cuối tab Quản trị dự án / Bảng điều khiển.
  - Tạo Component `DeleteProjectModal` với thiết kế Glassmorphism 2.0 (backdrop-blur, border-rose-500/20, text matching validation).
  - Tích hợp hook xử lý xóa, toast notification và cập nhật state `selectedProjectId`.

### 2. Android Client (Jetpack Compose + Room + Firebase)
- **`app/src/main/java/com/mapsupervision/app/workspace/MapHubScreen.kt`**:
  - Nâng cấp giao diện `DeleteProjectDialog` / `DangerZoneSection` theo chuẩn Material 3 + Custom Danger Palette.
  - Bổ sung đếm số lượng outbox pending events để hiển thị cảnh báo sinh động.
- **`data/src/main/java/com/mapsupervision/data/repository/ProjectRepositoryImpl.kt`**:
  - Đảm bảo luồng `deleteProject` và `completeLocalDeletion` kích hoạt xóa đồng bộ cả Firestore Cloud và Storage Manager cục bộ.

---

## Task Links

Generated tasks will be linked here after `/kn-plan --from @doc/specs/2026-08-24/project-deletion-ui` runs.
