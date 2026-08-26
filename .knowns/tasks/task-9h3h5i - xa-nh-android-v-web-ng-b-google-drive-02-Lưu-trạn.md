---
id: 9h3h5i
title: "[xa-nh-android-v-web-ng-b-google-drive-02] Lưu trạng thái ảnh đã xóa và xóa file Google Drive qua backend"
status: in-progress
priority: high
labels:
  - from-spec
  - spec:xa-nh-android-v-web-ng-b-google-drive
  - spec-date:2026-08-26
createdAt: '2026-08-26T04:53:29.874Z'
updatedAt: '2026-08-26T05:12:25.304Z'
timeSpent: 0
assignee: '@me'
spec: specs/2026-08-26/xa-nh-android-v-web-ng-b-google-drive
fulfills:
  - AC-2
  - AC-4
  - AC-7
  - AC-8
  - AC-9
order: 20
---
# [xa-nh-android-v-web-ng-b-google-drive-02] Lưu trạng thái ảnh đã xóa và xóa file Google Drive qua backend

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Mở rộng contract Firebase/backend để nhận sự kiện Android, giữ trạng thái chờ xử lý và xóa file Google Drive idempotent sau xác nhận có quyền.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Backend accepts only authenticated project members/admins for media deletion.
- [ ] #2 Confirmed deletion removes the Google Drive file idempotently and writes a Firebase tombstone.
- [ ] #3 Drive failures preserve the pending state and return an actionable error.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Reuse the existing Firebase Admin project access check and Drive client.
2. Add an authenticated DELETE media route that validates the Android-deleted marker, deletes the Drive file, and persists a tombstone.
3. Cover success, access denial, missing marker, and Drive failure behavior with route tests/build validation.
4. Record D1-D5 compliance and System Decision Impact.
<!-- SECTION:PLAN:END -->

