# Spec: Google Drive Media Distribution and Android Two-Way Restore

## Overview

Đặc tả kỹ thuật cho tính năng **Phân phối hình ảnh qua Google Drive (Decoupled Distribution)** và **Khôi phục ảnh lỗi về thiết bị Android (Two-Way Media Restore)** trong hệ thống MapSupervision. 

Giải pháp đảm bảo:
1. **Phân phối độc lập trên WebApp**: Sau khi ảnh được upload lên Google Drive từ bất kỳ thiết bị nào, WebApp luôn hiển thị được hình ảnh thông qua liên kết Google Drive trực tiếp (hoặc CDN cache), hoàn toàn không phụ thuộc vào thiết bị nguồn hay đường dẫn cục bộ.
2. **Khôi phục ảnh lỗi trên Android**: Khi một thiết bị Android bị thiếu file ảnh, mất file cục bộ do cài lại máy hoặc lỗi đường dẫn, hệ thống cho phép người dùng phát hiện trực quan, chọn đơn lẻ hoặc chọn hàng loạt ảnh bị lỗi để tải ngược lại (Restore) từ Google Drive về bộ nhớ máy và tái liên kết dữ liệu Room Database.

## Locked Decisions

- **D1 (Centralized Metadata & Snapshot Distribution)**: Khi ảnh được upload lên Google Drive từ thiết bị Android, metadata chuẩn hóa (`photoId`, `driveFileId`, `driveUrl`, `localPath`, `timestamp`, `objectType`, `objectCode`) được lưu vào Firestore collection `site_photos` và đóng gói trong bản Snapshot JSON trên Drive. WebApp và các thiết bị khác tra cứu và hiển thị ảnh thông qua metadata này một cách độc lập với thiết bị upload ban đầu.
- **D2 (WebApp 100% Drive Media Resolution)**: WebApp chỉ sử dụng link ảnh từ Google Drive (thông qua Drive Direct URL `lh3.googleusercontent.com/d/{id}` hoặc Media Proxy Route) để hiển thị. WebApp không bao giờ phụ thuộc vào local path của thiết bị upload và không tiêu tốn lượt đọc Firestore khi chạy qua Snapshot.
- **D3 (Android Missing Media Detection & Selective Restore)**: Khi Android phát hiện file ảnh cục bộ không tồn tại trên bộ nhớ máy (`File(localPath).exists() == false`), hệ thống đánh dấu trạng thái ảnh lỗi và cho phép người dùng chọn các ảnh lỗi cụ thể hoặc chọn toàn bộ để kích hoạt tiến trình tải ngược (Restore) từ Google Drive.
- **D4 (Download Stream & Room DB Re-linking)**: Tiến trình khôi phục ảnh trên Android tải dữ liệu nhị phân của ảnh từ Google Drive (thông qua Drive API với Service Account hoặc tải trực tiếp theo `driveFileId`), lưu file vật lý vào thư mục lưu trữ media cục bộ của app (`context.getExternalFilesDir` / `filesDir/photos/`), sau đó cập nhật lại trường `localPath` trong Room Database và xóa cờ cảnh báo lỗi.
- **D5 (Error UI & Batch Recovery UX)**: 
  - Hiển thị badge/icon cảnh báo lỗi trên từng thumbnail ảnh bị mất file cục bộ trong danh sách ảnh / bản đồ / chi tiết đối tượng.
  - Cung cấp nút "Khôi phục ảnh" tức thì khi mở xem chi tiết ảnh lỗi.
  - Tích hợp mục "Kiểm tra & Khôi phục ảnh lỗi" trong màn hình Đồng bộ / Cài đặt dự án, hỗ trợ "Chọn tất cả" và "Khôi phục đã chọn" kèm thanh tiến độ tải chi tiết.

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements

- **FR-1 (Drive Metadata Synchronization)**:
  - Khi thiết bị upload ảnh lên Drive thành công, `driveFileId` và `driveUrl` phải được cập nhật đầy đủ vào Room DB, Firestore và Snapshot JSON.
- **FR-2 (WebApp Drive-Only Media Viewer)**:
  - Tất cả các component hiển thị ảnh trên WebApp (`ImageModal`, `PhotoGallery`, `MapPopup`, `GisNodeDetails`, `ReportView`) sử dụng chuẩn hóa URL từ Google Drive (`googleDriveImageUrl` / `imageSourceUrl`).
