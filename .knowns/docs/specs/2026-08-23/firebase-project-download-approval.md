---
id: doc-ab0e87ebf1bff31d721ff635fd59110f
title: Firebase Project Download Approval
description: Specification for Firebase project discovery, admin approval, scoped download, merge, and background synchronization.
createdAt: '2026-08-23T10:01:24.045Z'
updatedAt: '2026-08-23T10:01:24.045Z'
tags:
  - spec
  - draft
  - firebase
  - sync
  - approval
---

# Firebase Project Download Approval

## Overview

Đặc tả luồng khám phá dự án trên Firebase, gửi yêu cầu tải, phê duyệt bởi admin và đồng bộ dự án về thiết bị Android. Mục tiêu là cho phép mọi người dùng đã đăng nhập nhìn thấy danh mục dự án an toàn, nhưng chỉ đọc hoặc tải dữ liệu chi tiết sau khi admin cấp quyền.

Phạm vi gồm:

- Danh mục dự án Firebase dành cho người dùng đã đăng nhập.
- Yêu cầu quyền tải theo từng người dùng–dự án.
- Phê duyệt, từ chối và thu hồi quyền trên cả web và Android.
- Phạm vi quyền theo nhóm dữ liệu và nhà thầu.
- Tải dữ liệu nghiệp vụ, liên kết media Google Drive, hợp nhất local và đồng bộ nền.
- Kiểm soát truy cập, tính toàn vẹn, khả năng tiếp tục sau gián đoạn và nhật ký quản trị.

Ngoài phạm vi:

- Cho phép người chưa đăng nhập xem danh mục.
- Sao chép toàn bộ ảnh/video từ Google Drive xuống thiết bị.
- Thay thế mô hình local-first, project-scoped Room database hoặc transactional outbox hiện có.
- Thay đổi nơi lưu trữ media khỏi Google Drive.

Tài liệu liên quan:

- @doc/guides/firebase-sync
- @doc/patterns/project-scoped-database
- @doc/specs/2026-08-22/data-architecture-specification

## Locked Decisions

- D1: Sau đăng nhập, mọi người dùng đều thấy danh mục dự án Firebase gồm dự án đang hoạt động và đã lưu trữ. Trước phê duyệt chỉ hiển thị tên dự án, mã dự án, ngày cập nhật và nhãn trạng thái; không được đọc hoặc tải dữ liệu chi tiết.
- D2: Quyền tải được duyệt một lần cho từng cặp người dùng–dự án và tiếp tục có hiệu lực cho các lần tải/cập nhật sau. Mọi admin toàn hệ thống có thể phê duyệt, từ chối hoặc thu hồi quyền trên cả web và Android; hai giao diện dùng chung trạng thái Firebase.
- D3: Sau phê duyệt, ứng dụng tải các nhóm dữ liệu do admin chọn và có thể giới hạn thêm theo nhà thầu. Ảnh/video vẫn nằm trên Google Drive; local chỉ lưu liên kết. Người dùng được cấp quyền tải bất kỳ nhóm dữ liệu nào của dự án sẽ được xem toàn bộ media của dự án đó.
- D4: Nếu dự án đã tồn tại trên thiết bị, dữ liệu cloud được hợp nhất vào dự án local. Các thay đổi local chưa đồng bộ được ưu tiên khi xung đột. Quá trình tải có thể tiếp tục sau gián đoạn và chỉ cập nhật dữ liệu local khi gói tải hoàn chỉnh, hợp lệ. Sau lần tải đầu, ứng dụng tự động đồng bộ nền khi có mạng và quyền truy cập còn hiệu lực.

## System Decision Impact

- Impact: existing
- Decision: @decision/20260822-2334-firebase-confined-to-data-only
- Acceptance gate: Việc triển khai phải cung cấp bằng chứng rằng Android chỉ truy cập Firebase qua interface thuộc domain và implementation thuộc :data; linked draft chỉ được chấp nhận sau khi task triển khai, kiểm thử boundary và security rules hoàn tất.

