---
id: r6tubb
title: "[proj-name-02] ViewModel & Local State Orchestration (FirebaseAccessViewModel & FirebaseAccessGate)"
status: done
priority: high
labels: []
createdAt: '2026-08-26T07:27:41.820Z'
updatedAt: '2026-08-26T07:41:28.905Z'
completedAt: '2026-08-26T07:41:28.905Z'
timeSpent: 0
spec: specs/2026-08-26/project-name-sync-unification.md
---
# [proj-name-02] ViewModel & Local State Orchestration (FirebaseAccessViewModel & FirebaseAccessGate)

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cung cấp danh sách localProjects và activeProjectId vào UI state của FirebaseAccessViewModel và đồng bộ metadata khi openOrDownloadProject.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 FirebaseAccessUiState cung cấp localProjects và activeProjectId
- [x] #2 openOrDownloadProject đồng bộ tên và mã dự án mới nhất từ Cloud vào SQLite Room DB
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: none — Local presence state and metadata sync on openOrDownloadProject implemented.
<!-- SECTION:NOTES:END -->

