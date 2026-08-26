# Spec: Vercel Public Dự án 269 Đồng bộ Firestore

## Overview

Quy chuẩn và đặc tả kỹ thuật cho tính năng **Vercel Public View của Dự án 269 - 2026** trên MapSupervision WebApp (`/269-2026` và `/api/public/269-2026`). Tính năng này phục vụ hiển thị dữ liệu giám sát trực quan, trung thực từ cơ sở dữ liệu gốc Firestore mà không sinh dữ liệu nhân tạo hay mock rỗng.

## Locked Decisions

- **D1 (Pure Firestore Source)**: Vercel Public là chế độ Read-Only 100%, đọc trực tiếp từ dữ liệu gốc của Project 269 lưu trên Firestore (`projects/6874375a-3366-4457-a978-b8ee71c4e461`). Không tạo/ghi dữ liệu mới hoặc sinh dữ liệu mock rỗng. Sử dụng bộ nhớ đệm ngắn hạn (30s - 60s) để tối ưu hạn ngạch đọc.
- **D2 (Integrity & Fault Tolerance)**: Khi Firestore gặp lỗi mạng hoặc bị Quota Exceeded, chỉ phục vụ bản cache dữ liệu thật gần nhất (nếu có) kèm metadata thông báo trạng thái. Nếu hoàn toàn chưa có cache thực tế, trả về lỗi kết nối (HTTP 503) để người dùng thử lại, tuyệt đối không tạo đối tượng giả định.
- **D3 (Sync Triggering & UX)**: Hỗ trợ đồng bộ tức thì thông qua nút "Làm mới / Đồng bộ" trên giao diện công khai và cơ chế nền SWR (Stale-While-Revalidate) nhằm đảm bảo dữ liệu trên Vercel luôn được cập nhật từ Firestore.

## System Decision Impact

- Impact: none

## Requirements

### Functional Requirements

- **FR-1 (Direct Live Fetching)**: Endpoint `/api/public/269-2026` truy vấn trực tiếp document dự án gốc và các subcollections liên quan (`gis_node`, `gis_route`, `task`, `note`, `work_plan`, `daily_log`, `site_photos`, `work_volume_progress`, `material_declaration`, `material_handover`, `report_draft`) từ Firestore.
- **FR-2 (No Artificial Data Generation)**: Tuyệt đối không chèn đối tượng rỗng nhân tạo vào response. Dữ liệu trả về phản ánh chính xác các bản ghi hiện hữu trong Firestore.
- **FR-3 (Short-lived Caching & Quota Protection)**: Cấu hình In-memory & Edge Cache với TTL ngắn (30 giây) để giảm tải số lượt đọc Firestore khi có nhiều client truy cập đồng thời.
- **FR-4 (Manual & Background Revalidation)**: Cung cấp nút "Làm mới" trên UI `/269-2026` cho phép người dùng kích hoạt yêu cầu tải mới nhất từ serverless API (bỏ qua cache client).
- **FR-5 (Media Direct Streaming/Redirection)**: Endpoint `/api/public/269-2026/media/[photoId]` ánh xạ trực tiếp tới URL hình ảnh/video thực tế trên Google Drive của dự án.

### Non-Functional Requirements

- **NFR-1 (Performance)**: Phản hồi API trung bình dưới 500ms khi cache warm, và dưới 1.5s khi thực hiện truy vấn Firestore trực tiếp.
- **NFR-2 (Reliability)**: Không throw unhandled runtime 500 error khi Firestore chạm quota limit; phản hồi có cấu trúc kèm cờ `isCached: true` và `quotaExceeded: true` nếu dùng cache cũ.

## Acceptance Criteria

- [x] **AC-1**: Truy cập `/api/public/269-2026` trả về `200 OK` với dữ liệu khớp chính xác 100% với dữ liệu trong Firestore của dự án 269.
- [x] **AC-2**: Khi dữ liệu trên Firestore được cập nhật (thêm task, sửa note, cập nhật tiến độ GIS), trang `/269-2026` phản ánh nội dung mới sau khi bấm nút "Làm mới" hoặc sau khi hết TTL cache (tối đa 60 giây).
- [x] **AC-3**: Toàn bộ các bảng danh mục (`gis_node`, `gis_route`, `site_photos`, v.v.) chỉ chứa các bản ghi thực tế từ Firestore, không có dữ liệu mẫu rỗng tự chế.
- [x] **AC-4**: Giao diện `/269-2026` hiển thị thời gian đồng bộ gần nhất (`updatedAtEpochMs`) và nút bấm Làm mới hoạt động mượt mà.

## Scenarios

### Scenario 1: Tải dữ liệu dự án công khai thành công (Happy Path)
**Given** Dự án 269 đã tồn tại trên Firestore với các node GIS và nhật ký công việc.  
**When** Người dùng truy cập `https://mapsupervision-webapp.vercel.app/269-2026`.  
**Then** API trả về HTTP 200, giao diện hiển thị bản đồ GIS, danh sách công việc và ảnh hiện trường thực tế từ Firestore.

### Scenario 2: Cập nhật dữ liệu từ Android/Web Admin và xem trên Public View
**Given** Một kỹ sư thêm ảnh hiện trường mới trên app Android đồng bộ lên Firestore.  
**When** Người xem trang Public bấm nút "Làm mới" trên giao diện `/269-2026`.  
**Then** Dữ liệu mới nhất được kéo từ Firestore về và ảnh mới xuất hiện trên giao diện công khai.

### Scenario 3: Xử lý khi Firestore tạm thời chạm hạn ngạch (Quota Exceeded)
**Given** Hạn ngạch đọc Firestore trong ngày bị vượt quá.  
**When** Người dùng gọi `/api/public/269-2026`.  
**Then** Hệ thống trả về bản snapshot thực tế gần nhất đã lưu trong cache kèm cảnh báo, không gây crash ứng dụng hay sinh dữ liệu rỗng.

## Technical Notes

- Sử dụng `getAdminDb()` trong `webapp/lib/firebase-admin.ts` với quyền Admin SDK để đọc dữ liệu Firestore.
- In-memory cache lưu trữ payload thực tế theo timestamp `updatedAtEpochMs`.
- Đảm bảo `instrumentation.ts` không chạy background migrations để bảo tồn 100% quota cho truy vấn dữ liệu thực.

## Task Links

- (Sẽ được tạo sau khi duyệt Spec)

## Open Questions

- Không còn câu hỏi bỏ ngỏ. Các quyết định D1, D2, D3 đã được khóa và xác nhận qua phiên Socratic.
