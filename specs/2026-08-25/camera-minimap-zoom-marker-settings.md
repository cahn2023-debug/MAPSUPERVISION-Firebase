# Specification: Cấu hình Mức Zoom và Kích thước Marker Minimap Camera & Video Preview

Tài liệu đặc tả yêu cầu, thiết kế tương tác UI/UX và kiến trúc kỹ thuật cho tính năng **Tùy chỉnh Mức Zoom & Kích thước Marker Minimap** trong màn hình Máy ảnh thực địa (`CameraOverlay`), hỗ trợ xem trước (live preview) tức thì qua Slider trong Cài đặt và áp dụng đồng bộ vào ảnh chụp / video ghi hình (`PhotoStampRenderer`).

---

## 1. Hiện trạng Hệ thống (Current State Analysis)

### 1.1 Mức Zoom Minimap hiện tại
- **Mức Zoom mặc định:** `MINIMAP_MAX_ZOOM = 19` (cận cảnh đường phố / công trình).
- **Phạm vi Zoom tự động:** `MINIMAP_MIN_ZOOM = 14` đến `MINIMAP_MAX_ZOOM = 19`.
- **Cơ chế tính toán:** `resolveMinimapViewport()` tự động hạ zoom từ 19 xuống 14 nếu camera cone hoặc các đối tượng GIS (`nodes`, `routes`, `movementPath`) nằm ngoài khung nhìn bản đồ thu nhỏ.

### 1.2 Kích thước Marker & Nón hướng nhìn hiện tại
- **Kích thước khung Minimap:** `mapSize = ((minOf(frameWidth, frameHeight) / 6f) * 1.5f).coerceAtLeast(120f)`.
- **Hệ số tỷ lệ:** `scale = (frameWidth / 3000f) * 1.4f`.
- **Tâm vị trí Camera (3 vòng tròn đồng tâm):**
  - Vòng ngoài (`outerDotRadius`): `52f * scale` (Màu đỏ mờ `Color.argb(60, 220, 50, 50)`).
  - Vòng giữa (`innerDotRadius`): `32f * scale` (Màu đỏ đậm `Color.argb(255, 220, 50, 50)`).
  - Điểm tâm (`coreDotRadius`): `14f * scale` (Chấm trắng trung tâm `Color.WHITE`).
- **Nón góc nhìn camera (FOV Cone):**
  - Bán kính nón: `coneLen = rect.width() * 0.42f * 0.8f` (~33.6% chiều rộng minimap).
  - Góc mở: `30 độ` (Quét từ `bearing - 15°` đến `bearing + 15°`).
- **Nút GIS xung quanh (`nodes`):**
  - Có nhãn: Bán kính `9f * scale`, chữ `8f * scale`.
  - Không nhãn: Bán kính `6f * scale` (highlighted) hoặc `4f * scale` (thường).

---

## 2. Các Quyết định Đã Chốt (Locked Decisions)

- **D1 — Thanh trượt tùy chỉnh Mức Zoom Minimap:**
  - Bổ sung Slider trong bảng Cài đặt Camera (`CameraSettingsSheet`).
  - Hỗ trợ dải zoom linh hoạt (Zoom 14 đến Zoom 20).
  - Minimap trên màn hình Camera và trong Sheet phản hồi cập nhật ngay lập tức (Live Preview).
- **D2 — Thanh trượt tùy chỉnh Kích thước Marker GPS & FOV Cone:**
  - Bổ sung Slider điều chỉnh tỷ lệ Marker Scale (`50%` đến `150%`, mặc định `100%`).
  - Thay đổi kích thước đồng bộ cho cả 3 vòng tròn chấm GPS (`outerDot`, `innerDot`, `coreDot`) và Nón hướng nhìn (`FOV Cone`).
- **D3 — Lưu trữ cấu hình bền vững (Persistence):**
  - Lưu giá trị `minimap_zoom` và `minimap_marker_scale` vào Local Preferences / DataStore.
  - Tự động áp dụng lại mỗi khi mở Camera hoặc chuyển đổi chế độ Ảnh/Video.
- **D4 — Đồng bộ Toàn diện Preview & Output:**
  - Đồng bộ thông số Zoom và Kích thước Marker cho cả màn hình xem trước (`CameraOverlay` HUD live Canvas) và file xuất ra (`applyStamp` cho ảnh chụp và `postProcessRecordedVideo` cho video).

