---
id: doc-ed69bd064dc60a78c68a15077a90884c
title: Firebase Project Catalog Recovery
description: Specification for repairing empty Android Firebase project catalogs, backfilling legacy projects, aligning createdByUid rules, and surfacing migration warnings.
createdAt: '2026-08-24T02:30:58.081Z'
updatedAt: '2026-08-24T02:37:59.756Z'
tags:
  - spec
  - draft
  - review-required
  - firebase
  - android
  - migration
  - security
---

## Overview

Khắc phục hồi quy khiến Android hiển thị danh mục Cloud rỗng dù Firebase đã có dự án. Android chỉ đọc projection `/projectCatalog`, trong khi dữ liệu cũ có thể chỉ tồn tại trong `/projects`. Các writer/backfill hiện ghi `createdByUid` nhưng Firestore rule của catalog không cho field này, khiến ghi bị từ chối.

Spec này bổ sung và, sau khi được duyệt, thay thế có giới hạn hợp đồng catalog trong @doc/specs/2026-08-23/firebase-project-sync-approval-approved: D1, FR-3 và NFR-2 được mở rộng để cho phép `createdByUid`. Các quyết định và yêu cầu khác của spec gốc vẫn giữ nguyên. Trước khi spec này được duyệt, spec gốc vẫn là chuẩn có hiệu lực.

## Locked Decisions

- D1: Dùng migration Firebase Admin SDK idempotent để khôi phục toàn bộ dự án hiện có. Migration suy ra metadata thiếu bằng fallback xác định được và ghi cảnh báo. Chỉ `ACTIVE`/`ARCHIVED` được đưa vào catalog; entry của dự án `DELETING`, `DELETED` hoặc có tombstone bị loại bỏ.
- D2: Catalog được phép chứa `createdByUid` và field này được đọc bởi mọi người dùng Firebase đã xác thực. UID bất biến sau khi thiết lập. Migration ưu tiên owner đáng tin cậy từ dữ liệu cũ; nếu không có, bắt buộc nhận Firebase Auth UID qua `--fallback-owner-uid`. Không dùng định danh service account làm owner.
- D3: Migration production bắt buộc chạy `--dry-run`, xuất thống kê và yêu cầu xác nhận rõ ràng trước khi ghi. Migration có thể hoàn tất kèm cảnh báo khi còn sai lệch; báo cáo được lưu bền vững và cảnh báo chỉ hiển thị cho admin trên Android/web.

## System Decision Impact

- Impact: draft new
- Decision: pending draft System Decision về public catalog ownership metadata và migration contract
- Acceptance gate: Chỉ chấp nhận decision sau khi spec được duyệt và rules, migration, Android/web admin warning cùng kiểm thử Firebase Emulator có bằng chứng đạt.

## Requirements

### Functional Requirements

