---
id: v0pbkr
title: "[proj-del-01] DAO Query Filtering and Self-Healing Purge"
status: done
priority: high
labels: []
createdAt: '2026-08-26T08:08:11.829Z'
updatedAt: '2026-08-26T08:49:01.481Z'
completedAt: '2026-08-26T08:49:01.481Z'
timeSpent: 0
spec: specs/2026-08-26/local-project-deletion-and-cloud-restoration
---
# [proj-del-01] DAO Query Filtering and Self-Healing Purge

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cập nhật ProjectDao.list và ProjectRepositoryImpl.list chỉ trả về dự án ACTIVE (isDeleted = 0); tự động dọn dẹp các dự án cũ bị kẹt CLOUD_DECISION_PENDING / DELETED khỏi SQLite.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ProjectDao.list chỉ lấy isDeleted = 0 AND deletionState = 'ACTIVE'
- [ ] #2 Tự động purge các dự án đã xóa cũ khỏi SQLite
<!-- AC:END -->

