---
id: 74bpkj
title: "[release-tooling-01] Implement core release automation script (scripts/release.mjs)"
status: done
priority: high
labels: []
createdAt: '2026-08-25T14:48:58.021Z'
updatedAt: '2026-08-25T14:51:02.981Z'
completedAt: '2026-08-25T14:51:02.981Z'
timeSpent: 0
---
# [release-tooling-01] Implement core release automation script (scripts/release.mjs)

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Xây dựng script Node.js ESM `scripts/release.mjs` có khả năng:
- Hỗ trợ flags: `--target=android|webapp|all`, `--bump=patch|minor|major|<version>`, `--dry-run`, `--interactive`.
- Đọc và phân tích Git Commits (Conventional Commits: feat, fix, refactor, chore, perf).
- Cập nhật versionCode và versionName trong `app/build.gradle.kts` cho Android.
- Cập nhật version trong `webapp/package.json` cho Webapp.
- Sinh file `docs/releases/{platform}/v{version}.md`.
- Cập nhật `CHANGELOG.md` tại thư mục gốc repository.
- Sinh file metadata `webapp/public/version.json`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented scripts/release.mjs supporting --target=android|webapp|all, --bump=patch|minor|major|<version>, --dry-run, --interactive, conventional git commit parsing, markdown release notes generation, CHANGELOG.md updating, and metadata output. Verified with dry-run on both platforms.
<!-- SECTION:NOTES:END -->

