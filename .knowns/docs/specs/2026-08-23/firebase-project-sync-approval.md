---
id: doc-aeafeb612778d57e9f6bc4b51ea86773
title: Firebase Project Sync Approval
description: Canonical revised specification for Firebase project catalog, admin approval, scoped bidirectional sync, media access, merge, revoke, and re-request.
createdAt: '2026-08-23T10:18:44.698Z'
updatedAt: '2026-08-23T10:18:44.698Z'
tags:
  - spec
  - draft
  - firebase
  - sync
  - approval
---

# Firebase Project Sync Approval

## Overview

Đặc tả luồng khám phá dự án Firebase, yêu cầu quyền, phê duyệt bởi admin, tải dữ liệu về Android và đồng bộ hai chiều theo phạm vi được cấp. Mọi người dùng đã đăng nhập có thể thấy danh mục metadata an toàn; dữ liệu chi tiết chỉ được đọc hoặc ghi sau khi admin phê duyệt.

Tài liệu này là bản nháp hợp nhất các quyết định D1–D7 và thay thế bản nháp chưa hoàn chỉnh @doc/specs/2026-08-23/firebase-project-download-approval cho mọi bước review, lập kế hoạch và triển khai tiếp theo.

Phạm vi:

- Catalog dự án ACTIVE và ARCHIVED cho người dùng đã đăng nhập.
- Yêu cầu, phê duyệt, từ chối, thu hồi và gửi lại yêu cầu.
- Quản trị dùng chung trạng thái trên web và Android.
- Quyền đọc/ghi theo nhóm dữ liệu và nhà thầu.
- Tải dữ liệu nghiệp vụ, tham chiếu media Google Drive, merge local và đồng bộ nền.
- Resume, atomic commit, audit và enforcement phía server.

Ngoài phạm vi:

- Catalog công khai cho người chưa đăng nhập.
- Tải trước toàn bộ ảnh/video từ Google Drive về thiết bị.
- Thay thế project-scoped Room database hoặc transactional outbox hiện có.
- Chuyển media khỏi Google Drive.

Tài liệu nền:

- @doc/guides/firebase-sync
- @doc/patterns/project-scoped-database
- @doc/specs/2026-08-22/data-architecture-specification

## Locked Decisions

- D1: Sau đăng nhập, mọi người dùng thấy danh mục dự án Firebase gồm dự án đang hoạt động và đã lưu trữ. Trước phê duyệt chỉ hiển thị tên dự án, mã dự án, ngày cập nhật và nhãn trạng thái; không đọc hoặc tải dữ liệu chi tiết.
- D2: Quyền được duyệt một lần cho từng cặp người dùng–dự án và có hiệu lực cho các lần tải/cập nhật sau. Mọi admin toàn hệ thống có thể phê duyệt, từ chối hoặc thu hồi trên cả web và Android; hai giao diện dùng chung trạng thái Firebase.
- D3: Admin chọn nhóm dữ liệu và có thể giới hạn thêm theo nhà thầu. Ảnh/video vẫn ở Google Drive; local chỉ lưu liên kết. Người dùng được cấp bất kỳ nhóm dữ liệu nào của dự án được xem toàn bộ media của dự án đó.
- D4: Dự án cloud trùng dự án local được hợp nhất vào database hiện có. Thay đổi local chưa đồng bộ được ưu tiên khi xung đột. Tải có thể tiếp tục sau gián đoạn, chỉ commit khi gói hoàn chỉnh và sau lần đầu sẽ tự đồng bộ nền khi có mạng và còn quyền.
- D5: Phê duyệt đồng thời cấp quyền đọc và chỉnh sửa/upload trong đúng nhóm dữ liệu và phạm vi nhà thầu admin đã chọn.
- D6: Khi bị thu hồi quyền, dữ liệu nghiệp vụ đã tải vẫn ở thiết bị trong chế độ chỉ đọc; tải mới, ghi/upload, đồng bộ nền và truy cập media bị chặn.
- D7: Sau REJECTED hoặc REVOKED, người dùng có thể gửi yêu cầu lại ngay.

## System Decision Impact

