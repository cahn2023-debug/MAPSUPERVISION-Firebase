---
id: inslpj
title: "[xa-nh-android-v-web-ng-b-google-drive-01] Xóa ảnh local Android và đồng bộ outbox"
status: done
priority: high
labels:
  - from-spec
  - spec:xa-nh-android-v-web-ng-b-google-drive
  - spec-date:2026-08-26
createdAt: '2026-08-26T04:53:29.737Z'
updatedAt: '2026-08-26T05:12:39.240Z'
completedAt: '2026-08-26T05:12:39.240Z'
timeSpent: 876
assignee: '@me'
spec: specs/2026-08-26/xa-nh-android-v-web-ng-b-google-drive
fulfills:
  - AC-1
  - AC-2
  - AC-3
order: 10
---
# [xa-nh-android-v-web-ng-b-google-drive-01] Xóa ảnh local Android và đồng bộ outbox

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Triển khai xóa ảnh cục bộ Android, xóa thư mục local rỗng và ghi/retry sự kiện xóa qua outbox mà không xóa dữ liệu web/Drive.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Ảnh bị xóa khỏi local database/file path và không còn trong danh sách ảnh đang hoạt động.
- [x] #2 Xóa ảnh cuối cùng làm thư mục media local rỗng được xóa, nhưng không tạo thao tác xóa Firebase/Google Drive.
- [x] #3 Sự kiện xóa chứa projectId, photoId, folder/object identity và timestamp, được ghi vào outbox khi offline và retry idempotent khi online.
- [x] #4 Ảnh Android còn lại không bị đánh dấu xóa hoặc tái tạo do sync.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Khảo sát PhotoRepository/PhotoViewModel, SitePhotoDao, storage manager và DomainEvent outbox để tái sử dụng contract hiện có; D1-D5=pass.
2. Thêm thao tác xóa ảnh cục bộ tại repository/use-case/UI, xóa file/thumbnail và thư mục rỗng, chỉ giữ tombstone/event cần cho đồng bộ.
3. Mở rộng domain event/outbox payload cho photo deletion với định danh ổn định, project/folder và timestamp; giữ retry/idempotency của dispatcher.
4. Bổ sung unit tests cho xóa một ảnh, xóa ảnh cuối/thư mục rỗng, offline outbox và không xóa cloud.
5. Chạy test module Android/data/photo, diagnostics và Knowns validation; ghi Spec Decision Compliance D1-D5.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented/verified local tombstone delete, media file and empty-folder cleanup, PhotoDeleted outbox payload, and Android UI action. System Decision Impact: none — uses the existing local-first/project-scoped sync and outbox guidance. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass.
Verification: :data:compileDebugKotlin and :photo:compileDebugKotlin pass; targeted Android tests pass. No P1/P2 review finding for this scope.
<!-- SECTION:NOTES:END -->

