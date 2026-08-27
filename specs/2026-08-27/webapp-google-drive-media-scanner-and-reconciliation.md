# Spec: WebApp Google Drive Media Scanner & Reconciliation

## Overview

Đặc tả kỹ thuật cho tính năng **Quét Google Drive, Kiểm tra và Bổ sung hình ảnh còn thiếu (Drive Media Scanner & Reconciliation)** trên giao diện WebApp của hệ thống MapSupervision.

Tính năng này cho phép quản trị viên / kỹ sư:
1. Quét toàn diện thư mục Google Drive của dự án để phát hiện các file ảnh/video thực tế đang tồn tại trên Drive nhưng chưa được ghi nhận trong cơ sở dữ liệu (`site_photos` trong Firestore / Snapshot).
2. Tự động phân tích cú pháp tên file và đường dẫn thư mục để tái tạo đầy đủ Metadata (Mã đối tượng, Thời gian chụp, Địa chỉ, Ghi chú).
3. Hiển thị báo cáo đối soát trực quan (Preview) và cho phép người dùng xác nhận bổ sung (đơn lẻ hoặc hàng loạt) vào hệ thống.
4. Tự động lưu vào Firestore và xuất bản bản Snapshot JSON mới lên Google Drive để WebApp và các thiết bị Android nhận dữ liệu đồng bộ ngay lập tức.

## Locked Decisions

- **D1 (Full Drive Photo Scan & Hierarchy Traversal)**: WebApp thực hiện quét toàn bộ thư mục `photos/` và `media/` của dự án trên Google Drive qua Service Account API, duyệt qua các thư mục con `photos/Nodes/{objectCode}/`, `photos/Routes/{objectCode}/`, `media/videos/` để lập danh sách toàn bộ các file ảnh/video thực tế trên Drive và so sánh đối chiếu với danh sách `site_photos` hiện có trong database.
- **D2 (Intelligent Metadata Extraction from Path & Naming)**: Tự động phân tích và khôi phục metadata ảnh bị thiếu từ cấu trúc thư mục (`objectCode`, `statusTag`) kết hợp tên file chuẩn (`yyyy-MM-dd HH.mm.ss - Address - Note.ext`) hoặc `createdTime` của Drive để tạo bản ghi chuẩn: `capturedAtEpochMs`, `objectCode`, `address`, `captureNote`, `mediaType`, `remoteUrl` (Google Drive URL / fileId).
- **D3 (Interactive Scan Report & Selective Confirmation)**: Hiển thị báo cáo kết quả quét trực quan trên WebApp (Tổng số ảnh trên Drive, Số ảnh đã khớp, Số ảnh mới phát hiện). Cho phép người dùng xem trước thumbnail, lọc danh sách, chọn ảnh cụ thể hoặc bấm "Bổ sung tất cả" để xác nhận thêm vào hệ thống.
- **D4 (Firestore Batch Insertion & Instant Snapshot Publishing)**: Khi người dùng bấm xác nhận bổ sung, WebApp ghi các document `site_photos` mới vào Firestore qua batch write, đồng thời tự động xuất bản một file Snapshot JSON mới vào thư mục `[Tên Dự Án]/Snapshots/` trên Drive để các thiết bị Android và WebApp đồng bộ tức thì.

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements

- **FR-1 (Drive Media Crawler & Directory Scanner)**:
  - Backend API endpoint `/api/projects/[projectId]/media/scan` (POST) quét đệ quy các thư mục ảnh trong thư mục dự án trên Drive.
  - Lập danh sách tất cả các file có đuôi `.jpg`, `.jpeg`, `.png`, `.webp`, `.mp4`, `.mov`.
  - Bỏ qua các file snapshot trong thư mục `Snapshots/` và các file thumbnail phụ (`*__thumb*` hoặc `* - thumbnail*`).
- **FR-2 (Metadata Extractor)**:
  - Phân rã cấu trúc folder:
    - Thư mục dạng `photos/Nodes/[MãĐiểm]` -> `objectType = NODE`, `objectCode = [MãĐiểm]`.
    - Thư mục dạng `photos/Routes/[MãTuyến]` -> `objectType = ROUTE`, `objectCode = [MãTuyến]`.
    - Thư mục con bổ sung (nếu có): `.../[StatusTag]` -> `statusTag = [StatusTag]`.
  - Phân rã tên file:
    - Tên file chuẩn: `yyyy-MM-dd HH.mm.ss - [Address] - [Note].[ext]` -> parse thời gian chụp `capturedAtEpochMs`, địa chỉ và ghi chú.
    - Tên file không chuẩn: Dùng `createdTime` của Drive file làm `capturedAtEpochMs`, phần còn lại của tên file làm `captureNote`.
  - Sinh mã định danh duy nhất `photoId = drive_[fileId]`.
- **FR-3 (Diff & Reconciliation Engine)**:
  - So sánh danh sách file từ Drive với các bản ghi `site_photos` hiện có (khớp theo `driveFileId` trong `remoteUrl` hoặc trùng khớp `objectCode` + `capturedAtEpochMs`).
  - Phân loại kết quả thành 2 nhóm:
    1. **Matched Photos**: Ảnh đã tồn tại đầy đủ trong database.
    2. **Discovered Missing Photos**: Ảnh có trên Drive nhưng chưa có trong Database.
