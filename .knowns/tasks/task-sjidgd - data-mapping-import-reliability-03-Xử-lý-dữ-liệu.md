---
id: sjidgd
title: "[data-mapping-import-reliability-03] Xử lý dữ liệu trùng và metadata mở rộng"
status: done
priority: high
labels:
  - from-spec
  - spec:data-mapping-import-reliability
  - spec-date:2026-08-23
createdAt: '2026-08-23T06:03:18.830Z'
updatedAt: '2026-08-23T10:01:19.736Z'
completedAt: '2026-08-23T08:58:59.187Z'
timeSpent: 0
spec: specs/2026-08-23/data-mapping-import-reliability
fulfills:
  - AC-7
  - AC-8
order: 30
---
# [data-mapping-import-reliability-03] Xử lý dữ liệu trùng và metadata mở rộng

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Đề xuất và cho phép chỉnh khóa nghiệp vụ; hiển thị xung đột, hỗ trợ bỏ qua/cập nhật; cho người dùng chọn từng cột ngoài mô hình để lưu metadata mở rộng.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Khóa nghiệp vụ được đề xuất, có thể chỉnh sửa và số bản ghi trùng được tính đúng.
- [x] #2 Cả hai chính sách bỏ qua/cập nhật bản ghi trùng có kiểm thử database.
- [x] #3 Metadata chỉ chứa các cột mở rộng được người dùng xác nhận.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Mở rộng model và trạng thái UI cho khóa nghiệp vụ chỉnh sửa, chính sách SKIP/UPDATE và danh sách cột metadata mở rộng đã xác nhận.
2. Tính số trùng theo khóa được chọn, truyền lựa chọn vào importer và hiển thị trước khi xác nhận.
3. Chỉ ghi các cột metadata được xác nhận dưới ExtendedData; bổ sung kiểm thử domain/data cho SKIP, UPDATE và metadata.
4. Chạy test liên quan và validate task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Hoàn thành: khóa nghiệp vụ CODE/COORDINATES/COMPOSITE có thể chỉnh sửa; chính sách SKIP/UPDATE được truyền qua mapping; custom columns chỉ được lưu khi xác nhận dưới ExtendedData. Tests domain/data và app validation pass. System Decision Impact: none — hành vi nằm trong Locked Decisions của spec và không thêm hướng dẫn hệ thống ngoài phạm vi. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Bổ sung WorkspaceImportHelperDuplicatePolicyTest cho SKIP và UPDATE; test app pass. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
<!-- SECTION:NOTES:END -->

