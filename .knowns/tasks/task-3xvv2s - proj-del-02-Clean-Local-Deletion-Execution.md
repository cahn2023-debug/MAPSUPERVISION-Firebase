---
id: 3xvv2s
title: "[proj-del-02] Clean Local Deletion Execution"
status: done
priority: high
labels: []
createdAt: '2026-08-26T08:08:16.869Z'
updatedAt: '2026-08-26T08:49:04.498Z'
completedAt: '2026-08-26T08:49:04.498Z'
timeSpent: 0
spec: specs/2026-08-26/local-project-deletion-and-cloud-restoration
---
# [proj-del-02] Clean Local Deletion Execution

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cập nhật hàm xóa dự án local trong ProjectRepositoryImpl: đóng DB scoped, xóa thư mục dữ liệu local và xóa bản ghi khỏi SQLite ngay lập tức mà không treo trạng thái.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Xóa dự án local loại bỏ hoàn toàn dữ liệu local
- [ ] #2 Dữ liệu trên Firestore/Storage không bị xóa khi chỉ xóa local
<!-- AC:END -->

