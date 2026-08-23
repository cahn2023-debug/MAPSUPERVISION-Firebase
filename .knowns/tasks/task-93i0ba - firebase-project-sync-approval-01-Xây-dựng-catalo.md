---
id: 93i0ba
title: "[firebase-project-sync-approval-01] Xây dựng catalog và hợp đồng quyền truy cập an toàn"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.524Z'
updatedAt: '2026-08-23T12:25:04.868Z'
completedAt: '2026-08-23T11:08:40.966Z'
timeSpent: 2047
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-1
  - AC-2
  - AC-5
  - AC-6
order: 10
---
# [firebase-project-sync-approval-01] Xây dựng catalog và hợp đồng quyền truy cập an toàn

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cung cấp nền tảng catalog metadata an toàn và hợp đồng quyền truy cập dùng chung cho các luồng yêu cầu, quản trị và đồng bộ. Phụ thuộc: không. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Người chưa xác thực hoặc người dùng thường không thể đọc project detail, thay đổi trạng thái quản trị hay tự mở rộng phạm vi quyền.
- [x] #2 Hệ thống từ chối APPROVED khi data groups rỗng hoặc contractor scope SCOPED không có nhà thầu.
- [x] #3 Repository trả về các trang catalog đã xác thực gồm ACTIVE/ARCHIVED với đúng tên, mã, ngày cập nhật và trạng thái; không đọc project detail.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add the domain catalog contract in \`FirebaseAccessModels.kt\` and \`FirebaseAccessRepository.kt\`: a typed ACTIVE/ARCHIVED entry containing only project name, project code, updated timestamp, and status, plus a repository method for authenticated catalog reads.
2. Implement the Firestore catalog read in \`FirebaseAccessRepositoryImpl.kt\` using a dedicated \`projectCatalog\` collection and strict allowlist parsing; malformed rows are skipped/reported without exposing project detail documents.
3. Add \`projectCatalog\` Firestore rules with authenticated read and admin-only, exact-shape writes; keep existing project/member detail rules unchanged.
4. Add focused tests for catalog parsing, status mapping, malformed-field rejection, and repository contract fakes without changing Room v48 or outbox schema.
5. Run targeted domain/data tests, rules/config validation, task validation, and record decision compliance plus System Decision Impact.

Decision gates: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. Linked data architecture D1-D3=pass (no Room schema/outbox contract change). System Decision Impact: none — this implements the approved catalog boundary without adding new durable guidance.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Flow start: owned sequentially; implementation follows kn-flow schedule 01→08.
Plan saved: domain catalog contract, data implementation, Firestore rules, focused tests, validation. Decision gates D1-D7=pass.
Adjusted task AC boundary: catalog repository contract is task 01; Android presentation remains task 03.
Implementation complete: dedicated sanitized projectCatalog read contract, strict ACTIVE/ARCHIVED parser, bounded deterministic pagination, exact-shape admin-only catalog writes, and focused parser tests. Verification: :data:compileDebugKotlin and FirebaseProjectCatalogParserTest pass; firestore.indexes.json parses and firebase deploy --only firestore:rules,firestore:indexes --dry-run succeeds. Firebase emulator execution remains unverified because installed Java 17 is below current Firebase tooling Java 21 requirement. Final review PASS with no remaining P1/P2/P3 findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — implemented the approved catalog boundary; no new durable guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

