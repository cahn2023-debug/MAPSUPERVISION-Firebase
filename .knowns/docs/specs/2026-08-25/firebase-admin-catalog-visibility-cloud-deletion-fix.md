---
id: doc-eb51714d7804cc9e2dd1e2a5d29ef4a4
title: 'Firebase Admin, Catalog Visibility & Cloud Deletion Fix'
description: Specification for fixing Firebase Admin key parsing, Android catalog project list visibility, and Cloud deletion flow.
createdAt: '2026-08-25T12:34:26.007Z'
updatedAt: '2026-08-25T13:24:46.086Z'
tags:
  - spec
  - firebase
  - android
  - draft
  - review-required
---

## Overview

Khắc phục triệt để 3 sự cố liên quan đến đồng bộ và quản trị dự án Firebase:
1. **Lỗi Parse Private Key Firebase Admin (`lib/firebase-admin.ts`)**: Giải mã thất bại chuỗi JSON / PEM service account do ký tự xuống dòng escaped (`\
`), `\r
` hoặc định dạng OpenSSL 3.0+, khiến backend không thể khởi tạo Admin Auth/Firestore, làm tê liệt API xóa dự án và migration catalog ngầm.
2. **Lỗi không hiển thị đầy đủ dự án trên Android Catalog (`FirebaseAccessRepositoryImpl.kt`)**: Bộ phân tích Firestore `projectCatalog` / `projects` trên Android quá nghiêm ngặt, loại bỏ hoàn toàn các dự án legacy hoặc tạo từ webapp thiếu `createdByUid`, `projectCode` hoặc `status`.
3. **Lỗi không xóa được dự án Cloud từ Android App (`MapHubScreen.kt`, `ProjectViewModel.kt`, API routes)**: Popup "Quyết định dữ liệu Cloud" gửi request xóa Cloud nhưng Backend chặn bởi điều kiện `REAUTH_REQUIRED` (`auth_time < 300s`) và thiếu phân quyền linh hoạt khi user đã xác thực qua Firebase Auth.

- D1: Đồng bộ giải pháp cho cả 3 điểm: (1) Sửa bộ giải mã Private Key trong firebase-admin.ts, (2) Bổ sung fallback an toàn cho Android Catalog Parser để hiển thị toàn bộ dự án legacy/webapp, và (3) Xử lý xóa Cloud trơn tru từ Android.
- D2: Catalog parser trên Android tự động dùng fallback an toàn (mã dự án từ slug/id, status mặc định ACTIVE, creator fallback từ admin session hoặc project data) để hiển thị ngay lập tức toàn bộ dự án mà không bị crash hay ẩn mất.
- D3: Nới lỏng ràng buộc ở Backend (webapp/app/api/projects/[projectId]/deletion/decision/route.ts & deletion/route.ts): Chỉ cần Firebase ID Token còn hạn và caller là Admin/Creator là cho phép xóa Cloud mà không bắt buộc auth_time < 300s. Android "Xóa Cloud" gửi request trực tiếp bằng Firebase ID Token hiện tại.

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements
- **FR-1 (Firebase Admin Key Parsing)**: `lib/firebase-admin.ts` phải chuẩn hóa và format `private_key` hỗ trợ mọi định dạng: `\r
`, `\
`, Windows line endings, unescaped/escaped PKCS#8 PEM blocks để `cert(parsed)` khởi tạo thành công Firebase Admin SDK trên server.
- **FR-2 (Android Catalog Parser Fallbacks)**: `parseFirebaseProjectCatalog` và `extractCatalogEntryFromProjectDoc` trong `FirebaseAccessRepositoryImpl.kt` không được trả về `null` khi thiếu `createdByUid` (sử dụng fallback UID của user hiện tại hoặc "legacy-admin"), tự động sinh `projectCode` từ `slug`/`id`, và mặc định `status = ACTIVE` nếu trường status vắng mặt.
- **FR-3 (Cloud Deletion API Auth Relaxing)**: Bỏ điều kiện bắt buộc `auth_time < 300s` trong route `/api/projects/[projectId]/deletion/decision` và `/api/projects/[projectId]/deletion` khi người dùng đã có Firebase ID Token hợp lệ và có quyền Admin / Creator.
- **FR-4 (Android Cloud Deletion Action)**: Khi người dùng bấm nút "Xóa Cloud" trong dialog "Quyết định dữ liệu Cloud" trên Android, ứng dụng gọi `decideProjectCloudDeletion(..., decision = "DELETE")` và `requestProjectDeletion(...)` thành công, cập nhật trạng thái local thành đã xóa và hoàn tất chu trình xóa Cloud.

