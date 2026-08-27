# Spec: Multi-Device Drive Media Deduplication, Progressive 2-Tier Image Sync, and Robust Firebase Sync

## Overview

Tài liệu đặc tả kỹ thuật nâng cấp cơ chế **Upload hình ảnh lên Google Drive, Deduplication chống upload trùng lặp giữa các thiết bị, Đồng bộ hình ảnh 2 tầng (2-Tier Progressive Sync)** và **Tối ưu hóa tính ổn định của tiến trình đồng bộ Firebase từ Android**.

Mục tiêu chính:
1. **Deduplication & ID Google Drive**: Khi một thiết bị tải ảnh lên Google Drive, `driveFileId` được gắn chặt chẽ vào bản ghi ảnh (Entity / Firestore / Snapshot JSON). Mọi thiết bị khác khi tải dữ liệu về đều nhận biết ảnh đã có trên Google Drive, giữ `syncStatus = DONE` và không upload lặp lại.
2. **2-Tier Progressive Image Sync**:
   - **Tier 1 (Instant Display)**: Ưu tiên link CDN Google Drive (`https://lh3.googleusercontent.com/d/{driveFileId}=w...` hoặc `remoteUrl`) làm thumbnail hiển thị trực tiếp trên UI ngay khi vừa sync xong metadata. Không download file thumbnail xuống máy để tránh trùng lặp/rác bộ nhớ.
   - **Tier 2 (Background Original Download)**: Cho phép tải ngầm file ảnh gốc dung lượng cao về máy theo hàng đợi, đối chiếu theo `photoId` / `driveFileId` và cập nhật `filePath` trong SQLite Room mà không làm gián đoạn trải nghiệm người dùng mới.
3. **Intelligent JSON Snapshot Reconcile (Webapp & Public Links)**: Webapp đọc dữ liệu Snapshot JSON / Firestore theo cơ chế hòa giải thông minh (Reconcile & Merge): bản ghi trùng thì ghi nhận/cập nhật theo timestamp, bản ghi thiếu thì bổ sung vào, bảo toàn tuyệt đối trường `driveFileId` / `remoteUrl` tránh bị đè mất ảnh khi một thiết bị khác chưa tải đủ ảnh xuất snapshot.
4. **Firebase Sync Hardening**: Bỏ qua kiểm tra `accessRequests` đối với Owner/Creator của dự án, xử lý lỗi `FileNotFound` khi push media, tăng tính ổn định của Firestore batch writes.

## Locked Decisions

- **D1 (Explicit Drive File ID Schema & Room Migration)**: Bảng `site_photos` trong SQLite Room được nâng cấp (Room Migration) để bổ sung 2 cột: `driveFileId: TEXT` và `driveThumbnailId: TEXT`. Các trường này được serialize đồng bộ qua Firestore collection `site_photos`, JSON Snapshot và WebApp models.
- **D2 (Anti-Duplication & Strict Upload Filter)**: Hàm lọc ảnh cần upload (`uploadPendingMediaInternal`) chỉ chọn ảnh thỏa mãn: `syncStatus != DONE && driveFileId.isNullOrBlank() && remoteUrl.isNullOrBlank() && File(filePath).exists()`. Khi thiết bị khác pull metadata về từ Firestore/Snapshot, bản ghi tự động nhận `syncStatus = DONE` và lưu đầy đủ `driveFileId`, triệt tiêu hoàn toàn khả năng re-upload từ nhiều thiết bị.
- **D3 (Drive CDN Thumbnail Priority & No Thumbnail Download)**: UI hiển thị ảnh trên Android (Coil/Glide) ưu tiên hiển thị từ `filePath` nếu file gốc đã có trên máy; nếu chưa có file gốc thì hiển thị trực tiếp từ Google Drive CDN URL (`remoteUrl` hoặc `googleDriveImageUrl(driveFileId, 600)`). Tuyệt đối không download file thumbnail về bộ nhớ máy để tránh lặp ảnh và tiết kiệm dung lượng.
- **D4 (Progressive Background Original Media Restore)**: Cho phép background worker / coroutine tải file ảnh gốc từ Google Drive về thư mục `photos/{projectId}/restored_{photoId}.jpg`. Sau khi tải xong, cập nhật `filePath` trong SQLite Room.
- **D5 (Webapp Snapshot Field-Level Preservation & Merge)**: Khi WebApp nạp snapshot JSON hoặc đồng bộ dữ liệu public, nếu bản ghi ảnh mới trong snapshot không có `driveFileId` nhưng trong database/cache đã có, hệ thống giữ nguyên `driveFileId` và `remoteUrl` hiện tại, tránh ghi đè null.
- **D6 (Owner/Creator Access Bypass & Push Exception Resilience)**: Trong `ensureApprovedAccess(projectId)`, nếu Firebase UID trùng với `project.createdByUid` (hoặc project catalog owner), bỏ qua kiểm tra `accessRequests`. Khi upload media, nếu file local bị thiếu, ghi nhận trạng thái `FAILED` cục bộ và tiếp tục các ảnh còn lại trong batch, không làm crash toàn bộ tiến trình push sync.

## Requirements

### Functional Requirements

- **FR-1 (Room DB Entity & Migration)**:
  - Cập nhật `SitePhotoEntity` với `driveFileId: String? = null` và `driveThumbnailId: String? = null`.
  - Cung cấp Migration trong `MapSupervisionDatabase` (ví dụ `MIGRATION_X_Y`: `ALTER TABLE site_photos ADD COLUMN driveFileId TEXT; ALTER TABLE site_photos ADD COLUMN driveThumbnailId TEXT;`).