- Impact: existing
- Decision: @decision/20260822-2334-firebase-confined-to-data-only
- Acceptance gate: Việc triển khai phải chứng minh Android chỉ truy cập Firebase qua domain interfaces và implementation thuộc :data; linked draft chỉ được chấp nhận sau khi task triển khai, kiểm thử module boundary và security rules hoàn tất.

## Actors and States

### Actors

- Authenticated user: xem catalog, gửi yêu cầu, theo dõi trạng thái, đọc/ghi dữ liệu trong phạm vi APPROVED.
- Global admin: xử lý yêu cầu và cấu hình data/contractor scope trên web hoặc Android.
- Sync worker: tải, xác minh, merge cloud data và đẩy local outbox trong phạm vi quyền.
- Firebase/Drive access layer: thực thi quyền phía server/rules; không tin cậy kiểm tra phía UI.

### Access states

Mỗi cặp userId–projectId có đúng một trạng thái hiệu lực:

- NOT_REQUESTED
- PENDING
- APPROVED
- REJECTED
- REVOKED

APPROVED lưu tối thiểu userId, projectId, allowedDataGroups, contractorScope, allowedContractors, approvedBy, approvedAt và updatedAt. Mọi chuyển trạng thái quản trị lưu actor, thời điểm và trạng thái trước/sau.

REJECTED hoặc REVOKED có thể chuyển lại PENDING khi người dùng gửi yêu cầu mới. Yêu cầu lặp khi đang PENDING phải idempotent.

## Requirements

### Functional Requirements

- FR-1: Chỉ người dùng đã xác thực Firebase mới đọc catalog hoặc tạo yêu cầu.
- FR-2: Sau đăng nhập, Android tải catalog gồm ACTIVE và ARCHIVED.
- FR-3: Mỗi catalog item trước phê duyệt chỉ trả projectName, projectCode, updatedAt và status; không trả dữ liệu nghiệp vụ, thành viên, nhà thầu hoặc media URL.
- FR-4: Catalog phải tách khỏi project document chi tiết hoặc có cơ chế tương đương chứng minh field allowlist; không mở đọc project document đầy đủ để dựng catalog.
- FR-5: NOT_REQUESTED, REJECTED hoặc REVOKED có thể gửi yêu cầu để chuyển thành PENDING ngay. Gửi lặp khi PENDING không tạo bản ghi trùng.
- FR-6: Android hiển thị trạng thái quyền cho từng dự án và chỉ bật tải/chỉnh sửa khi APPROVED.
- FR-7: Web và Android đọc/ghi cùng nguồn trạng thái; thay đổi trên một nền tảng xuất hiện trên nền tảng còn lại sau refresh/listener tiếp theo.
- FR-8: Chỉ tài khoản có custom claim admin=true được phê duyệt, từ chối, thu hồi hoặc sửa phạm vi quyền.
- FR-9: Khi phê duyệt, admin chọn ít nhất một data group. contractorScope là ALL hoặc SCOPED; SCOPED yêu cầu ít nhất một allowedContractor.
- FR-10: APPROVED có hiệu lực cho đúng userId–projectId đến khi bị thu hồi; tải lại/cập nhật không cần duyệt lại.
- FR-11: Mọi read/download chỉ trả allowedDataGroups. Với SCOPED, bản ghi ngoài allowedContractors và dữ liệu phụ thuộc không được xuất hiện trong payload hoặc local database.
- FR-12: Mọi write/upload chỉ được phép trong allowedDataGroups và contractor scope. Client không thể tự mở rộng phạm vi.
- FR-13: Có bất kỳ APPROVED data group nào thì người dùng được xem toàn bộ media của dự án. Server xác minh quyền ở từng request; file/thư mục Drive không public.
- FR-14: Local chỉ lưu business data và media references; binary media được đọc theo nhu cầu qua endpoint/proxy đã xác thực.
- FR-15: Gói tải chứa project identity, schemaVersion, syncVersion/checkpoint, phạm vi quyền, danh sách phần dữ liệu và integrity metadata.
- FR-16: Tải gián đoạn tiếp tục từ checkpoint hợp lệ; dữ liệu tạm không hiển thị như dữ liệu đã hoàn tất.
- FR-17: Chỉ commit vào project-scoped database sau khi toàn bộ gói vượt kiểm tra schema, identity, scope và integrity.
- FR-18: Cùng projectId/projectCode local được cập nhật vào database hiện có, không tạo project trùng.
- FR-19: Bản ghi local còn pending trong transactional outbox được ưu tiên khi xung đột; cloud data không xung đột vẫn được áp dụng.
- FR-20: Sau tải đầu, worker tự nhận cloud updates và đẩy local outbox khi có mạng, session hợp lệ và trạng thái APPROVED; mọi thao tác áp dụng đúng data/contractor scope.
- FR-21: Khi REVOKED, download, write/upload, background sync và media request tiếp theo bị từ chối. Business data đã tải vẫn mở được chỉ đọc và UI phải hiển thị trạng thái bị thu hồi.
- FR-22: Khi REJECTED hoặc REVOKED, thao tác gửi lại yêu cầu chuyển trạng thái về PENDING ngay và không xóa audit history cũ.
- FR-23: Mỗi thao tác admin tạo audit record gồm projectId, targetUserId, action, previousState, newState, actorAdminId và timestamp.
- FR-24: Lỗi auth, permission, network, invalid package và merge conflict có mã ổn định, thông báo có thể hành động; không báo thành công trước local commit.
- FR-25: Sign-out dừng các worker phụ thuộc session và không cho dùng credential cũ để đọc/ghi cloud hoặc media.