### Non-Functional Requirements
- **NFR-1 (Bảo mật)**: Giữ nguyên kiểm tra phân quyền caller (Admin hoặc Creator của project) trước khi thực hiện xóa Cloud.
- **NFR-2 (Tương thích ngược)**: Các dự án cũ (legacy projects) trên Firestore tiếp tục hoạt động và hiển thị đầy đủ trên cả Android lẫn Webapp.

- [x] **AC-1**: Khởi động Webapp Next.js không còn xuất hiện lỗi `[FirebaseAdmin] Could not parse FIREBASE_SERVICE_ACCOUNT_JSON: Error: Failed to parse private key`. Firebase Admin Auth và Firestore kết nối thành công.
- [x] **AC-2**: Trên Android App, màn hình "Dự án trên Cloud" hiển thị đầy đủ tất cả các dự án có trên Firestore (bao gồm "nha thau", "Dự án 269 - 2026", "123", "165-2026").
- [x] **AC-3**: Bấm "Xóa Cloud" trên popup "Quyết định dữ liệu Cloud" ở Android App gửi request thành công, backend xử lý transaction xóa document trên Firestore và cập nhật local project sang DELETED mà không gặp lỗi `REAUTH_REQUIRED`.
- [x] **AC-4**: Tất cả unit test / integration test liên quan đến Catalog Parser, Project Deletion và Firebase Admin đều pass.

## Scenarios

### Scenario 1: Khởi động Webapp và nạp Firebase Admin Credentials
**Given** file `.env.local` hoặc biến môi trường chứa `FIREBASE_SERVICE_ACCOUNT_JSON` với private key có dấu `\
` hoặc CRLF
**When** Webapp Next.js khởi động và chạy `instrumentation.ts` / API routes
**Then** `readCredential()` parse key thành công, `initializeApp` không ném lỗi và kết nối Firestore Admin bình thường.

### Scenario 2: Hiển thị danh mục dự án Cloud trên Android
**Given** Firestore chứa các document `projectCatalog` hoặc `projects` cũ bị thiếu `createdByUid`
**When** Android App mở màn hình "Dự án trên Cloud" hoặc gọi `refreshProjectCatalog()`
**Then** Android Catalog Parser áp dụng fallback, trả về danh sách đầy đủ toàn bộ dự án để người dùng chọn và mở.

### Scenario 3: Xóa dự án Cloud từ Android App
**Given** Một project đã bị xóa local và hiển thị dialog "Quyết định dữ liệu Cloud"
**When** Người dùng chọn "Xóa Cloud"
**Then** Android gọi endpoint decision với quyết định `DELETE`, backend xác thực ID Token thành công (không đòi hỏi auth_time < 5 phút) và tiến hành xóa dữ liệu trên Cloud.

## Technical Notes

- `webapp/lib/firebase-admin.ts`: Chuẩn hóa chuỗi `private_key` bằng cách thay thế `\\r` -> `""`, `\
` -> `
`, trim khoảng trắng và đảm bảo bọc đúng header `-----BEGIN PRIVATE KEY-----` / footer `-----END PRIVATE KEY-----`.
- `data/.../FirebaseAccessRepositoryImpl.kt`: Nới lỏng điều kiện null-check trong `parseFirebaseProjectCatalog` và `extractCatalogEntryFromProjectDoc` để dùng fallback.
- `webapp/app/api/projects/[projectId]/deletion/decision/route.ts`: Loại bỏ `Math.floor(Date.now() / 1000) - authTime > 300` check.

(Sẽ được tạo và liên kết sau khi spec được phê duyệt qua `/kn-plan --from @doc/specs/2026-08-25/firebase-admin-catalog-visibility-cloud-deletion-fix`)

## Open Questions

(Không còn câu hỏi mở sau phiên Socratic Grill-Me)
