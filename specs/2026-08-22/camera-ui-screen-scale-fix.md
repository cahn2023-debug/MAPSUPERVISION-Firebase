# Spec — Sửa lỗi UI camera lệch, không scale theo tỷ lệ màn hình (release)

Status: approved · Date: 2026-08-22 · Scope: `:app` — `CameraOverlay`

## Overview

Trên bản release, màn hình camera (`CameraOverlay`, điểm vào từ `WorkspaceAppShell`) bị cụm điều khiển dưới cùng tràn xuống / bị cắt khỏi đáy màn hình trên một số tỷ lệ màn hình. Spec này định nghĩa việc sửa bằng bố cục thích ứng + chuẩn hóa xử lý window insets sao cho toàn bộ UI điều khiển luôn nằm trong safe area trên mọi tỷ lệ, mật độ và cấu hình inset. Hành vi camera (preview, chụp, quay, stamp) không đổi.

## Locked Decisions

- **D1:** Phạm vi lỗi là UI điều khiển của màn hình camera — không phải vùng nhìn preview, không phải ảnh/video đầu ra.
- **D2:** Triệu chứng: cụm nút dưới cùng (trường ghi chú, thanh zoom, hàng ẢNH/VIDEO, hàng nút chính [Thêm media][Chụp][Xoay camera]) tràn xuống quá thấp hoặc bị cắt một phần ở đáy màn hình.
- **D3:** Chưa xác định được điều kiện kích hoạt cụ thể (máy/tỷ lệ/keyboard/xoay) → yêu cầu fix tổng quát: UI luôn nằm gọn trong safe area trên mọi tỷ lệ màn hình, mật độ, kiểu navigation bar (gesture/3-button) và display cutout.
- **D4:** Hướng giải pháp: bố cục thích ứng — đo chiều cao khả dụng (`BoxWithConstraints`); khi màn thấp thì giảm khoảng cách/kích thước và thu gọn/ẩn phần phụ theo thứ tự ưu tiên, đảm bảo hàng nút chính + hàng chế độ luôn hiển thị đủ; đồng thời rà và sửa xử lý inset (`navigationBarsPadding`/`imePadding`/cutout).

## System Decision Impact

- Impact: none (fix UI cục bộ, không đổi kiến trúc module, dữ liệu, auth, tích hợp ngoài).
- Decision: không áp dụng.
- Acceptance gate: không có.

## Requirements

### Functional Requirements

- **FR-1:** Cụm điều khiển dưới cùng phải nằm hoàn toàn trong safe area (trên navigation bar và display cutout) trên mọi tỷ lệ màn hình, cả portrait lẫn landscape.
- **FR-2:** Khi chiều cao khả dụng dưới ngưỡng cấu hình, bố cục chuyển sang chế độ compact theo thứ tự ưu tiên: (1) hàng nút chính, (2) hàng chọn ẢNH/VIDEO, (3) trường ghi chú, (4) thanh zoom + indicator. Phần phụ phía dưới danh sách bị thu gọn hoặc ẩn trước; hàng nút chính không bao giờ bị ẩn hay cắt.
- **FR-3:** Khi bàn phím mở để nhập ghi chú: trường ghi chú và ít nhất hàng nút chính phải nhìn thấy được; khi đóng bàn phím, bố cục trở về đúng vị trí ban đầu (không dịch thừa do inset cộng dồn).
- **FR-4:** Xử lý inset đúng trên cả gesture navigation lẫn 3-button navigation, và với màn hình có cutout.
- **FR-5:** Không thay đổi hành vi camera: preview FOV, chụp ảnh, quay video, đóng stamp, zoom, flash, xoay camera hoạt động như hiện tại.

### Non-Functional Requirements

- **NFR-1:** Không thêm dependency mới; chỉ dùng Compose/Material3 hiện có.
- **NFR-2:** Logic thích ứng được tách thành hàm thuần (pure function) đặt cạnh helpers hiện có để unit-test được mà không cần thiết bị.
- **NFR-3:** Không đổi cấu hình CameraX, không đụng module `:photo` pipeline (riêng `PhotoScreen.kt` ngoài phạm vi trừ khi khảo sát implementation thấy bắt buộc).

## Acceptance Criteria

