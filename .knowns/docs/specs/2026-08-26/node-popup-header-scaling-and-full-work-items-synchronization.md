---
id: doc-999d119bf410fdf75ca257aa554f353a
title: Node Popup Header Scaling and Full Work Items Synchronization
description: Specification for Node Popup Header Scaling and Full Work Items Synchronization
createdAt: '2026-08-26T15:21:09.876Z'
updatedAt: '2026-08-26T15:31:35.002Z'
tags:
  - spec
  - approved
---

## Overview

Nâng cấp giao diện và dữ liệu cho Popup chi tiết đối tượng (Node/Object Detail Dialog) trên ứng dụng Android:
1. Tối ưu Header popup tự co giãn theo kích thước màn hình điện thoại, chiều cao compact không chiếm diện tích dọc, tự động đưa mã hiệu / số hiệu nút xuống dòng dưới khi tên đối tượng dài.
2. Đồng bộ toàn bộ danh mục 36 hạng mục công việc chuẩn của dự án (như bảng tổng hợp ở Tab Báo cáo) vào popup của từng đối tượng để hiển thị và hỗ trợ nhập khối lượng thi công thực tế cho bất kỳ hạng mục nào.

## Locked Decisions

- **D1 (Header Layout)**: Dòng 1 hiển thị Tên đối tượng (tiêu đề chính) cùng các nút hành động (Bình thường / Đặt trung tâm / Nút đóng); Dòng 2 hiển thị Mã hiệu, Số hiệu nút và trạng thái phụ. Header giữ chiều cao gọn gàng, scale mượt mà trên mọi kích thước màn hình.
- **D2 (Full Work Items List)**: Popup của từng đối tượng hiển thị đầy đủ danh mục tất cả các hạng mục công việc của dự án (chuẩn hóa theo danh mục ở Tab Báo cáo). Nếu nút không có khối lượng trong hồ sơ thiết kế thì cột "KL thiết kế" hiển thị `0` (hoặc để trống), người dùng vẫn có thể nhập "KL thi công" thực tế.
- **D3 (Sorting & STT Alignment)**: Bảng vật tư popup giữ nguyên thứ tự sắp xếp và cột STT (1..N) đồng bộ 100% với Bảng tổng hợp ở Tab Báo cáo.
- **D4 (Item Count Badge)**: Badge số lượng hạng mục ở góc trên bên phải bảng vật tư hiển thị tổng số hạng mục của dự án (ví dụ: "36 hạng mục").

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements
- **FR-1**: Header của Popup đối tượng (`NodeIdentityHeader`) được tổ chức responsive 2 dòng: Dòng 1 chứa Tên đối tượng + Nút action (Đặt trung tâm / Đóng); Dòng 2 chứa Mã hiệu (`node.code`), Số hiệu (`node.mapNumberLabel`), Badge trạng thái / Điểm trung tâm.
- **FR-2**: Nguồn dữ liệu vật tư/hạng mục trong Popup đối tượng (`getSelectedNodeMaterialLines`) tải toàn bộ danh mục công việc của dự án từ thiết kế/báo cáo (`allWorkNames`), map với khối lượng thiết kế của nút hiện tại (`node.workVolumeSummary`) và khối lượng thi công đã nhập (`workVolumeProgress` / `workVolumeRows`).
- **FR-3**: Bảng vật tư trong Popup đối tượng hiển thị đầy đủ các cột: STT, Nội dung hạng mục, KL thiết kế, và Ô nhập/hiển thị KL thi công.
- **FR-4**: Cập nhật badge số lượng hạng mục thành tổng số hạng mục của dự án (ví dụ: `${allMaterialLines.size} hạng mục`).
- **FR-5**: Khi người dùng nhập KL thi công cho một hạng mục chưa từng có thiết kế ở nút đó, hệ thống ghi nhận và lưu `WorkVolumeProgress` chính xác theo `nodeCode` và `workName`.

