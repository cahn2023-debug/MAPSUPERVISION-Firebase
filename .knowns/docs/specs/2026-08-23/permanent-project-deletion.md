---
id: doc-f1129579c07034a2a63ea39677e1408d
title: Permanent Project Deletion
description: Revised specification for local-first project deletion, administrator Cloud retention/deletion choice, resumable restore and Cloud cleanup while preserving Google Drive media.
createdAt: '2026-08-23T14:29:38.781Z'
updatedAt: '2026-08-24T09:53:15.092Z'
tags:
  - spec
  - approved
  - project
  - deletion
  - firebase
  - security
---

## Overview

Bổ sung luồng xóa project theo nguyên tắc local-first trên MapSupervision. Hệ thống phân nhánh theo việc project đã từng có dữ liệu được xác nhận trên Cloud hay chưa:

- Project chưa từng upload Cloud: chỉ xóa dữ liệu local trên thiết bị, không tạo yêu cầu xóa Cloud.
- Project đã upload Cloud: xóa dữ liệu local trước, sau đó chờ quản trị viên có quyền trên project quyết định giữ lại hoặc bắt đầu xóa dữ liệu Cloud.

Nếu quản trị viên chọn xóa Cloud, dữ liệu ứng dụng Firebase được xóa theo quy trình nền an toàn, có xác nhận mạnh, audit, checkpoint, retry và xử lý thiết bị offline. File/thư mục media trên Google Drive không bị xóa hoặc thay đổi quyền.

## Locked Decisions

- D1: Project chỉ được coi là “đã upload Cloud” khi đã có dữ liệu project được xác nhận tồn tại trên Cloud. Outbox, upload đang chờ hoặc upload thất bại vẫn thuộc nhánh chưa từng upload.
- D2: Project chưa từng upload Cloud chỉ bị xóa local; hệ thống không tạo deletion request Cloud.
- D3: Với project đã upload Cloud, thiết bị xóa dữ liệu local trước rồi tạo trạng thái chờ quyết định Cloud. Nếu local xóa lỗi một phần, vẫn cho phép quyết định Cloud; lỗi local được ghi nhận và xử lý độc lập.
- D4: Bất kỳ quản trị viên nào có quyền trên project đều được chọn giữ lại hoặc bắt đầu xóa dữ liệu Cloud.
- D5: Chọn giữ lại giữ nguyên dữ liệu và quyền truy cập Cloud, ghi audit, chuyển yêu cầu sang `CLOUD_RETAINED`, không hiển thị lại prompt cho cùng yêu cầu và tự động khôi phục project local trên thiết bị quản trị viên.
- D6: Nếu khôi phục local thất bại hoặc thiết bị offline, chuyển sang `RESTORE_PENDING`, tự động retry khi có mạng và hỗ trợ retry thủ công.
- D7: Chọn bắt đầu xóa Cloud yêu cầu re-authentication gần thời điểm thao tác, nhập chính xác tên hoặc mã project và xác nhận lần cuối; sau đó xóa Cloud chạy nền theo checkpoint/retry.
- D8: Trong thời gian chờ quyết định, dữ liệu Cloud và quyền truy cập hiện tại vẫn giữ nguyên. Chỉ thiết bị đã xóa local mất bản local; prompt quyết định hiển thị idempotent cho quản trị viên đủ quyền.
- D9: Xóa Cloud có thể chạy độc lập ngay cả khi local còn lỗi; tiến trình local tiếp tục retry dọn dẹp và ghi trạng thái riêng.
- D10: Hai quyết định đồng thời dùng cơ chế first-write-wins idempotent. Quyết định ghi nhận thành công đầu tiên thắng; lựa chọn sau chỉ hiển thị trạng thái cuối cùng.
- D11: Không thay đổi file, thư mục hoặc quyền Google Drive khi project application data bị xóa hoặc được giữ lại; media tiếp tục tuân theo quyền hiện có của Google Drive.
- D12: Tombstone và audit tối thiểu được lưu giữ vô thời hạn để hỗ trợ kiểm toán, chống tạo lại project trùng và đồng bộ thiết bị offline.
- D13: Không bắt đầu xóa Cloud khi project đang là active project; phải chuyển sang project khác trước.

