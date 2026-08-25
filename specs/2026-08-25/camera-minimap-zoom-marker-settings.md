# Specification: Cấu hình Mức Zoom, Kích thước Marker & Điều khiển Hướng nhìn FOV Minimap Camera & Video Preview

Tài liệu đặc tả yêu cầu, thiết kế tương tác UI/UX và kiến trúc kỹ thuật cho tính năng **Tùy chỉnh Mức Zoom, Kích thước Marker, Góc mở & Độ dài Hướng nhìn FOV Minimap** trong màn hình Máy ảnh thực địa (`CameraOverlay`), hỗ trợ nâng vị trí Stamp xem trước trực tiếp khi mở Cài đặt và áp dụng đồng bộ vào ảnh chụp / video ghi hình (`PhotoStampRenderer`).

---

## 1. Hiện trạng & Vấn đề Cần giải quyết (Problems & Findings)

1. **Vấn đề che khuất Preview:** Khi mở Bottom Sheet Cài đặt Camera, bảng cài đặt chiếm nửa dưới màn hình và che phủ hoàn toàn Stamp & Minimap ở góc dưới, người dùng không thể quan sát hiệu ứng preview khi đang kéo thanh trượt.
2. **Lỗi khôi phục thông số:** Khi điều chỉnh mức zoom lên 20 hoặc thay đổi slider, luồng cập nhật GPS nền và hằng số `MINIMAP_MAX_ZOOM = 19` ép zoom quay trở lại mức mặc định 19.
3. **Nhu cầu điều khiển hướng nhìn (FOV Cone):** Hình nón hướng nhìn camera hiện đang cố định góc mở 30° và chiều dài ~33.6% minimap, chưa cho phép người sử dụng điều chỉnh góc quét rộng/hẹp và chiều dài tầm nhìn theo thực tế công trình.

---

## 2. Các Quyết định Đã Thống nhất (Locked Decisions)

- **D1 — Nâng vị trí Stamp & Minimap khi mở Cài đặt:**
  - Khi `showSettingsSheet == true`, Stamp & Minimap tự động được dịch chuyển lên phía trên mép của bảng Cài đặt (vùng không gian màn hình camera bên trên sheet), giúp người dùng vừa kéo slider vừa nhìn thấy trực quan preview thay đổi.
- **D2 — Sửa lỗi giữ trạng thái Zoom & Marker:**
  - Nâng `MINIMAP_MAX_ZOOM` lên `20` (hoặc `21`).
  - Đảm bảo luồng lấy vị trí nền tôn trọng `customMinimapZoom` và `customMarkerScale`, không bị ghi đè hay reset về mặc định.
- **D3 — Bộ điều khiển Hướng nhìn FOV Cone (Chiều rộng & Chiều dài):**
  - **Chiều rộng góc quét FOV (Angle):** Slider từ `15°` (rất hẹp) đến `90°` (rộng), mặc định `30°`.
  - **Chiều dài tia nhìn FOV (Length):** Slider từ `30%` đến `120%` (hệ số `0.3x` đến `1.5x`), mặc định `80%` (`1.0x`).
- **D4 — Lưu trữ 4 thông số vào Preferences (`camera_prefs`):**
  - `minimap_custom_zoom` (14..20)
  - `minimap_marker_scale` (0.5f..1.5f)
  - `minimap_fov_angle` (15f..90f)
  - `minimap_fov_length` (0.3f..1.5f)
  - Nút "Khôi phục mặc định" reset toàn bộ về giá trị chuẩn.
- **D5 — Đồng bộ 100% Preview & Output:**
  - Áp dụng đầy đủ cho Preview Live HUD, Photo Stamp và Video Timeline Stamp.

---

## 3. Thiết kế Kỹ thuật (Technical Design)

### 3.1 Domain Model (`com.mapsupervision.domain.model.CaptureStampMapScene`)
Bổ sung các thuộc tính:
```kotlin
data class CaptureStampMapScene(
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val cameraLatitude: Double? = null,
    val cameraLongitude: Double? = null,
    val bearingDeg: Float = 0f,
    val nodes: List<CaptureStampMapNode> = emptyList(),
    val routes: List<CaptureStampMapRoute> = emptyList(),
    val movementPath: List<Pair<Double, Double>> = emptyList(),
    val minimapZoom: Int? = null,
    val markerScale: Float = 1.0f,
    val fovAngleDeg: Float = 30.0f,
    val fovLengthScale: Float = 1.0f
)
```

### 3.2 Photo Stamp Engine (`PhotoStampRenderer.kt`)
- `MINIMAP_MAX_ZOOM = 20`.
- `drawMinimap`:
  - `val coneAngle = Math.toRadians((mapScene?.fovAngleDeg ?: 30.0f).toDouble()).toFloat()`
  - `val coneLen = rect.width() * 0.42f * 0.8f * (mapScene?.fovLengthScale ?: 1.0f)`
  - Cập nhật vẽ `arcTo` và `offsetCoordinate` nón tương ứng với góc quét và độ dài mới.

### 3.3 Giao diện Camera Overlay (`CameraOverlay.kt`)
- Trạng thái nâng vị trí Preview Canvas khi mở Cài đặt.
- Các thanh Slider trong Sheet Cài đặt:
  1. Mức thu phóng Minimap (Zoom: 14..20)
  2. Kích thước Marker GPS & Điểm GIS (50%..150%)
  3. Góc quét hướng nhìn FOV (15°..90°)
  4. Chiều dài tia nhìn FOV (30%..120%)
  5. Nút "Khôi phục mặc định"

---

## 4. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] **AC-1:** Mở Cài đặt Camera: Minimap & Stamp tự động di chuyển lên trên mép bảng cài đặt, nhìn rõ 100% nội dung preview.
- [ ] **AC-2:** Kéo Slider Zoom (14..20): Giữ nguyên mức zoom đã chọn, không bị nhảy ngược về mặc định khi GPS cập nhật.
- [ ] **AC-3:** Kéo Slider Góc quét FOV (15°..90°): Nón góc nhìn mở rộng hoặc thu hẹp trực quan ngay trên Minimap.
- [ ] **AC-4:** Kéo Slider Chiều dài FOV (30%..120%): Tia nón hướng nhìn dài ra hoặc ngắn lại trực quan ngay trên Minimap.
- [ ] **AC-5:** Chụp ảnh & quay video: Stamp mang đúng 4 thông số (Zoom, Marker Scale, FOV Angle, FOV Length).
- [ ] **AC-6:** Đóng/mở app: Tất cả 4 cài đặt được lưu giữ và khôi phục tự động.
