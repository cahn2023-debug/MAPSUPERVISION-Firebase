# Đặc tả kỹ thuật: Hoàn thiện hiển thị đầy đủ dữ liệu ánh xạ & Tối ưu giao diện Thẻ thông tin đối tượng (Node & Route Card)

## Overview

Cải thiện và nâng cấp giao diện Thẻ thông tin đối tượng (Node Card & Route Card) trên bản đồ giám sát MapSupervision. Đảm bảo hiển thị đầy đủ toàn bộ dữ liệu đã được ánh xạ (Mã nút, Số hiệu bản đồ `mapNumberLabel`, Nhà thầu, Tọa độ GPS, Thông tin mạng IP/Subnet/Gateway/Signal, Thông tin tuyến quang, và toàn bộ hạng mục công việc/vật tư/thuộc tính mở rộng). Đồng thời, tái cấu trúc bố cục giao diện với Sticky Header (cố định tiêu đề định danh), Sticky Footer (cố định 4 nút thao tác tác nghiệp), và vùng cuộn thích ứng (Adaptive Scrollable Body) tối đa 65-70% chiều cao màn hình để khắc phục hoàn toàn tình trạng trôi tiêu đề và cắt xén nội dung khi danh sách vật tư dài.

## Locked Decisions

- **D1 — Cấu trúc Thẻ thông tin thích ứng (Adaptive Floating Card):**
  - **Sticky Header:** Cố định trên đỉnh thẻ chứa Mã nút, Số hiệu bản đồ, Trạng thái thi công, Đặt/Bỏ trung tâm và Nút đóng thẻ.
  - **Scrollable Body:** Chiều cao thích ứng theo tỷ lệ màn hình (tối đa 60-70% chiều cao thiết bị), cuộn mượt các khối thông tin (Nhà thầu & Tọa độ, Thông tin mạng, Đường về trung tâm, Bảng hạng mục công việc/vật tư).
  - **Sticky Footer:** Cố định 4 nút thao tác tác nghiệp dưới đáy thẻ (`Xem ảnh`, `Chụp ảnh`, `Báo cáo`, `Ghi chú & CV`) để luôn sẵn sàng thao tác mà không cần cuộn hết danh sách vật tư.
- **D2 — Hiển thị đầy đủ tất cả trường dữ liệu ánh xạ:**
  - Tiêu đề thẻ hiển thị kết hợp cả Mã nút và Số hiệu bản vẽ/Số bản đồ nếu có: `Mã: [code]` kèm Badge / Label `Số hiệu: [mapNumberLabel]`.
  - Hiển thị đầy đủ Nhà thầu (`contractor`) và Tọa độ GPS (`latitude, longitude`).
  - Khối Thông tin mạng (`NodeNetworkSection`) hiển thị IP, Subnet, Gateway và công tắc chuyển trạng thái Trực tuyến/Ngoại tuyến (`signalStatus`).
- **D3 — Xử lý Hạng mục công việc & Vật tư:**
  - Toàn bộ vật tư thiết kế và các cột mở rộng được gộp chung thành các dòng hạng mục công việc hoàn chỉnh trong bảng `Vật tư / khối lượng`.
  - Bộ bóc tách `parseworkVolumeSummary` tự động loại bỏ tiền tố/dòng phân đoạn rác `ExtendedData:` để tránh tạo ra dòng trống vô nghĩa.
  - Tiêu đề cột của bảng vật tư (`Nội dung`, `KL thiết kế`, `KL thi công`) hiển thị rõ ràng, dễ nhìn, các ô nhập `KL thi công` có kích thước chuẩn touch target (tối thiểu 40-44dp), bo góc 6dp, viền nổi bật.
- **D4 — Đồng bộ hoàn thiện Thẻ thông tin Tuyến (Route Card):**
  - Cố định Header mã tuyến và Footer hành động.
  - Hiển thị đầy đủ: Điểm đầu, Điểm cuối, Chiều dài thiết kế, Nhà thầu, Số core quang, Sợi kết nối, Ô nhập ghi chú tuyến và 4 nút hành động.

## System Decision Impact

- Impact: none (kế thừa và hoàn thiện các entities hiện có `GisNode`, `GisRoute`, `PreparedMaterialLine`).

## Requirements

### Functional Requirements