## Actors and States

### Actors

- Authenticated user: xem danh mục, gửi yêu cầu, theo dõi trạng thái, tải và đồng bộ dữ liệu trong phạm vi được cấp.
- Global admin: xem mọi yêu cầu; phê duyệt, từ chối hoặc thu hồi quyền trên web và Android.
- Sync worker: tải, xác minh, hợp nhất dữ liệu cloud; tiếp tục transactional outbox cho thay đổi local có quyền ghi.
- Firebase/Google Drive access layer: thực thi quyền phía server/rules, không tin cậy kiểm tra phía UI.

### Access states

Mỗi cặp userId–projectId có đúng một trạng thái hiệu lực:

- NOT_REQUESTED
- PENDING
- APPROVED
- REJECTED
- REVOKED

Bản ghi APPROVED phải chứa tối thiểu userId, projectId, allowedDataGroups, contractorScope, allowedContractors, approvedBy, approvedAt và updatedAt. Mọi chuyển trạng thái bởi admin phải lưu actor, thời điểm và trạng thái trước/sau.

## Requirements

### Functional Requirements

- FR-1: Chỉ người dùng đã xác thực Firebase mới được đọc danh mục dự án hoặc tạo yêu cầu tải.
- FR-2: Sau đăng nhập thành công, ứng dụng Android tải danh mục gồm cả dự án ACTIVE và ARCHIVED.
- FR-3: Mỗi mục danh mục trước phê duyệt chỉ trả về projectName, projectCode, updatedAt và status. Không trả về mô tả, thành viên, nhà thầu, tiến độ, cấu trúc dữ liệu, media URL hoặc dữ liệu nghiệp vụ.
- FR-4: Danh mục an toàn phải được tách khỏi tài liệu dự án chi tiết hoặc được bảo vệ bằng cơ chế tương đương có thể chứng minh không rò rỉ field ngoài allowlist. Không mở quyền đọc tài liệu dự án đầy đủ chỉ để dựng danh sách.
- FR-5: Người dùng có thể gửi một yêu cầu cho dự án ở trạng thái NOT_REQUESTED. Gửi lặp trong khi PENDING không tạo bản ghi trùng.
- FR-6: Android hiển thị trạng thái NOT_REQUESTED, PENDING, APPROVED, REJECTED hoặc REVOKED cho từng dự án và chỉ bật thao tác tải khi quyền hiệu lực là APPROVED.
- FR-7: Web và Android phải đọc/ghi cùng nguồn trạng thái yêu cầu. Thay đổi của admin trên một nền tảng phải xuất hiện trên nền tảng còn lại sau lần refresh hoặc listener tiếp theo.
- FR-8: Bất kỳ global admin đã xác thực bằng custom claim admin=true đều có thể phê duyệt, từ chối hoặc thu hồi yêu cầu; người dùng thường không thể thực hiện các chuyển trạng thái quản trị.
- FR-9: Khi phê duyệt, admin bắt buộc chọn ít nhất một nhóm dữ liệu. Admin có thể đặt contractorScope là ALL hoặc SCOPED; khi SCOPED phải chọn ít nhất một nhà thầu.
- FR-10: Phê duyệt có hiệu lực lâu dài cho đúng userId–projectId cho đến khi bị thu hồi; tải lại và cập nhật không tạo yêu cầu phê duyệt mới.
- FR-11: Tải dự án chỉ bao gồm các nhóm dữ liệu được cấp. Khi contractorScope=SCOPED, các bản ghi gắn nhà thầu ngoài allowedContractors và dữ liệu phụ thuộc của chúng không được đưa vào gói tải hoặc phản hồi đồng bộ.
- FR-12: Sau khi có bất kỳ phạm vi dữ liệu APPROVED nào trong dự án, người dùng được xem toàn bộ media của dự án. Quyền media phải được xác minh phía server ở mỗi lần truy cập; không biến file hoặc thư mục Google Drive thành public.
- FR-13: Gói tải lưu dữ liệu nghiệp vụ cùng tham chiếu media; không tải trước binary ảnh/video xuống project storage local. Khi người dùng mở media, ứng dụng đọc qua endpoint/proxy đã xác thực.
- FR-14: Mỗi gói tải phải có projectId/projectCode, schemaVersion, syncVersion hoặc mốc cập nhật, phạm vi quyền, danh sách phần dữ liệu và thông tin kiểm tra tính toàn vẹn.
- FR-15: Tải bị gián đoạn phải có thể tiếp tục từ checkpoint hợp lệ. Dữ liệu tạm không được xuất hiện như dữ liệu dự án đã hoàn tất.
- FR-16: Chỉ sau khi toàn bộ gói tải vượt qua kiểm tra schema, project identity, phạm vi quyền và tính toàn vẹn, hệ thống mới áp dụng thay đổi vào project-scoped local database.
- FR-17: Nếu projectCode/projectId đã tồn tại local, hệ thống cập nhật đúng project database thay vì tạo bản sao.
- FR-18: Khi hợp nhất, bản ghi local có thay đổi chưa được transactional outbox xác nhận đồng bộ phải được giữ ưu tiên. Dữ liệu cloud được áp dụng cho các bản ghi không xung đột hoặc đã đồng bộ.
- FR-19: Sau lần tải đầu, worker tự động nhận cập nhật cloud khi có mạng, người dùng còn đăng nhập và quyền vẫn APPROVED. Worker phải áp dụng cùng data-group và contractor scope như lần tải đầu.
- FR-20: Luồng upload local → Firebase hiện có tiếp tục dùng transactional outbox. Việc cho phép người dùng đã được duyệt ghi/upload dữ liệu nào phụ thuộc quyết định tại OQ-1 và phải được enforcement phía server.
- FR-21: Khi quyền chuyển sang REVOKED, mọi lần tải mới, đồng bộ nền và truy cập media tiếp theo phải bị từ chối ngay sau khi client nhận trạng thái hoặc server kiểm tra quyền.
- FR-22: Các thao tác quản trị phải tạo audit record chứa projectId, targetUserId, action, previousState, newState, actorAdminId và timestamp.
- FR-23: Lỗi xác thực, bị từ chối quyền, mất mạng, gói không hợp lệ và xung đột không thể hợp nhất phải có mã lỗi ổn định và thông báo có thể hành động; không được báo thành công khi chưa commit dữ liệu local.