### Non-Functional Requirements

- NFR-1 Security: Firestore rules, Storage rules và server API thực thi quyền độc lập với UI.
- NFR-2 Privacy: Catalog không rò field ngoài allowlist qua payload, listener, lỗi hoặc log.
- NFR-3 Architecture: Firebase Android SDK chỉ nằm trong :data; ViewModel/feature dùng domain interfaces.
- NFR-4 Integrity: Merge/checkpoint idempotent; retry cùng syncVersion không nhân đôi hoặc áp dụng hai lần.
- NFR-5 Offline safety: Network/process failure không hỏng database đang dùng hoặc làm mất pending outbox.
- NFR-6 Auditability: User mục tiêu không sửa/xóa audit; admin truy vấn theo project, user và thời gian.
- NFR-7 Performance: Catalog và hàng đợi admin hỗ trợ phân trang, không tải vô hạn trong một response.
- NFR-8 Media security: Dùng authenticated endpoint hoặc short-lived URL; không lưu public Drive URL dùng được sau revoke.
- NFR-9 Testability: Emulator Suite bao phủ rules/state transitions; merge/checkpoint có repository/worker tests không phụ thuộc production.

## Acceptance Criteria

- [ ] AC-1: User đã đăng nhập nhưng chưa được duyệt thấy ACTIVE và ARCHIVED chỉ với tên, mã, ngày cập nhật và trạng thái.
- [ ] AC-2: User ở AC-1 không đọc được project detail/subcollections hoặc media qua SDK, API hay Drive URL.
- [ ] AC-3: Yêu cầu đầu tạo một PENDING; gửi lặp khi PENDING vẫn có một yêu cầu hiệu lực.
- [ ] AC-4: Yêu cầu Android xuất hiện cho admin trên Android và web; quyết định từ một nền tảng phản ánh trên nền tảng còn lại.
- [ ] AC-5: User không có admin claim không thể xử lý yêu cầu hoặc sửa scope.
- [ ] AC-6: Không thể APPROVE khi data groups rỗng hoặc SCOPED có allowedContractors rỗng.
- [ ] AC-7: APPROVED đọc/tải đúng data groups và contractors; dữ liệu ngoài scope không vào payload/local.
- [ ] AC-8: APPROVED ghi/upload thành công trong scope và bị server/rules từ chối ngoài scope.
- [ ] AC-9: Quyền tiếp tục có hiệu lực cho tải/cập nhật sau mà không yêu cầu lại.
- [ ] AC-10: APPROVED cho bất kỳ data group xem được toàn bộ project media qua authenticated endpoint, không tải trước binary.
- [ ] AC-11: PENDING, REJECTED, REVOKED hoặc không có access record bị chặn cloud detail/media.
- [ ] AC-12: Cùng dự án local được cập nhật, không tạo bản sao.
- [ ] AC-13: Merge giữ local pending outbox và áp dụng cloud data không xung đột.
- [ ] AC-14: Mất mạng/process death không đổi database hiện tại; lần sau resume và chỉ commit gói hợp lệ.
- [ ] AC-15: Retry cùng checkpoint/syncVersion không tạo bản ghi trùng.
- [ ] AC-16: Sau lần tải đầu, cloud updates tự về và local authorized changes tự lên khi có mạng.
- [ ] AC-17: Sau REVOKED, local business data còn đọc được nhưng không sửa; download, upload, sync và media đều bị từ chối.
- [ ] AC-18: REJECTED hoặc REVOKED gửi lại ngay chuyển về PENDING, giữ nguyên audit history.
- [ ] AC-19: Audit ghi đúng admin, target user, project, action, previous/new state và timestamp.
- [ ] AC-20: Emulator tests chứng minh catalog allowlist, admin-only transitions, scoped reads/writes, revoke và re-request.
- [ ] AC-21: Module-boundary check chứng minh không có Firebase SDK mới ngoài :data.

