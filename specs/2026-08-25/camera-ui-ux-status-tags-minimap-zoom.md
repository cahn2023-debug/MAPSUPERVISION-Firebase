# Specification: Hoàn thiện UI/UX Camera, Thẻ trạng thái ("Hiện trạng", "Thi công", ...) và Chuẩn hóa Minimap Zoom 19

## Overview

Tài liệu đặc tả yêu cầu, thiết kế giao diện cao cấp (UI/UX Pro Max) và giải pháp kỹ thuật cho **Màn hình Máy ảnh thực địa (CameraOverlay / CameraX HUD)**. Tính năng hoàn thiện trải nghiệm phân loại nhanh giai đoạn công trình bằng các thẻ trạng thái ("Hiện trạng", "Thi công", "Hoàn trả", "Vướng mắc", ...), khắc phục triệt để lỗi Minimap bị zoom xa (thu nhỏ cả thành phố), và chuẩn hóa mức hiển thị bản đồ thu nhỏ ở **Zoom 19** sắc nét, tập trung chính xác vào đối tượng khảo sát.

---

## Locked Decisions

- **D1 — Hệ thống Thẻ trạng thái (Status Tags / Presets):**
  - Mặc định khi mở Camera: tự động chọn thẻ **"Hiện trạng"** (Current State).
  - Kỹ sư có thể chuyển đổi 1-chạm mượt mà giữa các thẻ hệ thống: **"Hiện trạng"**, **"Thi công"**, **"Hoàn trả"**, **"Vướng mắc"** và các thẻ tùy chỉnh của dự án (`MediaStatusTag`).
  - Thẻ được chọn sẽ:
    1. Lưu trực tiếp vào trường `SitePhoto.statusTag` trong cơ sở dữ liệu Room SQLite và Firestore.
    2. Định tuyến cấu trúc thư mục lưu trữ media trên thiết bị (`/files/<projectSlug>/<ObjectCode>/<StatusTag>/...`) qua `storageManager.resolveMediaFolder`.
    3. Hiển thị badge trạng thái trực quan trên giao diện chụp ảnh HUD và đóng dấu (watermark stamp) lên góc ảnh/video.

- **D2 — Thiết kế UI/UX Pro Max cho Dải Thẻ Trạng Thái:**
  - Áp dụng phong cách **Glassmorphism 2.0**:
    - Dải chip cuộn ngang (Horizontal scrollable pill row) đặt ở vị trí thuận tiện ngón cái (Thumb-friendly, chiều cao >= 44dp).
    - Thẻ đang chọn (Active): Nền sáng Cyan `#00E5FF`, chữ đậm `#060814`, viền phát sáng vi tế (`glow-cyan`).
    - Thẻ chưa chọn (Inactive): Kính mờ bán trong suốt `rgba(10, 13, 26, 0.65)`, viền mảnh `1px solid rgba(255, 255, 255, 0.15)`, chữ trắng `0.85 alpha`.
  - Hiệu ứng tương tác (Micro-interactions): Chạm nhẹ chuyển trạng thái mượt mà, phản hồi trực quan tức thì.

- **D3 — Chuẩn hóa Mức Zoom Minimap (Zoom 19):**
  - **Khắc phục lỗi zoom xa:** Sử dụng `buildCaptureMinimapScope(nodeCode, nodes, routes)` để giới hạn phạm vi tính toán viewport minimap cho chỉ nút/tuyến hiện tại, loại bỏ việc nạp toàn bộ 50+ nút của cả thành phố vào minimap preview.
  - Cố định và duy trì mức zoom chuẩn **Zoom 19** (độ chi tiết mặt đường, vỉa hè, hố ga, tuyến cáp tại hiện trường).
  - Đồng bộ hằng số tải tile bản đồ OSM `MINIMAP_TILE_ZOOM = 19` trong `StampDataRepositoryImpl.kt` và `PhotoStampRenderer.kt`.
  - Nâng cấp độ tương phản văn bản tọa độ (Vĩ độ / Kinh độ) dưới minimap, đảm bảo dễ đọc trên mọi nền sáng/tối.

---

## System Decision Impact

- **Impact:** draft new
- **Decision:** `@decision/camera-ui-ux-status-tags-and-zoom19`
- **Acceptance gate:** Đạt toàn bộ kiểm thử Unit Test (`CameraOverlayHelpersTest`, `PhotoStampRendererTest`, `WorkspaceCaptureMinimapScopeTest`, `PhotoRepositoryImplTest`) và xác thực hiển thị UI trực quan trên Camera.

---

## Requirements

### Functional Requirements

- **FR-1 — Thanh chọn Thẻ trạng thái trên Camera:**
  - Bổ sung `StatusTagSelector` trên giao diện `CameraOverlay.kt`.
  - Danh sách thẻ bao gồm các thẻ hệ thống `MediaStatusTags.systemNames` kết hợp thẻ tùy chỉnh từ `MediaStatusTagRepository`.
  - Khi chụp ảnh hoặc quay video, `selectedStatusTag` và `noteText` được truyền đầy đủ vào hàm lưu trữ `savePhoto`.
