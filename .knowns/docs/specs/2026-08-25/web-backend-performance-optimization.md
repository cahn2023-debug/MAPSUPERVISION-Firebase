---
id: doc-285a8394715267e447de652655327134
title: Web Backend Performance Optimization
description: Specification for preserving web features while reducing backend P95 latency across Firestore, Next.js API, Firebase Admin, and Google Drive.
createdAt: '2026-08-25T06:39:00.909Z'
updatedAt: '2026-08-25T06:39:00.909Z'
tags:
  - spec
  - draft
---

## Overview

Tối ưu backend của web app MapSupervision mà không thay đổi tính năng, schema Firestore, hợp đồng Firebase dùng chung hoặc hành vi tương thích với Android. Phạm vi gồm Firebase Web SDK trong trình duyệt, lớp đồng bộ Firestore, Next.js API dùng Firebase Admin, và luồng media Google Drive.

### Current Web Feature Inventory

- Xác thực: đăng ký/đăng nhập Firebase, xác minh email, nhận diện quyền admin.
- Tổng quan và GIS: chọn dự án, thống kê, bản đồ MapLibre với node/route và bộ lọc hiển thị.
- Nghiệp vụ: xem/tạo công việc, cập nhật trạng thái, tạo nhật ký và theo dõi dữ liệu dự án.
- Media: danh sách ảnh, tải lười ảnh xem trước, upload và stream media qua Next.js API/Google Drive.
- Quản trị: danh bạ người dùng, phê duyệt truy cập, thành viên/phạm vi nhà thầu, cấu hình dự án và vòng đời xóa dự án.
- Kiến trúc hiện tại: phần lớn đọc/ghi nghiệp vụ dùng Firebase Web SDK trực tiếp; thao tác media và xóa dự án dùng Next.js API với Firebase Admin.

## Locked Decisions

- D1: Tối ưu toàn bộ tầng dữ liệu web gồm Firestore client, Next.js API, Firebase Admin và Google Drive. Ưu tiên giảm độ trễ cho mở/chuyển dự án, thao tác nghiệp vụ và media. Giữ nguyên schema, hợp đồng Firebase và khả năng tương thích Android.
- D2: Đo baseline và kết quả sau tối ưu trong cùng điều kiện. P95 của từng nhóm luồng phải giảm ít nhất 30% trên cả dữ liệu thực đã ẩn danh và fixture tổng hợp quy mô lớn. Không dùng SLO tuyệt đối.
- D3: Dữ liệu dự án đang mở và nghiệp vụ hiện tại giữ realtime; danh mục/admin làm mới theo sự kiện hoặc yêu cầu; media tải lười. Không hiển thị cache cũ. Màn hình chờ dữ liệu cốt lõi mới nhất rồi tải phần phụ. Bản đồ tải đủ geometry cần thiết; công việc, nhật ký, media và danh sách quản trị dùng query/phân trang Firestore.
- D4: Đọc và ghi nghiệp vụ thông thường tiếp tục dùng Firestore SDK; thao tác đặc quyền, media và vòng đời dự án đi qua Next.js API/Firebase Admin. App Check và rate limiting triển khai theo giai đoạn quan sát rồi cưỡng chế. Production chỉ thu telemetry tối thiểu không PII. Gói tối ưu phát hành một lần sau khi đạt test và benchmark, kèm rollback.

## System Decision Impact

- Impact: existing
- Decision: @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision
- Acceptance gate: Các tối ưu liên quan API xóa dự án phải giữ nguyên local-first lifecycle, quyền quyết định của project admin, tính idempotent, checkpoint/retry độc lập và không thay đổi Google Drive media hoặc permission theo quyết định đã được chấp nhận.

## Requirements

### Functional Requirements