## Scenarios

### Scenario 1: Catalog và yêu cầu

**Given** user đã đăng nhập và chưa có quyền  
**When** mở catalog và yêu cầu một dự án  
**Then** chỉ metadata D1 hiển thị và một PENDING được tạo.

### Scenario 2: Admin phê duyệt đa nền tảng

**Given** có PENDING  
**When** global admin trên web hoặc Android chọn data groups/contractors và approve  
**Then** nền tảng còn lại nhận APPROVED và Android cho đọc/ghi đúng scope.

### Scenario 3: Tải dự án và xem media

**Given** user APPROVED  
**When** tải dự án và mở media  
**Then** business data được lưu local theo scope; media được stream qua endpoint xác thực và không tải trước.

### Scenario 4: Merge dự án đã có

**Given** dự án đã tồn tại local và có pending outbox  
**When** cloud có bản mới  
**Then** local pending thắng xung đột, cloud non-conflicting data được áp dụng, không tạo project trùng.

### Scenario 5: Tải bị gián đoạn

**Given** gói chưa hoàn tất  
**When** mất mạng hoặc process dừng  
**Then** database đang dùng không đổi; lần sau resume từ checkpoint và commit nguyên tử.

### Scenario 6: Ghi ngoài phạm vi

**Given** user APPROVED cho một số group/contractor  
**When** client cố đọc hoặc ghi ngoài scope  
**Then** server/rules từ chối hoặc lọc; UI validation không phải lớp bảo vệ duy nhất.

### Scenario 7: Thu hồi quyền

**Given** user từng APPROVED và đã có dữ liệu local  
**When** admin revoke  
**Then** business data local chuyển chỉ đọc; cloud download/upload/sync và media bị chặn.

### Scenario 8: Gửi lại yêu cầu

**Given** access state là REJECTED hoặc REVOKED  
**When** user gửi lại ngay  
**Then** state chuyển PENDING, không tạo duplicate và audit cũ được giữ.

## Technical Notes

- D1 cần catalog collection/view chỉ chứa allowlist thay vì mở read cho project documents đầy đủ.
- Mở rộng mô hình admin claim/projectMembers hiện có thành access record với state, data-group scope, contractor scope và audit trail.
- allowedDataGroups phải ánh xạ có version tới Firestore subcollections và Room entities; schema mới không được tự mở quyền.
- Android tiếp tục dùng FirebaseAccessRepository/domain models; implementation Firebase đặt trong :data.
- Download/merge tôn trọng ProjectScopedDatabaseProvider và transactional outbox.
- Media đi qua endpoint xác thực Firebase ID token và quyền dự án; không phát URL Drive công khai dài hạn.
- Web và Android dùng cùng transition rules; admin action và re-request cần transaction/idempotency.

## Task Links

Chưa tạo task. Sau khi phê duyệt, dùng /kn-plan --from @doc/specs/2026-08-23/firebase-project-sync-approval hoặc /kn-flow @doc/specs/2026-08-23/firebase-project-sync-approval.

## Open Questions

Không còn câu hỏi mở.
