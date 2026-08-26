---
id: 2ddj6z
title: "[node-popup-01] Synchronize full project work items catalog into selectedNodeMaterialLines"
status: done
priority: high
labels: []
createdAt: '2026-08-26T15:22:04.870Z'
updatedAt: '2026-08-26T15:31:21.903Z'
completedAt: '2026-08-26T15:31:21.903Z'
timeSpent: 0
spec: specs/2026-08-26/node-popup-header-scaling-and-full-work-items-synchronization
fulfills:
  - AC-2
  - AC-3
  - AC-4
  - AC-5
order: 1
---
# [node-popup-01] Synchronize full project work items catalog into selectedNodeMaterialLines

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cập nhật getSelectedNodeMaterialLines() và derived indexes để nạp đầy đủ danh mục tất cả các hạng mục công việc của dự án (36 hạng mục), đồng bộ đúng thứ tự/STT với Tab Báo cáo, gán KL thiết kế = 0 cho hạng mục không có trong tóm tắt thiết kế của nút và map đúng KL thi công thực tế.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: none — synchronized all project work items into selectedNodeMaterialLines with exact STT order matching report tab.
<!-- SECTION:NOTES:END -->

