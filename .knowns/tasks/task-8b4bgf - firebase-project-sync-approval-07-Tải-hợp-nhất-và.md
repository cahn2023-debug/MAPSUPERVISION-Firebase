---
id: 8b4bgf
title: "[firebase-project-sync-approval-07] Tải, hợp nhất và đồng bộ dự án bền vững"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.986Z'
updatedAt: '2026-08-23T12:25:05.138Z'
completedAt: '2026-08-23T12:22:32.596Z'
timeSpent: 278
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-9
  - AC-12
  - AC-13
  - AC-14
  - AC-15
  - AC-16
  - AC-17
order: 70
---
# [firebase-project-sync-approval-07] Tải, hợp nhất và đồng bộ dự án bền vững

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cung cấp tải có resume, commit nguyên tử, merge không tạo dự án trùng và đồng bộ hai chiều an toàn theo quyền. Phụ thuộc: task 06. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Tải cùng projectId/projectCode cập nhật database hiện có, không tạo project trùng và giữ local pending outbox khi xung đột.
- [x] #2 Mất mạng hoặc process death không đổi database hiện tại; lần sau resume và chỉ commit gói hợp lệ hoàn toàn.
- [x] #3 Retry cùng checkpoint/syncVersion là idempotent và không tạo bản ghi trùng.
- [x] #4 Sau tải đầu, cloud updates tự về và local changes trong scope tự lên khi có mạng; REVOKED giữ local chỉ đọc và dừng cloud sync.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Gate push/pull/media sync in FirebaseSyncRepositoryImpl on the APPROVED access record (admins remain allowed), preserving Room/outbox state on denied/revoked access.
2. Keep existing checkpoint metadata, deterministic envelope IDs, source-device filtering, and upsert merge behavior for resume/idempotent retries.
3. Add/adjust repository test setup for explicit access-check bypass in isolated unit tests and verify existing sync/media scenarios.
4. Run targeted sync tests and compile, validate task, and record D1-D7 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete: sync push/pull/pending-media now require a current APPROVED accessRequests/{projectId}__{uid} record (or admin claim) before cloud work; REVOKED/PENDING/REJECTED/no record are rejected before local mutation. Existing checkpoint metadata, deterministic table/document IDs, source-device filtering, and upsert merge preserve resume/idempotent retry semantics and local pending rows. Unit test harness explicitly disables the production access guard while exercising isolated sync behavior. Verification: FirebaseSyncRepositoryImplTest passed (6 tests, 0 failures); data compile passed with only pre-existing Kotlin warning. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — enforced the approved sync boundary without new durable guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