- **FR-3 (Android Missing Media Scanner)**:
  - Cung cấp use-case / repository method `checkMissingLocalPhotos(projectId)` trên Android để quét toàn bộ danh sách `PhotoEntity` trong Room DB, kiểm tra sự tồn tại của file vật lý theo `localPath`.
  - Nếu `localPath` rỗng hoặc file không tồn tại nhưng có `driveFileId` hợp lệ -> gắn cờ `isMissingLocalFile = true`.
- **FR-4 (Android Media Restore Engine)**:
  - `DriveMediaUploadClient` (hoặc `DriveMediaRestoreClient`) bổ sung phương thức `downloadPhotoFromDrive(driveFileId, destinationFile): Result<File>`.
  - Tải file từ Google Drive bằng Service Account token hoặc direct streaming link, ghi an toàn vào file tạm rồi đổi tên vào file đích (`.tmp -> .jpg`).
  - Cập nhật bản ghi `PhotoEntity` trong SQLite Room DB với `localPath = destinationFile.absolutePath`.
- **FR-5 (Batch Restore Flow on Android)**:
  - Người dùng có thể chọn danh sách `List<String> photoIds` bị lỗi và bấm "Khôi phục đã chọn".
  - Hiển thị thông báo tiến độ (ví dụ: `Đang khôi phục 3/10 ảnh...`) và xử lý tải tuần tự/song song có kiểm soát giới hạn (concurrency limit = 3).
  - Tự động bỏ qua hoặc báo lỗi chi tiết đối với ảnh không thể tải được mà không làm gián đoạn toàn bộ tiến trình.
- **FR-6 (UI Warning & Action Badges)**:
  - Thumbnail của ảnh bị thiếu file hiển thị icon mây gạch chéo hoặc biểu tượng cảnh báo màu cam.
  - Khi bấm vào ảnh lỗi, mở dialog/bottom-sheet thông báo: "Ảnh chưa có trên thiết bị này. Đã có trên Google Drive" kèm nút bấm "Tải về máy".

### Non-Functional Requirements

- **NFR-1 (Data Resilience & Self-Healing)**: Hệ thống có khả năng tự phục hồi dữ liệu media bị mất trên mọi thiết bị Android mà không cần can thiệp thủ công vào hệ thống file.
- **NFR-2 (Bandwidth & Quota Efficiency)**: Chỉ tải các ảnh người dùng yêu cầu khôi phục, không tự động tải ép buộc toàn bộ kho ảnh gây tốn dung lượng 4G/5G và bộ nhớ máy.
- **NFR-3 (Zero WebApp Interruption)**: WebApp hoạt động 100% mượt mà từ Google Drive, độc lập hoàn toàn với việc điện thoại của kỹ sư có bật mạng hay không.

## Acceptance Criteria

- [x] **AC-1**: WebApp hiển thị chính xác mọi hình ảnh của dự án được upload từ bất kỳ thiết bị Android nào qua liên kết Google Drive, không phụ thuộc vào thiết bị nguồn.
- [x] **AC-2**: Trên app Android, khi file ảnh cục bộ bị xóa khỏi bộ nhớ máy, ứng dụng tự động nhận diện và hiển thị biểu tượng cảnh báo ảnh chưa có trên máy.
- [x] **AC-3**: Người dùng có thể bấm nút "Khôi phục ảnh" trong màn hình chi tiết ảnh để tải lại file từ Google Drive về máy thành công; sau khi tải xong, ảnh hiển thị sắc nét và `localPath` trong DB được cập nhật.
- [x] **AC-4**: Trong màn hình Đồng bộ trên Android, người dùng có thể xem danh sách toàn bộ ảnh lỗi, chọn nhiều ảnh (hoặc Chọn tất cả) và bấm "Khôi phục đã chọn" để tải hàng loạt về máy kèm thanh tiến độ.
- [x] **AC-5**: Quá trình khôi phục ảnh xử lý mượt mà khi mất mạng giữa chừng, tự động retry hoặc thông báo lỗi rõ ràng mà không làm hỏng dữ liệu Room DB.

## Scenarios

