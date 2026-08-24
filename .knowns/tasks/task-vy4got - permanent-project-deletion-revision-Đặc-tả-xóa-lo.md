---
id: vy4got
title: "[permanent-project-deletion-revision] Đặc tả xóa local trước và admin quyết định dữ liệu Cloud"
status: todo
priority: high
labels:
  - spec-revision
  - project-deletion
  - android
  - firebase
createdAt: '2026-08-24T02:29:39.776Z'
updatedAt: '2026-08-24T02:30:04.894Z'
timeSpent: 0
spec: specs/2026-08-23/permanent-project-deletion
---
# [permanent-project-deletion-revision] Đặc tả xóa local trước và admin quyết định dữ liệu Cloud

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Sửa đổi @doc/specs/2026-08-23/permanent-project-deletion cho luồng Android: dự án chưa upload chỉ xóa local; dự án đã upload xóa local trước và tạo thông báo để admin đăng nhập quyết định giữ hay xóa dữ liệu Cloud. Đây là work item tách khỏi hồi quy Firebase project catalog.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Dự án chưa từng upload Cloud được xóa hoàn toàn trên thiết bị mà không tạo yêu cầu xóa Cloud.
- [ ] #2 Dự án đã upload Cloud được xóa local và tạo một quyết định chờ admin, không tự xóa dữ liệu Cloud.
- [ ] #3 Khi admin đăng nhập, Android hiển thị popup idempotent cho phép giữ dự án Cloud hoặc khởi tạo xóa Cloud an toàn.
- [ ] #4 Spec sửa đổi xác định rõ quyền, trạng thái, offline/retry, audit và quan hệ với deletion lifecycle hiện có.
<!-- AC:END -->

