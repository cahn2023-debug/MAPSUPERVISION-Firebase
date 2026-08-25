---
id: doc-7bf2f675cdfc7734b2d85d86d100322d
title: Versioning and Changelog Automation
description: Specification for Versioning and Changelog Automation mechanism for Android and Webapp
createdAt: '2026-08-25T14:47:41.980Z'
updatedAt: '2026-08-25T15:10:44.050Z'
tags:
  - spec
  - approved
---

## Overview

Cơ chế quản lý phiên bản (Versioning) và tự động hóa sinh tài liệu ghi nhận thay đổi (Release Notes / Changelog) cho cả ứng dụng Android (`com.mapsupervision`) và WebApp (`mapsupervision-webapp`).

Hệ thống cho phép bump version độc lập hoặc phối hợp cho từng nền tảng, tự động tổng hợp thay đổi từ Conventional Git Commits hoặc giao diện tương tác (Interactive CLI), cập nhật mã nguồn cấu hình (`app/build.gradle.kts`, `webapp/package.json`), sinh file release docs chi tiết (`docs/releases/{platform}/vX.Y.Z.md`), cập nhật `CHANGELOG.md` tổng hợp tại thư mục gốc, và tạo file metadata runtime (`webapp/public/version.json`, Android AppConfig/BuildConfig) phục vụ hiển thị phiên bản trên UI cũng như hỗ trợ tính năng "Có gì mới" (What's New) trong tương lai.

## Locked Decisions

- **D1: Quản lý phiên bản độc lập theo từng nền tảng:**
  - Android quản lý `versionCode` (số nguyên tăng dần) và `versionName` (chuỗi SemVer e.g. `1.2.0`) trong `app/build.gradle.kts`.
  - WebApp quản lý `version` (SemVer e.g. `0.2.0`) trong `webapp/package.json`.
  - Có công cụ chung hỗ trợ bump riêng rẽ (`--target=android`, `--target=webapp`) hoặc đồng thời (`--target=all`).

- **D2: Cấu trúc lưu trữ Changelog và Release Notes:**
  - File tổng hợp tại gốc: `CHANGELOG.md` theo chuẩn "Keep a Changelog" (Added, Changed, Deprecated, Removed, Fixed, Security).
  - File chi tiết từng bản phát hành theo nền tảng: `docs/releases/android/v{version}.md` và `docs/releases/webapp/v{version}.md`.
  - Giữ lại lịch sử rõ ràng, dễ truy vết theo ngày tháng và commit hash.

- **D3: Công cụ tự động hóa phát hành (Release Automation Tooling):**
  - Xây dựng script Node.js/TypeScript (`scripts/release.mjs`) tích hợp sẵn các lệnh npm (`npm run release`, `npm run release:android`, `npm run release:webapp`).
  - Hỗ trợ 2 chế độ:
    1. *Tự động (Automated)*: Phân tích Git Log từ tag trước đến HEAD theo chuẩn Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`, `perf:`).
    2. *Tương tác (Interactive CLI)*: Nhập prompt trực tiếp tiêu đề, mô tả tính năng nổi bật và ghi chú bổ sung.
  - Tự động thực hiện: Bump version file cấu hình -> Sinh file markdown release note -> Cập nhật CHANGELOG.md -> Cập nhật file runtime metadata -> Tùy chọn tạo git commit & git tag tương ứng.

- **D4: Hiển thị Runtime Metadata & Thông tin UI:**
  - Xuất metadata `webapp/public/version.json` (chứa `version`, `buildDate`, `commitHash`, `highlights`).
  - WebApp hiển thị phiên bản ở Sidebar / Footer và cung cấp endpoint/hook kiểm tra phiên bản.
  - Android cập nhật `BuildConfig` / `AppConfig` và hiển thị phiên bản tại màn hình Thông tin ứng dụng (About screen), sẵn sàng cho popup / modal "Có gì mới" (What's New).

## System Decision Impact

- Impact: none
- Acceptance gate: Không ảnh hưởng đến các quyết định kiến trúc cốt lõi (như lưu trữ Firebase/Firestore hay vòng đời dữ liệu dự án), đóng vai trò là cơ chế quy trình phát triển và vận hành (DevOps & DX).

## Requirements

### Functional Requirements

- **FR-1 (Version Bumping):** Hỗ trợ tăng version theo ngữ nghĩa SemVer (`major`, `minor`, `patch`) hoặc chỉ định số version tùy chỉnh cho Android và Webapp. Đối với Android, tự động tăng `versionCode` tương ứng.
- **FR-2 (Git Commit Parsing):** Tự động phân tích các git commit từ lần release/tag gần nhất, nhóm theo các danh mục: Tính năng mới (Features), Sửa lỗi (Bug Fixes), Cải tiến hiệu năng (Performance), Tái cấu trúc & Dọn dẹp (Refactor & Chores).
- **FR-3 (Release Note Generation):** Tự động sinh file `docs/releases/android/v{version}.md` hoặc `docs/releases/webapp/v{version}.md` chứa ngày phát hành, danh sách thay đổi chi tiết, mã commit.
- **FR-4 (Root Changelog Aggregation):** Tự động cập nhật hoặc thêm mục mới nhất vào đầu file `CHANGELOG.md` tại thư mục gốc của repository.
- **FR-5 (Metadata Generation):** Xuất file `webapp/public/version.json` và cập nhật thông tin phiên bản phục vụ WebApp và Android.
- **FR-6 (CLI Interface & NPM Scripts):** Cung cấp các lệnh CLI trực quan qua npm scripts (`npm run release`, `npm run release:android`, `npm run release:webapp`, `npm run release:dry-run`).
- **FR-7 (UI Integration):** WebApp đọc và hiển thị phiên bản hiện tại trên giao diện (footer/sidebar), có thể xem danh sách thay đổi tóm tắt.

### Non-Functional Requirements

- **NFR-1 (Reliability & Dry-Run):** Có chế độ `--dry-run` để xem trước các thay đổi (preview files changed, version diffs, changelog diffs) mà không ghi đè dữ liệu.
- **NFR-2 (Idempotency & Clean Rollback):** Nếu có lỗi trong quá trình sinh release (ví dụ lỗi parse git hoặc file lock), script phải báo lỗi rõ ràng và không để lại file rác dở dang.
- **NFR-3 (Zero External Heavy Dependencies):** Sử dụng các module Node.js chuẩn (native fs, path, readline, child_process) để chạy trơn tru mà không yêu cầu cài đặt thêm các package nặng nề.

## Acceptance Criteria

- [ ] **AC-1:** Chạy `npm run release:webapp -- --bump=patch` (hoặc interactive) cập nhật `webapp/package.json` lên patch version mới, sinh file `docs/releases/webapp/vX.Y.Z.md`, cập nhật `CHANGELOG.md` và `webapp/public/version.json`.
- [ ] **AC-2:** Chạy `npm run release:android -- --bump=minor` cập nhật `versionCode` (+1) và `versionName` trong `app/build.gradle.kts`, sinh file `docs/releases/android/vX.Y.Z.md` và cập nhật `CHANGELOG.md`.
- [ ] **AC-3:** Chạy với flag `--dry-run` in ra toàn bộ nội dung release note dự kiến và các file sẽ bị sửa đổi mà không thực sự ghi đĩa.
- [ ] **AC-4:** Khi không có conventional commit nào, script cho phép người dùng nhập thủ công tóm tắt các tính năng / cập nhật qua Interactive CLI.
- [ ] **AC-5:** File `CHANGELOG.md` tuân thủ đúng định dạng Keep a Changelog (có đường link, ngày tháng `YYYY-MM-DD`, các mục Added / Fixed / Changed rõ ràng).
- [ ] **AC-6:** WebApp hiển thị chính xác số phiên bản lấy từ build/metadata tại góc giao diện.

## Scenarios

### Scenario 1: Phát hành bản cập nhật tính năng mới cho WebApp (Automated Commits)
**Given** Nhà phát triển đã commit các tính năng với tiền tố `feat(auth): add google sign-in` và `fix(map): fix marker offset`.
**When** Chạy lệnh `npm run release:webapp -- --bump=minor`.
**Then** `webapp/package.json` tăng `0.1.0` -> `0.2.0`, file `docs/releases/webapp/v0.2.0.md` được tạo chứa 2 mục thay đổi, `CHANGELOG.md` có mục `[0.2.0] - 2026-08-25`, và `webapp/public/version.json` được cập nhật.

### Scenario 2: Phát hành bản vá lỗi khẩn cấp cho Android (Interactive Mode)
**Given** Bản sửa lỗi không theo format commit chuẩn hoặc cần ghi chú phát hành tùy biến cho kiểm thử viên.
**When** Chạy lệnh `npm run release:android`.
**Then** Script mở CLI hỏi: loại bump (patch), ghi chú thay đổi (ví dụ: "Sửa lỗi crash khi xóa dự án offline"), tự động tăng `versionCode` và `versionName` trong `app/build.gradle.kts`, tạo `docs/releases/android/v1.1.1.md`.

### Scenario 3: Xem trước quá trình phát hành (Dry Run)
**Given** Người dùng muốn kiểm tra xem nội dung changelog và phiên bản tiếp theo sẽ là gì trước khi áp dụng.
**When** Chạy `npm run release:webapp -- --dry-run`.
**Then** Terminal in ra toàn bộ nội dung markdown của release notes mà không có bất kỳ file nào trên ổ đĩa bị thay đổi.

## Technical Notes

- Vị trí script: `scripts/release.mjs` (ES Module chạy trực tiếp bằng Node.js v18+).
- Cấu trúc thư mục release documentation:
  ```
  MAPSUPERVISION-Firebase/
  ├── CHANGELOG.md
  ├── docs/
  │   └── releases/
  │       ├── android/
  │       │   └── v1.1.0.md
  │       └── webapp/
  │           └── v0.2.0.md
  ├── scripts/
  │   └── release.mjs
  ├── webapp/
  │   ├── package.json
  │   └── public/
  │       └── version.json
  └── app/
      └── build.gradle.kts
  ```
- Định dạng file `version.json`:
  ```json
  {
    "version": "0.2.0",
    "platform": "webapp",
    "releaseDate": "2026-08-25",
    "commitHash": "a1b2c3d",
    "highlights": [
      "Tính năng A",
      "Sửa lỗi B"
    ]
  }
  ```

## Task Links

- `@task/74bpkj`: [release-tooling-01] Implement core release automation script (scripts/release.mjs) [done]
- `@task/tusgs7`: [release-tooling-02] Setup release docs directories, root CHANGELOG.md and npm commands [done]
- `@task/yysfgk`: [release-tooling-03] Integrate version display in WebApp UI and Android About info [done]
- `@task/o6r10r`: [release-tooling-04] Add tests and verify release automation workflow [done]

## Open Questions

- *Hiện tại không còn câu hỏi mở nào tồn tại (tất cả 4 nhánh thiết kế D1-D4 đã được thống nhất qua quá trình Socratic dialog).*