- **FR-1:** Thẻ thông tin Nút (`selectedNode != null`) hiển thị đồng thời `code` và `mapNumberLabel` (nếu `mapNumberLabel` không rỗng).
- **FR-2:** Thẻ thông tin Nút hiển thị đầy đủ Nhà thầu, Tọa độ GPS, Thông tin mạng IP/Subnet/Gateway và trạng thái tín hiệu trực tuyến/ngoại tuyến.
- **FR-3:** Bảng Vật tư / Khối lượng hiển thị chính xác mọi dòng vật tư/hạng mục công việc đã ánh xạ, cho phép nhập liệu khối lượng thi công thực tế và lưu trữ tức thời vào `workVolumeProgress`.
- **FR-4:** `parseworkVolumeSummary` lọc bỏ triệt để dòng `ExtendedData:` để không hiển thị như một dòng vật tư lỗi.
- **FR-5:** Thẻ thông tin Tuyến (`selectedRoute != null`) hiển thị đầy đủ thông tin tuyến và thông tin mạng quang.
- **FR-6:** Các nút hành động tác nghiệp (`Xem ảnh`, `Chụp ảnh`, `Báo cáo`, `Ghi chú & CV`) luôn hiển thị cố định ở chân thẻ cho cả Nút và Tuyến.

### Non-Functional Requirements

- **NFR-1 (UI/UX Pro Max):** Tuân thủ tiêu chuẩn giao diện cao cấp: Theme Dark Mode hài hòa, tỷ lệ phân chia cột bảng vật tư rõ ràng (50% Tên - 25% KL thiết kế - 25% KL thi công), độ tương phản văn bản đạt chuẩn WCAG (>= 4.5:1), touch target cho ô nhập liệu và các nút bấm >= 44dp.
- **NFR-2 (Performance):** Scroll state mượt mà (60/120 FPS), không gây re-render toàn bộ bản đồ khi nhập số liệu tiến độ vào ô BasicTextField.
- **NFR-3 (Responsiveness):** Thích ứng linh hoạt trên nhiều kích thước màn hình điện thoại và máy tính bảng (chiều cao tối đa tính theo `Modifier.heightIn(max = screenHeight * 0.70f)` hoặc tỷ lệ tương đương).

## Acceptance Criteria

- [ ] **AC-1:** Khi người dùng bấm vào một Nút trên bản đồ:
  - Header thẻ hiển thị rõ `Mã: [Code]` và nếu có `mapNumberLabel` thì hiển thị `Số hiệu: [Label]`.
  - Có đầy đủ các khối: Nhà thầu, Tọa độ GPS, Thông tin mạng (IP, Subnet, Gateway, Trực tuyến/Ngoại tuyến), Đường về trung tâm (nếu chưa là trung tâm).
- [ ] **AC-2:** Khi danh sách vật tư dài (nhiều hơn 5-10 dòng):
  - Tiêu đề thẻ và 4 nút hành động ở đáy thẻ vẫn giữ nguyên vị trí, không bị trôi hay che khuất.
  - Vùng nội dung bên trong cuộn mượt mà.
  - Không xuất hiện dòng `ExtendedData:` trong danh sách vật tư.
- [ ] **AC-3:** Khi nhập khối lượng thi công vào ô nhập:
  - Giá trị cập nhật ngay lập tức vào state và dữ liệu của Nút.
- [ ] **AC-4:** Khi bấm vào một Tuyến trên bản đồ:
  - Thẻ thông tin tuyến hiển thị đầy đủ Mã tuyến, Điểm đầu/cuối, Chiều dài, Nhà thầu, Số core quang, Sợi kết nối, ô nhập Ghi chú tuyến và 4 nút hành động.

## Scenarios

### Scenario 1: Người dùng chọn một Nút có nhiều vật tư và số hiệu bản vẽ
**Given** Dự án đã nhập file Excel có ánh xạ Mã nút `C01`, Cột số bản đồ `Số 15`, Nhà thầu `VNPT`, Tọa độ, IP và 10 cột vật tư thiết kế.  
**When** Kỹ sư bấm chọn nút `C01` trên bản đồ.  
**Then** Thẻ thông tin hiển thị rõ `Mã: C01 | Số hiệu: 15`, Nhà thầu `VNPT`, Tọa độ GPS, 10 dòng vật tư đầy đủ số lượng thiết kế, ô nhập thi công thao tác mượt mà, và 4 nút tác nghiệp ở đáy thẻ luôn sẵn sàng.

### Scenario 2: Người dùng cuộn xem danh sách vật tư
**Given** Thẻ thông tin nút đang mở với 10 dòng vật tư.  
**When** Kỹ sư vuốt cuộn danh sách vật tư xuống các mục cuối.  
**Then** Thanh Header (Mã nút, Số hiệu, Nút đóng) và thanh Footer (4 nút chức năng) cố định trên màn hình, chỉ có danh sách thông tin ở giữa cuộn mượt mà.
