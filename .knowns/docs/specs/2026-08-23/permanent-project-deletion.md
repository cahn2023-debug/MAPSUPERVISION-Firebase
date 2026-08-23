---
id: doc-f1129579c07034a2a63ea39677e1408d
title: Permanent Project Deletion
description: Specification for admin-only permanent deletion of project application data across Firebase and local devices while preserving Google Drive media.
createdAt: '2026-08-23T14:29:38.781Z'
updatedAt: '2026-08-23T14:29:38.781Z'
tags:
  - spec
  - draft
  - project
  - deletion
  - firebase
  - security
---

## Overview

Bổ sung luồng xóa project vĩnh viễn trên MapSupervision. Admin hợp lệ có thể xóa dữ liệu ứng dụng của project trên Firebase và thiết bị local theo quy trình nền an toàn, có xác nhận mạnh, audit tối thiểu, retry và xử lý thiết bị offline. Ảnh/video nằm trên Google Drive không bị xóa.

## Locked Decisions

- D1: Chỉ admin tạo project hoặc super-admin được xóa vĩnh viễn dữ liệu project local và Firebase. Không được xóa project đang hoạt động; phải chuyển project trước. Ảnh/video Google Drive được giữ lại. Admin phải xác thực lại và nhập chính xác tên hoặc mã project.
- D2: Khi thiết bị thành viên nhận trạng thái project đã bị xóa sau lần kết nối lại, hệ thống hỏi có xóa bản local không. Đồng ý thì xóa và gỡ khỏi danh sách; từ chối thì giữ bản local chỉ đọc, cho xem và xuất/backup, không chỉnh sửa hoặc đồng bộ.
- D3: Sau xác nhận, project chuyển sang `DELETING` và bị chặn truy cập ngay. Xóa Firebase chạy nền, có checkpoint/retry. Lỗi một phần chuyển `DELETE_FAILED`, vẫn khóa project và cho retry từ bước chưa hoàn tất. Chỉ xóa local của admin khởi tạo sau khi Firebase hoàn tất. Xóa toàn bộ dữ liệu nghiệp vụ, catalog chi tiết, thành viên, yêu cầu/quyền truy cập và hàng đợi đồng bộ; giữ tombstone và audit tối thiểu.
- D4: Xóa yêu cầu phải idempotent. Hai admin thao tác đồng thời chỉ tạo một yêu cầu; thao tác sau hiển thị trạng thái đang xử lý, không chạy song song hoặc tạo bản ghi trùng. Nếu còn dữ liệu local chưa đồng bộ, hiển thị cảnh báo và yêu cầu xác nhận thêm.

## System Decision Impact

- Impact: draft new
- Decision: @decision/20260823-2129-permanent-project-deletion-lifecycle
- Acceptance gate: linked draft must be reviewed and accepted with evidence from authorization, deletion worker, local database lifecycle, offline-device, and audit tests before the system decision becomes current.

## Requirements

### Functional Requirements

- FR-1: UI chỉ hiển thị thao tác xóa vĩnh viễn cho admin tạo project hoặc super-admin; quyền phải được thực thi ở server/Firebase rules, không chỉ ở UI.
- FR-2: Không cho bắt đầu xóa project đang là active project; yêu cầu chuyển sang project khác trước.
- FR-3: Luồng xác nhận yêu cầu reauthentication gần thời điểm thao tác, nhập chính xác project name hoặc project code, và xác nhận riêng nếu còn outbox/upload chưa đồng bộ.
- FR-4: Khi xác nhận hợp lệ, tạo một deletion request idempotency key duy nhất, ghi actor/admin, project identity, requestedAt và trạng thái `DELETING`; mọi truy cập, ghi, upload và sync của project bị chặn ngay.
- FR-5: Worker xóa theo checkpoint toàn bộ application data Firebase của project: business collections, project catalog/detail, project members, access requests/permissions và pending sync/outbox records; không xóa Google Drive media.
- FR-6: Worker có thể resume/retry từ checkpoint; retry cùng request không nhân đôi thao tác. Lỗi một phần phải để lại `DELETE_FAILED`, lý do có mã ổn định và hành động retry cho admin.
- FR-7: Chỉ sau khi cloud deletion hoàn tất, thiết bị admin khởi tạo mới đóng database project, xóa database/package local, xóa project khỏi catalog local và chuyển về màn hình quản lý.
- FR-8: Firebase giữ tombstone không chứa dữ liệu nghiệp vụ và audit tối thiểu gồm project ID/code, admin actor, timestamp, trạng thái trước/sau, request ID và kết quả; user thường không được sửa/xóa.
- FR-9: Thiết bị thành viên phát hiện tombstone ở lần sync/listener tiếp theo; nếu user đồng ý, xóa local project. Nếu user từ chối, local project chuyển read-only, cho xem và export/backup, cấm mutation, upload, media request và cloud sync.
- FR-10: Thiết bị hoàn toàn offline không được giả định biết trạng thái xóa; khi kết nối lại phải đồng bộ tombstone trước khi hiển thị prompt và không báo xóa thành công trước khi local commit.
- FR-11: UI hiển thị trạng thái `DELETING`, `DELETE_FAILED` và kết quả cuối cùng với thông báo có thể hành động; không tạo yêu cầu trùng khi thao tác lại.
- FR-12: Ảnh/video Google Drive và file/thư mục media liên quan vẫn tồn tại sau project deletion; thao tác xóa project không được gửi lệnh xóa media.

### Non-Functional Requirements

