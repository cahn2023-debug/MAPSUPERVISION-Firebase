---
id: oegie4
title: Release automation and versioning workflow
layer: project
status: proposed
tags:
  - release
  - versioning
  - changelog
  - automation
createdAt: '2026-08-25T15:11:30.973Z'
updatedAt: '2026-08-25T15:11:30.973Z'
---

Cơ chế phát hành và quản lý phiên bản (Versioning & Changelog Automation) được triển khai qua script `scripts/release.mjs`.
Lệnh phát hành:
- `npm run release:webapp -- --bump=patch|minor|major`
- `npm run release:android -- --bump=patch|minor|major`
- `npm run release -- --dry-run` (xem trước thay đổi)
- `npm run release -- -i` (giao diện tương tác nhập ghi chú)
- `npm run test:release` (chạy test suite kiểm thử quy trình release)
Tài liệu release được lưu tại `docs/releases/{platform}/vX.Y.Z.md` và tự động cập nhật `CHANGELOG.md` theo chuẩn Keep a Changelog.
