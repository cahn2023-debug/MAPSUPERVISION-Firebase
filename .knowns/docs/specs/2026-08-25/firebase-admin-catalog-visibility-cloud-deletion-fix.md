---
id: doc-eb51714d7804cc9e2dd1e2a5d29ef4a4
title: 'Firebase Admin, Catalog Visibility & Cloud Deletion Fix'
description: Specification for fixing Firebase Admin key parsing, Android catalog project list visibility, and Cloud deletion flow.
createdAt: '2026-08-25T12:34:26.007Z'
updatedAt: '2026-08-25T14:16:41.029Z'
tags:
  - spec
  - approved
---

## Overview

Khắc phục các sự cố liên quan đến đồng bộ và quản trị dự án Firebase, đồng thời chuẩn hóa cách hiển thị danh sách dự án Cloud trên Android:

1. **Lỗi Parse Private Key Firebase Admin (`lib/firebase-admin.ts`)**: Giải mã thất bại chuỗi JSON / PEM service account do ký tự xuống dòng escaped (`\
`), `\\r\
` hoặc định dạng OpenSSL 3.0+, khiến backend không thể khởi tạo Admin Auth/Firestore, làm tê liệt API xóa dự án và migration catalog ngầm.
2. **Lỗi không hiển thị đầy đủ dự án trên Android Catalog (`FirebaseAccessRepositoryImpl.kt`)**: Bộ phân tích Firestore `projectCatalog` / `projects` trên Android quá nghiêm ngặt, loại bỏ hoàn toàn các dự án legacy hoặc tạo từ webapp thiếu `createdByUid`, `projectCode` hoặc `status`.
3. **Lỗi không xóa được dự án Cloud từ Android App (`MapHubScreen.kt`, `ProjectViewModel.kt`, API routes)**: Popup "Quyết định dữ liệu Cloud" gửi request xóa Cloud nhưng Backend chặn bởi điều kiện `REAUTH_REQUIRED` và thiếu phân quyền linh hoạt khi user đã xác thực qua Firebase Auth.
4. **Hiển thị sai định danh dự án trên Android**: Danh sách đang dùng mã/code hoặc ID làm tiêu đề thẻ thay vì tên dự án, gây khó nhận biết dự án.

## Locked Decisions

- D1: Trên danh sách dự án Android, tiêu đề thẻ phải hiển thị tên dự án; mã dự án/code không được hiển thị ở bất kỳ vị trí nào trong thẻ.
- D2: Nếu tên dự án thiếu hoặc rỗng, tiêu đề được để rỗng; không dùng mã dự án/code làm giá trị thay thế.

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements

- **FR-1 (Firebase Admin Key Parsing)**: `lib/firebase-admin.ts` phải chuẩn hóa và format `private_key` hỗ trợ mọi định dạng: `\\r\
`, `\
`, Windows line endings, unescaped/escaped PKCS#8 PEM blocks để `cert(parsed)` khởi tạo thành công Firebase Admin SDK trên server.
- **FR-2 (Android Catalog Parser Fallbacks)**: `parseFirebaseProjectCatalog` và `extractCatalogEntryFromProjectDoc` trong `FirebaseAccessRepositoryImpl.kt` không được trả về `null` khi thiếu `createdByUid` (sử dụng fallback UID của user hiện tại hoặc "legacy-admin"), tự động sinh `projectCode` từ `slug`/`id`, và mặc định `status = ACTIVE` nếu trường status vắng mặt.
- **FR-3 (Cloud Deletion API Auth Relaxing)**: Bỏ điều kiện bắt buộc `auth_time < 300s` trong route `/api/projects/[projectId]/deletion/decision` và `/api/projects/[projectId]/deletion` khi người dùng đã có Firebase ID Token hợp lệ và có quyền Admin / Creator.
- **FR-4 (Android Cloud Deletion Action)**: Khi người dùng bấm nút "Xóa Cloud" trong dialog "Quyết định dữ liệu Cloud" trên Android, ứng dụng gọi `decideProjectCloudDeletion(..., decision = "DELETE")` và `requestProjectDeletion(...)` thành công, cập nhật trạng thái local thành đã xóa và hoàn tất chu trình xóa Cloud.
- **FR-5 (Android Project Name Display)**: Mỗi thẻ trong danh sách dự án Android phải dùng trường tên dự án làm nội dung tiêu đề. Không được dùng `projectCode`, mã code, UUID, slug hoặc ID làm tiêu đề thay thế.
- **FR-6 (Hide Project Code)**: Mã dự án/code và các giá trị định danh kỹ thuật tương đương không được hiển thị trong thẻ dự án Android.
- **FR-7 (Empty Project Name)**: Khi trường tên dự án không tồn tại, `null` hoặc chuỗi rỗng, tiêu đề thẻ phải hiển thị rỗng và vẫn giữ thẻ có thể thao tác mở dự án.

