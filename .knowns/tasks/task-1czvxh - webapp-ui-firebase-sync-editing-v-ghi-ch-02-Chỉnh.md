---
id: 1czvxh
title: "[webapp-ui-firebase-sync-editing-v-ghi-ch-02] Chỉnh sửa Nhiệm vụ, Nhật ký và Ghi chú"
status: done
priority: high
labels:
  - from-spec
  - spec:webapp-ui-firebase-sync-editing-v-ghi-ch
  - spec-date:2026-08-25
  - webapp
createdAt: '2026-08-25T17:00:49.261Z'
updatedAt: '2026-08-25T17:23:57.658Z'
completedAt: '2026-08-25T17:13:45.319Z'
timeSpent: 353
assignee: '@me'
spec: specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch
fulfills:
  - AC-3
  - AC-4
  - AC-5
  - AC-6
order: 20
---
# [webapp-ui-firebase-sync-editing-v-ghi-ch-02] Chỉnh sửa Nhiệm vụ, Nhật ký và Ghi chú

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bổ sung form/list editor cho task, daily_log và shared note; hiển thị realtime, loading/error state, quyền UI theo admin/member scope và kết nối các helper sync.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Task có thao tác sửa nội dung/trạng thái và xóa tombstone từ UI.
- [x] #2 Daily log có thao tác sửa/xóa giữ đúng field dữ liệu.
- [x] #3 Shared note có form tạo/sửa/xóa và hiển thị realtime.
- [x] #4 UI chặn thao tác khi member thiếu quyền phù hợp và hiển thị loading/error.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

1. Extend webapp/app/page.tsx imports/state/handlers with task, daily_log and note editor state, using @doc/specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch and the helpers from task xc48oq.
2. Add permission-aware editor controls to the Nhiệm vụ & Nhật ký tab: edit/save/delete task and daily_log records while preserving existing realtime lists and status actions.
3. Add a shared project note form/list editor to the Hình ảnh & Ghi chú tab, including title/objectCode/content and tombstone deletion.
4. Add focused UI-independent tests for permission gating/field normalization where practical, then run npm test, tsc and build.
5. Record review findings, Spec Decision Compliance D1-D5 and System Decision Impact before completing the task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Review: PASS. CRUD/realtime editors and permission gating are wired to the approved sync contract.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass
System Decision Impact: none — implementation follows the approved spec.
<!-- SECTION:NOTES:END -->

