---
id: vtdoe0
title: "[fac-05] Hiển thị tên dự án và ẩn mã code trên Android"
status: done
priority: medium
labels:
  - from-spec
  - spec:firebase-admin-catalog-visibility-cloud-deletion-fix
  - spec-date:2026-08-25
  - android
createdAt: '2026-08-25T13:59:45.736Z'
updatedAt: '2026-08-25T14:34:45.979Z'
completedAt: '2026-08-25T14:16:07.937Z'
timeSpent: 869
assignee: '@me'
spec: specs/2026-08-25/firebase-admin-catalog-visibility-cloud-deletion-fix
fulfills:
  - AC-5
  - AC-6
order: 50
---
# [fac-05] Hiển thị tên dự án và ẩn mã code trên Android

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cập nhật danh sách dự án Cloud trên Android để tiêu đề thẻ dùng tên dự án, ẩn hoàn toàn mã/code/UUID/slug/ID, và để tiêu đề rỗng khi tên dự án thiếu hoặc rỗng.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Với entry có projectName là "Migration catalog cần rà soát" và projectCode/ID, thẻ Android hiển thị tên dự án làm tiêu đề và không hiển thị mã/code/UUID/slug/ID.
- [x] #2 Với entry có projectName rỗng hoặc thiếu và có projectCode/ID, tiêu đề vẫn rỗng, không fallback sang mã và thao tác mở dự án không bị thay đổi.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Kiểm tra ProjectCatalogCard giữ nguyên binding tiêu đề từ FirebaseProjectCatalogEntry.projectName và không có fallback sang mã (D1, D2).
2. Bỏ phần hiển thị projectCode trong app/src/main/java/com/mapsupervision/app/FirebaseProjectCatalogScreen.kt; giữ nguyên status badge và các nút thao tác.
3. Chạy kiểm thử/build Android phù hợp để xác nhận UI compile và không hồi quy luồng mở/yêu cầu truy cập.
4. Validate task, ghi Spec Decision Compliance: D1=pass, D2=pass và System Decision Impact: none — thay đổi hiển thị cục bộ, không tạo hướng dẫn hệ thống mới.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete: removed only the projectCode text from ProjectCatalogCard; projectName binding, status badge, and open/request actions remain unchanged. Verification: :app:testDebugUnitTest BUILD SUCCESSFUL.
Review PASS after P1 fix: parser now preserves empty projectName for missing/blank catalog names; project card renders projectName only and keeps existing actions. Regression tests cover parseFirebaseProjectCatalog and extractCatalogEntryFromProjectDoc.
Spec Decision Compliance: D1=pass, D2=pass
System Decision Impact: none — this is a scoped UI/catalog fallback correction and adds no new durable project guidance.
<!-- SECTION:NOTES:END -->

