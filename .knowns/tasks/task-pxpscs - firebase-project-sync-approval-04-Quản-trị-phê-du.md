---
id: pxpscs
title: "[firebase-project-sync-approval-04] Quản trị phê duyệt trên Android"
status: done
priority: medium
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.682Z'
updatedAt: '2026-08-23T12:25:04.983Z'
completedAt: '2026-08-23T12:08:19.016Z'
timeSpent: 251
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-4
  - AC-5
  - AC-6
  - AC-19
order: 40
---
# [firebase-project-sync-approval-04] Quản trị phê duyệt trên Android

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cho phép global admin xử lý hàng đợi yêu cầu và cấu hình data-group/contractor scope trên Android bằng trạng thái Firebase dùng chung. Phụ thuộc: task 02. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Global admin trên Android xem được hàng đợi và thực hiện approve, reject hoặc revoke với data-group/contractor scope hợp lệ.
- [x] #2 Người không có admin claim không thấy hoặc không thực hiện được thao tác quản trị.
- [x] #3 Quyết định Android cập nhật nguồn trạng thái dùng chung và tạo audit record chính xác.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend FirebaseAccessViewModel with admin request queue state, bounded queue refresh, and guarded approve/reject/revoke actions using the shared repository transition contract.
2. Add admin queue UI to the Android catalog surface; expose valid data-group approval defaults, reject/revoke controls, and hide all admin actions for non-admin sessions.
3. Keep source-of-truth transitions in FirebaseAccessRepository so audit is transactionally recorded and server rules remain authoritative.
4. Add/adjust ViewModel tests and run app compile plus targeted FirebaseAccessViewModelTest.
5. Validate task and record D1-D7 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete: FirebaseAccessViewModel now loads a bounded admin queue and exposes guarded approve/reject/revoke transitions with configurable data-group/contractor parameters. FirebaseProjectCatalogScreen renders the queue only when session.isAdmin, hides controls for ordinary users, and routes decisions through FirebaseAccessRepository's transactional audit path. Default approval scope is a valid non-empty data group with ContractorScope.ALL; callers can pass SCOPED contractor selections. Verification: :app:compileDebugKotlin passed; :app:testDebugUnitTest --tests com.mapsupervision.app.auth.FirebaseAccessViewModelTest passed (4 tests, 0 failures); data lifecycle tests remain green. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — added the approved Android admin presentation without new durable guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