## System Decision Impact

- Impact: draft new
- Decision: @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision
- Acceptance gate: linked draft must be revised/evidenced against the local-first branch, Cloud-retain restore path, independent local/Cloud retries, administrator decision authorization, race handling, and media preservation before acceptance.

## Requirements

### Functional Requirements

- FR-1: Hệ thống phải phân loại project theo D1 trước khi bắt đầu xóa; project chưa từng upload không được tạo deletion request Cloud.
- FR-2: Nhánh local-only phải đóng database/package project, xóa dữ liệu local trong phạm vi project, xóa project khỏi catalog local và không tác động đến project khác.
- FR-3: Nhánh đã-upload phải hoàn tất bước xóa local trước khi hiển thị quyết định Cloud; nếu local xóa lỗi một phần, phải hiển thị lỗi nhưng vẫn cho phép quản trị viên quyết định Cloud theo D3.
- FR-4: Chỉ quản trị viên có quyền trên project được nhìn thấy và thực hiện quyết định Cloud; quyền phải được kiểm tra ở server/Firebase rules, không chỉ ở UI.
- FR-5: Trong trạng thái chờ quyết định, Cloud project, dữ liệu và quyền truy cập hiện tại vẫn hoạt động; hệ thống tạo tối đa một prompt idempotent cho mỗi deletion decision request.
- FR-6: Chọn giữ lại phải ghi actor, project identity, timestamp, request ID và kết quả; chuyển sang `CLOUD_RETAINED`; không xóa application data hoặc media; bắt đầu khôi phục local trên thiết bị quản trị viên.
- FR-7: Khôi phục local phải có trạng thái `RESTORE_PENDING`, retry tự động khi online, retry thủ công và không tạo database/package trùng.
- FR-8: Chọn xóa Cloud phải yêu cầu reauthentication gần thời điểm thao tác, nhập chính xác tên hoặc mã project và xác nhận lần cuối. Project active phải bị từ chối.
- FR-9: Khi xác nhận hợp lệ, tạo một deletion request idempotency key duy nhất; trạng thái xóa Cloud chuyển sang `DELETING` và chặn truy cập, ghi, upload, media request và sync Cloud của project.
- FR-10: Worker xóa theo checkpoint toàn bộ application data Firebase trong phạm vi project: business collections, project catalog/detail, members, access requests/permissions và pending sync/outbox records; không gửi lệnh xóa Google Drive.
- FR-11: Worker Cloud phải resume/retry từ checkpoint; retry cùng request không nhân đôi thao tác. Lỗi một phần chuyển `DELETE_FAILED`, giữ project locked, ghi mã lỗi ổn định và cho phép retry.
- FR-12: Tiến trình xóa local và Cloud độc lập sau khi quyết định Cloud được ghi nhận. Lỗi local không được làm mất dữ liệu Cloud ngoài lựa chọn của quản trị viên; lỗi Cloud không được làm hỏng project local khác.
- FR-13: Sau khi Cloud deletion hoàn tất, thiết bị khởi tạo tiếp tục retry local cleanup nếu còn thiếu; khi local cleanup hoàn tất thì đóng database, xóa database/package, xóa khỏi catalog local và chuyển về màn hình quản lý.
- FR-14: Firebase giữ tombstone không chứa business payload và audit tối thiểu gồm project ID/code, actor, timestamp, trạng thái trước/sau, request ID và kết quả; tombstone/audit không cho target user sửa/xóa và được lưu vô thời hạn.
- FR-15: Thiết bị thành viên nhận tombstone ở lần sync/listener tiếp theo. Nếu user đồng ý, xóa local và gỡ khỏi catalog; nếu từ chối, giữ local read-only, cho xem và export/backup, cấm mutation, upload, media request và Cloud sync.
- FR-16: Thiết bị offline không được giả định project đã bị xóa. Khi kết nối lại, phải đồng bộ trạng thái/tombstone trước khi hiển thị prompt và không báo thành công trước khi local commit.
- FR-17: UI hiển thị các trạng thái `LOCAL_DELETE_FAILED`, `RESTORE_PENDING`, `CLOUD_RETAINED`, `DELETING`, `DELETE_FAILED` và kết quả cuối cùng với hành động retry phù hợp.

