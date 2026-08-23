---
id: 3rw9vs
title: "[data-mapping-import-reliability-02] Sửa luồng xác nhận ánh xạ và validation"
status: done
priority: high
labels:
  - from-spec
  - spec:data-mapping-import-reliability
  - spec-date:2026-08-23
createdAt: '2026-08-23T06:03:18.776Z'
updatedAt: '2026-08-23T09:02:45.707Z'
completedAt: '2026-08-23T07:43:34.651Z'
timeSpent: 0
spec: specs/2026-08-23/data-mapping-import-reliability
fulfills:
  - AC-4
  - AC-5
  - AC-6
order: 20
---
# [data-mapping-import-reliability-02] Sửa luồng xác nhận ánh xạ và validation

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Khắc phục trạng thái khiến người dùng không thể xác nhận ánh xạ; kiểm tra trường bắt buộc và toàn bộ dòng, hiển thị lỗi có thể hành động và hỗ trợ nhập phần hợp lệ kèm báo cáo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Có kiểm thử tái hiện lỗi không thể xác nhận ánh xạ trước khi sửa.
- [x] #2 Trạng thái loading/error luôn kết thúc xác định và ánh xạ hợp lệ cho phép xác nhận.
- [x] #3 Validation báo đúng lỗi trường/cột/dòng và nhập phần hợp lệ tạo báo cáo đầy đủ.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Tao test tai hien loi khong the xac nhan anh xa hoac mac ket loading/error tren WorkspaceImportMappingActions va ExcelMappingDialog -> verify: test fails trc khi fix.
2. Hoan thien module MappingValidator trong storage-import de validate toan bo du lieu dong truoc khi xac nhan, tinh toan valid/invalid rows, loi truong bat buoc (ma vi tri / toa do), huong dan sua va cho phep nhap phan hop le (partial import).
3. Cap nhat ExcelMappingDialog va WorkspaceImportMappingActions: hien thi so dong hop le/loi, bat/tat nut xac nhan ro rang, hien thi thong bao huong dan khac phuc, dam bao trang thai loading/error luon ket thuc xac dinh -> verify: test passes.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Đã xác minh lại sau wave tích hợp: test app/domain validation pass, compile app pass. System Decision Impact: none — sửa luồng xác nhận và validation trong phạm vi spec, không thêm hướng dẫn hệ thống mới. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
<!-- SECTION:NOTES:END -->

