---
id: o6r10r
title: "[release-tooling-04] Add tests and verify release automation workflow"
status: done
priority: medium
labels: []
createdAt: '2026-08-25T14:49:26.980Z'
updatedAt: '2026-08-25T15:10:39.008Z'
completedAt: '2026-08-25T15:10:39.008Z'
timeSpent: 0
---
# [release-tooling-04] Add tests and verify release automation workflow

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Viết test suite kiểm thử cho script release `scripts/release.mjs` (test dry-run, test bump version SemVer cho gradle và package.json, test git commit parser, test sinh release markdown và version.json). Chạy kiểm tra toàn diện quy trình release.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Created unit & integration test suite in tests/release-automation.test.mjs covering version bumping, conventional commits parsing, markdown release notes generation, build gradle and package.json version readers, and dry-run CLI execution. All 6 tests passing cleanly.
<!-- SECTION:NOTES:END -->

