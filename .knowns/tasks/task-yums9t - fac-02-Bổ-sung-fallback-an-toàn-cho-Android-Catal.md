---
id: yums9t
title: "[fac-02] Bổ sung fallback an toàn cho Android Catalog Parser trong FirebaseAccessRepositoryImpl.kt"
status: done
priority: high
labels: []
createdAt: '2026-08-25T12:38:04.001Z'
updatedAt: '2026-08-25T13:24:29.014Z'
completedAt: '2026-08-25T12:40:33.084Z'
timeSpent: 0
spec: specs/2026-08-25/firebase-admin-catalog-visibility-cloud-deletion-fix
fulfills:
  - AC-2
order: 2
---
# [fac-02] Bổ sung fallback an toàn cho Android Catalog Parser trong FirebaseAccessRepositoryImpl.kt

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cập nhật parseFirebaseProjectCatalog và extractCatalogEntryFromProjectDoc trong FirebaseAccessRepositoryImpl.kt để bổ sung fallback an toàn khi thiếu createdByUid, projectCode, status; đảm bảo toàn bộ dự án trên Cloud hiển thị đầy đủ trên Android.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Android Catalog Parser áp dụng fallback an toàn cho createdByUid, projectCode, status để hiển thị đầy đủ dự án Firestore mà không bị crash hay loại bỏ.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Đã bổ sung cơ chế fallback toàn diện cho parseFirebaseProjectCatalog và extractCatalogEntryFromProjectDoc (mặc định owner từ session/doc, auto-gen projectCode từ slug/id, status mặc định ACTIVE). Cập nhật FirebaseProjectCatalogParserTest.
Spec Decision Compliance: D2=pass
System Decision Impact: none — safe catalog parsing fallback
<!-- SECTION:NOTES:END -->