- **FR-4 (UI Scanner & Reconciliation Modal on WebApp)**:
  - Thêm nút "Quét Drive & Đối soát ảnh" trong thanh công cụ quản lý ảnh / Data Hub trên WebApp.
  - Hộp thoại hiển thị:
    - Thống kê tổng quan: `Tổng ảnh Drive: X | Đã đồng bộ: Y | Ảnh mới phát hiện: Z`.
    - Danh sách lưới/bảng các ảnh mới phát hiện kèm thumbnail, mã đối tượng, thời gian, tên file.
    - Checkbox chọn từng ảnh hoặc "Chọn tất cả".
    - Nút bấm "Bổ sung ảnh đã chọn (Z ảnh)" và hiển thị trạng thái xử lý.
- **FR-5 (Batch Commit & Snapshot Update)**:
  - API endpoint `/api/projects/[projectId]/media/reconcile` (POST) nhận danh sách ảnh được chọn.
  - Ghi các bản ghi `site_photos` mới vào Firestore.
  - Tạo và đẩy Snapshot JSON mới lên thư mục `Snapshots/` trên Google Drive.

### Non-Functional Requirements

- **NFR-1 (Safe & Idempotent)**: Quét và bổ sung ảnh hoàn toàn là thao tác cộng dồn (add-only/idempotent), không ghi đè hoặc làm hỏng dữ liệu ảnh hiện có.
- **NFR-2 (Drive Rate-Limit Resilience)**: Quá trình quét xử lý phân trang Drive API (pageSize = 100) và giới hạn đệ quy an toàn để tránh quá tải quota API.
- **NFR-3 (Responsive Feedback)**: Giao diện WebApp hiển thị trạng thái loading rõ ràng trong suốt quá trình quét và cập nhật dữ liệu.

## Acceptance Criteria

- [x] **AC-1**: Khi người dùng nhấn nút "Quét Google Drive" trên WebApp, hệ thống quét toàn bộ thư mục ảnh của dự án và trả về danh sách ảnh đã khớp và ảnh mới phát hiện.
- [x] **AC-2**: Hệ thống trích xuất chính xác mã đối tượng (`objectCode`), thời gian chụp và ghi chú từ cấu trúc thư mục Drive và tên file chuẩn.
- [x] **AC-3**: Hộp thoại hiển thị đầy đủ thumbnail xem trước và danh sách các ảnh mới tìm thấy trên Drive.
- [x] **AC-4**: Khi người dùng chọn các ảnh và bấm "Bổ sung vào dự án", các ảnh này được lưu thành công vào Firestore và xuất hiện ngay trên giao diện WebApp.
- [x] **AC-5**: Bản Snapshot JSON mới được tự động tạo trên Google Drive, cho phép các thiết bị Android tải về danh sách ảnh mới và khôi phục khi cần.

## Scenarios

### Scenario 1: Quét và phát hiện 5 ảnh chụp từ máy khác được upload thủ công lên Drive
**Given** Kỹ sư dùng máy tính tải trực tiếp 5 file ảnh vào thư mục `photos/Nodes/DC-05/` trên Google Drive dự án.  
**When** Quản trị viên mở WebApp và bấm "Quét Drive & Đối soát ảnh".  
**Then** WebApp phát hiện chính xác 5 ảnh mới với mã đối tượng `DC-05`, hiển thị danh sách xem trước kèm thời gian chụp được giải mã từ tên file.

### Scenario 2: Xác nhận bổ sung toàn bộ ảnh mới vào hệ thống
**Given** Báo cáo quét hiển thị 5 ảnh mới phát hiện.  
**When** Quản trị viên bấm "Bổ sung tất cả (5 ảnh)" và xác nhận.  
**Then** Hệ thống lưu 5 bản ghi `site_photos` vào Firestore, cập nhật bản Snapshot JSON trên Drive, và 5 bức ảnh này lập tức hiển thị trên bản đồ và thư viện ảnh của WebApp.

## Technical Notes

- **WebApp Backend**:
  - `webapp/lib/google-drive-media.ts`: Bổ sung hàm `parseMediaFileName`, `scanProjectDriveMedia` và `uploadDriveSnapshot`.
  - `webapp/app/api/projects/[projectId]/media/scan/route.ts`: API endpoint kích hoạt quét và đối soát.
  - `webapp/app/api/projects/[projectId]/media/reconcile/route.ts`: API endpoint lưu trữ các ảnh được chọn vào Firestore & xuất bản Snapshot.
- **WebApp Frontend**:
  - `webapp/components/DriveMediaReconcileModal.tsx`: Component hiển thị modal quét, thống kê và danh sách ảnh kèm nút xác nhận.
  - `webapp/app/269-2026/page.tsx`: Tích hợp nút `☁️ Quét Drive` và modal đối soát ảnh.

## Task Links

- [x] Task-1: Drive Media Crawler & Filename Parser Engine (`google-drive-media.ts`, `media-scanner.test.ts`)
- [x] Task-2: Scan & Reconciliation API Endpoints (`api/projects/[projectId]/media/scan/route.ts`, `api/projects/[projectId]/media/reconcile/route.ts`)
- [x] Task-3: WebApp Reconciliation UI Modal & Dashboard Integration (`DriveMediaReconcileModal.tsx`, `app/269-2026/page.tsx`)
- [x] Task-4: Automated Unit Tests & Production Build Verification (61/61 tests pass, Next.js 15 production build passed)

## Spec Decision Compliance

- `D1=pass`: Full Drive Photo Scan & Hierarchy Traversal.
- `D2=pass`: Intelligent Metadata Extraction from Path & Naming.
- `D3=pass`: Interactive Scan Report & Selective Confirmation.
- `D4=pass`: Firestore Batch Insertion & Instant Snapshot Publishing.

## Open Questions

- Không còn câu hỏi bỏ ngỏ. Tất cả các quyết định D1-D4 đã được chốt và kiểm thử thành công.