### Non-Functional Requirements

- NFR-1 Security: Firestore rules, Storage rules và server API phải thực thi quyền độc lập với UI. Client không được tự khai admin, tự mở rộng allowedDataGroups hoặc allowedContractors.
- NFR-2 Privacy: Trước phê duyệt, truy vấn catalog không được trả về field ngoài allowlist tại FR-3, kể cả qua lỗi, log hoặc listener.
- NFR-3 Architecture: Firebase Android SDK tiếp tục bị giới hạn trong module :data; ViewModel và feature module chỉ dùng domain interfaces.
- NFR-4 Integrity: Merge và checkpoint phải idempotent; retry cùng syncVersion không tạo bản ghi trùng hoặc áp dụng thay đổi hai lần.
- NFR-5 Offline safety: Mất mạng hoặc process death không làm hỏng project database đang hoạt động và không làm mất outbox local chưa đồng bộ.
- NFR-6 Auditability: Audit record quản trị không được người dùng mục tiêu sửa/xóa và phải truy vấn được theo dự án, người dùng và thời gian.
- NFR-7 Performance: Catalog và hàng đợi yêu cầu admin phải hỗ trợ phân trang; không tải không giới hạn toàn bộ dự án/yêu cầu vào một phản hồi.
- NFR-8 Media security: Media được truyền qua endpoint đã xác thực hoặc URL ngắn hạn; không lưu public Drive URL có thể dùng sau khi quyền bị thu hồi.
- NFR-9 Testability: Firebase Emulator Suite phải bao phủ rules và trạng thái phê duyệt; merge/checkpoint phải kiểm thử được bằng repository/worker tests không phụ thuộc Firebase production.

