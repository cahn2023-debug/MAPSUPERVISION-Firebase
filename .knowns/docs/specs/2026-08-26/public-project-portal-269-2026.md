---
id: doc-422a02271566a9dda23de8370b438ab6
title: Public Project Portal 269-2026
description: Specification for Public Project Portal 269-2026
createdAt: '2026-08-26T07:47:31.480Z'
updatedAt: '2026-08-26T07:47:47.502Z'
tags:
  - spec
  - approved
---

# Đặc tả Kỹ thuật: Public Project Portal (269 - 2026)

## Overview
Xây dựng và hoàn thiện website public chuyên nghiệp, cao cấp cho dự án công khai **269 - 2026** tại URL `https://mapsupervision-webapp.vercel.app/269-2026`. Hệ thống cung cấp trải nghiệm giám sát hiện trường trực quan, đầy đủ bản đồ GIS tương tác MapLibre, thư viện ảnh thực địa chất lượng cao kèm lightbox, nhật ký thi công, tiến độ khối lượng và các bảng dữ liệu quản lý công trình ở chế độ chỉ xem (read-only), không yêu cầu đăng nhập.

## Locked Decisions
- **D1 (Bố cục & Trải nghiệm tổng thể)**: Đồng bộ trọn vẹn phong cách giao diện cao cấp của Webapp chính sang trang public `/269-2026`. Bao gồm Header dự án chuyên nghiệp, Thẻ chỉ số tổng quan (KPIs: Tuyến, Node, Ảnh, Nhật ký, Tiến độ), Hệ thống Tabs linh hoạt (Tổng quan & Bản đồ GIS, Thư viện ảnh, Nhật ký giám sát, Tiến độ khối lượng, Tra cứu Tuyến/Node GIS, Vật tư & Kế hoạch), hỗ trợ Responsive hoàn hảo trên Desktop, Tablet và Mobile.
- **D2 (Tính năng tương tác chuyên sâu)**: Bản đồ GIS MapLibre đầy đủ chế độ bản đồ (Vệ tinh, Đường phố, Dark mode), công cụ đo khoảng cách, chọn đối tượng xem chi tiết và liên kết ảnh; Lightbox xem ảnh toàn màn hình với zoom/pan và thông số thực địa; Bộ lọc nhanh theo Nhà thầu/Tuyến/Từ khóa; Bộ chuyển đổi Theme Sáng/Tối mượt mà.
- **D3 (Cơ chế đồng bộ & Hiệu năng)**: Tự động cập nhật dữ liệu nền mỗi 30 giây + Nút bấm "Làm mới dữ liệu" thủ công tức thì + Hiển thị mốc thời gian cập nhật thực tế, lưu giữ state ở client để chuyển đổi tab mượt mà không bị gián đoạn.
- **D4 (Kiến trúc định tuyến & Code sạch)**: Hoàn thiện trực tiếp route `/269-2026` với cấu trúc module hóa, tái sử dụng các token và component cốt lõi, code sạch tuân thủ TypeScript strict mode và Clean Code.

## System Decision Impact
- Impact: none

## Requirements

### Functional Requirements
- **FR-1**: Hiển thị thông tin dự án công khai (Tên dự án, Mã dự án, Thời gian cập nhật gần nhất, Trạng thái hoạt động) với giao diện hiện đại.
- **FR-2**: Cung cấp Thẻ thống kê tổng quan (KPI metrics cards): Số lượng Node, Tuyến cáp, Ảnh hiện trường, Nhật ký thi công, Khối lượng hoàn thành, Vật tư.
- **FR-3**: Tích hợp Bản đồ GIS MapLibre tương tác trực tiếp:
  - Hiển thị đầy đủ vị trí các Node, hướng tuyến Route có màu phân biệt theo Nhà thầu.
  - Hỗ trợ đổi lớp bản đồ nền (Vệ tinh Google/Esri, Giao thông OSM/Carto, Dark Canvas).
  - Tích hợp công cụ đo đạc khoảng cách tuyến (Measure tool).
  - Click vào Node hoặc Tuyến hiển thị popup chi tiết thông số và danh sách ảnh chụp liên quan.
- **FR-4**: Thư viện ảnh thực địa nâng cao (Site Photo Gallery):
  - Hiển thị dạng lưới thẻ ảnh chất lượng cao kèm nhãn mã đối tượng, kỹ sư thực hiện, mốc thời gian.
  - Tích hợp Lightbox xem ảnh toàn màn hình kèm zoom, tải ảnh và hiển thị chi tiết thông tin GPS/kỹ thuật.
  - Bộ lọc ảnh theo Nhà thầu, Mã đối tượng, Kỹ sư và Tìm kiếm từ khóa.
- **FR-5**: Tab Nhật ký giám sát & Công việc (Daily Logs & Tasks):
  - Danh sách nhật ký thi công thực tế theo ngày (Hạng mục công việc, Khối lượng thực hiện, Đơn vị, Nhân lực, Thời tiết, Ghi chú).
  - Trạng thái công việc giám sát (Todo, In Progress, Done).
