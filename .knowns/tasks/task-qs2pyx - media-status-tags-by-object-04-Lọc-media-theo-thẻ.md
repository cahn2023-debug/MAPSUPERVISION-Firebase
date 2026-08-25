---
id: qs2pyx
title: "[media-status-tags-by-object-04] Lọc media theo thẻ trong Báo cáo"
status: done
priority: medium
labels:
  - from-spec
  - spec:media-status-tags-by-object
  - spec-date:2026-08-24
createdAt: '2026-08-24T11:29:25.601Z'
updatedAt: '2026-08-25T02:44:37.147Z'
completedAt: '2026-08-25T02:44:16.659Z'
timeSpent: 246
assignee: '@me'
spec: specs/2026-08-24/media-status-tags-by-object
fulfills:
  - AC-12
  - AC-13
order: 40
---
# [media-status-tags-by-object-04] Lọc media theo thẻ trong Báo cáo

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Thêm dropdown status tag vào thẻ Báo cáo Android, cạnh nút Ẩn/Hiện ảnh, để lọc toàn dự án nhưng giữ nhóm theo đối tượng.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Add the report status-tag dropdown immediately before the photo show/hide button.
- [x] #2 Filter report media across all objects while retaining object grouping.
- [x] #3 Cover All and selected-tag filtering behavior with UI/model tests.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend ReportingViewModel with project-scoped system/custom status-tag options and refresh them with the active report snapshot.
2. Add a status-tag dropdown immediately before the report photo show/hide button; combine its filter with the existing object filter while preserving object grouping in visible/hidden photo sections.
3. Add a small report photo filtering helper and tests covering All versus a selected tag, including tagged and untagged media.
4. Run reporting tests/compile, review the real diff, validate task/SDD, record D1–D6 compliance and System Decision Impact, then complete the task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Bắt đầu task 04 theo schedule tuần tự; phạm vi là ReportingViewModel/ReportingScreen và test lọc tag, không thay đổi export contract.
Implementation complete: ReportingViewModel now exposes four system tags plus project custom tags; ReportingScreen places a status-tag dropdown immediately before the photo visibility button, combines it with the existing object filter, and preserves grouping by object in both visible and hidden modes. Added filterReportPhotosByStatusTag tests for All/tagged+untagged media and selected-tag filtering across objects. Verification: :reporting:testDebugUnitTest passed; git diff --check passed for task files. Review: no P1/P2 findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass. System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.

Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass
System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.
<!-- SECTION:NOTES:END -->

