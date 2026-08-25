---
id: yysfgk
title: "[release-tooling-03] Integrate version display in WebApp UI and Android About info"
status: done
priority: medium
labels: []
createdAt: '2026-08-25T14:49:07.990Z'
updatedAt: '2026-08-25T15:09:00.034Z'
completedAt: '2026-08-25T15:09:00.034Z'
timeSpent: 0
---
# [release-tooling-03] Integrate version display in WebApp UI and Android About info

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Tích hợp hiển thị phiên bản hiện tại trên giao diện WebApp (Sidebar / Footer) sử dụng dữ liệu từ package.json / public/version.json, và kiểm tra hiển thị phiên bản trên Android (About Screen / App Info). Đảm bảo giao diện phản ánh đúng số version sau khi bump.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Integrated version badge in WebApp sidebar header loaded dynamically from version.json (with fallback), and enabled buildConfig in Android app buildFeatures to expose BuildConfig.VERSION_NAME and BuildConfig.VERSION_CODE across Android UI.
<!-- SECTION:NOTES:END -->