- **FR-6**: Tab Tiến độ khối lượng & Vật tư (Progress & Materials):
  - Bảng tổng hợp tiến độ khối lượng thi công thực tế.
  - Bảng kê khai báo vật tư và bàn giao vật tư tại công trình.
- **FR-7**: Tab Tra cứu Dữ liệu Kỹ thuật GIS & Kế hoạch (GIS Directory & Plans):
  - Danh sách tra cứu bảng dữ liệu Node, Tuyến, Ghi chú kỹ thuật, Kế hoạch thi công với bộ lọc tìm kiếm nhanh.
- **FR-8**: Hỗ trợ chuyển đổi Theme Sáng / Tối (Light / Dark Mode) đồng bộ hệ thống màu của ứng dụng (không sử dụng màu tím theo quy chuẩn).

### Non-Functional Requirements
- **NFR-1 (Hiệu năng & Tối ưu tải)**: Tải trang nhanh, lazy load MapLibre và hình ảnh qua API proxy an toàn (`/api/public/269-2026/media/[photoId]`).
- **NFR-2 (Bảo mật & Phân quyền)**: Trang hoàn toàn ở chế độ Read-Only, không để lộ bất kỳ API key nhạy cảm hay khả năng ghi đè dữ liệu.
- **NFR-3 (Thiết kế & Khả năng tiếp cận - UI/UX Pro Max)**: Thiết kế đẳng cấp, viền sắc nét, tương phản chuẩn WCAG AAA, typography rõ ràng, hiệu ứng chuyển động và micro-interactions mượt mà.

## Acceptance Criteria
- [ ] **AC-1**: Truy cập `/269-2026` hiển thị ngay giao diện dự án công khai đẹp mắt, không lỗi hiển thị, có nhãn "Dự án công khai · Chế độ chỉ xem".
- [ ] **AC-2**: Bản đồ GIS MapLibre hiển thị chính xác các Node và Tuyến cáp với màu sắc nhà thầu, cho phép chuyển đổi lớp nền (Vệ tinh / Giao thông / Dark) và click xem chi tiết.
- [ ] **AC-3**: Thư viện ảnh tải mượt mà qua proxy media, bấm vào ảnh mở Lightbox toàn màn hình xem rõ nét kèm đầy đủ metadata.
- [ ] **AC-4**: Chuyển đổi qua lại giữa các tab (Tổng quan & Bản đồ, Thư viện ảnh, Nhật ký & Công việc, Tiến độ & Vật tư, Tra cứu dữ liệu) tức thì, giữ nguyên bộ lọc và không bị tải lại toàn trang.
- [ ] **AC-5**: Bộ lọc theo Nhà thầu và Ô tìm kiếm hoạt động chính xác trên Bản đồ, Ảnh và Bảng dữ liệu.
- [ ] **AC-6**: Nút bấm "Làm mới dữ liệu" và cơ chế auto-poll 30s hoạt động trơn tru, hiển thị rõ thời gian cập nhật mới nhất.
- [ ] **AC-7**: Giao diện hiển thị sắc nét trên cả màn hình điện thoại di động (Mobile) và máy tính để bàn (Desktop).

## Scenarios

### Scenario 1: Người dùng/Khách hàng truy cập xem bản đồ và tiến độ công trình
**Given** Người dùng mở đường dẫn `https://mapsupervision-webapp.vercel.app/269-2026` trên trình duyệt
**When** Trang tải thành công
**Then** Hiển thị Header dự án "269 - 2026", các thẻ chỉ số KPI tổng quan, và Bản đồ GIS với các tuyến cáp/node được định vị chính xác trên nền bản đồ vệ tinh/giao thông.

### Scenario 2: Kiểm tra ảnh thực địa và chi tiết kỹ thuật
**Given** Người dùng đang ở Tab Thư viện ảnh hoặc click vào một Node trên bản đồ
**When** Người dùng chọn một hình ảnh cụ thể
**Then** Lightbox toàn màn hình hiển thị ảnh chất lượng cao cùng thông tin Mã đối tượng, Kỹ sư hiện trường, Ngày giờ chụp và Tọa độ GPS.

### Scenario 3: Lọc dữ liệu theo Nhà thầu
**Given** Người dùng muốn kiểm tra khối lượng thi công của một Nhà thầu cụ thể
**When** Người dùng chọn Nhà thầu từ thanh lọc
**Then** Bản đồ chỉ làm nổi bật các tuyến/node của nhà thầu đó, danh sách ảnh và nhật ký thi công tự động lọc tương ứng.

## Technical Notes
- Sử dụng `next/dynamic` với ssr: false cho `GisWebMap` component để đảm bảo render client mượt mà không lỗi SSR.
- Áp dụng các biến theme CSS sẵn có `--bg`, `--surface`, `--ink`, `--accent`, `--line`, v.v. đã được chuẩn hóa trong `globals.css`.
- Proxy hình ảnh sử dụng Next.js route `/api/public/269-2026/media/[photoId]`.

## Task Links
Sẽ được tạo và liên kết sau khi Spec được phê duyệt.