## Acceptance Criteria

- [ ] AC-1: Với tài khoản thường đã đăng nhập nhưng chưa được duyệt, catalog hiển thị cả ACTIVE và ARCHIVED, mỗi mục chỉ có tên, mã, ngày cập nhật và trạng thái.
- [ ] AC-2: Cùng tài khoản ở AC-1 không thể đọc project document/subcollection chi tiết hoặc media bằng Firebase SDK, REST/API hay URL Drive trực tiếp.
- [ ] AC-3: Gửi yêu cầu lần đầu tạo đúng một trạng thái PENDING; gửi lặp khi PENDING vẫn chỉ tồn tại một yêu cầu hiệu lực.
- [ ] AC-4: Yêu cầu được tạo trên Android xuất hiện cho global admin trên cả Android và web; quyết định trên một nền tảng được phản ánh trên nền tảng còn lại.
- [ ] AC-5: Người không có admin claim không thể phê duyệt, từ chối, thu hồi hoặc sửa allowedDataGroups/allowedContractors.
- [ ] AC-6: Admin không thể phê duyệt khi không có nhóm dữ liệu; contractorScope=SCOPED không thể lưu với danh sách nhà thầu rỗng.
- [ ] AC-7: Sau APPROVED, người dùng tải được đúng nhóm dữ liệu và đúng phạm vi nhà thầu; dữ liệu ngoài phạm vi không xuất hiện trong payload hoặc local database.
- [ ] AC-8: Phê duyệt còn hiệu lực cho lần tải lại và cập nhật sau mà không tạo yêu cầu mới.
- [ ] AC-9: Người dùng APPROVED cho bất kỳ nhóm dữ liệu nào xem được media toàn dự án qua endpoint đã xác thực, nhưng project storage local không chứa bản sao tải trước của binary media.
- [ ] AC-10: Tài khoản PENDING, REJECTED, REVOKED hoặc không có bản ghi quyền đều bị chặn tải dữ liệu chi tiết và truy cập media.
- [ ] AC-11: Khi thiết bị đã có cùng dự án, tải lại cập nhật project database hiện có, không tạo project trùng.
- [ ] AC-12: Với xung đột giữa cloud và bản ghi local còn pending trong outbox, merge giữ bản ghi local/outbox; dữ liệu cloud không xung đột vẫn được áp dụng.
- [ ] AC-13: Khi mất mạng hoặc process bị dừng giữa tải, project database hiện tại không đổi; lần chạy sau tiếp tục từ checkpoint và chỉ commit sau khi gói hợp lệ hoàn toàn.
- [ ] AC-14: Retry cùng checkpoint/syncVersion không tạo bản ghi trùng và cho kết quả dữ liệu giống một lần tải thành công duy nhất.
- [ ] AC-15: Sau tải đầu, cập nhật cloud hợp lệ tự về thiết bị khi có mạng và quyền còn APPROVED.
- [ ] AC-16: Sau REVOKED, download, background sync và media request tiếp theo bị từ chối; audit record ghi đúng admin, người dùng, dự án, trạng thái và thời gian.
- [ ] AC-17: Emulator tests chứng minh catalog allowlist, admin-only transitions, scoped project reads và revoked access đều được rules/API enforcement.
- [ ] AC-18: Kiểm tra dependency/module boundary chứng minh không có Firebase SDK mới ngoài :data ở Android.

## Scenarios

### Scenario 1: Xem catalog và gửi yêu cầu

**Given** người dùng thường đã đăng nhập và chưa có quyền với dự án  
**When** người dùng mở danh sách dự án Firebase và chọn yêu cầu tải  
**Then** ứng dụng chỉ hiển thị metadata D1 và tạo một yêu cầu PENDING duy nhất.

### Scenario 2: Admin phê duyệt trên web, người dùng tải trên Android

**Given** có yêu cầu PENDING  
**When** global admin trên web chọn nhóm dữ liệu, chọn phạm vi nhà thầu và phê duyệt  
**Then** Android nhận trạng thái APPROVED và cho phép tải đúng phạm vi mà không cần yêu cầu lại.

