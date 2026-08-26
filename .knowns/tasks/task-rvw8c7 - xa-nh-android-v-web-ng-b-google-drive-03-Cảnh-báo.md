---
id: rvw8c7
title: "[xa-nh-android-v-web-ng-b-google-drive-03] Cảnh báo và thao tác giữ hoặc xóa ảnh trên web"
status: done
priority: high
labels:
  - from-spec
  - spec:xa-nh-android-v-web-ng-b-google-drive
  - spec-date:2026-08-26
createdAt: '2026-08-26T04:53:29.978Z'
updatedAt: '2026-08-26T05:12:59.018Z'
completedAt: '2026-08-26T05:12:59.018Z'
timeSpent: 0
assignee: '@me'
spec: specs/2026-08-26/xa-nh-android-v-web-ng-b-google-drive
fulfills:
  - AC-4
  - AC-5
  - AC-6
  - AC-8
  - AC-9
order: 30
---
# [xa-nh-android-v-web-ng-b-google-drive-03] Cảnh báo và thao tác giữ hoặc xóa ảnh trên web

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Hiển thị trạng thái ảnh đã xóa trên Android theo từng ảnh, hỗ trợ bỏ qua/giữ lại và hộp thoại xác nhận xóa Drive với loading/error state.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Web keeps Android-deleted photos visible with a per-photo warning.
- [x] #2 Keep action removes the warning without deleting Drive media.
- [x] #3 Drive delete action requires explicit confirmation and shows busy/error states.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend the site_photos listener to retain Android-deleted records needed for review.
2. Add per-photo keep/delete actions to the lightbox UI.
3. Route Drive deletion through the authenticated backend and handle retryable errors.
4. Build/test the webapp and record D1-D5 compliance and System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented site_photos listener retention, per-photo Android deletion warning, keep action, confirmed Drive deletion action, busy/error UI. Verification: web build and test suite pass. System Decision Impact: none — behavior is scoped to the approved spec and existing media contract. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass.
Task completed after review; no blocking P1 findings.
<!-- SECTION:NOTES:END -->

