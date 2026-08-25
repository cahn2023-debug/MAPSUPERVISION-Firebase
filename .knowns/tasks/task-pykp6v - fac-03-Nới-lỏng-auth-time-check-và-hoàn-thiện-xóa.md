---
id: pykp6v
title: "[fac-03] Nới lỏng auth_time check và hoàn thiện xóa Cloud từ Android App"
status: done
priority: high
labels: []
createdAt: '2026-08-25T12:38:10.068Z'
updatedAt: '2026-08-25T13:24:32.020Z'
completedAt: '2026-08-25T12:41:58.989Z'
timeSpent: 0
spec: specs/2026-08-25/firebase-admin-catalog-visibility-cloud-deletion-fix
fulfills:
  - AC-3
order: 3
---
# [fac-03] Nới lỏng auth_time check và hoàn thiện xóa Cloud từ Android App

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Nới lỏng điều kiện auth_time trong webapp/app/api/projects/[projectId]/deletion/decision/route.ts và webapp/app/api/projects/[projectId]/deletion/route.ts; đồng thời hoàn thiện gọi xóa Cloud từ Android ProjectViewModel/MapHubScreen.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Nới lỏng ràng buộc auth_time trong route deletion decision và hoàn tất gửi request xóa Cloud từ Android ProjectViewModel.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Đã nới lỏng kiểm tra auth_time trong webapp/app/api/projects/[projectId]/deletion/decision/route.ts, webapp/app/api/projects/[projectId]/deletion/route.ts, và webapp/lib/project-deletion.ts. Cập nhật fallback requestId và typedIdentity an toàn cho Android decideCloudDeletion trong ProjectViewModel.kt.
Spec Decision Compliance: D3=pass
System Decision Impact: none — relaxed cloud deletion auth_time gating per user spec
<!-- SECTION:NOTES:END -->