### Non-Functional Requirements

- NFR-1 Security: authorization, project permission, reauthentication, active-project guard và deletion state phải được kiểm tra ở server/Firebase rules.
- NFR-2 Integrity: phân loại upload, decision request, deletion request, checkpoint, restore và tombstone phải idempotent; không làm mất dữ liệu của project khác.
- NFR-3 Offline safety: lỗi mạng/process không làm hỏng database project khác, không xóa nhầm local data ngoài project đích và không tạo quyết định Cloud giả.
- NFR-4 Auditability: audit tối thiểu không chứa business payload, ghi được first-write-wins outcome và không cho target user chỉnh sửa/xóa.
- NFR-5 Testability: có unit/repository/worker/UI/rules tests cho nhánh chưa upload, local-first, quyền, reauthentication, retry, local failure, restore offline, race, active-project guard, read-only export và media preservation.

## Acceptance Criteria

- [ ] AC-1: Project chưa từng có dữ liệu Cloud được xóa local hoàn toàn trong phạm vi project, không tạo deletion request Cloud và không tác động project khác.
- [ ] AC-2: Project đã upload Cloud bị xóa local trước; trong lúc chờ quyết định, dữ liệu và quyền truy cập Cloud vẫn còn nguyên.
- [ ] AC-3: Chỉ quản trị viên có quyền trên project có thể xem và ghi quyết định Cloud; user không có quyền không thể bypass UI để gọi thành công API/rule.
- [ ] AC-4: Chọn giữ lại ghi audit, chuyển sang `CLOUD_RETAINED`, không xóa Cloud/media và tự động khôi phục local; prompt cùng request không lặp lại.
- [ ] AC-5: Khôi phục lỗi hoặc offline tạo `RESTORE_PENDING`, tự retry khi online và có retry thủ công mà không tạo local database/package trùng.
- [ ] AC-6: Chọn xóa Cloud chỉ bắt đầu sau reauthentication, nhập đúng tên/mã và xác nhận lần cuối; project active bị từ chối.
- [ ] AC-7: Hai quản trị viên chọn khác nhau đồng thời chỉ ghi nhận một quyết định theo first-write-wins; lựa chọn sau thấy trạng thái cuối cùng.
- [ ] AC-8: Xóa local lỗi một phần vẫn cho phép quyết định Cloud; khi chọn xóa Cloud, worker Cloud chạy độc lập và local tiếp tục retry.
- [ ] AC-9: Cloud worker xóa đúng các data groups trong phạm vi project, giữ media Google Drive, ghi checkpoint, retry được sau lỗi và chuyển `DELETE_FAILED` khi không hoàn tất.
- [ ] AC-10: Tombstone/audit tối thiểu tồn tại vô thời hạn, không chứa business data và không bị target user sửa/xóa.
- [ ] AC-11: Thiết bị thành viên offline nhận trạng thái sau reconnect; chọn xóa thì local bị xóa, chọn từ chối thì read-only và export/backup hoạt động, mutation/sync bị từ chối.
- [ ] AC-12: File/thư mục và quyền Google Drive không thay đổi; dữ liệu của project khác không bị tác động và không có lệnh xóa media.

## Scenarios

### Scenario 1: Project chưa từng upload

**Given** project chưa từng có dữ liệu được xác nhận trên Cloud, dù có outbox hoặc upload thất bại  
**When** người dùng xóa project local  
**Then** hệ thống chỉ xóa dữ liệu local trong phạm vi project, không tạo yêu cầu Cloud và giữ nguyên project khác.

