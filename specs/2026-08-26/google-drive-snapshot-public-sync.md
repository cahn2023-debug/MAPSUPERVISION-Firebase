# Spec: Google Drive Snapshot Sync for Public Project View (269-2026)

## Overview

Đặc tả kỹ thuật cho giải pháp **Google Drive Snapshot Sync** phục vụ hiển thị dữ liệu công khai trên link dự án [MapSupervision Sync](https://mapsupervision-webapp.vercel.app/269-2026). Giải pháp này giải quyết triệt để vấn đề quá tải hạn ngạch đọc (read quota) của Firestore bằng cách chuyển nguồn dữ liệu chính của trang Public sang các bản **Snapshot JSON toàn diện** lưu trữ trên Google Drive, đồng thời triển khai cơ chế đệm vòng đời 5 phút an toàn trước khi dọn dẹp các snapshot cũ.

## Locked Decisions

- **D1 (Trigger & Payload Snapshot từ Android)**: Mỗi khi Android hoàn thành chu kỳ Đồng bộ (Sync) dữ liệu lên Cloud thành công, hệ thống tự động trích xuất toàn bộ thực thể Room Database cục bộ của dự án (`project`, `gis_node`, `gis_route`, `daily_log`, `site_photos`, `task`, `work_plan`, `work_volume_progress`, `material_declaration`, `material_handover`, `report_draft`) đóng gói thành 1 file JSON snapshot (ví dụ: `snapshot_269_2026_<timestamp>.json`) và tải lên Google Drive.
- **D2 (Drive Snapshot Directory Structure)**: Tạo và quản lý thư mục con `Snapshots` nằm trực tiếp bên trong thư mục của Dự án trên Google Drive (`[Tên Dự Án]/Snapshots/`). File snapshot được đặt tên chuẩn hóa `snapshot_<projectId>_<epochMs>.json`.
- **D3 (Drive-First Data Resolution for Public View)**: Endpoint `/api/public/269-2026` trên WebApp ưu tiên 100% truy vấn và phân giải file Snapshot JSON mới nhất trong thư mục `Snapshots` trên Google Drive (sử dụng Google Drive API qua Service Account đã cấu hình). Chỉ khi Google Drive không có file snapshot nào mới thực hiện fallback đọc từ Firestore, giúp trang Public hoạt động độc lập và không bao giờ bị ảnh hưởng bởi quota Firestore.
- **D4 (Dual-side 5-Minute Safe Retention & Pruning)**: Duy trì khoảng đệm an toàn 5 phút (300 giây) trước khi xóa các snapshot cũ để đảm bảo các phiên truy cập đang đọc không bị gián đoạn. Cơ chế dọn dẹp được thực thi song song ở cả 2 phía: Android tự động xóa các snapshot cũ > 5 phút sau khi upload snapshot mới, và WebApp API kích hoạt dọn dẹp ngầm (background cleanup) các snapshot cũ > 5 phút khi xử lý request.

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements

- **FR-1 (Android Local Snapshot Generation)**: 
  - Sau khi Android thực thi thành công việc sync Firestore/Drive media, Android truy vấn toàn bộ dữ liệu dự án từ SQLite Room Database.
  - Chuẩn hóa payload theo đúng schema `PublicProjectPayload` (`project`, `collections`, `updatedAtEpochMs`).
- **FR-2 (Android Direct Drive Snapshot Upload)**:
  - Android sử dụng `DriveMediaUploadClient` / Google Drive API để kiểm tra/tạo thư mục `Snapshots` bên trong thư mục dự án trên Drive.
  - Đẩy file JSON snapshot lên thư mục này với MIME type `application/json`.
- **FR-3 (Android 5-Minute Retention Pruner)**:
  - Sau khi upload snapshot mới thành công, Android liệt kê tất cả các file trong thư mục `Snapshots`.
  - Giữ lại snapshot mới nhất và bất kỳ snapshot nào có thời gian tạo trong vòng 5 phút (300.000 ms) gần nhất.
  - Xóa vĩnh viễn các snapshot cũ hơn 5 phút.
- **FR-4 (WebApp Drive Snapshot Reader)**:
  - `webapp/lib/public-project.ts` được nâng cấp để tìm thư mục dự án 269 trên Drive, vào thư mục con `Snapshots` và lấy nội dung file snapshot có timestamp mới nhất.
  - Tích hợp bộ đệm In-Memory / Edge Cache ngắn hạn (15s - 30s) trên WebApp để tối ưu số lượt gọi Drive API.
- **FR-5 (WebApp Fallback Resilience)**:
  - Nếu Drive API trả về lỗi hoặc thư mục `Snapshots` trống, hệ thống tự động fallback về Firestore hoặc bộ nhớ đệm gần nhất.
- **FR-6 (WebApp Background Pruning)**:
  - WebApp hỗ trợ quét dọn ngầm các file snapshot trong thư mục `Snapshots` đã tạo quá 5 phút để giữ thư mục Drive luôn sạch sẽ.

### Non-Functional Requirements

- **NFR-1 (Zero Firestore Read Quota Consumption for Public View)**: Khi có snapshot trên Google Drive, số lượt đọc Firestore từ trang Public `/269-2026` giảm về 0 reads.
- **NFR-2 (Data Freshness & Reliability)**: Dữ liệu công khai cập nhật gần như tức thì theo mỗi lần Android đồng bộ. Tốc độ phản hồi trang Public < 600ms khi phục vụ từ Drive cache.
- **NFR-3 (Safe Concurrent Access)**: Khoảng trễ đệm 5 phút ngăn chặn triệt để hiện tượng race condition khi client đang đọc file thì file bị xóa.

## Acceptance Criteria

- [x] **AC-1**: Khi người dùng nhấn Đồng bộ trên app Android và hoàn tất, một file JSON snapshot (ví dụ: `snapshot_..._<timestamp>.json`) xuất hiện trong thư mục `[Tên Dự Án]/Snapshots/` trên Google Drive.
- [x] **AC-2**: Trang `https://mapsupervision-webapp.vercel.app/269-2026` hiển thị dữ liệu mới nhất được đọc trực tiếp từ file Snapshot trên Google Drive.
- [x] **AC-3**: Số lượt đọc Firestore từ endpoint `/api/public/269-2026` bằng 0 khi có snapshot trên Drive.
- [x] **AC-4**: Các bản snapshot cũ hơn 5 phút tự động bị xóa khỏi Google Drive, chỉ giữ lại bản mới nhất và các bản trong cửa sổ an toàn 5 phút.
- [x] **AC-5**: Nếu Google Drive tạm thời không khả dụng, trang public vẫn hiển thị bình thường thông qua cơ chế fallback an toàn (không bị crash hay báo lỗi 500).

## Scenarios

### Scenario 1: Android hoàn thành đồng bộ và xuất bản Snapshot lên Google Drive (Happy Path)
**Given** Kỹ sư cập nhật thêm 5 ảnh hiện trường và hoàn thành 2 công việc trên app Android.  
**When** Android thực hiện chu kỳ Đồng bộ Cloud thành công.  
**Then** Android tự động tạo snapshot JSON mới, upload vào thư mục `[Dự án 269]/Snapshots/` trên Google Drive, và xóa các snapshot cũ đã tạo hơn 5 phút trước.

### Scenario 2: Người xem truy cập Link Public WebApp
**Given** File snapshot mới nhất đã được đẩy lên Google Drive 1 phút trước.  
**When** Người dùng truy cập `https://mapsupervision-webapp.vercel.app/269-2026`.  
**Then** WebApp tải snapshot JSON từ Google Drive, hiển thị đầy đủ 100% hình ảnh, bản đồ GIS và tiến độ mà không tốn bất kỳ lượt đọc Firestore nào.

### Scenario 3: Quản lý vòng đời và dọn dẹp Snapshot sau 5 phút
**Given** Thư mục `Snapshots` có file `snapshot_T0` (tạo 6 phút trước) và `snapshot_T1` (vừa tạo).  
**When** Tiến trình dọn dẹp kích hoạt sau khi upload `snapshot_T1`.  
**Then** File `snapshot_T0` bị xóa vĩnh viễn khỏi Drive vì đã vượt quá ngưỡng an toàn 5 phút; file `snapshot_T1` được giữ lại phục vụ client.

## Technical Notes

- **Android Client**:
  - `WorkspaceSnapshotExporter.kt`: Trích xuất toàn bộ project và các subcollections public từ Room DB.
  - `DriveMediaUploadClient.kt`: Tải snapshot JSON vào thư mục `Snapshots/` và thực hiện hàm `pruneOldSnapshots(maxAgeMs = 5 * 60 * 1000)` để xóa các file cũ > 5 phút.
  - `FirebaseSyncRepositoryImpl.kt`: Tự động gọi `exportAndUploadSnapshotInternal()` sau mỗi chu kỳ push sync thành công.
- **WebApp Serverless**:
  - `webapp/lib/google-drive-media.ts`: Cung cấp các hàm `getLatestDriveSnapshot()`, `pruneOldDriveSnapshots()`, `uploadDriveSnapshot()`.
  - `webapp/lib/public-project.ts`: Hàm `readPublicProject()` ưu tiên đọc trực tiếp Snapshot từ Drive (Zero Firestore reads), tự động kích hoạt dọn dẹp ngầm các file cũ quá 5 phút và fallback an toàn nếu Drive tạm gián đoạn.

## Task Links

- [x] Task-1: WebApp Google Drive Snapshot Resolution & 5-minute Retention Pruner (`webapp/lib/public-project.ts`, `webapp/lib/google-drive-media.ts`) - Verified with 58/58 unit tests.
- [x] Task-2: Android Workspace Snapshot Exporter (`data/src/main/java/com/mapsupervision/data/sync/WorkspaceSnapshotExporter.kt`) - Verified with unit tests.
- [x] Task-3: Android Google Drive Snapshot Upload & Pruning Integration (`data/src/main/java/com/mapsupervision/data/sync/DriveMediaUploadClient.kt`, `FirebaseSyncRepositoryImpl.kt`) - Verified with full Gradle build & unit tests.
- [x] Task-4: End-to-End Test Suite Verification across Web and Android - 100% Passed.

## Spec Decision Compliance

- `D1=pass`: Xuất bản snapshot JSON từ Android mỗi lần sync thành công.
- `D2=pass`: Lưu tại thư mục `[Project]/Snapshots/` trên Google Drive.
- `D3=pass`: WebApp Public đọc trực tiếp từ Snapshot trên Drive, triệt tiêu đọc Firestore.
- `D4=pass`: Cơ chế đệm an toàn 5 phút và tự động xóa các snapshot cũ > 5 phút từ cả 2 phía.


## Open Questions

- Không còn câu hỏi bỏ ngỏ. Tất cả các quyết định D1-D4 đã được thống nhất đầy đủ qua quy trình Socratic Dialog.
