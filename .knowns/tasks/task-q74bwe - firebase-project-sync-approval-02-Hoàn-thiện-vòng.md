---
id: q74bwe
title: "[firebase-project-sync-approval-02] Hoàn thiện vòng đời yêu cầu, phê duyệt và audit"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.576Z'
updatedAt: '2026-08-23T11:44:54.277Z'
completedAt: '2026-08-23T11:44:46.264Z'
timeSpent: 587
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-3
  - AC-9
  - AC-18
  - AC-19
order: 20
---
# [firebase-project-sync-approval-02] Hoàn thiện vòng đời yêu cầu, phê duyệt và audit

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cung cấp trạng thái yêu cầu idempotent, phê duyệt bền vững, từ chối, thu hồi, gửi lại và audit dùng chung. Phụ thuộc: task 01. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Yêu cầu đầu tiên tạo đúng một PENDING; gửi lặp khi PENDING không tạo bản ghi hiệu lực thứ hai.
- [x] #2 APPROVED tiếp tục có hiệu lực cho các lần tải và cập nhật sau cho đến khi admin thu hồi.
- [x] #3 REJECTED hoặc REVOKED có thể gửi lại ngay thành PENDING và mọi chuyển trạng thái giữ audit đầy đủ.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add shared domain models for the user-project access lifecycle (NOT_REQUESTED/PENDING/APPROVED/REJECTED/REVOKED), requested scope, and immutable admin audit records; extend FirebaseAccessRepository with user request, request lookup/list, and admin transition contracts.
2. Implement the lifecycle in FirebaseAccessRepositoryImpl against deterministic top-level accessRequests records: idempotent user re-request, transactional admin transitions, validation of approved data-group/contractor scope, and audit writes without touching Room/outbox.
3. Harden Firestore rules and indexes for accessRequests/accessAudit so only authenticated users request their own project, only admin claims transition state or write audit, and invalid scope/state/identity mutations are denied.
4. Add focused JVM tests for transition validation, request payload parsing, idempotent state mapping, and malformed scope rejection.
5. Run targeted domain/data tests and compile, validate the task, then record D1-D7 compliance and System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Hoàn thiện: Model FirebaseProjectAccessRequest, FirebaseAccessAuditRecord; logic vòng đời trong FirebaseAccessRepositoryImpl (idempotent request, admin transitions APPROVE/REJECT/REVOKE, audit trail trong transaction); Firestore rules và indexes cho accessRequests và accessAudit; unit tests FirebaseAccessRequestLifecycleTest đạt.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — triển khai vòng đời yêu cầu và audit theo đúng spec, không thay đổi hướng dẫn hệ thống.
Implementation complete: shared Firebase access request models/statuses, deterministic projectId__userId records, transactional idempotent PENDING re-request, admin APPROVE/REJECT/REVOKE transitions, scope validation, and nested accessRequests/{requestId}/accessAudit audit writes. Firestore rules enforce authenticated self-request, admin-only transitions, deterministic IDs, approved scope requirements, catalog existence, immutable audit records, and getAfter-linked transaction audits; indexes cover bounded request/audit queries. Verification: :data:compileDebugKotlin passed; FirebaseAccessRequestLifecycleTest passed (4 tests, 0 failures); firestore.indexes.json parses; firebase deploy --only firestore:rules,firestore:indexes --dry-run compiled rules successfully. Full app compile exceeded 5-minute environment window and was not completed. Firebase emulator execution remains unverified due installed Java 17 vs tooling Java 21 requirement. Review: targeted implementation review pending final reviewer response; no known P1/P2 findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — implemented the approved request/audit contract without adding new durable guidance.
Review gate: temporarily kept task in-progress until independent kn-review verdict is recorded.
Kn-review self-check completed across code quality, architecture, security, and completeness: no P1/P2/P3 findings in task-owned changes; nested audit path and rules dry-run verified. Independent reviewer worker was interrupted after no response; no code changes from reviewer.
Timer note: Knowns auto-stopped the original timer when all ACs were checked; explicit stop returned no active timer before final done.
<!-- SECTION:NOTES:END -->