### Scenario 2: Project đã upload, local-first và giữ Cloud

**Given** project đã có dữ liệu Cloud và người dùng yêu cầu xóa local  
**When** bước local hoàn tất, quản trị viên chọn giữ lại  
**Then** Cloud vẫn hoạt động với quyền hiện tại, hệ thống ghi `CLOUD_RETAINED`, tự động khôi phục project local và không hiển thị lại prompt cho cùng request.

### Scenario 3: Khôi phục local offline hoặc lỗi

**Given** quản trị viên đã chọn giữ Cloud nhưng thiết bị offline hoặc restore lỗi  
**When** worker restore không hoàn tất  
**Then** hệ thống ghi `RESTORE_PENDING`, tự retry khi có mạng, cho retry thủ công và không tạo bản local trùng.

### Scenario 4: Đang chờ quyết định Cloud

**Given** project đã upload, local đã bị xóa, nhưng chưa có quyết định Cloud  
**When** quản trị viên đủ quyền hoặc thiết bị khác truy cập project  
**Then** Cloud data và quyền hiện tại vẫn giữ nguyên; prompt quyết định được hiển thị idempotent cho quản trị viên đủ quyền.

### Scenario 5: Xóa Cloud sau local failure

**Given** local deletion lỗi một phần và quản trị viên đã reauthenticate, nhập đúng mã project  
**When** quản trị viên xác nhận bắt đầu xóa Cloud  
**Then** Cloud deletion chạy độc lập theo checkpoint/retry, còn local cleanup tiếp tục retry và hai trạng thái được ghi riêng.

### Scenario 6: Hai quản trị viên quyết định đồng thời

**Given** hai quản trị viên có quyền cùng chọn giữ và xóa Cloud  
**When** server nhận hai lựa chọn gần như đồng thời  
**Then** lựa chọn ghi nhận thành công đầu tiên thắng; lựa chọn sau không tạo thao tác thứ hai và hiển thị trạng thái cuối cùng.

### Scenario 7: Thành viên offline khi Cloud bị xóa

**Given** thành viên có bản local nhưng offline khi Cloud deletion hoàn tất  
**When** thiết bị reconnect và nhận tombstone  
**Then** hệ thống đồng bộ tombstone trước, hỏi có xóa local; đồng ý thì xóa, từ chối thì giữ read-only và cho export/backup nhưng cấm edit/sync.

### Scenario 8: Bảo toàn media và project khác

**Given** project có media Google Drive và thiết bị có nhiều project  
**When** application data của project bị giữ lại hoặc bị xóa Cloud  
**Then** file/thư mục và quyền Drive không đổi, project khác không đổi và không phát sinh lệnh xóa media.

## Technical Notes

- Luồng phải phối hợp project-scoped Room database/provider với storage/package lifecycle; không xóa file khi database còn đang mở.
- Có thể tái sử dụng pattern project-scoped database tại @doc/patterns/project-scoped-database.
- Catalog và quyền Firebase phải tuân thủ đặc tả sync/approval hiện hành tại @doc/specs/2026-08-23/firebase-project-sync-approval-approved.
- Cần tách decision request, deletion request, restore state, tombstone và audit để local/Cloud retry độc lập mà không mất bằng chứng.
- Không đưa thao tác xóa Google Drive vào transaction xóa application data.
- Task revision chính: `vy4got` — cập nhật đặc tả local-first và quyết định Cloud.

## Task Links

- vy4got: [permanent-project-deletion-revision] — done
- iixn7n: [permanent-project-deletion-06] — done
- y5uqki: [permanent-project-deletion-07] — done
- 930kkg: [permanent-project-deletion-08] — done
- t9fie1: [permanent-project-deletion-09] — done
- avrsg3: [permanent-project-deletion-10] — done

Previous cloud-first baseline tasks remain linked in Knowns history as completed implementation work; the revision wave above supersedes their execution contract.

## Open Questions

Không còn câu hỏi mở trong phạm vi lifecycle của đặc tả này.
