---
id: zaftn2
title: "[proj-name-01] Parser & Repository Self-Healing (FirebaseAccessRepository & FirebaseSyncRepository)"
status: done
priority: high
labels: []
createdAt: '2026-08-26T07:27:27.793Z'
updatedAt: '2026-08-26T07:39:24.822Z'
completedAt: '2026-08-26T07:39:24.822Z'
timeSpent: 0
spec: specs/2026-08-26/project-name-sync-unification.md
---
# [proj-name-01] Parser & Repository Self-Healing (FirebaseAccessRepository & FirebaseSyncRepository)

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Sửa logic bóc tách Firestore docData['data'], docData['payload'] và bổ sung cơ chế self-healing trong listProjectCatalog cùng pushPending sync projectCatalog.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 extractCatalogEntryFromProjectDoc và parseFirebaseProjectCatalog bóc tách chính xác tên dự án từ docData['data']
- [x] #2 listProjectCatalog tự động heal các document projectCatalog bị rỗng hoặc lỗi tên
- [x] #3 pushPending bảng projects đồng bộ bản ghi tương ứng vào projectCatalog
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: none — Parser and repository self-healing logic aligned with canonical Firestore project catalog schema.
<!-- SECTION:NOTES:END -->

