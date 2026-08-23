---
id: n77z6v
title: "[firebase-project-sync-approval-06] Thực thi quyền dữ liệu và media theo phạm vi"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.910Z'
updatedAt: '2026-08-23T12:25:05.072Z'
completedAt: '2026-08-23T12:18:56.906Z'
timeSpent: 15
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-2
  - AC-7
  - AC-8
  - AC-10
  - AC-11
  - AC-17
order: 60
---
# [firebase-project-sync-approval-06] Thực thi quyền dữ liệu và media theo phạm vi

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bảo đảm mọi đọc, ghi và truy cập media được server/rules thực thi đúng data groups, contractors và trạng thái quyền. Phụ thuộc: task 01 và 02. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Server/rules chỉ cho đọc và ghi đúng allowedDataGroups và allowedContractors; request ngoài phạm vi bị từ chối hoặc lọc.
- [x] #2 Người dùng APPROVED cho bất kỳ data group nào xem được toàn bộ media dự án qua endpoint xác thực mà không có Drive URL công khai dài hạn.
- [x] #3 PENDING, REJECTED, REVOKED hoặc không có access record bị chặn project detail và media; REVOKED đồng thời bị chặn mọi ghi/upload.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add shared access-record checks to Firestore project subcollection rules: APPROVED required, group allowlist for data collections, and media allowed only after any approved group.
2. Tighten Storage rules and the authenticated web media proxy so PENDING/REJECTED/REVOKED/nonexistent access cannot read or upload; Drive binaries remain private.
3. Update media-route tests/mocks for accessRecords and verify admin/non-approved behavior.
4. Run rules dry-run, TypeScript, media tests, and validate the task.
5. Record D1-D7 compliance and System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete: Firestore project detail/subcollection reads and writes now require APPROVED access, map data collections to allowedDataGroups, and allow site_photos only when any approved group exists; access records are deterministic per user/project. Storage rules require the same APPROVED access record. The authenticated Next media proxy now checks accessRequests before both Drive upload and stream, so Drive URLs remain private and revoked users are blocked. Verification: firestore rules dry-run compiled successfully; storage dry-run could not run because Firebase Storage is not provisioned for project mapsupervision; webapp npx tsc --noEmit passed; media-route tests passed (14/14). Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — enforced the approved access boundary without new durable guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

