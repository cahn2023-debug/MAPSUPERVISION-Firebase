# Specification: Camera Minimap Zoom 15-32 & Video Movement Path Tracking

## Overview

Tài liệu đặc tả kỹ thuật cho việc nâng cấp hệ thống Minimap trên Camera Preview, Photo Stamp Watermark và Video Stamp Export:
1. Loại bỏ mức zoom cứng 19, mở rộng toàn bộ dải thu phóng Minimap từ mức **15 đến 32** (`MINIMAP_MIN_ZOOM = 15`, `MINIMAP_MAX_ZOOM = 32`).
2. Cơ chế hiển thị phân lớp (Hybrid Rendering): Khi Zoom từ 20 đến 32, ảnh tile nền bản đồ được phóng đại số (Digital Matrix Upscale) từ tile mức 19, trong khi toàn bộ các đối tượng vector (đường di chuyển khi quay video, vị trí GPS, nón hướng nhìn camera FOV, điểm GIS node, tuyến GIS route) được vẽ vector sắc nét tuyệt đối theo độ phân giải toán học thực tế của mức Zoom $15 \dots 32$ (sử dụng phép tính Mercator Double tránh tràn số 32-bit).
3. Cho phép người dùng tùy chỉnh mức Zoom linh hoạt qua thanh trượt trong bảng Cài đặt Máy ảnh và lưu nhớ tự động vào bộ nhớ cấu hình (`SharedPreferences`).
4. Giữ nguyên và tối ưu tính năng theo dõi, vẽ vệt hành trình di chuyển theo thời gian thực khi quay video trên cả màn hình live preview và file video xuất cuối cùng.

---

## Locked Decisions

- **D1 (Hybrid Minimap Rendering 15-32):** Nâng dải zoom Minimap lên `15..32` (`MINIMAP_MIN_ZOOM = 15`, `MINIMAP_MAX_ZOOM = 32`). Khi Zoom $> 19$, tile nền OSM được tải ở mức tối đa (zoom 19) và tự động phóng to số (digital matrix upscale) làm nền, toàn bộ lớp vector (đường vẽ video, GIS node, route, cone GPS) được tính toán bằng Mercator Double và vẽ vector sắc nét tuyệt đối theo mức Zoom thực tế $15 \dots 32$.
- **D2 (Flexible & Persistent Default Zoom):** Loại bỏ mức zoom cứng 19. Hệ thống ưu tiên mức zoom người dùng đã lưu trong Cài đặt (`minimap_custom_zoom` trong SharedPreferences, giá trị khởi tạo mặc định là 20).
- **D3 (Settings Sheet Slider Control):** Cung cấp thanh trượt Slider 15 đến 32 trong Bottom Sheet Cài đặt Máy ảnh với bước nhảy 1 đơn vị, nhãn hiển thị trực quan `Mức thu phóng Minimap (Zoom: X)`, và nút bấm "Khôi phục mặc định (Zoom 20)" khi có thay đổi.
- **D4 (Real-time Video Movement Path):** Giữ nguyên và tối ưu tính năng vẽ đường hành trình khi quay video: đường polyline phát sáng Cyan (`#00E5FF`) tự thích ứng độ dày nét vẽ (stroke 3dp - 5dp), liên tục lấy mẫu mỗi 250ms và đồng bộ chính xác trên cả Camera Live Preview và Video Stamp Export.

---

## System Decision Impact

- **Impact:** existing
- **Decision:** @decision/camera-minimap-zoom-vector-overlay
- **Acceptance gate:** Toàn bộ test suite `:domain`, `:data`, `:photo`, `:app` và validation SDD pass 100%.

---

## Requirements

### Functional Requirements

- **FR-1:** Hỗ trợ dải mức zoom từ 15 đến 32 trong `PhotoStampRenderer`, `CameraOverlay`, `StampDataRepositoryImpl`, và các model liên quan.
- **FR-2:** Xử lý phép tính Mercator ($2^{\text{zoom}}$) bằng kiểu `Double` thay vì phép dịch bit `1 shl zoom` của Int32 để ngăn ngừa hoàn toàn lỗi tràn số nguyên khi zoom $\ge 31$.
- **FR-3:** Khi Zoom $> 19$, `PhotoStampRenderer` vẽ tile bitmap zoom 19 với ma trận biến đổi tọa độ tỷ lệ $2^{\text{zoom} - 19}$, giữ cho tâm vị trí camera luôn đồng trục tuyệt đối với các điểm vector.
- **FR-4:** Tích hợp thanh trượt Zoom 15 - 32 trong `CameraOverlay` Settings Bottom Sheet, lưu `minimap_custom_zoom` vào `cameraPrefs`.
- **FR-5:** Thu thập vệt hành trình GPS (`CameraMovementPath`) trong khi quay video, vẽ đường polyline Cyan trên live preview minimap và đóng dấu vào các frame video xuất ra qua `VideoStampTimelineSample`.