- FR-1: Cung cấp benchmark harness tái lập được cho ba journey: mở web/chuyển dự án; thao tác nghiệp vụ; media upload/preview. Harness phải ghi P50, P95, số Firestore document reads, thời gian API/Drive và tỷ lệ lỗi.
- FR-2: Trước khi sửa tối ưu, đóng băng một Performance Test Profile mô tả phiên bản ứng dụng, benchmark host, network profile, dữ liệu thực đã ẩn danh, fixture lớn, thao tác mẫu và số lần chạy. Baseline và hậu kiểm phải dùng cùng profile.
- FR-3: Khi đăng nhập hoặc chuyển dự án, web chỉ chặn giao diện đến khi metadata dự án, quyền truy cập và dữ liệu cốt lõi của màn hình hiện tại đã mới; dữ liệu phụ không được làm chậm thời điểm sẵn sàng này.
- FR-4: Khi người dùng chuyển dự án hoặc rời màn hình, mọi listener không còn cần thiết phải được hủy. Không được tiếp tục nhận hoặc áp dụng dữ liệu từ dự án/màn hình cũ.
- FR-5: Chỉ dữ liệu của dự án đang mở và màn hình nghiệp vụ đang hoạt động được duy trì realtime. Danh mục dự án và màn hình admin phải tải theo sự kiện hoặc yêu cầu thay vì duy trì listener toàn cục khi không hiển thị.
- FR-6: Geometry node/route cần thiết cho bản đồ phải được tải đầy đủ và nhất quán trước khi bản đồ được coi là sẵn sàng. Các collection lớn khác, gồm công việc, nhật ký, media và danh sách quản trị, phải hỗ trợ query có giới hạn và phân trang ổn định, không thiếu hoặc trùng bản ghi giữa các trang.
- FR-7: Không hiển thị dữ liệu cache cũ khi mở hoặc chuyển màn hình. Trong thời gian chờ dữ liệu mới, UI phải thể hiện trạng thái loading theo dữ liệu cốt lõi và phần phụ.
- FR-8: Media metadata và ảnh xem trước chỉ được tải khi cần. Upload, download/preview và kiểm tra quyền media tiếp tục đi qua Next.js API, Firebase Admin và Google Drive mà không làm lộ credential hoặc Drive identifier ngoài hợp đồng hiện có.
- FR-9: Đọc và ghi nghiệp vụ thông thường tiếp tục dùng Firebase Web SDK và Firestore Security Rules. Các thao tác đặc quyền, media và vòng đời dự án phải dùng Next.js API với xác minh Firebase ID token, quyền dự án và validation đầu vào.
- FR-10: Next.js API phải hỗ trợ Firebase App Check và rate limiting với hai chế độ triển khai: observe-only và enforce. Chuyển sang enforce chỉ sau khi telemetry chứng minh client hợp lệ không bị chặn ngoài ngưỡng lỗi được phê duyệt.
- FR-11: Telemetry production chỉ được ghi các trường allowlist: journey/route, duration, success/error category, Firestore read count hoặc operation count, payload-size bucket và timestamp. Không ghi token, email, nội dung nghiệp vụ, URL media, raw user ID hoặc raw project ID.
- FR-12: Tối ưu không được thay đổi tên collection/document field, envelope dữ liệu, RBAC, Firestore/Storage contract hoặc lifecycle dùng chung với Android.
- FR-13: Gói tối ưu phải có hướng dẫn rollback về phiên bản backend/web trước đó và cấu hình tắt enforcement App Check/rate limiting mà không cần migration dữ liệu.

### Non-Functional Requirements

- NFR-1: P95 của từng journey trong FR-1 phải giảm ít nhất 30% so với baseline tương ứng trên cả hai bộ dữ liệu của Performance Test Profile.
- NFR-2: Không journey nào được tăng tỷ lệ lỗi hoặc sai lệch kết quả so với baseline; mọi dữ liệu, quyền và trạng thái nghiệp vụ quan sát được phải tương đương trước tối ưu.
- NFR-3: Kết quả benchmark phải có đủ số lần chạy để báo cáo P95 ổn định theo Performance Test Profile và phải loại riêng lỗi môi trường khỏi lỗi ứng dụng.
- NFR-4: Không thêm PII hoặc secret vào client bundle, log, metric, trace, fixture hoặc artifact benchmark.
- NFR-5: Firestore emulator/security tests, API route tests, web unit tests, production build và các contract test dùng chung Android-Firebase phải vượt qua trước phát hành.
- NFR-6: Tối ưu phải ưu tiên thay đổi nhỏ, có bằng chứng từ baseline; không tái kiến trúc ngoài các điểm cần thiết để đạt D1-D4.

## Acceptance Criteria

- [ ] AC-1: Performance Test Profile và báo cáo baseline tồn tại cho đủ ba journey trên dữ liệu thực ẩn danh và fixture lớn trước khi thay đổi tối ưu được đánh giá.
- [ ] AC-2: Báo cáo hậu kiểm chứng minh P95 của từng journey giảm ít nhất 30% so với baseline tương ứng trên cả hai bộ dữ liệu, trong cùng profile.
- [ ] AC-3: Instrumentation chứng minh chuyển dự án hoặc rời tab hủy listener cũ; không có callback cũ cập nhật state và số listener/read không tiếp tục tăng sau nhiều lần chuyển.
- [ ] AC-4: Kiểm thử mở/chuyển dự án chứng minh dữ liệu cốt lõi mới nhất hiển thị trước dữ liệu phụ, không hiển thị cache cũ và không chờ collection của tab không hoạt động.
- [ ] AC-5: Bản đồ hiển thị đủ node/route hợp lệ của fixture; danh sách phân trang trả đủ tập kết quả theo truy vấn, thứ tự ổn định và không trùng/thiếu bản ghi khi tải các trang liên tiếp.
- [ ] AC-6: Admin/catalog không duy trì listener khi màn hình không hoạt động và vẫn làm mới đúng sau sự kiện thay đổi hoặc thao tác refresh được định nghĩa.
- [ ] AC-7: Media preview chỉ phát sinh request khi media đi vào trạng thái cần xem; upload/preview vẫn kiểm tra token và quyền dự án, trả đúng mã lỗi cho unauthorized, forbidden, missing và upstream failure.
- [ ] AC-8: App Check và rate limiting có test cho observe-only/enforce, request hợp lệ, token thiếu/sai, vượt hạn mức và rollback cấu hình.
- [ ] AC-9: Kiểm tra allowlist telemetry xác nhận không artifact nào chứa token, email, nội dung nghiệp vụ, URL media hoặc raw user/project ID.
- [ ] AC-10: Web tests, API tests, Firestore emulator/security tests, production build và contract tests Android-Firebase đều pass; schema và hợp đồng Firebase không thay đổi.
- [ ] AC-11: API xóa dự án tiếp tục thỏa @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision và toàn bộ project-deletion tests hiện có.
- [ ] AC-12: Release checklist ghi nhận một lần phát hành gói tối ưu, điều kiện go/no-go và rollback đã được diễn tập trong môi trường không phải production.