- FR-1: Mọi user Firebase đã xác thực đọc được catalog gồm các dự án đủ điều kiện `ACTIVE` và `ARCHIVED`.
- FR-2: Catalog entry có đúng các field `projectName`, `projectCode`, `createdByUid`, `updatedAtEpochMs` và `status`; không chứa business payload, member, contractor hoặc media URL.
- FR-3: `createdByUid` là Firebase Auth UID không rỗng, công khai cho mọi user đã xác thực và không được thay đổi sau khi entry được tạo.
- FR-4: Firestore rules cho phép admin tạo/cập nhật catalog theo exact shape mới, từ chối non-admin write, field ngoài allowlist và mọi thay đổi `createdByUid` đã tồn tại.
- FR-5: Mọi đường tạo/cập nhật/archive project trên web, Android hoặc sync backend phải duy trì `/projects` và `/projectCatalog` nhất quán theo cùng hợp đồng field.
- FR-6: Migration đọc toàn bộ project hiện có, nhận diện cả document dạng trực tiếp và payload lồng, rồi tạo/cập nhật projection catalog tương ứng.
- FR-7: Fallback metadata phải xác định được qua cùng input: tên dùng `name`/`projectName` rồi project ID; mã dùng `projectCode`/slug rồi project ID; thời gian dùng `updatedAt`/`createdAt` hợp lệ rồi giá trị mặc định ổn định.
- FR-8: Migration ưu tiên `createdByUid` đáng tin cậy trong project; nếu thiếu thì dùng `--fallback-owner-uid`. UID fallback phải tồn tại trong Firebase Authentication trước khi execute.
- FR-9: Dự án `DELETING`, `DELETED` hoặc có tombstone không được tạo catalog; catalog entry còn sót của chúng phải được liệt kê để xóa.
- FR-10: `--dry-run` không ghi dữ liệu và phải báo số lượng eligible, create, update, unchanged, delete, warning và discrepancy.
- FR-11: Execute yêu cầu xác nhận production rõ ràng, ghi theo batch an toàn và có thể chạy lại mà không nhân đôi hoặc thay đổi kết quả đã đúng.
- FR-12: Sau execute, mọi project eligible chưa có catalog hoặc có catalog sai được ghi vào báo cáo discrepancy. Có discrepancy không làm run thất bại nhưng trạng thái phải là `COMPLETED_WITH_WARNINGS`.
- FR-13: Báo cáo migration chỉ admin đọc được, không chứa business payload và đủ dữ liệu để truy vết run, số lượng, project ID lỗi và nguyên nhân ổn định.
- FR-14: Android và web hiển thị cảnh báo `COMPLETED_WITH_WARNINGS` cho admin; user thường vẫn thấy các catalog entry đã khôi phục và không thấy chi tiết migration.
- FR-15: Lỗi truy vấn catalog phải hiển thị như lỗi có thể hành động; chỉ response thành công với danh sách rỗng mới hiển thị empty state.
- FR-16: Writer/backfill không được nuốt lỗi permission hoặc schema thành empty-state thành công; lỗi phải được log có mã ổn định và chuyển tới trạng thái UI/admin phù hợp.
- FR-17: Việc yêu cầu/phê duyệt/quyền dữ liệu sau khi catalog xuất hiện tiếp tục tuân theo @doc/specs/2026-08-23/firebase-project-sync-approval-approved.

### Non-Functional Requirements

- NFR-1 Security: Rules thực thi authentication, admin-only write, exact shape và `createdByUid` immutability độc lập với UI.
- NFR-2 Privacy: Catalog chủ ý công khai `createdByUid` cho user đã xác thực nhưng không rò thêm identity profile hoặc dữ liệu ngoài allowlist.
- NFR-3 Integrity: Migration và mọi writer idempotent; chạy lại không tạo duplicate hoặc làm đổi owner hợp lệ.
- NFR-4 Auditability: Mỗi migration run có ID, actor/credential context, mode, timestamp, counts, warnings và kết quả.
- NFR-5 Rollout safety: Production bắt buộc dry-run trước execute; rules tương thích phải được triển khai trước khi client writer mới được coi là hoạt động.
- NFR-6 Architecture: Firebase Android SDK vẫn chỉ nằm trong `:data`; UI dùng domain interface/state.
- NFR-7 Testability: Firebase Emulator/rules tests và migration tests không phụ thuộc dữ liệu production.
- NFR-8 Performance: Migration dùng phân trang/batch có giới hạn; catalog client tiếp tục phân trang, không tải vô hạn.

## Acceptance Criteria

