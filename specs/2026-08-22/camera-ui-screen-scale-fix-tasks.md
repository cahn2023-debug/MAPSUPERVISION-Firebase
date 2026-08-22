# Task Ledger — Camera UI Screen Scale Fix

Spec: @doc/specs/2026-08-22/camera-ui-screen-scale-fix.md (approved)
Flow: `/kn-flow @doc/specs/2026-08-22/camera-ui-screen-scale-fix.md` · Bắt đầu 2026-08-22
Ghi chú: Knowns MCP/CLI không khả dụng trong phiên này → task ledger lưu tại đây (không viết tay vào `.knowns/`). Trạng thái cập nhật sau mỗi wave.

## Tasks

### [camera-ui-scale-01] Helper bố cục thích ứng dạng hàm thuần + unit test
- Fulfills: **AC-2**, **AC-5**
- Kết quả mong đợi: hàm thuần tính layout điều khiển camera (compact mode, thứ tự ưu tiên FR-2, ngưỡng chiều cao khả dụng) đặt cạnh helpers hiện có; unit test phủ: màn thường, màn thấp bật compact, ranh giới ngưỡng, thứ tự ẩn phần phụ.
- Phụ thuộc: none
- Status: **in-progress** (2026-08-22)

### [camera-ui-scale-02] Áp dụng bố cục thích ứng + sửa inset trong CameraOverlay
- Fulfills: **AC-1**, **AC-3**, **AC-4**
- Kết quả mong đợi: `CameraOverlay.kt` dùng helper của 01 qua `BoxWithConstraints`; cụm nút dưới luôn trong safe area mọi tỷ lệ; inset IME/nav-bar không cộng dồn; cutout xử lý đúng; không đổi hành vi camera (FR-5/D5).
- Phụ thuộc: camera-ui-scale-01
- Status: **todo**

### [camera-ui-scale-03] Release build trên Windows + smoke test thiết bị thật
- Fulfills: **AC-6**
- Kết quả mong đợi: unit test + assembleRelease pass trên máy Windows của user (theo runbook `specs/2026-08-22/release-signed-apk-runbook.md`); user xác nhận UI camera đúng trên thiết bị thật.
- Phụ thuộc: camera-ui-scale-02
- Status: **todo**

## Parallel Gate

Tuần tự (không song song): 02 tích hợp helper của 01 trên cùng vùng code UI camera; 03 phụ thuộc artifact build của 02; một máy thực thi duy nhất (Windows của user).

## Wave Log

- Wave 1 (camera-ui-scale-01): đã duyệt task set, bắt đầu implement.

## Compliance

- Spec Decision Compliance: ghi khi từng task hoàn thành, dạng `D<n>=pass|conflict`
- System Decision Impact: đánh giá khi đóng từng wave