### Non-Functional Requirements

- **NFR-1 (Hiệu năng):** Quá trình vẽ vector và upscale tile không gây tụt khung hình (duy trì $\ge 55$ FPS khi preview và quay video).
- **NFR-2 (Độ chính xác toán học):** Độ sai lệch giữa tọa độ vector GPS và tâm nón camera trên bản đồ $< 0.5$ pixel trên canvas minimap.

---

## Acceptance Criteria

- [ ] **AC-1:** `PhotoStampRenderer.MINIMAP_MIN_ZOOM == 15` và `PhotoStampRenderer.MINIMAP_MAX_ZOOM == 32`.
- [ ] **AC-2:** Thu phóng từ 15 đến 32 không ném ngoại lệ `ArithmeticException` hoặc lỗi tràn số bit Mercator.
- [ ] **AC-3:** Khi thay đổi slider zoom từ 15 đến 32 trong Cài đặt, Minimap trên camera live preview cập nhật tức thời theo mức zoom tương ứng và lưu lại cho các lần mở sau.
- [ ] **AC-4:** Khi quay video, đường di chuyển được vẽ liên tục và hiển thị rõ ràng trên Minimap ở mọi mức zoom từ 15 đến 32.
- [ ] **AC-5:** Ảnh chụp và Video xuất ra có con dấu Minimap thể hiện đúng mức zoom đã chọn và đầy đủ đường hành trình video.
- [ ] **AC-6:** Tất cả unit test trong dự án (`.\gradlew testDebugUnitTest`) vượt qua 100%.

---

## Scenarios

### Scenario 1: Người dùng tùy chỉnh Zoom lên mức 25 và chụp ảnh
- **Given:** Người dùng đang ở màn hình Camera Overlay với mức zoom mặc định là 20.
- **When:** Người dùng mở Cài đặt, kéo thanh trượt Zoom Minimap lên mức 25 và chụp ảnh.
- **Then:** Bản đồ minimap trên ảnh chụp hiển thị chi tiết vector ở mức zoom 25 (tile nền 19 được phóng to mượt mà, điểm GPS và nón camera ở tâm chính xác).

### Scenario 2: Quay video di chuyển thực địa ở mức Zoom 22
- **Given:** Người dùng chuyển sang chế độ Video và đặt Zoom Minimap là 22.
- **When:** Bấm nút Quay và di chuyển qua các vị trí khác nhau trong 10 giây.
- **Then:** Đường polyline màu Cyan vẽ liên tục nối các điểm di chuyển trên Minimap, và video sau khi xuất lưu giữ trọn vẹn vệt hành trình này.

---

## Technical Notes

- Trong `PhotoStampRenderer.kt`:
  - Thay thế `1 shl zoom` bằng `kotlin.math.pow(2.0, zoom.toDouble())` hoặc `(1L shl zoom.coerceAtMost(30)).toDouble() * ...` trong các hàm tính toán pixel thế giới (`worldPixelPosition`).
  - Hàm `drawMinimap`: Tính toán tỷ lệ phóng đại tile `val tileScale = 2.0.pow((zoom - tileZoom).coerceAtLeast(0))`.
- Trong `CameraOverlay.kt`:
  - Cập nhật Slider `valueRange = 15f..32f`, `steps = 16`.
  - Khôi phục mặc định đặt về 20.

---

## Task Links

- [TODO] `rr9opa` - [camera-minimap-zoom-15-32-video-path-01] Mo rong dai zoom 15-32 va an toan so Mercator trong PhotoStampRenderer
- [TODO] `7nzyar` - [camera-minimap-zoom-15-32-video-path-02] Tich hop Slider Zoom 15-32 trong Cai dat Camera va luu SharedPreferences
- [TODO] `58yfng` - [camera-minimap-zoom-15-32-video-path-03] Dong bo duong di chuyen video thoi gian thuc va xuat Video Stamp o dai zoom 15-32
- [TODO] `8i9ba4` - [camera-minimap-zoom-15-32-video-path-04] Kiem thu tu dong va xac minh hoi quy toan dien