- [ ] AC-1: Sau migration, user thường đã đăng nhập thấy mọi project legacy đủ điều kiện `ACTIVE`/`ARCHIVED` trên Android với tên, mã, owner UID, ngày cập nhật và trạng thái.
- [ ] AC-2: Dry-run production không tạo/cập nhật/xóa document và xuất đủ counts create/update/unchanged/delete/warning/discrepancy.
- [ ] AC-3: Execute cùng input hai lần tạo cùng trạng thái cuối, không duplicate và không đổi `createdByUid` đã hợp lệ.
- [ ] AC-4: Project thiếu tên/mã/thời gian được backfill bằng fallback xác định được và có warning; project thiếu owner dùng đúng Firebase Auth UID từ `--fallback-owner-uid`.
- [ ] AC-5: Execute bị từ chối trước khi ghi nếu fallback owner không tồn tại trong Firebase Authentication.
- [ ] AC-6: Project `DELETING`, `DELETED` hoặc có tombstone không xuất hiện trong catalog; entry còn sót được dry-run báo và execute xóa.
- [ ] AC-7: Rules cho phép signed-in read và admin exact-shape create/update có `createdByUid`, đồng thời từ chối unauthenticated read, non-admin write, extra fields và owner mutation.
- [ ] AC-8: Tạo project mới qua web/Android tạo hoặc cập nhật catalog thành công, không còn `PERMISSION_DENIED` do bất nhất `createdByUid`.
- [ ] AC-9: Run còn sai lệch trả `COMPLETED_WITH_WARNINGS`, lưu report và hiển thị cảnh báo cho admin trên Android/web; user thường không thấy report nhưng vẫn thấy entry hợp lệ.
- [ ] AC-10: Lỗi Firestore/rules hiển thị error state có hành động retry thay vì empty state “Chưa có dự án”.
- [ ] AC-11: Emulator tests chứng minh schema/rules, migration legacy, owner immutability, deletion filtering, partial warning và signed-in catalog visibility.
- [ ] AC-12: Kiểm tra module boundary chứng minh không có Firebase SDK mới ngoài `:data`.
- [ ] AC-13: Các luồng access request, approve và open/download vẫn đạt các AC liên quan của spec gốc.

## Scenarios

### Scenario 1: Khôi phục catalog legacy thành công

**Given** Firebase có project `ACTIVE` trong `/projects` nhưng chưa có `/projectCatalog`  
**When** admin chạy dry-run, xác nhận rồi execute migration  
**Then** catalog entry exact-shape được tạo và user thường nhìn thấy dự án trên Android.

### Scenario 2: Thiếu metadata và owner

**Given** project cũ thiếu tên, mã, thời gian và `createdByUid`  
**When** migration chạy với một `--fallback-owner-uid` hợp lệ  
**Then** metadata được suy ra xác định được, owner nhận UID fallback và report chứa cảnh báo tương ứng.

### Scenario 3: Project đang xóa

**Given** project có trạng thái `DELETING`/`DELETED` hoặc tombstone  
**When** migration đánh giá catalog  
**Then** project không được backfill và entry catalog còn sót được lên kế hoạch xóa.

### Scenario 4: Migration hoàn tất một phần

**Given** một số project không thể reconcile nhưng các project khác hợp lệ  
**When** execute kết thúc  
**Then** project hợp lệ vẫn xuất hiện, run là `COMPLETED_WITH_WARNINGS`, admin thấy cảnh báo và user thường không thấy chi tiết nội bộ.

### Scenario 5: Writer tương lai

**Given** admin tạo project mới sau khi rules mới được triển khai  
**When** writer commit project và catalog  
**Then** catalog có `createdByUid` hợp lệ, commit thành công và dự án xuất hiện sau refresh.

### Scenario 6: Chặn thay đổi owner

**Given** catalog entry đã có `createdByUid`  
**When** client/admin cố cập nhật UID sang giá trị khác qua Firestore client  
**Then** rules từ chối update và owner cũ được giữ nguyên.

## Technical Notes

- Root cause hiện tại: Android đọc `/projectCatalog`; Android admin backfill và web writer ghi `createdByUid`, nhưng rule exact-shape không cho field này. Backfill bắt lỗi và chỉ ghi debug, nên danh sách rỗng bị trình bày như trạng thái hợp lệ.
- Rollout tối thiểu: kiểm thử rules/migration; deploy rules tương thích; chạy dry-run; execute migration; xác minh bằng user thường; sau đó phát hành UI cảnh báo admin nếu cần.
- Migration dùng Admin SDK để không phụ thuộc việc admin mở ứng dụng, nhưng vẫn tự kiểm tra schema và owner trước khi ghi.
- Báo cáo migration nên dùng collection/admin endpoint riêng với admin-only read; không đặt chi tiết report trong public catalog.

## Related Work

- `vy4got` — [permanent-project-deletion-revision] Đặc tả xóa local trước và admin quyết định dữ liệu Cloud — todo.

## Task Links

Chưa tạo task triển khai; chỉ tạo sau khi spec được duyệt.

## Open Questions

- Không còn câu hỏi mở trong phạm vi catalog recovery.
