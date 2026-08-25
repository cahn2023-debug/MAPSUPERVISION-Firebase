---
id: x1frpv
title: "[webapp-ui-firebase-sync-editing-v-ghi-ch-03] Hoàn chỉnh Quản trị và Cấp quyền"
status: done
priority: high
labels:
  - from-spec
  - spec:webapp-ui-firebase-sync-editing-v-ghi-ch
  - spec-date:2026-08-25
  - permissions
  - admin
createdAt: '2026-08-25T17:00:49.428Z'
updatedAt: '2026-08-25T17:23:57.723Z'
completedAt: '2026-08-25T17:15:13.304Z'
timeSpent: 71
assignee: '@me'
spec: specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch
fulfills:
  - AC-6
  - AC-7
order: 30
---
# [webapp-ui-firebase-sync-editing-v-ghi-ch-03] Hoàn chỉnh Quản trị và Cấp quyền

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Hoàn thiện UI quản trị member/access request, chỉnh phạm vi TASKS/NOTES/DEFAULT và contractor, đồng bộ trạng thái realtime, bảo toàn audit và trạng thái revoke/re-request.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Admin queue hiển thị và chuyển trạng thái request APPROVE/REJECT/REVOKE/RE-APPROVE đúng audit flow.
- [x] #2 Data group TASKS/NOTES/DEFAULT và contractor scope được chọn, validate và lưu realtime.
- [x] #3 Member permission panel chỉnh/sửa/thu hồi quyền project không làm hỏng trạng thái request.
- [x] #4 Admin UI có focus/keyboard semantics cơ bản cho controls quyền.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

1. Review the existing AdminApprovalQueue/AdminAccessPanel wiring against @doc/specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch and current Firestore request/rules contract.
2. Normalize admin data-group defaults and labels for TASKS/NOTES/DEFAULT while preserving legacy group names and Android compatibility.
3. Improve keyboard-accessible group selection, validation feedback, busy states, and permission summary in the admin tab without changing audit transition semantics.
4. Run tsc, npm test, build and rules dry-run; review the real diff and record D1-D5/System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Review: PASS. Admin groups/defaults and keyboard semantics follow the approved access contract; audit transitions remain unchanged.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass
System Decision Impact: none — implementation follows the approved spec.
<!-- SECTION:NOTES:END -->

