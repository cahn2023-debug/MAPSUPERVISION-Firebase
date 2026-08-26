# Specification: Daily Log Immediate Display & Photo Date Filtering

## Overview

Tài liệu đặc tả kỹ thuật cho việc chuẩn hóa và tối ưu hiển thị tại màn hình "Nhật ký" (Progress & Daily Log Hub) trên ứng dụng Android:
1. **Lọc ảnh nhật ký theo ngày được chọn**: Khu vực "Ảnh nhật ký gần nhất" và "Ảnh đối chiếu" chỉ hiển thị các ảnh được chụp trong ngày đang chọn trên lịch (`photosForSelectedDate`), thay vì lấy toàn bộ ảnh của mọi ngày trong dự án. Ẩn hoàn toàn khối ảnh này nếu ngày được chọn không có ảnh chụp nào.
2. **Lưu và hiển thị nhật ký tức thời**: Cho phép người dùng ghi nhật ký tự do/toàn công trường (không bắt buộc chọn vị trí/tuyến). Khi người dùng bấm "Lưu nhật ký" từ Form hoặc bấm "Xác nhận" từ Trợ lý AI (Gemma Assistant), bản ghi nhật ký được lưu ngay vào Room DB, lịch tự động chọn đúng ngày của nhật ký đó và hiển thị ngay lập tức trong danh sách "NHẬT KÝ NGÀY..." và thẻ "TỔNG HỢP NHẬT KÝ TRONG NGÀY".

---

## Locked Decisions

- **D1 (Lọc ảnh nhật ký theo ngày):** Mục "Ảnh nhật ký gần nhất" và "Ảnh đối chiếu" chỉ lọc và hiển thị danh sách ảnh chụp đúng theo ngày được chọn (`photosForSelectedDate`). Nếu ngày được chọn không có ảnh chụp nào (`photosForSelectedDate.isEmpty()`), ẩn hoàn toàn 2 thẻ này.
- **D2 (Vị trí thi công là tùy chọn):** Cho phép ghi nhật ký chung mà không bắt buộc liên kết vị trí/tuyến (vị trí là không bắt buộc như giao diện ghi chú). Loại bỏ điều kiện chặn lưu nhật ký khi `nodeCode` hoặc `routeCode` null/rỗng trong `WorkspaceMapProgressActions.kt`.
- **D3 (Tự động chuyển ngày & hiển thị tức thời):** Khi lưu thành công nhật ký (từ form hoặc khi xác nhận từ Trợ lý AI Gemma), hệ thống tự động chọn đúng ngày của nhật ký đó trên Lịch (`updateSelectedDateMillis`, `updateCurrentMonth`, `updateCurrentYear` nếu khác tháng) và hiển thị tức thời trong danh sách nhật ký và tổng hợp ngày.

---

## System Decision Impact

- **Impact:** none
- **Acceptance gate:** Giao diện Compose cập nhật chính xác, không còn hiển thị ảnh sai ngày và mọi hành động ghi nhật ký được phản ánh tức thì.

---

## Requirements

### Functional Requirements

- **FR-1:** Sửa logic hiển thị ảnh trong tab "Nhật ký" (`ProgressHubScreen.kt`):
  - Thay thế `photos.take(8)` bằng `photosForSelectedDate.take(8)`.
  - Bao bọc khối "Ảnh đối chiếu" và "Ảnh nhật ký gần nhất" trong điều kiện `if (photosForSelectedDate.isNotEmpty())`.
- **FR-2:** Sửa logic lưu nhật ký trong `WorkspaceMapProgressActions.kt`:
  - Cho phép lưu `DailyLog` khi `nodeCode == null` và `routeCode == null`.
  - Không chặn hoặc ném lỗi khi không có vị trí liên kết.
  - Vẫn hỗ trợ tính toán khối lượng công việc và cập nhật tiến độ nếu người dùng có chọn vị trí/tuyến.
- **FR-3:** Đảm bảo tính nhất quán giữa AI Chat (Gemma Assistant) và màn hình Nhật ký:
  - Khi xác nhận hành động `ChatActionType.ADD_DAILY_LOG`, ngày ghi nhật ký (`dateEpochDay`) được truyền chính xác.
  - Sau khi lưu, trạng thái Lịch trên `ProgressHubViewModel` được cập nhật đồng bộ về ngày của bản ghi mới.

### Non-Functional Requirements

- **NFR-1 (UI/UX):** Tuân thủ Design System (Glassmorphic, Dark theme, bảng màu cam/xanh mint chuẩn, không dùng màu tím). Trạng thái phản hồi tức thì với Snackbar thông báo và hiệu ứng chuyển đổi mượt mà.
- **NFR-2 (Clean Code & Testability):** Giữ mã nguồn ngắn gọn, không phát sinh code thừa, có unit test kiểm tra logic lọc ảnh và lưu nhật ký.

---

## Acceptance Criteria

