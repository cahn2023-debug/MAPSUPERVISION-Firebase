---
id: ry4iav
title: "[firebase-project-catalog-recovery-01] Chuẩn hóa hợp đồng catalog và mọi writer"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-catalog-recovery
  - spec-date:2026-08-24
createdAt: '2026-08-24T03:03:50.485Z'
updatedAt: '2026-08-24T03:52:31.875Z'
completedAt: '2026-08-24T03:32:55.603Z'
timeSpent: 1222
assignee: '@me'
spec: specs/2026-08-24/firebase-project-catalog-recovery-approved
fulfills:
  - AC-7
  - AC-8
  - AC-12
order: 10
---
# [firebase-project-catalog-recovery-01] Chuẩn hóa hợp đồng catalog và mọi writer

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Căn chỉnh schema public projectCatalog, Firestore rules và mọi đường tạo/cập nhật/archive dự án trên Android/web theo @doc/specs/2026-08-24/firebase-project-catalog-recovery-approved. Phụ thuộc: không.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 User đã xác thực đọc được catalog exact-shape gồm projectName, projectCode, createdByUid, updatedAtEpochMs và status, không có field ngoài allowlist.
- [x] #2 Rules từ chối unauthenticated read, non-admin write, extra field và mọi thay đổi createdByUid đã tồn tại; admin payload hợp lệ được chấp nhận.
- [x] #3 Tạo, cập nhật hoặc archive dự án từ Android/web duy trì /projects và /projectCatalog nhất quán mà không còn PERMISSION_DENIED do createdByUid.
- [x] #4 Mọi thay đổi Android tiếp tục giữ Firebase SDK trong :data và chỉ đưa contract/state qua domain interface.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Reconcile the catalog contract in `domain/.../FirebaseAccessModels.kt`, parser and repository-facing serialization so `createdByUid` is required for new public entries while preserving the :data/domain boundary.
2. Update `firestore.rules` `/projectCatalog/{projectId}` exact-shape validation to allow a non-empty `createdByUid`, enforce authenticated reads/admin writes, and keep an existing owner UID immutable.
3. Align production writers/backfill in `data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt` and `webapp/lib/sync.ts` so create/update/archive paths emit the same catalog contract and do not hide schema/permission drift.
4. Add focused parser/rules/writer regression coverage, including valid owner metadata, extra-field rejection, owner mutation rejection, and successful project creation/catalog projection; run targeted data/web checks and module-boundary validation.
5. Validate task/spec references and record D1=pass, D2=pass, D3=pass plus the required System Decision Impact marker before handing off to task 02.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-flow. Task 01 ownership started; implementing catalog contract/rules/writer alignment before dependent migration task.
Plan saved for sequential flow. Scope: domain/data contract, firestore.rules, web writer, focused regression tests, and module-boundary validation. No new dependency planned.
Implementation complete: firestore.rules now validates the five-field projectCatalog shape, requires non-empty createdByUid, and keeps owner immutable on updates. Android parser/extractor reject catalog/project docs without owner until migration repairs them, preventing nullable-owner batch writes. Existing web writer already emits createdByUid and now passes the aligned rule contract. Verification: :data:testDebugUnitTest --tests com.mapsupervision.data.sync.FirebaseProjectCatalogParserTest passed; firebase deploy --only firestore:rules --dry-run compiled successfully; webapp npm test passed (24/24); enforceModuleBoundaries passed; git diff --check passed. Review: no P1 after fix. P2 deferred to i7odyl: Firestore Emulator persona/rules tests are blocked by installed Java 17 while current firebase-tools requires Java 21. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed) — catalog public schema now includes immutable createdByUid and matching rules.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass
<!-- SECTION:NOTES:END -->