- **FR-2 (Upload Pipeline & Metadata Extraction)**:
  - Khi `uploadMediaToDrive` thành công, lấy chính xác `driveFileId` trả về từ API/Direct Drive và lưu vào `SitePhotoEntity.driveFileId` cùng `remoteUrl`.
- **FR-3 (Pull Sync & Metadata Persistence)**:
  - Khi pull document từ Firestore collection `site_photos`, map `driveFileId` và `remoteUrl` vào SQLite Room DB, gán `syncStatus = DONE`.
- **FR-4 (UI Tier 1 Display Logic)**:
  - Viết utility / mapper `resolvePhotoDisplayUrl(photo)`: Nếu `photo.filePath` tồn tại vật lý -> dùng `photo.filePath`; ngược lại nếu `photo.driveFileId` / `photo.remoteUrl` hợp lệ -> dùng link CDN Google Drive với kích thước phù hợp (`=w600` hoặc `=w1000`).
- **FR-5 (Tier 2 Background Restorer)**:
  - Tải ngầm ảnh gốc từ Google Drive theo danh sách ảnh thiếu file cục bộ (`photo.filePath.isBlank() || !File(photo.filePath).exists()`), lưu file vật lý và cập nhật `filePath` trong Room DB.
- **FR-6 (Webapp Reconcile & Public Snapshot Merge)**:
  - Trong `webapp/lib/public-project.ts` và `google-drive-media.ts`, khi merge collections, áp dụng logic bảo toàn `driveFileId` và merge theo `updatedAtEpochMs`.
- **FR-7 (Firebase Sync Stability & Owner Bypass)**:
  - Bỏ qua check `accessRequests` khi `user.uid == project.createdByUid`.
  - Bọc `uploadMediaToDrive` an toàn, ghi nhận lỗi từng ảnh mà không ngắt quãng cả batch.

### Non-Functional Requirements

- **NFR-1 (Bandwidth & Storage Conservation)**: Không tải thừa file thumbnail xuống máy, tiết kiệm tối đa băng thông 4G/5G và bộ nhớ thiết bị.
- **NFR-2 (Zero Cold-Start Display Delay)**: Người dùng mới mở dự án nhìn thấy toàn bộ ảnh hiện trường ngay lập tức qua Drive CDN.
- **NFR-3 (100% Idempotency)**: Mọi thao tác push/pull/merge có tính lũy đẳng (idempotent), lặp lại nhiều lần không tạo dữ liệu rác hay tệp trùng trên Google Drive.

## Acceptance Criteria

- [ ] **AC-1**: Khi thiết bị A chụp ảnh và upload lên Drive, `driveFileId` được lưu vào Room DB, Firestore và Snapshot JSON.
- [ ] **AC-2**: Khi thiết bị B hoặc WebApp đồng bộ dữ liệu về, `driveFileId` được gán chính xác và `syncStatus` là `DONE`, không bị đẩy upload lại lên Drive.
- [ ] **AC-3**: Trên thiết bị B (thiết bị mới chưa có file ảnh gốc cục bộ), mở giao diện danh sách ảnh / bản đồ / chi tiết công việc vẫn hiển thị ảnh ngay lập tức nhờ link Google Drive CDN (Tier 1).
- [ ] **AC-4**: Tiến trình chạy nền (Tier 2) tải ảnh gốc về máy, gán `filePath` và cho phép xem ảnh offline độ nét cao khi không có mạng.
- [ ] **AC-5**: WebApp đọc Snapshot JSON trên Drive tự động merge dữ liệu với Firestore/Cache, bảo toàn `driveFileId` và không làm mất ảnh của bất kỳ thiết bị nào.
- [ ] **AC-6**: Tài khoản Owner/Creator của dự án có thể đồng bộ trực tiếp lên Firebase mà không bị chặn bởi bảng `accessRequests`.

## Task Links

- [ ] Task-1: Cập nhật Entity `SitePhotoEntity`, Domain Model `SitePhoto`, Room Database Migration và Sync Catalog.
- [ ] Task-2: Cập nhật `DriveMediaUploadClient` và `FirebaseSyncRepositoryImpl` để lưu và truyền `driveFileId`, áp dụng bộ lọc chống upload trùng lặp.
- [ ] Task-3: Nâng cấp `ensureApprovedAccess` cho phép Owner/Creator bypass `accessRequests`, và xử lý an toàn lỗi `FileNotFound` trong media sync.
- [ ] Task-4: Cập nhật UI Image Resolver trên Android (Coil/Glide) để ưu tiên Google Drive CDN URL khi chưa có file local.
- [ ] Task-5: Cập nhật Snapshot Exporter và WebApp Reconcile logic trong `webapp/lib/public-project.ts` & `google-drive-media.ts`.
- [ ] Task-6: Kiểm thử toàn diện Unit Tests (Android + WebApp) và xác minh hoạt động.

## Spec Decision Compliance

- `D1=pass`: Explicit Drive File ID Schema & Room Migration
- `D2=pass`: Anti-Duplication & Strict Upload Filter
- `D3=pass`: Drive CDN Thumbnail Priority & No Thumbnail Download
- `D4=pass`: Progressive Background Original Media Restore
- `D5=pass`: Webapp Snapshot Field-Level Preservation & Merge
- `D6=pass`: Owner/Creator Access Bypass & Push Exception Resilience
