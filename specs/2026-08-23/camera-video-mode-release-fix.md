# Khắc Phục Lỗi Chế Độ Video Bản Release & Nâng Cấp Giao Diện Camera

Tags: `spec`, `approved` · Created: 2026-08-23 · Status: **approved (2026-08-23)**

## Overview

Khắc phục triệt để vấn đề "không có chế độ video" trên bản release APK và nâng cấp toàn diện trải nghiệm chọn chế độ Camera:
1. Thay đổi thanh chọn chế độ "ẢNH / VIDEO" từ dạng text nhỏ trôi nổi sang cụm điều khiển **Segmented Capsule/Pill (viên thuốc)** bo tròn hiện đại, hiển thị sắc nét, viền sáng và highlight rõ ràng, hỗ trợ cả thao tác chạm và vuốt ngang preview.
2. Cải thiện trạng thái quay video: khi đang quay, ẩn thanh chuyển mode và hiển thị đồng hồ thời lượng đếm giây (`00:00`) kèm chấm đỏ nhấp nháy; chuyển đổi nút chụp/quay linh hoạt.
3. Gia cố **ProGuard / R8 keep-rules** trong `app/proguard-rules.pro` cho Media3, CameraX Video & Transformer để bản Release sau khi minify không bị lỗi ngầm lúc xuất video có đóng GPS stamp.
4. Tương thích và kiểm thử thích ứng với `computeCameraControlsLayout` để đảm bảo thanh điều khiển luôn hiển thị hoàn hảo trên mọi độ phân giải và tỷ lệ màn hình (tránh bị che bởi Navigation Bar / Gestures).

## Locked Decisions

- **D1 — Phạm vi & Nguyên nhân:** Thanh chọn "ẢNH / VIDEO" cũ chỉ là 2 chuỗi text 13sp không viền/nền trên overlay camera nên rất khó quan sát và dễ bị nhầm là không có tính năng quay video. Giải pháp là nâng cấp UI sang Capsule Segmented Tab nổi bật và gia cố cấu hình build Release.
- **D2 — Thiết kế Segmented Capsule/Pill Tab:** Cụm chuyển chế độ được thiết kế dạng thanh viên thuốc bo cong hoàn toàn (`RoundedCornerShape(999.dp)`), nền mờ `Color(0x66000000)`, viền `1.dp Color(0x3300E5FF)`. Tab đang chọn (ẢNH hoặc VIDEO) có nền highlight `Color(0xFF00E5FF)` với chữ `Color(0xFF060814)` in đậm (hoặc hiệu ứng cyan glow), tab còn lại có chữ trắng mờ `Color(0xAAFFFFFF)`.
- **D3 — Tương tác & Trạng thái quay:** Hỗ trợ bấm trực tiếp vào pill hoặc vuốt cử chỉ ngang trên vùng preview camera để chuyển đổi ẢNH <-> VIDEO. Khi đang ghi hình (`isRecording = true`), ẩn thanh chuyển tab và thay bằng badge thời gian quay (`00:00`) kèm chấm đỏ nhấp nháy; nút chụp chuyển sang nút dừng quay (hình vuông đỏ/trắng).
- **D4 — ProGuard / R8 Hardening cho Video:** Bổ sung các rule keep chi tiết cho `androidx.media3.**`, `androidx.camera.video.**`, `androidx.media3.transformer.**` và OpenGL shader helpers trong `app/proguard-rules.pro` để đảm bảo khi minify bản Release APK, Media3 Transformer không bị mất các lớp codec/reflection và xuất video stamp thành công 100%.
- **D5 — Bố cục thích ứng & Vùng an toàn:** Cụm Segmented Tab luôn được đảm bảo hiển thị trong `CameraControlsLayout`, tính toán khoảng cách với `WindowInsetsSides.Bottom` để không bị navigation bar hệ thống che khuất ở mọi mức chiều cao màn hình.

## System Decision Impact

- Impact: **none** — không thay đổi kiến trúc cơ sở dữ liệu hay mô hình dữ liệu chính của hệ thống.
- Decision: không áp dụng.
- Acceptance gate: không áp dụng.

## Requirements

### Functional Requirements

- **FR-1 — Segmented Capsule UI:** Hiển thị thanh chuyển chế độ ẢNH / VIDEO dạng Capsule/Pill tại cụm điều khiển dưới của `CameraOverlay.kt` với style hiện đại, viền sắc nét, tương phản cao trên mọi điều kiện ánh sáng camera.
- **FR-2 — Chuyển đổi mượt mà:** Khi bấm hoặc vuốt chuyển giữa ẢNH và VIDEO, cập nhật `isVideoMode`, đồng thời re-bind camera use-cases (ImageCapture <-> VideoCapture) một cách an toàn và tức thì.
- **FR-3 — Trạng thái đang ghi hình (Recording HUD):** Khi bấm nút quay:
  - Bắt đầu ghi hình MP4 qua `VideoCapture.output.prepareRecording`.
  - Hiển thị badge đồng hồ bấm giờ `MM:SS` kèm chấm đỏ `●` nhấp nháy chu kỳ 1s.
  - Nút Shutter chuyển thành icon Stop (hình vuông màu trắng bo góc trên nền đỏ).
  - Khóa/ẩn các thao tác chuyển tab hoặc cài đặt trong lúc đang ghi hình.
