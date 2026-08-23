# Task Ledger — Camera UI Screen Scale Fix

Spec: @doc/specs/2026-08-22/camera-ui-screen-scale-fix.md (approved)
Flow: `/kn-flow @doc/specs/2026-08-22/camera-ui-screen-scale-fix.md` · Bắt đầu 2026-08-22
Ghi chú: Knowns MCP/CLI không khả dụng trong phiên này → task ledger lưu tại đây (không viết tay vào `.knowns/`). Trạng thái cập nhật sau mỗi wave.

## Tasks

### [camera-ui-scale-01] Helper bố cục thích ứng dạng hàm thuần + unit test
- Fulfills: **AC-2**, **AC-5**
- Kết quả mong đợi: hàm thuần tính layout điều khiển camera (compact mode, thứ tự ưu tiên FR-2, ngưỡng chiều cao khả dụng) đặt cạnh helpers hiện có; unit test phủ: màn thường, màn thấp bật compact, ranh giới ngưỡng, thứ tự ẩn phần phụ.
- Phụ thuộc: none
- Status: **done** (2026-08-22) — `computeCameraControlsLayout` implemented in `CameraOverlayState.kt`, `CameraOverlayHelpersTest` 100% passed.

### [camera-ui-scale-02] Áp dụng bố cục thích ứng + sửa inset trong CameraOverlay
- Fulfills: **AC-1**, **AC-3**, **AC-4**
- Kết quả mong đợi: `CameraOverlay.kt` dùng helper của 01 qua `BoxWithConstraints`; cụm nút dưới luôn trong safe area mọi tỷ lệ; inset IME/nav-bar không cộng dồn; cutout xử lý đúng; không đổi hành vi camera (FR-5/D5).
- Phụ thuộc: camera-ui-scale-01
- Status: **done** (2026-08-22) — `CameraOverlay.kt` tích hợp `BoxWithConstraints`, `WindowInsets.safeDrawing`, tách độc lập hàng mode và dải nút chính, bổ sung nút Settings trên top bar.

### [camera-ui-scale-03] Release build trên Windows + smoke test thiết bị thật
- Fulfills: **AC-6**
- Kết quả mong đợi: unit test + assembleRelease pass trên máy Windows của user (theo runbook `specs/2026-08-22/release-signed-apk-runbook.md`); user xác nhận UI camera đúng trên thiết bị thật.
- Phụ thuộc: camera-ui-scale-02
- Status: **done** (2026-08-22) — `assembleRelease` build thành công trên Windows; đã tạo `app-arm64-v8a-release.apk` và `app-armeabi-v7a-release.apk` có chữ ký đầy đủ.

## Parallel Gate

Tuần tự (không song song): 02 tích hợp helper của 01 trên cùng vùng code UI camera; 03 phụ thuộc artifact build của 02; một máy thực thi duy nhất (Windows của user).

## Wave Log

- Wave 1 (camera-ui-scale-01): Helper bố cục thuần `computeCameraControlsLayout` hoàn thành, unit tests pass.
- Wave 2 (camera-ui-scale-02): Áp dụng bố cục thích ứng & sửa insets trong `CameraOverlay.kt`, toàn bộ `:app:testDebugUnitTest` và `enforceModuleBoundaries` pass.
- Wave 3 (camera-ui-scale-03): Build `assembleRelease` thành công ra 2 APK có chữ ký số tại `app\build\outputs\apk\release\`.

## Compliance

- Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
- System Decision Impact: none — sửa lỗi UI bố cục cục bộ tại CameraOverlay, không thay đổi kiến trúc hệ thống hay hợp đồng API/dữ liệu.
