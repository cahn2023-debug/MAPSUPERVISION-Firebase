---
id: tusgs7
title: "[release-tooling-02] Setup release docs directories, root CHANGELOG.md and npm commands"
status: done
priority: medium
labels: []
createdAt: '2026-08-25T14:49:02.980Z'
updatedAt: '2026-08-25T14:52:28.975Z'
completedAt: '2026-08-25T14:52:28.975Z'
timeSpent: 0
---
# [release-tooling-02] Setup release docs directories, root CHANGELOG.md and npm commands

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Khởi tạo cấu trúc thư mục docs/releases/android, docs/releases/webapp, tạo file CHANGELOG.md chuẩn theo Keep a Changelog và cấu hình các npm scripts tại package.json (root hoặc webapp) để lập trình viên dễ dàng chạy: npm run release, npm run release:android, npm run release:webapp, npm run release:dry-run.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Configured root package.json and webapp/package.json npm release scripts. Created docs/releases/android/README.md, docs/releases/webapp/README.md, and initialized root CHANGELOG.md with Keep a Changelog baseline format. Verified npm run release:dry-run works from root.
<!-- SECTION:NOTES:END -->