## Scenarios

### Scenario 1: Mở web và chuyển dự án

**Given** người dùng hợp lệ có quyền trên nhiều dự án và dữ liệu fixture lớn  
**When** người dùng đăng nhập hoặc chọn dự án khác  
**Then** listener cũ được hủy, quyền và dữ liệu cốt lõi mới nhất được tải trước, tab không hoạt động không bị tải, và journey được ghi metric theo allowlist.

### Scenario 2: Thao tác nghiệp vụ realtime

**Given** người dùng đang ở tab công việc hoặc nhật ký của dự án hiện tại  
**When** một bản ghi được tạo hoặc cập nhật từ client hợp lệ khác  
**Then** màn hình đang hoạt động nhận cập nhật realtime, thứ tự phân trang vẫn ổn định và màn hình không hoạt động không duy trì listener không cần thiết.

### Scenario 3: Hiển thị bản đồ dự án lớn

**Given** dự án có nhiều node và route hợp lệ  
**When** người dùng mở tổng quan bản đồ  
**Then** toàn bộ geometry cần thiết được tải và hiển thị đúng, trong khi task, log và media không bị tải chỉ để bản đồ sẵn sàng.

### Scenario 4: Media tải lười

**Given** dự án có nhiều media record  
**When** người dùng mở tab media và cuộn đến một ảnh  
**Then** metadata được phân trang, preview chỉ được yêu cầu khi cần, API xác minh token/quyền và stream đúng nội dung từ Drive.

### Scenario 5: Mạng chậm hoặc lỗi upstream

**Given** Firestore hoặc Google Drive phản hồi chậm/lỗi  
**When** người dùng mở màn hình hoặc media  
**Then** web không hiển thị cache cũ như dữ liệu mới, trạng thái loading/error đúng phạm vi được hiển thị, listener/request được dọn dẹp và lỗi được phân loại mà không ghi PII.

### Scenario 6: App Check/rate limiting

**Given** lớp bảo vệ đang ở observe-only hoặc enforce  
**When** request hợp lệ, thiếu App Check hoặc vượt hạn mức được gửi  
**Then** observe-only chỉ ghi metric cho phép; enforce chặn đúng request không hợp lệ; rollback cấu hình khôi phục đường đi hợp lệ mà không thay đổi dữ liệu.

### Scenario 7: Android hoạt động đồng thời

**Given** Android và web cùng sử dụng một dự án Firebase  
**When** web tối ưu query/listener hoặc thực hiện ghi nghiệp vụ  
**Then** Android tiếp tục đọc, đồng bộ và áp dụng RBAC với schema/hợp đồng hiện tại, không cần migration.

## Technical Notes

- Rà soát hiện tại cho thấy webapp/app/page.tsx subscribe toàn bộ projects và khởi tạo listener cho mọi syncTables khi chọn dự án; đây là ứng viên đo baseline, không phải kết luận tối ưu trước số liệu.
- webapp/lib/sync.ts hiện có listener toàn collection cho users và projects, accessRequests giới hạn 100, cùng listener collection tổng quát cho từng bảng dự án.
- Next.js server routes hiện tập trung ở media và project deletion; media xác minh ID token/quyền rồi truy cập Firestore và Google Drive, còn deletion dùng transaction, recursive delete và checkpoint.
- Mọi index/query mới phải dùng field hiện có và được kiểm chứng bằng emulator/index tests; không được đổi schema để đạt mục tiêu.
- Tối ưu vòng đời xóa dự án bị ràng buộc bởi System Decision đã liên kết và không được thay đổi Drive media/permission.

## Task Links

Sẽ được bổ sung sau khi chạy /kn-plan --from @doc/specs/2026-08-25/web-backend-performance-optimization.

## Open Questions

- Không còn câu hỏi quyết định sản phẩm. Performance Test Profile phải đóng băng cardinality, network profile, benchmark host và số lần chạy trước khi baseline trở thành hợp lệ.