### Non-Functional Requirements

- **NFR-1 (Bảo mật)**: Giữ nguyên kiểm tra phân quyền caller (Admin hoặc Creator của project) trước khi thực hiện xóa Cloud.
- **NFR-2 (Tương thích ngược)**: Các dự án cũ (legacy projects) trên Firestore tiếp tục hoạt động và hiển thị đầy đủ trên Android lẫn Webapp.
- **NFR-3 (Nhất quán UI)**: Quy tắc hiển thị tên/ẩn mã phải áp dụng nhất quán cho mọi nguồn catalog và mọi trạng thái dự án trên màn hình danh sách Android.

## Acceptance Criteria

- [x] **AC-1**: Khởi động Webapp Next.js không còn xuất hiện lỗi `[FirebaseAdmin] Could not parse FIREBASE_SERVICE_ACCOUNT_JSON`; Firebase Admin Auth và Firestore kết nối thành công.
- [x] **AC-2**: Trên Android App, màn hình "Dự án trên Cloud" hiển thị đầy đủ tất cả các dự án có trên Firestore, bao gồm cả dự án legacy.
- [x] **AC-3**: Bấm "Xóa Cloud" trên popup "Quyết định dữ liệu Cloud" ở Android App gửi request thành công, backend xử lý transaction xóa document trên Firestore và cập nhật local project sang DELETED mà không gặp lỗi `REAUTH_REQUIRED`.
- [x] **AC-4**: Tất cả unit test / integration test liên quan đến Catalog Parser, Project Deletion và Firebase Admin đều pass.
- [x] **AC-5**: Với dự án có tên "Migration catalog cần rà soát", tiêu đề thẻ trên Android hiển thị đúng "Migration catalog cần rà soát", không hiển thị `projectCode`, mã code, UUID, slug hoặc ID.
- [x] **AC-6**: Với dự án có tên rỗng hoặc thiếu, tiêu đề thẻ hiển thị rỗng; ứng dụng không thay thế bằng mã dự án/code và thẻ vẫn có thể mở được.

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

**Then** Android gọi endpoint decision với quyết định `DELETE`, backend xác thực ID Token thành công và tiến hành xóa dữ liệu trên Cloud.

### Scenario 4: Hiển thị tên dự án và ẩn mã trên Android

**Given** catalog trả về một dự án có tên "Migration catalog cần rà soát", cùng `projectCode`, UUID hoặc ID

**When** người dùng mở màn hình "Dự án trên Cloud"

**Then** tiêu đề thẻ hiển thị "Migration catalog cần rà soát"; không có mã/code/UUID/slug/ID nào được hiển thị trong thẻ.

### Scenario 5: Tên dự án rỗng

**Given** catalog trả về một dự án không có tên hoặc tên là chuỗi rỗng

**When** người dùng mở màn hình "Dự án trên Cloud"

**Then** tiêu đề thẻ để rỗng, không fallback sang mã dự án/code, và các thao tác mở dự án vẫn hoạt động.

## Technical Notes

- `webapp/lib/firebase-admin.ts`: Chuẩn hóa chuỗi `private_key` bằng cách thay thế `\\r` -> "", `\
` -> xuống dòng, trim khoảng trắng và đảm bảo bọc đúng header/footer PEM.
- `data/.../FirebaseAccessRepositoryImpl.kt`: Nới lỏng điều kiện null-check trong `parseFirebaseProjectCatalog` và `extractCatalogEntryFromProjectDoc` để dùng fallback.
- Android catalog/project-card UI: thay binding tiêu đề hiện tại bằng trường tên dự án; loại bỏ các binding hiển thị mã/code/UUID/slug/ID trong thẻ.
- Khi tên rỗng, truyền chuỗi rỗng trực tiếp đến thành phần hiển thị; không thêm fallback kỹ thuật.
- `webapp/app/api/projects/[projectId]/deletion/decision/route.ts`: Loại bỏ kiểm tra `auth_time` quá 5 phút nhưng vẫn giữ kiểm tra token và quyền.

## Task Links

- wxn6p8: [fac-01] Sửa bộ giải mã Private Key trong webapp/lib/firebase-admin.ts — done
- yums9t: [fac-02] Bổ sung fallback an toàn cho Android Catalog Parser trong FirebaseAccessRepositoryImpl.kt — done
- pykp6v: [fac-03] Nới lỏng auth_time check và hoàn thiện xóa Cloud từ Android App — done
- 7i32xc: [fac-04] Kiểm thử tự động và xác minh toàn diện — done
- vtdoe0: [fac-05] Hiển thị tên dự án và ẩn mã code trên Android — done

## Open Questions

- Không còn câu hỏi mở sau phiên Socratic Grill-Me.