- **FR-2 — Phạm vi Minimap và Thu phóng Zoom 19:**
  - `WorkspaceAppShell.kt` truyền danh sách `nodes` và `routes` đã được giới hạn qua `buildCaptureMinimapScope(pendingCapture, designNodes, designRoutes)`.
  - `CameraOverlay.kt` tính toán và hiển thị minimap ở Zoom 19 khi người dùng ở gần đối tượng.
  - Khắc phục cơ chế chốt zoom (`resolveLatchedMinimapZoom`) để không bị kẹt ở zoom thấp khi dữ liệu vị trí cập nhật.
- **FR-3 — Đồng bộ Lưu trữ & Watermark:**
  - Hàm `WorkspaceViewModel.savePhoto` lưu nhận diện `statusTag` vào bản ghi `SitePhoto`.
  - Ảnh xuất ra được lưu đúng cây thư mục phân loại theo `statusTag`.
  - `PhotoStampLayoutCalculator` và `PhotoStampRenderer` hỗ trợ hiển thị thẻ trạng thái trên stamp ảnh nếu có.

### Non-Functional Requirements

- **NFR-1 — Hiệu năng (60 FPS Camera Preview):** Việc vẽ minimap tile và chip selector không gây giật lag khung hình preview của CameraX.
- **NFR-2 — Khả năng sử dụng thực địa:** Các nút bấm và chip thẻ đạt kích thước tối thiểu 44dp để kỹ sư dễ dàng thao tác bằng một tay khi đang đeo găng tay ngoài công trường.
- **NFR-3 — Hoạt động Offline 100%:** Các thẻ và việc tính toán tọa độ, minimap fallback hoạt động mượt mà cả khi không có kết nối Internet.

---

## Acceptance Criteria

- [ ] **AC-1:** Mở Camera khi chọn một đối tượng (Node/Route) hiển thị dải chip trạng thái với "Hiện trạng" được chọn sẵn.
- [ ] **AC-2:** Chạm chọn bất kỳ thẻ nào ("Thi công", "Hoàn trả", "Vướng mắc") đổi trạng thái highlight tức thì sang màu Cyan `#00E5FF`.
- [ ] **AC-3:** Ảnh/Video sau khi chụp được lưu với `statusTag` tương ứng trong cơ sở dữ liệu và thư mục lưu trữ.
- [ ] **AC-4:** Minimap trên Camera hiển thị cận cảnh ở Zoom 19 (không bị thu nhỏ toàn thành phố).
- [ ] **AC-5:** Tất cả Unit Tests và Regression Tests hiện hữu vượt qua (Passed).

---

## Scenarios

### Scenario 1: Kỹ sư chụp ảnh ghi nhận thi công tại hiện trường
**Given** Kỹ sư đang mở ứng dụng và bấm chụp ảnh tại nút `H01`
**When** Camera mở lên, kỹ sư chạm vào thẻ "Thi công" và bấm nút chụp
**Then** Ảnh được chụp với stamp chứa thông tin nút `H01`, thẻ `[Thi công]`, minimap hiển thị chi tiết tại Zoom 19, và file được lưu vào thư mục `H01/Thi công/`.

### Scenario 2: Chuyển đổi linh hoạt giữa các thẻ trạng thái
**Given** Camera đang mở ở chế độ Video
**When** Kỹ sư chuyển từ thẻ "Hiện trạng" sang thẻ "Vướng mắc" và nhập ghi chú "Đang vướng cáp viễn thông"
**Then** Video quay xong lưu trữ đúng trạng thái "Vướng mắc" và ghi chú đính kèm.

---

## Technical Notes

1. **`WorkspaceAppShell.kt`**:
   - Thay vì truyền trực tiếp `workspaceState.designNodes`, gọi `buildCaptureMinimapScope(pendingCapture, workspaceState.designNodes, workspaceState.designRoutes)` để trích xuất `scopedNodes` và `scopedRoutes`.
2. **`CameraOverlay.kt`**:
   - Thêm `var selectedStatusTag by remember { mutableStateOf("Hiện trạng") }`.
   - Bổ sung Composable `StatusTagRow` với phong cách Glassmorphism 2.0.
   - Nâng cấp callback `onSavePhoto` hoặc truyền `statusTag` và `noteText` vào `photoPipelineService.createCaptureOutputFile` và `WorkspaceViewModel.savePhoto`.
3. **`StampDataRepositoryImpl.kt`**:
   - Cập nhật `MINIMAP_TILE_ZOOM = 19`.
4. **`PhotoStampLayout.kt` & `PhotoStampRenderer.kt`**:
   - Đảm bảo hiển thị thẻ trạng thái và độ tương phản của text tọa độ dưới minimap sắc nét.
