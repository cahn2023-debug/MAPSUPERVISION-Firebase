---
id: xc48oq
title: "[webapp-ui-firebase-sync-editing-v-ghi-ch-01] Đồng bộ CRUD và tombstone Firebase"
status: done
priority: high
labels:
  - from-spec
  - spec:webapp-ui-firebase-sync-editing-v-ghi-ch
  - spec-date:2026-08-25
  - firebase
  - sync
createdAt: '2026-08-25T17:00:49.129Z'
updatedAt: '2026-08-25T17:23:57.587Z'
completedAt: '2026-08-25T17:07:40.596Z'
timeSpent: 382
assignee: '@me'
spec: specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch
fulfills:
  - AC-3
  - AC-4
  - AC-5
  - AC-6
  - AC-8
order: 10
---
# [webapp-ui-firebase-sync-editing-v-ghi-ch-01] Đồng bộ CRUD và tombstone Firebase

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Hoàn thiện contract web ↔ Android cho task, daily_log và note: typed helpers tạo/sửa/xóa, last-write-wins theo updatedAtEpochMs, tombstone không tái xuất hiện, và rules/test quyền ghi theo data group.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Task, daily_log và note có helper tạo/cập nhật/tombstone giữ nguyên id/projectId và envelope fields.
- [x] #2 Firestore rules map TASKS/NOTES/DEFAULT đúng collection, chặn stale timestamp, payload lệch project và hard delete.
- [x] #3 Focused sync-contract tests pass cùng TypeScript và Firestore rules compilation.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

1. Inspect and preserve the existing project-scoped envelope/collection contract in @doc/specs/2026-08-23/firebase-project-sync-approval-approved and map data groups to task, daily_log, and note writes.
2. Extend webapp/lib/sync.ts with typed update/tombstone helpers for task, daily_log, and note. Every write will preserve projectId/id, set updatedAtEpochMs, sourceDeviceId, lastSyncedAtEpochMs, and use last-write-wins-compatible payloads.
3. Align firestore.rules data-group checks so APPROVED members with TASKS, NOTES, or DEFAULT can write only the intended collections; retain Admin bypass and REVOKED denial.
4. Add focused tests for helper payloads, tombstone behavior, timestamp precedence, and permission-sensitive mapping where the current web test harness supports it.
5. Run the focused web tests plus TypeScript/build diagnostics and record Spec Decision Compliance for D1-D5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Review: PASS. P1 fixed: note payload omits blank title; Firestore rules enforce projectId path, group mapping, timestamp ordering and tombstone-only deletes.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass
System Decision Impact: none — implementation follows the approved spec.
<!-- SECTION:NOTES:END -->