- [ ] **AC-1:** Khi chọn bất kỳ ngày nào trên Lịch trong tab "Nhật ký", danh sách "Ảnh nhật ký gần nhất" chỉ chứa các ảnh có ngày chụp trùng với ngày được chọn.
- [ ] **AC-2:** Nếu ngày được chọn không có ảnh chụp nào, thẻ "Ảnh nhật ký gần nhất" và "Ảnh đối chiếu" tự động ẩn hoàn toàn khỏi màn hình.
- [ ] **AC-3:** Người dùng nhập công việc (ví dụ: "Thi công đường ống"), số nhân công, khối lượng... và để trống vị trí điểm nút -> Bấm "Lưu nhật ký & đồng bộ tiến độ" -> Nhật ký được lưu vào DB thành công và xuất hiện ngay trong danh sách nhật ký của ngày đó.
- [ ] **AC-4:** Khi người dùng gửi lệnh cho Trợ lý AI Gemma và bấm nút "Xác nhận" trong thẻ chờ -> Nhật ký được thêm vào DB, Lịch tự động chuyển đến ngày của nhật ký và hiển thị ngay trên màn hình.
- [ ] **AC-5:** Thẻ "TỔNG HỢP NHẬT KÝ TRONG NGÀY" tính toán đúng tổng nhân công, vị trí thi công (hoặc "Không liên kết" nếu không chọn vị trí), và khối lượng lũy kế theo ngày đang chọn.

---

## Scenarios

### Scenario 1: Xem nhật ký ngày 26/08/2026 có ảnh
- **Given**: Ngày 26/08/2026 có 3 ảnh chụp và 2 bản ghi nhật ký.
- **When**: Người dùng chuyển sang tab "Nhật ký" và chọn ngày 26/08/2026 trên lịch.
- **Then**:
  - Thẻ "Ảnh đối chiếu" hiển thị đúng số lượng khớp/chưa khớp của 3 ảnh này.
  - Thẻ "Ảnh nhật ký gần nhất" hiển thị 3 ảnh của ngày 26/08/2026.
  - Thẻ "TỔNG HỢP NHẬT KÝ TRONG NGÀY" và danh sách nhật ký hiển thị 2 bản ghi của ngày 26/08/2026.

### Scenario 2: Chuyển sang ngày không có ảnh chụp
- **Given**: Ngày 15/08/2026 có 1 bản ghi nhật ký nhưng không có ảnh chụp nào.
- **When**: Người dùng bấm chọn ngày 15/08/2026 trên lịch.
- **Then**:
  - Thẻ "Ảnh đối chiếu" và thẻ "Ảnh nhật ký gần nhất" không hiển thị (bị ẩn hoàn toàn).
  - Danh sách nhật ký hiển thị 1 bản ghi của ngày 15/08/2026.

### Scenario 3: Thêm nhật ký chung không chọn vị trí
- **Given**: Người dùng đang ở màn hình điền nhật ký.
- **When**: Người dùng nhập công việc "Dọn dẹp mặt bằng thi công", số nhân công 4 người, không chọn vị trí, bấm "Lưu nhật ký & đồng bộ tiến độ".
- **Then**: Hệ thống lưu bản ghi vào DB, hiển thị Snackbar thông báo, và bản ghi lập tức xuất hiện trong mục "NHẬT KÝ NGÀY..." bên dưới.

---

## Technical Notes

### Các file cần điều chỉnh:
1. `app/src/main/java/com/mapsupervision/app/workspace/ProgressHubScreen.kt`:
   - Dòng 1707 - 1758: Thay đổi điều kiện hiển thị ảnh từ `if (photos.isNotEmpty())` sang `if (photosForSelectedDate.isNotEmpty())`.
   - Thay đổi vòng lặp `photos.take(8)` thành `photosForSelectedDate.take(8)`.
   - Đảm bảo thẻ "Ảnh đối chiếu" cũng chỉ hiển thị khi `photosForSelectedDate.isNotEmpty()`.
2. `app/src/main/java/com/mapsupervision/app/workspace/WorkspaceMapProgressActions.kt`:
   - Dòng 121 - 130: Bỏ đoạn `if (normalizedNodeCode.isNullOrBlank() && normalizedRouteCode.isNullOrBlank())` chặn lưu nhật ký khi không có vị trí. Cho phép `normalizedNodeCode` và `normalizedRouteCode` là `null`.
   - Đảm bảo các dòng `DailyLogLine` và `DailyLog` được lưu chính xác với `nodeCode = null`, `routeCode = null`.
3. `app/src/main/java/com/mapsupervision/app/workspace/GemmaChatViewModel.kt`:
   - Kiểm tra và đảm bảo khi confirm `ChatActionType.ADD_DAILY_LOG`, nếu `draft.dateEpochDay <= 0L`, truyền ngày hiện tại (`LocalDate.now().toEpochDay()`).

---

## Task Links

(Sẽ được tạo sau khi đặc tả được duyệt)
