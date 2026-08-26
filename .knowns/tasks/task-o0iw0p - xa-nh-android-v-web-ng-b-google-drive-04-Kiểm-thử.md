---
id: o0iw0p
title: "[xa-nh-android-v-web-ng-b-google-drive-04] Kiểm thử tích hợp và xác minh luồng xóa ảnh"
status: done
priority: medium
labels:
  - from-spec
  - spec:xa-nh-android-v-web-ng-b-google-drive
  - spec-date:2026-08-26
createdAt: '2026-08-26T04:53:30.068Z'
updatedAt: '2026-08-26T05:13:50.751Z'
completedAt: '2026-08-26T05:12:59.698Z'
timeSpent: 0
assignee: '@me'
spec: specs/2026-08-26/xa-nh-android-v-web-ng-b-google-drive
fulfills:
  - AC-10
order: 40
---
# [xa-nh-android-v-web-ng-b-google-drive-04] Kiểm thử tích hợp và xác minh luồng xóa ảnh

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bổ sung kiểm thử Android/web/backend cho local delete, outbox retry/idempotency, warning, confirmation, Drive success/failure và permission denial; chạy build/lint/SDD validation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Android compile and targeted repository/outbox/view-model tests pass.
- [x] #2 Web build and existing test suite pass.
- [x] #3 Spec/task validation and review checks report no blocking findings.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run Android compile and targeted unit tests.
2. Run web build and test suite.
3. Inspect the integrated diff and run whitespace/diagnostic checks.
4. Record D1-D5 compliance and System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verification complete: Android compile and targeted tests pass; web build passes; web suite passes 52 tests with 1 skipped emulator test and 0 failures; git diff --check passes. System Decision Impact: none — no durable guidance changed beyond the approved spec. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass.
Task completed after review; no blocking P1 findings.
Added and passed DELETE media route test with mocked Google Drive; final web suite: 53 passed, 1 skipped, 0 failed.
<!-- SECTION:NOTES:END -->

