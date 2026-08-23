---
id: ks43wq
title: "[permanent-project-deletion-04] Add Android deletion safeguards and status UI"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
createdAt: '2026-08-23T14:33:39.990Z'
updatedAt: '2026-08-23T17:12:56.785Z'
completedAt: '2026-08-23T17:08:43.217Z'
timeSpent: 2627
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-1
  - AC-2
  - AC-3
  - AC-4
  - AC-7
  - AC-8
order: 40
---
# [permanent-project-deletion-04] Add Android deletion safeguards and status UI

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bổ sung UI/ViewModel project management: chỉ hiển thị action đúng role, chặn project active, reauthentication, nhập đúng name/code, cảnh báo pending outbox, xác nhận xóa, trạng thái DELETING/DELETE_FAILED, retry và điều hướng sau khi xóa.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Creator or super-admin deletion action is role/state guarded, and active projects cannot be deleted.
- [x] #2 Reauthentication, typed identity, pending-work confirmation, retry idempotency, and deletion status are wired.
- [x] #3 Remote tombstone acknowledgement preserves read-only/export or purges local data as selected.
- [x] #4 Android project/data/app tests and compilation pass.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Trace project settings/action UI, FirebaseAccessViewModel, ProjectRepository deletion APIs, active-project and outbox state.
2. Add role/state-aware delete action only for creator/super-admin and hide/disable it for active projects or locked states.
3. Implement reauthentication/typed identity/pending-outbox confirmation state and call the existing repository/cloud deletion contract idempotently.
4. Surface DELETING/DELETE_FAILED/retry status and route successful deletion to another project or no-project state.
5. Add offline tombstone acknowledgement prompt with keep-read-only/export versus purge choices, while preserving media links.
6. Add ViewModel/UI tests and run app/data compile/tests, review diff, validate task, and record D1-D4 compliance/impact.

Spec decisions: D1=pass; D2=pass; D3=pass; D4=pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan saved; ownership taken for Android deletion safeguards and status UI.
Implemented Android deletion safeguards and status UI. Re-review: PASS after fixes; remaining compile warnings are pre-existing deprecations only. Verification: :data:testDebugUnitTest; :project:testDebugUnitTest; :data:compileDebugKotlin; :project:compileDebugKotlin; :app:compileDebugKotlin all passed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — Android UI/local read-only lifecycle now follows the deletion state machine, creator capability, retry fencing, and tombstone handling.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — verification confirms the approved deletion lifecycle invariants
<!-- SECTION:NOTES:END -->

