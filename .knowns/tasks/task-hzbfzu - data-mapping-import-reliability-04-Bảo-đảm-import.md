---
id: hzbfzu
title: "[data-mapping-import-reliability-04] Bảo đảm import nguyên tử theo project"
status: done
priority: high
labels:
  - from-spec
  - spec:data-mapping-import-reliability
  - spec-date:2026-08-23
createdAt: '2026-08-23T06:03:18.881Z'
updatedAt: '2026-08-23T09:02:46.663Z'
completedAt: '2026-08-23T08:59:13.666Z'
timeSpent: 0
spec: specs/2026-08-23/data-mapping-import-reliability
fulfills:
  - AC-9
  - AC-10
order: 40
---
# [data-mapping-import-reliability-04] Bảo đảm import nguyên tử theo project

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Ghi toàn bộ lần import trong giao dịch nguyên tử của database project hiện hoạt; rollback sạch khi lỗi và ngăn mọi thay đổi chéo project.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Commit import chạy trong một transaction của database project hiện hoạt.
- [x] #2 Kiểm thử fault injection chứng minh rollback không để lại dữ liệu hoặc metadata mồ côi.
- [x] #3 Kiểm thử hai project chứng minh không có ghi hoặc cập nhật chéo project.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Thêm API commitImportedGeometry cho repository và triển khai transaction theo project hiện hoạt.
2. Chuyển luồng Excel/non-Excel sang commit nguyên tử, gồm imported file, node và route; bảo đảm lỗi ghi reset trạng thái UI.
3. Bổ sung fault-injection rollback và cross-project isolation tests.
4. Chạy test repository/app và validate task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Hoàn thành: commitImportedGeometry ghi imported file, nodes và routes trong transaction project-scoped; Excel/non-Excel đều dùng API này. Fault-injection rollback và cross-project isolation tests pass. System Decision Impact: none — triển khai transaction đã được quyết định trong spec, không thêm hướng dẫn hệ thống mới. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
<!-- SECTION:NOTES:END -->

