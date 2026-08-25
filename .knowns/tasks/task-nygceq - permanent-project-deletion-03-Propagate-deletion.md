---
id: nygceq
title: "[permanent-project-deletion-03] Propagate deletion state across workers and offline devices"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
createdAt: '2026-08-23T14:33:39.934Z'
updatedAt: '2026-08-25T01:33:45.039Z'
completedAt: '2026-08-23T16:24:51.010Z'
timeSpent: 1663
order: 30
---
# [permanent-project-deletion-03] Propagate deletion state across workers and offline devices

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Kết nối deletion state với background sync/worker và thiết bị thành viên: khóa read/write/upload/media/sync khi DELETING, chỉ xóa local admin sau cloud success, phát hiện tombstone khi reconnect, prompt đồng ý xóa, giữ read-only và export/backup khi từ chối, ngăn mutation và sync.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 DELETING/DELETE_FAILED/DELETED projects are blocked from local sync, media upload, scoped DB opening, and remote row mutation.
- [x] #2 Member reconnect can read a scoped tombstone, mark local project DELETED without deleting rows, and keep it available for read-only/export.
- [x] #3 Member acknowledgement can decline without purge or accept with complete local data/storage purge; admin purge remains cloud-completion gated.
- [x] #4 Tombstone access is limited to project members/admins and includes no business payload; other projects and Google Drive media are untouched.
- [x] #5 Focused Android tests and compile/test verification pass.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Trace shared Room, ProjectScopedDatabaseProvider, repositories, Firebase sync and background workers for project deletion state boundaries.
2. Add a single deletion-state guard so DELETING/DELETE_FAILED/DELETED projects cannot open, mutate, upload, or sync; preserve read-only/export access for offline members.
3. Propagate cloud tombstone/deletion metadata into local Project rows during reconnect without deleting member data automatically.
4. Add explicit member acknowledgement path: accept purges local project and removes catalog entry; decline marks read-only/exportable and blocks mutation/sync.
5. Ensure admin local purge only runs after cloud-completion marker and leaves locked data on failure.
6. Add focused repository/sync/worker tests, run Android module verification, validate task, review diff, and record D1-D4 compliance/impact.

Spec decisions: D1=pass; D2=pass; D3=pass; D4=pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan saved; ownership taken for shared DB, sync, worker, and offline tombstone propagation.
Implementation complete: sync guards, tombstone propagation/member access, remote read-only acknowledgement, and tests added.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — implementation/verification preserves the approved permanent deletion lifecycle and its security/data-retention invariants.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — verification confirms the approved deletion lifecycle invariants
Reconciled with the approved local-first spec revision: detached from the current spec execution set and retained as completed cloud-first baseline history. No implementation files changed.
SDD metadata cleanup: removed stale fulfills links because this completed task is retained as detached historical baseline after the local-first spec revision; implementation history and acceptance criteria remain unchanged.
SDD metadata cleanup completed: stale fulfills links cleared because this completed task is retained as detached historical baseline after the local-first spec revision; implementation history and acceptance criteria remain unchanged.
<!-- SECTION:NOTES:END -->

