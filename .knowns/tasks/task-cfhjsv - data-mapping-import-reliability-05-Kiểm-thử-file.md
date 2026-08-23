---
id: cfhjsv
title: "[data-mapping-import-reliability-05] Kiểm thử file thực tế và hồi quy import"
status: blocked
priority: medium
labels:
  - from-spec
  - spec:data-mapping-import-reliability
  - spec-date:2026-08-23
createdAt: '2026-08-23T06:03:18.934Z'
updatedAt: '2026-08-23T09:49:05.164Z'
timeSpent: 0
spec: specs/2026-08-23/data-mapping-import-reliability
fulfills:
  - AC-11
  - AC-12
order: 50
---
# [data-mapping-import-reliability-05] Kiểm thử file thực tế và hồi quy import

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Chạy kiểm thử end-to-end với file thực tế, đối chiếu dữ liệu database, chuyển ngoại lệ thành fixture đã ẩn dữ liệu nhạy cảm và xác nhận các luồng non-Excel không hồi quy.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Các file thực tế được chạy qua preview, xác nhận và đối chiếu dữ liệu database.
- [ ] #2 Mọi lỗi riêng của file thực tế được tái tạo bằng fixture đã loại bỏ dữ liệu nhạy cảm.
- [x] #3 Toàn bộ test import bảng và non-Excel liên quan đều thành công.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Nhận file dữ liệu thực tế do người dùng cung cấp và chạy preview/xác nhận/đối chiếu database cho từng định dạng.
2. Ẩn dữ liệu nhạy cảm và chuyển mọi ngoại lệ thực tế thành fixture hồi quy.
3. Chạy toàn bộ test import bảng và non-Excel liên quan; ghi nhận kết quả và validate task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Đã chạy các test parser bảng, validation app/domain, repository transaction và các test import liên quan hiện có; đều pass. AC-11 bị chặn vì repository không có file .xls/.xlsx/.csv thực tế và chưa có file do người dùng cung cấp để preview/xác nhận/đối chiếu. System Decision Impact: none — chỉ kiểm thử hồi quy trong phạm vi spec, không thay đổi hướng dẫn hệ thống. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Debug 2026-08-23: import appeared silent because picker callbacks were no-op and generic import coroutine exceptions were uncaught. Added picker feedback, picker launch guard, and top-level ImportStatus.FAILED handling. MediaProvider revoke_uri_permission warning is a system warning during storage-provider file move, not the app root cause.
<!-- SECTION:NOTES:END -->