- NFR-1 Security: Authorization, reauthentication, project ownership/super-admin check và deletion state phải được kiểm tra ở server/Firebase rules.
- NFR-2 Integrity: Deletion request, checkpoint và tombstone phải idempotent, không làm mất dữ liệu của project khác.
- NFR-3 Offline safety: lỗi mạng/process không làm hỏng database project khác hoặc mất outbox ngoài phạm vi project đang xóa.
- NFR-4 Auditability: audit tối thiểu không chứa business payload và không cho target user chỉnh sửa/xóa.
- NFR-5 Testability: có unit/repository/worker/UI/rules tests cho quyền, retry, race, active-project guard, offline prompt, read-only export và media preservation.

## Acceptance Criteria

- [ ] AC-1: Admin tạo project hoặc super-admin hợp lệ nhìn thấy nút xóa; user khác không thể gọi thành công API/Cloud Function xóa dù bypass UI.
- [ ] AC-2: Xóa project active bị từ chối với hướng dẫn chuyển project; không tạo deletion request.
- [ ] AC-3: Thiếu reauthentication, nhập sai tên/mã hoặc chưa xác nhận cảnh báo outbox thì không bắt đầu xóa.
- [ ] AC-4: Xác nhận hợp lệ tạo đúng một request và chuyển project sang `DELETING`; thao tác đồng thời/retry không tạo request thứ hai.
- [ ] AC-5: Trong `DELETING`, project không thể mở, ghi, upload, media-read hoặc background-sync.
- [ ] AC-6: Worker xóa đúng các Firebase data groups trong phạm vi project, giữ Google Drive media, ghi checkpoint và có thể tiếp tục sau lỗi.
- [ ] AC-7: Lỗi một phần hiển thị `DELETE_FAILED`, giữ project locked và retry tiếp tục từ checkpoint còn thiếu.
- [ ] AC-8: Chỉ sau cloud success, local admin database/package bị đóng và xóa; nếu cloud chưa success thì local admin data vẫn còn nhưng bị khóa.
- [ ] AC-9: Tombstone/audit tối thiểu còn truy vấn được bởi admin/audit tooling và không chứa business data; project không còn trong catalog hoạt động.
- [ ] AC-10: Thiết bị thành viên nhận tombstone sau reconnect và hiển thị prompt; chọn đồng ý xóa thì local bị xóa, chọn từ chối thì project read-only và export/backup hoạt động, mutation/sync bị từ chối.
- [ ] AC-11: Dữ liệu và media của project khác không bị tác động; media Google Drive của project bị xóa không được gọi.

## Scenarios

### Scenario 1: Admin xóa thành công

**Given** admin tạo project hoặc super-admin đã reauthenticate, project không active, và đã nhập đúng project code  
**When** admin xác nhận xóa và xác nhận cảnh báo outbox nếu có  
**Then** project chuyển `DELETING`, bị khóa, worker xóa application data Firebase theo checkpoint, giữ Google Drive media, ghi tombstone/audit, rồi xóa local admin database sau cloud success.

### Scenario 2: Từ chối vì project đang active

**Given** project đang là active project  
**When** admin chọn xóa  
**Then** hệ thống từ chối, không tạo request và hướng dẫn chuyển project.

### Scenario 3: Worker lỗi và retry

**Given** project đang `DELETING` và worker mất mạng sau một số checkpoint  
**When** retry được kích hoạt  
**Then** project chuyển `DELETE_FAILED` hoặc giữ trạng thái retryable, vẫn bị khóa, và lần chạy sau tiếp tục từ checkpoint chưa hoàn tất mà không xóa trùng.

### Scenario 4: Thành viên offline sau khi cloud đã xóa

**Given** thành viên có bản local nhưng thiết bị offline tại thời điểm admin xóa  
**When** thiết bị kết nối lại và nhận tombstone, sau đó user mở project  
**Then** hệ thống hỏi có xóa local không; đồng ý thì xóa, từ chối thì giữ read-only và cho export/backup nhưng cấm edit/sync.

### Scenario 5: Hai admin thao tác đồng thời

**Given** hai admin hợp lệ cùng gửi yêu cầu cho một project  
**When** server nhận các request gần như đồng thời  
**Then** chỉ một request được tạo; request còn lại trả trạng thái đang xử lý và không chạy worker song song.

### Scenario 6: Bảo toàn media và project khác

**Given** project có media Google Drive và thiết bị có nhiều project  
**When** project bị xóa vĩnh viễn  
**Then** file/thư mục media vẫn tồn tại, dữ liệu project khác không đổi, và mọi lệnh xóa media đều không được phát sinh.

## Technical Notes

- Luồng phải phối hợp project-scoped Room database/provider với storage/package lifecycle; không xóa file khi database còn đang mở.
- Có thể tái sử dụng pattern project-scoped database tại @doc/patterns/project-scoped-database.
- Catalog và quyền Firebase phải tuân thủ đặc tả sync/approval hiện hành tại @doc/specs/2026-08-23/firebase-project-sync-approval-approved.
- Cần phân biệt trạng thái deletion request, tombstone và audit để retry không làm mất bằng chứng.
- Không đưa thao tác xóa Google Drive vào transaction xóa application data.

## Task Links

Chưa tạo task; chỉ tạo sau khi spec được duyệt.

## Open Questions

- [ ] Chính sách truy cập/lưu giữ lâu dài đối với media Google Drive mồ côi sau khi project application data đã bị xóa là gì?
- [ ] Tombstone nên được giữ vô thời hạn hay có thời hạn lưu trữ theo chính sách audit?
