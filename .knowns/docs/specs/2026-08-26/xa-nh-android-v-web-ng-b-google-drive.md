---
id: doc-55dc22c79e4171fd7b402f2f9674c790
title: Xóa ảnh Android và web đồng bộ Google Drive
description: Specification for local Android image deletion, web review warning, and confirmed Google Drive deletion.
createdAt: '2026-08-26T04:51:32.363Z'
updatedAt: '2026-08-26T04:53:38.541Z'
tags:
  - spec
  - approved
  - android
  - webapp
  - firebase
  - google-drive
  - media
  - deletion
  - sync
---

## Overview

Bổ sung luồng xóa ảnh nhất quán giữa ứng dụng Android và webapp MAP Supervision. Android cho phép xóa ảnh cục bộ và tự đồng bộ sự kiện khi có mạng; web hiển thị cảnh báo trên từng ảnh để người dùng kiểm tra và chủ động quyết định có xóa ảnh tương ứng trên Google Drive hay giữ lại. Xóa từ web phải xóa file thực trên Google Drive sau khi người dùng xác nhận.

## Locked Decisions

- D1: Android chỉ xóa ảnh cục bộ. Ảnh trên Google Drive/web và bản ghi Firebase được giữ lại cho đến khi người dùng thao tác trên web.
- D2: Nếu thư mục Android không còn ảnh sau thao tác xóa, chỉ xóa thư mục cục bộ Android; thư mục và dữ liệu ảnh trên web/Drive vẫn giữ nguyên.
- D3: Web hiển thị trạng thái “Đã xóa trên Android” trên từng ảnh, có nút xóa ảnh trên Drive và lựa chọn bỏ qua/giữ lại.
- D4: Nút xóa ảnh trên Drive phải mở hộp thoại xác nhận nêu rõ ảnh sẽ bị xóa khỏi Google Drive; chỉ xác nhận mới thực hiện xóa.
- D5: Android ghi sự kiện xóa vào hàng đợi cục bộ khi offline và tự gửi lên Firebase khi có mạng; web chỉ hiển thị cảnh báo sau khi sự kiện được đồng bộ thành công.

## System Decision Impact

- Impact: existing
- Decision: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
- Acceptance gate: giữ nguyên envelope, phạm vi project/member và cơ chế outbox/tombstone hiện có; kiểm tra quyền và không để bản ghi/ảnh cũ tái xuất hiện do đồng bộ offline.

## Requirements

### Functional Requirements

- FR-1: Android cho phép người dùng xóa một ảnh khỏi thư mục cục bộ.
- FR-2: Sau khi xóa ảnh cục bộ, Android phải tạo sự kiện xóa ảnh với định danh ổn định của ảnh, thư mục, project và thời điểm xóa.
- FR-3: Khi thiết bị offline, sự kiện xóa phải được lưu trong outbox cục bộ và không làm mất thao tác xóa local.
- FR-4: Khi có mạng, Android phải gửi lại các sự kiện outbox chưa đồng bộ lên Firebase theo cơ chế retry hiện có; sự kiện phải được xử lý lặp lại an toàn.
- FR-5: Khi nhận được sự kiện Android đã xóa ảnh, Firebase/web phải lưu trạng thái chờ người dùng xử lý, không xóa file Google Drive và không xóa bản ghi ảnh ngay.
- FR-6: Nếu thư mục local Android không còn ảnh sau thao tác xóa, Android phải xóa thư mục local; không gửi yêu cầu xóa thư mục hoặc ảnh trên web/Drive.
- FR-7: Web phải hiển thị trạng thái “Đã xóa trên Android” trên đúng ảnh tương ứng sau khi sự kiện được đồng bộ.
- FR-8: Web phải cung cấp thao tác bỏ qua/giữ lại ảnh; thao tác này loại bỏ cảnh báo nhưng không xóa file trên Google Drive.
- FR-9: Web phải cung cấp thao tác “Xóa ảnh trên Drive” cho từng ảnh đang có cảnh báo.
- FR-10: Khi người dùng xác nhận xóa trên web, backend phải xóa file thực trên Google Drive và cập nhật trạng thái Firebase để ảnh không tiếp tục hiển thị như đang chờ xử lý.
- FR-11: Nếu xóa Google Drive thất bại, web phải hiển thị lỗi và giữ trạng thái cảnh báo để người dùng thử lại; không hiển thị thành công sớm.
- FR-12: Các thao tác web phải kiểm tra quyền truy cập project và quyền xóa hiện có ở backend/Firestore rules; ẩn nút trên UI không phải là lớp bảo mật duy nhất.

### Non-Functional Requirements

- NFR-1: Đồng bộ offline phải không làm mất hoặc nhân đôi sự kiện khi retry.
- NFR-2: Xóa từ web phải có trạng thái loading/disabled, kết quả thành công/thất bại rõ ràng và hộp thoại xác nhận không thể bị bỏ qua do double-click.
- NFR-3: Mọi thay đổi phải tương thích với schema/envelope đồng bộ ảnh hiện có và không làm ảnh Android còn tồn tại bị đánh dấu sai.
- NFR-4: Không ghi thông tin xác thực Google Drive hoặc secret vào client/bản ghi Firebase.

## Acceptance Criteria