### Scenario 3: Admin xử lý trên Android

**Given** global admin đăng nhập Android  
**When** admin từ chối, phê duyệt hoặc thu hồi một yêu cầu  
**Then** trạng thái dùng chung được cập nhật, web phản ánh quyết định và audit record được tạo.

### Scenario 4: Tải dự án mới với media trên Drive

**Given** người dùng có quyền APPROVED  
**When** tải dự án lần đầu  
**Then** dữ liệu nghiệp vụ được lưu local theo scope, media chỉ lưu tham chiếu và được xem qua endpoint đã xác thực.

### Scenario 5: Hợp nhất dự án đã tồn tại

**Given** cùng dự án đã có trên thiết bị và một số thay đổi local còn pending trong outbox  
**When** cloud có bản mới và đồng bộ chạy  
**Then** thay đổi local pending được giữ, dữ liệu cloud không xung đột được áp dụng và không tạo project trùng.

### Scenario 6: Mất mạng giữa lúc tải

**Given** gói tải chưa hoàn tất  
**When** kết nối mất hoặc process bị đóng  
**Then** dữ liệu dự án đang dùng không bị thay đổi; checkpoint được giữ và lần sau tiếp tục an toàn.

### Scenario 7: Thu hồi quyền

**Given** người dùng từng được APPROVED  
**When** admin thu hồi quyền  
**Then** download, background sync và truy cập media tiếp theo bị từ chối; cách xử lý dữ liệu đã có local tuân theo quyết định OQ-2.

### Scenario 8: Tấn công mở rộng phạm vi

**Given** người dùng APPROVED cho một số data group/contractor  
**When** client sửa request để yêu cầu nhóm hoặc nhà thầu ngoài phạm vi  
**Then** server/rules từ chối hoặc lọc ngoài phạm vi, không dựa vào kiểm tra UI.

## Technical Notes

- Firestore hiện chỉ cho project member đọc project document. Để đáp ứng D1 mà không rò rỉ dữ liệu, ưu tiên một catalog collection/view chỉ chứa allowlist FR-3 thay vì mở quyền đọc collection projects đầy đủ.
- Mô hình quyền hiện có dùng admin custom claim và projectMembers; đặc tả này mở rộng thành request/access record có trạng thái, data-group scope, contractor scope và audit trail.
- Android tiếp tục dùng FirebaseAccessRepository/domain models; Firebase implementation đặt trong :data theo @decision/20260822-2334-firebase-confined-to-data-only.
- Download/merge phải tôn trọng ProjectScopedDatabaseProvider và transactional event outbox hiện có.
- Truy cập media nên đi qua server endpoint xác thực Firebase ID token và quyền dự án; không phát Drive URL công khai lâu dài.
- Cần ánh xạ ổn định giữa allowedDataGroups và các Firestore subcollections/Room entities. Ánh xạ này phải có version để thay đổi schema không tự mở thêm quyền.
- Web và Android phải dùng cùng transition rules; thao tác admin nên có idempotency key hoặc transaction để tránh quyết định cạnh tranh.

## Task Links

Chưa tạo task. Task sẽ được liên kết sau khi spec được phê duyệt và chạy /kn-plan --from @doc/specs/2026-08-23/firebase-project-download-approval.

## Open Questions

- [ ] OQ-1: Phê duyệt tải có đồng thời cấp quyền chỉnh sửa/upload cho các nhóm dữ liệu và nhà thầu đã chọn, hay quyền đọc và quyền ghi phải cấu hình riêng?
- [ ] OQ-2: Khi quyền bị thu hồi, dữ liệu nghiệp vụ đã tải xuống local phải bị xóa, bị khóa/ẩn, hay được giữ chỉ đọc?
- [ ] OQ-3: Sau REJECTED hoặc REVOKED, người dùng được gửi lại yêu cầu ngay, sau một khoảng chờ, hay chỉ khi admin mở lại?
