---
id: f6csl2
title: "[media-status-tags-by-object-03] Phân loại và lọc media trong phần Hình ảnh"
status: done
priority: high
labels:
  - from-spec
  - spec:media-status-tags-by-object
  - spec-date:2026-08-24
createdAt: '2026-08-24T11:29:25.490Z'
updatedAt: '2026-08-25T02:44:37.094Z'
completedAt: '2026-08-25T02:39:14.497Z'
timeSpent: 1013
assignee: '@me'
spec: specs/2026-08-24/media-status-tags-by-object
fulfills:
  - AC-1
  - AC-6
  - AC-11
order: 30
---
# [media-status-tags-by-object-03] Phân loại và lọc media trong phần Hình ảnh

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bổ sung thẻ hệ thống, thêm tag tùy chỉnh, chọn/đổi/bỏ status tag và lọc theo đối tượng đang chọn trong màn hình Hình ảnh Android.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Expose system and project-custom status tags in the photo UI.
- [x] #2 Retain the active status-tag choice during the capture/import session.
- [x] #3 Filter the selected object's media by status tag and test the ViewModel behavior.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Hoàn thiện PhotoViewModel và các fake/test cần thiết để quản lý tag hệ thống/tag tùy chỉnh theo project, giữ lựa chọn tag trong phiên chụp/import, đổi/bỏ tag khi review và lọc media của object đang chọn.
2. Kiểm tra PhotoScreen và PhotoPipelineService để các điều khiển UI, đường dẫn capture/import/demo và statusTag được nối đúng với ViewModel; sửa tối thiểu các lỗi compile/wiring.
3. Bổ sung test ViewModel tập trung cho lựa chọn tag trong phiên, tạo tag tùy chỉnh, lọc theo object/tag và cập nhật tag khi review.
4. Chạy test module photo, rà soát diff/review, kiểm tra AC của task và validate SDD trước khi hoàn tất.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Tiếp tục kn-flow: triển khai phần Hình ảnh Android còn dang dở, giữ nguyên các thay đổi liên quan khác trong worktree.
Đã lưu kế hoạch tiếp tục task 03 trong kn-flow; phạm vi chỉ giới hạn UI/ViewModel Hình ảnh và test liên quan.
Implementation complete: PhotoScreen now exposes four system tags and project custom tags, retains active tag across capture/import/demo flows, supports review add/change/remove, and filters the selected object's media by status tag. PhotoPipelineService passes statusTag into image/video capture/import paths. Added PhotoViewModel tests for capture-folder retention, custom-tag exposure, filter toggling, and review persistence. Verification: :photo:testDebugUnitTest passed (27 tests); git diff --check passed for task files. Review: no P1/P2 findings; removed unused moveMediaToStatusFolder helper. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass. System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.

Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass
System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.
<!-- SECTION:NOTES:END -->

