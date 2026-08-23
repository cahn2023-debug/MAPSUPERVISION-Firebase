# Tasks: Khắc Phục Lỗi Chế Độ Video Bản Release & Nâng Cấp Giao Diện Camera

Spec: @doc/specs/2026-08-23/camera-video-mode-release-fix.md
Created: 2026-08-23 · Status: **done (2026-08-23)**

## Task List

- [x] **[cam-vid-01]** Nâng cấp Giao diện Segmented Capsule Tab ẢNH / VIDEO, Cử chỉ vuốt chuyển mode & Recording Timer HUD trong CameraOverlay
  - Fulfills: AC-1, AC-2, AC-3, AC-4, FR-1, FR-2, FR-3, FR-4
  - Scope: `app/src/main/java/com/mapsupervision/app/CameraOverlay.kt`, `app/src/main/java/com/mapsupervision/app/CameraOverlayState.kt`
  - Order: 10
  - Status: **done** (2026-08-23) — Đã triển khai `CameraModeSelector`, `CameraRecordingTimerBadge`, cử chỉ swipe preview horizontal drag, và nâng cấp nút chụp/quay video.

- [x] **[cam-vid-02]** Bổ sung ProGuard / R8 keep-rules cho Media3, CameraX Video & Transformer trong `app/proguard-rules.pro`
  - Fulfills: AC-5, FR-5
  - Scope: `app/proguard-rules.pro`
  - Order: 20
  - Status: **done** (2026-08-23) — Đã thêm đầy đủ keep-rules cho Media3, Transformer, CameraX Video và photo worker pipeline.

- [x] **[cam-vid-03]** Cập nhật Unit Tests cho Camera Overlay Helpers và chạy kiểm thử tự động toàn diện
  - Fulfills: AC-6, NFR-1, NFR-2, NFR-3
  - Scope: `app/src/test/java/com/mapsupervision/app/CameraOverlayHelpersTest.kt`
  - Order: 30
  - Status: **done** (2026-08-23) — Đã thêm unit test `formatRecordingDuration` và chạy `./gradlew :app:testDebugUnitTest` 100% pass (326 actionable tasks executed/up-to-date).

## Schedule & Compliance

- **Wave 1 (cam-vid-01):** UI Capsule Tab + Recording HUD + Swipe Gestures hoàn thành.
- **Wave 2 (cam-vid-02):** ProGuard keep-rules hardening hoàn thành.
- **Wave 3 (cam-vid-03):** Unit tests pass 100%.
- **Spec Decision Compliance:** D1=pass, D2=pass, D3=pass, D4=pass, D5=pass.
- **System Decision Impact:** none — không thay đổi schema cơ sở dữ liệu hay mô hình dữ liệu chính.