- [x] **AC-1:** Trên mô phỏng/thiết bị màn cao (≥20:9) và màn 16:9: toàn bộ cụm điều khiển dưới cùng hiển thị đầy đủ trong bounds gốc; không phần tử nào bị cắt bởi mép màn hình hoặc navigation bar (kiểm chứng bằng screenshot hoặc Compose test assert bounds).
- [x] **AC-2:** Ở chiều cao khả dụng thấp (Compose test với chiều cao cố định, ví dụ 500dp): chế độ compact kích hoạt — hàng nút chính và hàng ẢNH/VIDEO hiển thị đầy đủ; phần phụ thu gọn/ẩn đúng thứ tự ưu tiên FR-2.
- [x] **AC-3:** Mở bàn phím nhập ghi chú rồi đóng: không còn tình trạng cụm nút bị đẩy lệch hoặc bị cắt sau khi keyboard đóng (không double-count inset).
- [x] **AC-4:** Xoay ngang: cụm điều khiển vẫn nằm trọn trong safe area, không bị cắt bởi cutout.
- [x] **AC-5:** Unit test cho helper layout thích ứng pass; toàn bộ test hiện có của module `:app` liên quan pass.
- [x] **AC-6:** Bản release: `assembleRelease` build thành công trên máy Windows của user (theo runbook `specs/2026-08-22/release-signed-apk-runbook.md`) và UI camera xác nhận đúng trên thiết bị thật.

## Scenarios

### Scenario 1: Màn thường (Happy Path)
**Given** Thiết bị màn 20:9 portrait, navigation gesture
**When** Mở màn hình camera
**Then** Toàn bộ cụm điều khiển hiển thị đủ, đáy cụm cách navigation bar đúng khoảng an toàn, không phần tử bị cắt.

### Scenario 2: Màn thấp / chế độ compact
**Given** Chiều cao khả dụng dưới ngưỡng (màn ngắn hoặc nhiều inset)
**When** Mở màn hình camera
**Then** Bố cục compact kích hoạt: hàng nút chính + hàng ẢNH/VIDEO nguyên vẹn; thanh zoom/indicator hoặc ghi chú thu gọn theo thứ tự ưu tiên.

### Scenario 3: Bàn phím
**Given** Camera đang mở
**When** Tap vào trường ghi chú (keyboard mở) rồi đóng keyboard
**Then** Trường ghi chú nhìn thấy khi gõ; sau khi đóng, cụm nút trở về đúng vị trí, không bị cắt hay dư khoảng trống.

### Scenario 4: Xoay ngang
**Given** Camera đang mở
**When** Xoay máy sang ngang
**Then** Cụm điều khiển nằm trong safe area, không bị cutout che.

### Scenario 5: Regression camera
**Given** Sau khi áp fix
**When** Chụp ảnh, quay video có stamp, zoom, đổi flash, đổi camera
**Then** Tất cả hoạt động như phiên bản trước fix — kết quả ảnh/video và stamp không đổi.

## Technical Notes

Ứng viên nguyên nhân gốc (xác minh lúc implement):

1. **Inset chồng nhau:** `Column` dưới dùng `navigationBarsPadding().imePadding()` — trên thiết bị mà IME insets bao gồm vùng navigation bar, hai modifier này cộng dồn gây đẩy/tràn. Nên cân nhắc `windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))` hoặc consumption API.
2. **Chiều cao xếp lớp cố định:** Tổng cao các hàng (~300dp+) vượt chiều cao khả dụng trên màn ngắn → cần `BoxWithConstraints` + chế độ compact.
3. **Display cutout:** chưa thấy xử lý cutout riêng cho overlay fullscreen.

Gợi ý triển khai (không ràng buộc): tách hàm thuần ví dụ `computeCameraControlsLayout(availableHeightDp: Int): CameraControlsLayout` vào `app/src/main/java/com/mapsupervision/app/CameraOverlayState.kt` (đã có test tương ứng `CameraOverlayHelpersTest.kt`). File sửa chính: `CameraOverlay.kt`.

Build/verify: VM agent không build được Android — soạn lệnh PowerShell cho user chạy trên Windows, kiểm chứng qua log (xem runbook release hiện có).

## Task Links

- Ledger: `specs/2026-08-22/camera-ui-screen-scale-fix-tasks.md`
- [camera-ui-scale-01] Helper bố cục thích ứng + unit test — done (AC-2, AC-5)
- [camera-ui-scale-02] Áp dụng bố cục thích ứng + sửa inset trong CameraOverlay — done (AC-1, AC-3, AC-4)
- [camera-ui-scale-03] Release build Windows + smoke test thiết bị thật — done (AC-6)

## Open Questions

- [ ] Model/thiết bị release cụ thể để kiểm chứng thủ công AC-6? (user cung cấp khi verify)
- [ ] Ngưỡng chiều cao khả dụng bật chế độ compact (quyết định lúc plan/implement, đề xuất khởi điểm ~560dp)?