### Non-Functional Requirements
- **NFR-1**: Trải nghiệm UI/UX mượt mà, chiều cao Header và Body popup được giới hạn hợp lý (`heightIn(max = cardMaxBodyHeight)`), cuộn nội dung độc lập, không bị tràn màn hình trên các thiết bị Android từ 5.0 inch đến tablet.
- **NFR-2**: Tối ưu hiệu năng tính toán danh sách hạng mục qua `ensureIndexes` và derived state, không gây giật lag khi mở popup đối tượng trên bản đồ.

## Acceptance Criteria

- [x] **AC-1**: Trên màn hình điện thoại, header popup không bị kéo cao bất thường; tên đối tượng nằm ở dòng trên và mã hiệu/số hiệu nút nằm ở dòng dưới một cách cân đối, đẹp mắt.
- [x] **AC-2**: Mở popup của bất kỳ đối tượng nào (ví dụ nút 203) đều hiển thị đầy đủ tất cả các hạng mục công việc của dự án (đúng 36 hạng mục như trong Tab Báo cáo thay vì chỉ 15 hạng mục).
- [x] **AC-3**: Hạng mục không có trong thiết kế của nút đó hiển thị KL thiết kế là 0 hoặc trống, và có ô nhập KL thi công hoạt động bình thường.
- [x] **AC-4**: Thứ tự các hạng mục và STT trong popup trùng khớp với thứ tự trong Bảng tổng hợp khối lượng thi công ở Tab Báo cáo.
- [x] **AC-5**: Nhập khối lượng thi công và lưu thành công, dữ liệu phản ánh ngay lập tức trên cả Popup đối tượng và Bảng tổng hợp ở Tab Báo cáo.

## Scenarios

### Scenario 1: Mở popup đối tượng có tên dài trên điện thoại
**Given** Người dùng chọn một đối tượng có tên dài (ví dụ: "ĐƯỜNG PHỦ MỸ - ĐƯỜNG TRÁNH HUYỆN BA VÌ") trên thiết bị màn hình nhỏ.  
**When** Popup đối tượng hiển thị.  
**Then** Header hiển thị tên ở dòng 1 với nút đóng/trung tâm; dòng 2 hiển thị rõ ràng "Mã: ...", "Số hiệu: 203" mà không bị vỡ layout hay chiếm quá nhiều diện tích.

### Scenario 2: Xem và nhập khối lượng cho hạng mục chưa có thiết kế ban đầu
**Given** Nút 203 chỉ có 15 hạng mục trong hồ sơ thiết kế, dự án có tổng cộng 36 hạng mục.  
**When** Người dùng mở popup nút 203 và cuộn xem danh sách hạng mục.  
**Then** Người dùng nhìn thấy đủ 36 hạng mục theo đúng STT; đối với hạng mục STT 28 ("Tủ đèn" - vốn không có ở thiết kế nút 203), KL thiết kế hiển thị là `0`, người dùng có thể nhập KL thi công là `2` và lưu thành công.

## Technical Notes

- Cập nhật hàm `WorkspaceViewModel.getSelectedNodeMaterialLines()` trong [WorkspaceMapProgressActions.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/app/src/main/java/com/mapsupervision/app/workspace/WorkspaceMapProgressActions.kt) để lấy danh sách đầy đủ các hạng mục của dự án (từ derived index `allProjectWorkNames` hoặc `reporting` helper) thay vì chỉ lấy từ `node.workVolumeSummary`.
- Tinh chỉnh composable `NodeIdentityHeader` và `ElevatedCard` trong [MapHubScreen.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-Firebase/app/src/main/java/com/mapsupervision/app/workspace/MapHubScreen.kt) với bố cục 2 dòng gọn gàng, hỗ trợ cột STT trong bảng `selectedNodeMaterialLines`.

## Task Links

- `2ddj6z`: [node-popup-01] Synchronize full project work items catalog into selectedNodeMaterialLines (done)
- `5k9j9b`: [node-popup-02] Responsive NodeIdentityHeader layout and work items table STT column (done)
- `zpd7m7`: [node-popup-03] Verification, regression testing, and SDD validation (done)

## Open Questions

- Không có (tất cả các quyết định D1 - D4 đã được thống nhất qua quy trình Grill-me).
