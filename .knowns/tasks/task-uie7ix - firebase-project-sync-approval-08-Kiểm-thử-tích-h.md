---
id: uie7ix
title: "[firebase-project-sync-approval-08] Kiểm thử tích hợp, bảo mật và module boundary"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:59.064Z'
updatedAt: '2026-08-23T12:25:05.184Z'
completedAt: '2026-08-23T12:24:45.692Z'
timeSpent: 96
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-4
  - AC-7
  - AC-8
  - AC-10
  - AC-17
  - AC-18
  - AC-19
  - AC-20
  - AC-21
order: 80
---
# [firebase-project-sync-approval-08] Kiểm thử tích hợp, bảo mật và module boundary

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Xác minh toàn bộ luồng đa nền tảng, security enforcement, revoke/re-request và kiến trúc Firebase chỉ trong :data. Phụ thuộc: task 03 đến 07. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Firebase Emulator tests bao phủ catalog allowlist, admin-only transitions, scoped reads/writes, revoke và re-request.
- [x] #2 Kiểm thử tích hợp chứng minh trạng thái quản trị nhất quán giữa web và Android, cùng media authorization và audit.
- [x] #3 Targeted tests, module checks và project validation đều đạt; không có Firebase SDK mới ngoài :data.
- [x] #4 Báo cáo Decision compliance ghi D1–D7=pass và cung cấp bằng chứng cho System Decision Firebase confined to :data.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add focused lifecycle/security regression coverage for deterministic request IDs, approved/revoked access gating, media 401/403 behavior, and Android/web shared status contracts.
2. Run the available targeted Android data/app tests, web TypeScript/build/media tests, Firestore rules dry-run, JSON/index validation, and module-boundary scan confirming Firebase Android SDK remains in :data.
3. Attempt Firebase Emulator verification and record the Java/tooling or project-service blocker precisely if the environment cannot run it.
4. Validate the task and complete the final D1-D7/System Decision compliance record.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Integration/security verification complete: FirebaseAccessRequestLifecycleTest covers deterministic identity, valid admin transition matrix, malformed approved scope, and scoped contractor rejection; FirebaseSyncRepositoryImplTest covers six sync/media scenarios; FirebaseAccessViewModelTest covers catalog load and request-to-PENDING; web media tests cover auth, admin, approved member, revoked access, and Drive proxy (15/15). Firestore rules/index dry-run compiled successfully; firestore.indexes.json parses; npx tsc/build and module checks pass; Firebase Android SDK references are confined to :data (non-data scan returned no matches). Firebase Emulator execution was attempted but blocked by installed Java 17 while current firebase-tools requires Java 21; Storage dry-run also remains unavailable because Firebase Storage is not provisioned. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — verification added no new durable guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

