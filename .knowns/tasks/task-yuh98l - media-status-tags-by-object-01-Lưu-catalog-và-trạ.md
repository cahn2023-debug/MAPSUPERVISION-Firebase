---
id: yuh98l
title: "[media-status-tags-by-object-01] Lưu catalog và trạng thái thẻ media"
status: done
priority: high
labels:
  - from-spec
  - spec:media-status-tags-by-object
  - spec-date:2026-08-24
createdAt: '2026-08-24T11:29:25.342Z'
updatedAt: '2026-08-25T02:15:06.471Z'
completedAt: '2026-08-24T11:44:57.825Z'
timeSpent: 0
assignee: '@me'
spec: specs/2026-08-24/media-status-tags-by-object
fulfills:
  - AC-2
  - AC-3
  - AC-10
order: 10
---
# [media-status-tags-by-object-01] Lưu catalog và trạng thái thẻ media

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Thêm mô hình dữ liệu, Room migration và repository cho một status tag tùy chọn trên media và catalog tag tùy chỉnh theo dự án, không ảnh hưởng tag node/tuyến hiện có.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Persist an optional status tag separately from node/route matching tags.
- [x] #2 Persist and validate project-scoped custom tags.
- [x] #3 Add migration and repository tests for legacy media and tag catalog.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a project-scoped MediaStatusTag model/repository contract and a nullable statusTag on SitePhoto, separate from node/route tagCodes.
2. Add Room entity/DAO/database wiring and a v51→v52 migration for the custom-tag catalog and SitePhoto status tag.
3. Update PhotoRepository mappings and scoped/shared data mirroring so legacy photos retain null status tags without migration moves.
4. Extend project storage migration payloads and database-provider hydration/bridge paths for the custom-tag catalog.
5. Add focused repository, migration, and scoped-database regression tests; run the relevant Gradle tests and validate the task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan approved by the user's explicit request to run /kn-flow and implement immediately.
Implemented Room v52 statusTag and media_status_tags catalog; preserved existing photo_tags node/route mapping; added scoped/shared hydration and migration payload handling. Verification: :data:compileDebugKotlin passed; PhotoRepositoryImplTest 5/5 and MapSupervisionDatabaseMigrationTest 22/22 passed (result XML). Review: fixed P2 migration verification coverage. System Decision Impact: none — implementation follows the approved spec without changing durable guidance. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass.
Metadata reconciliation:
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass
System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.
<!-- SECTION:NOTES:END -->

