# Đặc tả kỹ thuật: Bổ sung tính năng "Gỡ ảnh khỏi dự án" (Web & Android, Giữ ảnh trên Google Drive)

## Overview

Bổ sung tính năng cho phép Quản trị viên và Chủ dự án gỡ bỏ ảnh khỏi dự án trên Web và toàn bộ thiết bị Android (đồng bộ qua Cloud Firestore Tombstone), trong khi vẫn **bảo lưu nguyên vẹn $100\%$ tệp ảnh gốc trên Google Drive**. Giải pháp này đảm bảo an toàn dữ liệu, chống mất mát file gốc, và thực hiện tức thì mà không gặp lỗi nghẽn hoặc phân quyền từ Google Drive API.

## Locked Decisions

- **D1 — Hành vi Gỡ ảnh khỏi dự án (Bảo lưu Google Drive):**
  - Khi thực hiện gỡ ảnh, hệ thống cập nhật bản ghi Firestore trong `projects/{projectId}/site_photos/{photoId}` với:
    - `isDeleted: true`
    - `androidDeletionStatus: "PROJECT_REMOVED"`
    - `deletedAtEpochMs: Date.now()`
    - `updatedAtEpochMs: Date.now()`
  - **Tuyệt đối không xóa file trên Google Drive**, giữ nguyên đường link Drive cho mục đích lưu trữ hoặc truy xuất sau này nếu cần.
- **D2 — Phân quyền & Đồng bộ tức thì:**
  - Người dùng có quyền quản trị ảnh (`canManagePhotos`: Admin, Project Owner, Member Admin) có thể thao tác.
  - Web UI lập tức ẩn ảnh (bộ lọc `isDeleted === false`) và đóng Modal Preview.
  - Ứng dụng Android khi pull sync Firestore sẽ đọc tombstone `isDeleted: true` và tự động gỡ ảnh khỏi cơ sở dữ liệu SQLite cục bộ.
- **D3 — Trải nghiệm UI/UX (UI/UX Standards):**
  - Nút bấm trong `PhotoLightboxModal`: **"🗑️ Gỡ khỏi dự án"** với nút màu đỏ cảnh báo (`primary-button danger`).
  - Hộp thoại xác nhận rõ ràng: *"Ảnh sẽ được gỡ bỏ khỏi dự án trên Web và các thiết bị Android (tệp gốc trên Google Drive vẫn được bảo lưu an toàn). Bạn có chắc chắn muốn tiếp tục?"*.
  - Nút nhanh trên `PhotoCardItem`: **"Gỡ ảnh"**.

## Requirements

### Functional Requirements

- **FR-1:** Giao diện `PhotoLightboxModal` hiển thị nút "🗑️ Gỡ khỏi dự án" cho người có quyền quản lý ảnh.
- **FR-2:** Khi bấm "Gỡ khỏi dự án", xuất hiện hộp thoại xác nhận với nội dung bảo lưu Google Drive rõ ràng.
- **FR-3:** Thực hiện ghi tombstone `isDeleted: true` trực tiếp lên Firestore, ẩn ảnh ngay trên giao diện Web.
- **FR-4:** Đồng bộ trạng thái xóa về tất cả thiết bị Android để gỡ bỏ ảnh cục bộ khi đồng bộ hóa.

### Non-Functional Requirements

- **NFR-1 (Reliability):** Thao tác $100\%$ thành công thông qua kết nối Firestore, không bị ảnh hưởng bởi lỗi Drive API hay thiếu biến môi trường service account trên Vercel.
- **NFR-2 (Safety):** Bảo toàn tệp ảnh gốc trên Google Drive.

## Acceptance Criteria

- [x] **AC-1:** Nút "🗑️ Gỡ khỏi dự án" hiển thị trong modal Preview khi xem ảnh.
- [x] **AC-2:** Nhấn xác nhận trong hộp thoại sẽ ghi `isDeleted: true` và `androidDeletionStatus: "PROJECT_REMOVED"` lên Firestore.
- [x] **AC-3:** Modal Preview tự động đóng và ảnh biến mất khỏi danh sách trên Web.
- [x] **AC-4:** File gốc trên Google Drive vẫn tồn tại nguyên vẹn.