### Scenario 1: Kỹ sư B xem ảnh do Kỹ sư A upload trên WebApp
**Given** Kỹ sư A chụp 5 ảnh hiện trường trên máy Android A và hoàn thành đồng bộ lên Google Drive.  
**When** Kỹ sư B hoặc Chủ đầu tư mở WebApp tại link dự án.  
**Then** WebApp hiển thị đầy đủ 5 ảnh với độ phân giải cao trực tiếp từ Google Drive mà không cần máy Android A phải mở mạng hay duy trì kết nối.

### Scenario 2: Kỹ sư đổi điện thoại mới hoặc lỡ xóa thư mục ảnh trên Android (Khôi phục hàng loạt)
**Given** Kỹ sư đăng nhập dự án trên điện thoại mới; dữ liệu văn bản/Room DB đã sync về nhưng máy chưa có các file ảnh cục bộ (bị thiếu 15 ảnh).  
**When** Kỹ sư vào màn hình Đồng bộ -> chọn "Kiểm tra & Khôi phục ảnh lỗi" -> bấm "Chọn tất cả" -> bấm "Khôi phục đã chọn".  
**Then** App Android tải tuần tự 15 file ảnh từ Google Drive về thư mục media của app, cập nhật lại `localPath` trong Room DB, và toàn bộ 15 ảnh chuyển sang trạng thái khả dụng đầy đủ trên máy.

### Scenario 3: Khôi phục ảnh đơn lẻ khi xem chi tiết
**Given** Một bức ảnh trong danh sách công việc hiển thị biểu tượng cảnh báo vì mất file cục bộ.  
**When** Người dùng chạm vào ảnh và nhấn "Khôi phục từ Google Drive".  
**Then** Ứng dụng hiển thị vòng xoay tiến độ tải, tải file về máy và tự động làm mới giao diện hiển thị ảnh gốc ngay lập tức.

## Technical Notes

- **Android Client Implementation Details**:
  - `DriveMediaUploadClient.kt`: Bổ sung hàm `downloadMediaFile(driveFileId: String, targetFile: File): Boolean` và `extractDriveFileId(value: String): String`.
  - `FieldRepositories.kt` & `PhotoRepository.kt`: Triển khai `restoreFromDrive(photo: SitePhoto): AppResult<SitePhoto>`.
  - `FirebaseSyncRepositoryImpl.kt`: Triển khai `restoreMissingMedia(projectId: String, photoIds: List<String>): AppResult<MediaRestoreResult>`.
  - `WorkspaceUiShared.kt` & `MapHubDetails.kt`: Thêm `toDriveDirectUrl`, badge nhận diện ảnh Drive khi thiếu file cục bộ và fallback load ảnh toàn màn hình.
- **WebApp Verification**:
  - `webapp/lib/google-drive-image.ts`: Hỗ trợ phân phối 100% hình ảnh độc lập với thiết bị nguồn.
  - Bộ test suite 58 unit tests trên WebApp đạt 100% Passed.

## Task Links

- [x] Task-1: Android Google Drive Media Downloader Engine (`DriveMediaUploadClient.kt`, `DriveMediaUploadClientTest.kt`)
- [x] Task-2: Android Missing Media Detection & Repository Restore Service (`PhotoRepository.kt`, `FieldRepositories.kt`, `FirebaseSyncRepositoryImpl.kt`, `FirebaseSyncRepositoryImplTest.kt`)
- [x] Task-3: Android UI Missing Photo Warning Badge & Fullscreen Resolution (`WorkspaceUiShared.kt`, `MapHubDetails.kt`)
- [x] Task-4: WebApp Media Resolution Audit & End-to-End Verification (`webapp/lib/google-drive-image.ts`, WebApp test suite)

## Spec Decision Compliance

- `D1=pass`: Centralized Metadata & Snapshot Distribution.
- `D2=pass`: WebApp 100% Drive Media Resolution.
- `D3=pass`: Android Missing Media Detection & Selective Restore.
- `D4=pass`: Download Stream & Room DB Re-linking.
- `D5=pass`: Error UI & Batch Recovery UX.

## Open Questions

- Không còn câu hỏi bỏ ngỏ. Tất cả các quyết định D1-D5 đã được chốt và kiểm thử thành công.