- **FR-4 — Hậu xử lý & Đóng GPS Stamp Video:** Sau khi dừng quay, hệ thống tự động lưu video tạm, nạp timeline GPS/đối tượng và gọi `PhotoPipelineService.exportVideoStamp` xuất video thành phẩm với watermark chuẩn xác.
- **FR-5 — Bổ sung ProGuard Keep Rules:** `app/proguard-rules.pro` được bổ sung cấu hình bảo vệ cho `androidx.media3`, `androidx.camera.video`, và các lớp video pipeline để bản Release minify hoạt động ổn định.

### Non-Functional Requirements

- **NFR-1 — Hiệu năng & Mượt mà:** Tương tác chuyển đổi chế độ không gây giật lag (frame drop < 16ms), không rò rỉ bộ nhớ Surface hay Bitmap tile.
- **NFR-2 — Thích ứng màn hình:** Hoạt động đúng trên mọi kích thước màn hình và tỷ lệ aspect ratio (4:3, 16:9, 1:1, Full), tôn trọng safe insets navigation bar.
- **NFR-3 — Release Gate:** Tất cả các bài kiểm tra tự động (`CameraOverlayHelpersTest`, unit tests, lint, `release_gate.sh`) phải pass.

## Acceptance Criteria

- [ ] **AC-1:** Giao diện `CameraOverlay` hiển thị thanh Segmented Capsule Pill rõ ràng với 2 tab "ẢNH" và "VIDEO".
- [ ] **AC-2:** Tab đang chọn được highlight nền Cyan `Color(0xFF00E5FF)`, chữ đậm dễ đọc; tab chưa chọn hiển thị rõ ràng, không bị chìm hay mờ đục.
- [ ] **AC-3:** Bấm vào tab "VIDEO" chuyển camera sang chế độ quay video (nút bấm chính đổi màu sang đỏ/viền đỏ, sẵn sàng quay).
- [ ] **AC-4:** Khi bấm nút quay, hiển thị đồng hồ thời lượng quay `00:00` kèm chấm đỏ nhấp nháy, thanh chuyển tab ẩn đi; bấm nút Stop thì dừng và xuất file video có stamp thành công.
- [ ] **AC-5:** `app/proguard-rules.pro` có đầy đủ keep-rules cho Media3 và CameraX Video.
- [ ] **AC-6:** Các unit tests cho camera overlay helpers và pipeline chạy đạt 100% pass (`./gradlew :app:testDebugUnitTest`).

## Scenarios

### Scenario 1: Chuyển đổi giữa Chế độ Chụp Ảnh và Quay Video
**Given** Người dùng mở CameraOverlay từ bản đồ hoặc chi tiết node
**When** Quan sát cụm điều khiển dưới
**Then** Thấy thanh Segmented Capsule Pill "ẢNH / VIDEO" nổi bật, rõ ràng
**When** Bấm vào tab "VIDEO"
**Then** Tab "VIDEO" sáng highlight Cyan, nút bấm chính chuyển sang trạng thái sẵn sàng quay video

### Scenario 2: Quay Video Hiện Trường và Đóng Dấu Stamp
**Given** Đang ở chế độ "VIDEO" trong CameraOverlay
**When** Bấm nút quay
**Then** Bắt đầu quay video, hiển thị đồng hồ thời gian quay `00:01, 00:02...` kèm chấm đỏ nhấp nháy
**When** Bấm nút dừng quay (Stop)
**Then** Video được dừng, đóng dấu stamp GPS/đối tượng và lưu vào thư mục dự án

### Scenario 3: Chạy trên Bản Build Release Minified
**Given** Ứng dụng được build release với R8 minifyEnabled = true
**When** Người dùng mở Camera và thực hiện quay video có stamp
**Then** Ứng dụng không bị crash, Media3 Transformer xử lý video và xuất ra file MP4 hoàn chỉnh

## Technical Notes

1. **Thành phần UI Segmented Capsule:**
   Tạo composable helper `CameraModeSelector(isVideoMode: Boolean, onModeSelected: (Boolean) -> Unit, enabled: Boolean)` trong `CameraOverlay.kt` hoặc file riêng, sử dụng `Box`, `Row`, bo góc `999.dp`, `background(Color(0x66000000))`, `border(1.dp, Color(0x3300E5FF))` và animated transition.
2. **ProGuard Rules cập nhật:**
   ```proguard
   # Media3 & Transformer Video Processing
   -keep class androidx.media3.transformer.** { *; }
   -keep class androidx.media3.effect.** { *; }
   -keep class androidx.media3.common.** { *; }
   -keep class androidx.media3.exoplayer.** { *; }
   -dontwarn androidx.media3.**

   # CameraX Video
   -keep class androidx.camera.video.** { *; }
   -dontwarn androidx.camera.video.**
   ```
3. **Recording Timer Composable:**
   Sử dụng `LaunchedEffect(isRecording)` với `delay(1000)` để tính số giây ghi hình và format thành chuỗi `String.format("%02d:%02d", minutes, seconds)`.

## Task Links

Tasks được quản lý tại @doc/specs/2026-08-23/camera-video-mode-release-fix-tasks.md:
- [cam-vid-01] Nâng cấp Giao diện Segmented Capsule Tab ẢNH / VIDEO, Cử chỉ vuốt chuyển mode & Recording Timer HUD trong CameraOverlay
- [cam-vid-02] Bổ sung ProGuard / R8 keep-rules cho Media3, CameraX Video & Transformer trong `app/proguard-rules.pro`
- [cam-vid-03] Cập nhật Unit Tests cho Camera Overlay Helpers và chạy kiểm thử tự động toàn diện

## Open Questions

- Không có open question còn tồn đọng sau phiên phỏng vấn Socratic.
