---
id: x85rvu
title: "[google-drive-image-url-display-01] Standardize Google Drive image URLs across web app"
status: in-progress
priority: medium
labels:
  - from-spec
  - spec:google-drive-image-url-display
  - spec-date:2026-08-25
createdAt: '2026-08-25T15:11:30.467Z'
updatedAt: '2026-08-25T15:14:02.629Z'
timeSpent: 0
assignee: '@me'
spec: specs/2026-08-25/google-drive-image-url-display
fulfills:
  - AC-1
  - AC-2
  - AC-3
  - AC-4
  - AC-5
  - AC-6
  - AC-7
order: 10
---
# [google-drive-image-url-display-01] Standardize Google Drive image URLs across web app

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the approved Google Drive image URL contract across all web app image components. Accept raw Google Drive file IDs, build https://lh3.googleusercontent.com/d/{fileId}=w{width}?authuser=0 with default width 1000, preserve non-Google image URLs, and render a shared placeholder for invalid IDs or image load failures. Add focused tests for URL generation, fallback behavior, and integration coverage.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Tạo helper thuần cho URL ảnh Google Drive: nhận raw file ID, mặc định width=1000, hỗ trợ width tùy component, trả undefined cho input rỗng/malformed và giữ nguyên URL không phải Google Drive khi resolve nguồn ảnh.
2. Thay preview blob/API trong SitePhotoPreview bằng nguồn ảnh trực tiếp; card dùng width=600, lightbox dùng width=1000; hiển thị placeholder cho input không hợp lệ và callback onError.
3. Giữ tương thích record hiện có: trích xuất raw file ID từ URL Drive legacy trước khi gọi helper, không thay đổi URL ngoài Drive hoặc contract upload/download backend.
4. Bổ sung test cho URL mặc định/tùy chỉnh, raw ID, URL Drive legacy, URL ngoài Drive, input invalid; cập nhật test component-level nếu cấu trúc test hiện tại hỗ trợ.
5. Chạy test media và full web test suite, TypeScript/build phù hợp; kiểm tra diff và Knowns validation.
6. Đối chiếu AC-1..AC-7 và D1..D7; ghi System Decision Impact: none vì triển khai theo execution rule đã approved, rồi chỉ hoàn tất task sau review/verify.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan prepared under approved kn-flow authorization. Spec Decision Compliance at planning: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. Implementation will be sequential because the shared image preview contract is touched.
<!-- SECTION:NOTES:END -->