- [ ] AC-1: Xóa một ảnh trên Android làm ảnh biến mất khỏi thư mục local, tạo đúng sự kiện xóa và không xóa ảnh tương ứng trên Drive.
- [ ] AC-2: Khi Android offline, sự kiện vẫn nằm trong outbox; sau khi có mạng, sự kiện được gửi một lần có hiệu lực và web nhận được.
- [ ] AC-3: Khi xóa ảnh cuối cùng của thư mục trên Android, thư mục local bị xóa nhưng thư mục/ảnh tương ứng trên web và Drive vẫn còn.
- [ ] AC-4: Sau khi Firebase nhận sự kiện, web hiển thị nhãn “Đã xóa trên Android” trên đúng ảnh và có nút xóa Drive cùng nút bỏ qua/giữ lại.
- [ ] AC-5: Chọn bỏ qua/giữ lại làm mất cảnh báo, không xóa file Drive và không tạo yêu cầu xóa cloud.
- [ ] AC-6: Chọn “Xóa ảnh trên Drive” yêu cầu xác nhận; hủy xác nhận không thay đổi dữ liệu.
- [ ] AC-7: Xác nhận xóa thành công làm file bị xóa khỏi Google Drive và trạng thái ảnh trên web/Firebase được cập nhật, không còn cảnh báo chờ xử lý.
- [ ] AC-8: Khi Google Drive trả lỗi hoặc mất mạng, web hiển thị lỗi, giữ ảnh ở trạng thái chờ xử lý và cho phép thử lại.
- [ ] AC-9: Người dùng không đủ quyền không thể xóa ảnh Drive dù gọi trực tiếp endpoint; thao tác hợp lệ của người có quyền vẫn hoạt động.
- [ ] AC-10: Test Android/web bao phủ local delete, empty-folder cleanup, outbox retry/idempotency, warning state, confirmation, Drive deletion success/failure và permission denial.

## Scenarios

### Scenario 1: Xóa ảnh Android khi online

**Given** thư mục Android có nhiều ảnh và ảnh đã có định danh đồng bộ
**When** người dùng xóa một ảnh
**Then** ảnh bị xóa khỏi local, sự kiện được ghi và gửi lên Firebase; ảnh trên web/Drive được giữ lại và web hiển thị cảnh báo trên ảnh đó.

### Scenario 2: Xóa ảnh Android khi offline

**Given** Android không có kết nối mạng
**When** người dùng xóa ảnh
**Then** ảnh bị xóa local, sự kiện nằm trong outbox; khi có mạng, sự kiện được retry và web chỉ hiển thị cảnh báo sau khi Firebase nhận được.

### Scenario 3: Xóa ảnh cuối cùng trong thư mục

**Given** thư mục Android chỉ còn một ảnh
**When** người dùng xóa ảnh cuối
**Then** ảnh và thư mục local bị xóa; thư mục/ảnh trên web và Drive không bị xóa tự động.

### Scenario 4: Người dùng giữ lại ảnh trên Drive

**Given** web hiển thị ảnh có trạng thái “Đã xóa trên Android”
**When** người dùng chọn bỏ qua/giữ lại
**Then** cảnh báo biến mất, file Drive vẫn tồn tại và ảnh không bị gửi vào luồng xóa cloud.

### Scenario 5: Người dùng xác nhận xóa Drive

**Given** ảnh có trạng thái “Đã xóa trên Android” và người dùng có quyền
**When** người dùng bấm xóa, xác nhận trong hộp thoại
**Then** backend xóa file thực trên Google Drive và cập nhật trạng thái Firebase; UI hiển thị kết quả thành công.

### Scenario 6: Xóa Drive thất bại

**Given** người dùng đã xác nhận xóa nhưng Google Drive trả lỗi
**When** backend xử lý yêu cầu
**Then** web hiển thị lỗi, giữ trạng thái chờ xử lý và cho phép thử lại; không ghi nhận xóa thành công.

### Scenario 7: Yêu cầu xóa không đủ quyền

**Given** người dùng không có quyền xóa trong project
**When** người dùng gọi thao tác xóa trực tiếp hoặc qua UI
**Then** UI không cho thao tác hoặc backend từ chối, file Drive và trạng thái ảnh không thay đổi.

## Technical Notes

- Ưu tiên mở rộng outbox/event envelope và tombstone/status contract hiện có thay vì tạo cơ chế đồng bộ riêng.
- Sự kiện cần phân biệt “xóa local Android” với “đã xóa cloud” và “người dùng giữ lại”, tránh listener tái tạo ảnh local hoặc tự xóa Drive.
- Định danh ảnh phải là định danh ổn định dùng chung giữa Android, Firebase và Google Drive file ID; không dùng URL render làm khóa.
- Thao tác xóa Drive nên đi qua backend hiện có để bảo vệ credentials và ghi nhận kết quả.
- Tham chiếu: @doc/specs/2026-08-25/google-drive-image-url-display, @doc/specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch, @doc/specs/2026-08-24/media-status-tags-by-object.

- @task-inslpj [xa-nh-android-v-web-ng-b-google-drive-01] Xóa ảnh local Android và đồng bộ outbox — todo
- @task-9h3h5i [xa-nh-android-v-web-ng-b-google-drive-02] Lưu trạng thái ảnh đã xóa và xóa file Google Drive qua backend — todo
- @task-rvw8c7 [xa-nh-android-v-web-ng-b-google-drive-03] Cảnh báo và thao tác giữ hoặc xóa ảnh trên web — todo
- @task-o0iw0p [xa-nh-android-v-web-ng-b-google-drive-04] Kiểm thử tích hợp và xác minh luồng xóa ảnh — todo

## Open Questions

Không còn câu hỏi mở trong phạm vi đã chốt.
