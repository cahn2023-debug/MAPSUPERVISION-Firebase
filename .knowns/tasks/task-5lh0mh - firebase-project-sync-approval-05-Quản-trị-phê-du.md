---
id: 5lh0mh
title: "[firebase-project-sync-approval-05] Quản trị phê duyệt trên web"
status: done
priority: medium
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.792Z'
updatedAt: '2026-08-23T12:25:05.029Z'
completedAt: '2026-08-23T12:13:51.568Z'
timeSpent: 463
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-4
  - AC-5
  - AC-6
  - AC-19
order: 50
---
# [firebase-project-sync-approval-05] Quản trị phê duyệt trên web

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cho phép global admin xử lý cùng hàng đợi yêu cầu và phạm vi quyền trên web, đồng bộ nhất quán với Android. Phụ thuộc: task 02. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Global admin trên web xem và xử lý cùng hàng đợi yêu cầu với đầy đủ approve, reject, revoke và cấu hình scope.
- [x] #2 Web chặn người không có admin claim và chặn cấu hình scope không hợp lệ.
- [x] #3 Quyết định web phản ánh trên Android và tạo audit record chính xác.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add typed web access-request rows and shared Firestore subscription/transaction helper for admin transitions and nested audit writes.
2. Subscribe the admin queue only after admin claim resolution; expose approve/reject/revoke controls with data-group and contractor-scope validation.
3. Render the queue in the existing web Admin tab and keep non-admin users out of both UI and writes.
4. Run TypeScript, Next production build, and existing web media tests.
5. Validate task and record D1-D7 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete: webapp/lib/sync.ts now parses/subscribes the bounded accessRequests queue and performs validated APPROVE/REJECT/REVOKE transactions with nested accessAudit writes. The Admin tab renders queue rows, data-group input, ALL/SCOPED contractor scope, contractor validation, and guarded actions; subscription/UI are enabled only after admin custom-claim resolution. Android and web share the same Firestore records/rules/audit path. Verification: webapp `npx tsc --noEmit` passed; `npm run build` passed; `npm run test:media` passed (14/14). Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — added the approved web presentation/transaction wiring without new durable guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