---

## 3. Yêu cầu Tính năng (Requirements)

### 3.1 Yêu cầu Chức năng (Functional Requirements)
- **FR-1 — Giao diện Cài đặt Minimap trong Settings Sheet:**
  - Bổ sung 2 mục điều khiển trực quan:
    1. **Mức Zoom Bản đồ:** Slider từ `14` đến `20` kèm hiển thị nhãn giá trị (ví dụ: `Zoom 19 - Cận cảnh`, `Zoom 17 - Vừa`, `Zoom 15 - Toàn cảnh`).
    2. **Kích thước Điểm định vị (Marker):** Slider từ `50%` đến `150%` (bước nhảy `5%` hoặc liên tục) kèm nhãn phần trăm rõ ràng.
    3. Nút **"Khôi phục mặc định" (Reset)** để nhanh chóng đưa về `Zoom 19` và `Marker 100%`.
- **FR-2 — Live Minimap HUD Preview tức thì:**
  - Khi kéo slider Zoom hoặc Marker Scale, Minimap ở góc dưới trái màn hình Camera lập tức vẽ lại với mức zoom và kích thước mới mà không làm giật lag hay reload lại luồng CameraX.
- **FR-3 — Đóng dấu Stamp Đồng bộ trên Ảnh và Video:**
  - Mô-đun `:photo` (`PhotoStampLayoutCalculator` & `PhotoStampRenderer`) nhận thêm tham số `markerScale: Float = 1.0f` và `customMinimapZoom: Int? = null`.
  - Toàn bộ kích thước chấm GPS, nón hướng nhìn và mức zoom tile bản đồ khi render stamp xuất file sẽ khớp chính xác với tỷ lệ người dùng đã thiết lập.
- **FR-4 — Khả năng hoạt động Offline & Fallback:**
  - Khi không có internet để tải tile mức zoom mới, minimap hiển thị vector giả lập hoặc tile đã cache trong bộ nhớ mà không gây crash ứng dụng.

### 3.2 Yêu cầu Phi Chức năng (Non-Functional Requirements)
- **NFR-1 — Hiệu năng (Smooth 60 FPS):** Thao tác kéo slider mượt mà, không kích hoạt recomposition toàn bộ màn hình camera, chỉ trigger vẽ lại Canvas minimap.
- **NFR-2 — Độ bền vững dữ liệu:** Không bị mất thiết lập khi xoay màn hình, thoát ứng dụng hoặc chuyển đổi giữa các dự án.

---

## 4. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] **AC-1:** Mở Cài đặt Camera (`Icons.Outlined.Settings`) hiển thị đầy đủ 2 thanh trượt: Mức Zoom Minimap (14–20) và Kích thước Marker (50%–150%).
- [ ] **AC-2:** Kéo Slider thay đổi mức Zoom: Minimap góc trái dưới màn hình lập tức cập nhật mức phóng to/thu nhỏ tương ứng.
- [ ] **AC-3:** Kéo Slider thay đổi Kích thước Marker: Điểm chấm GPS đỏ và nón góc nhìn vàng phóng to/thu nhỏ tương ứng trong khoảng 50%–150%.
- [ ] **AC-4:** Chụp ảnh hoặc quay video: File ảnh/video sau khi xuất có stamp minimap mang đúng mức Zoom và kích thước Marker vừa cài đặt.
- [ ] **AC-5:** Đóng và mở lại Camera: Các giá trị Zoom và Marker Scale đã chọn vẫn được lưu giữ nguyên vẹn.

---

## 5. Kế hoạch Thực hiện (Task Links)

- [ ] **[cam-map-01]** Nâng cấp `PhotoStampLayoutCalculator` và `PhotoStampRenderer` hỗ trợ tham số `markerScale` và zoom tùy chỉnh.
- [ ] **[cam-map-02]** Thiết kế UI Slider Zoom & Marker Scale trong `CameraOverlay` Settings Bottom Sheet.
- [ ] **[cam-map-03]** Tích hợp DataStore/SharedPreferences lưu và nạp cấu hình Minimap.
- [ ] **[cam-map-04]** Viết Unit Test và kiểm thử tương tác thực tế trên Camera HUD.
