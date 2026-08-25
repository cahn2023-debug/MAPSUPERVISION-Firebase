---
id: x85rvu
title: "[google-drive-image-url-display-01] Standardize Google Drive image URLs across web app"
status: done
priority: medium
labels:
  - from-spec
  - spec:google-drive-image-url-display
  - spec-date:2026-08-25
createdAt: '2026-08-25T15:11:30.467Z'
updatedAt: '2026-08-25T15:21:48.501Z'
completedAt: '2026-08-25T15:20:07.638Z'
timeSpent: 478
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
- [x] #1 URL helper returns https://lh3.googleusercontent.com/d/1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB=w1000?authuser=0 for the sample raw file ID with default width.
- [x] #2 URL helper uses the component-provided width, e.g. width=600 produces =w600?authuser=0.
- [x] #3 Existing photo card and lightbox render through the shared image source helper using width 600 and 1000 respectively.
- [x] #4 Empty or malformed image input yields a placeholder without creating a Google image URL.
- [x] #5 An image load error switches the preview component to the placeholder.
- [x] #6 Non-Google image URLs remain unchanged.
- [x] #7 Focused image helper tests pass and the web app build/type checks pass.
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
Implementation complete: added client-safe Google Drive image URL helpers, direct lh3.googleusercontent.com rendering for thumbnail/lightbox, legacy Drive URL ID extraction, non-Google URL preservation, invalid-source placeholder, and image onError placeholder fallback. Review PASS: no P1/P2/P3 findings in scoped feature files; artifacts are substantive and wired in the current web app. Verification: test:media 21/21 pass; full web test 49 pass, 1 emulator-gated skip; npx tsc --noEmit pass; npm run build pass; git diff --check pass with line-ending warnings only. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — implementation follows the approved spec execution rule and introduces no additional durable project guidance.
Added task-level acceptance criteria mapped to spec AC-1..AC-7; all are verified by the implementation and test/build evidence above.
Task completed after implementation, review, targeted/full verification, task-level AC verification, and clean entity validation.


Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — implementation follows the approved Google Drive image URL spec and introduces no additional durable project guidance.
Post-review formatting check: npm run test:media 21/21 pass; npx tsc --noEmit pass; git diff --check pass with only existing line-ending warnings.
<!-- SECTION:NOTES:END -->

