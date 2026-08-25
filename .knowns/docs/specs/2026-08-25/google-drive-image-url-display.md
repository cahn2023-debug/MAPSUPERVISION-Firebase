---
id: doc-40cf3c5cb473d6ca07849423c76192b4
title: Google Drive Image URL Display
description: Specification for rendering Google Drive images in the web app with standardized lh3.googleusercontent.com URLs.
createdAt: '2026-08-25T15:07:11.712Z'
updatedAt: '2026-08-25T15:20:48.226Z'
tags:
  - spec
  - approved
  - web
  - media
  - google-drive
---

## Overview

Chuẩn hóa cách web app hiển thị ảnh được lưu trên Google Drive bằng URL `lh3.googleusercontent.com`. Web app nhận raw Google Drive file ID, dựng URL ảnh theo kích thước do component cung cấp, và xử lý dữ liệu/ảnh không khả dụng bằng placeholder.

## Locked Decisions

- D1: Chỉ ảnh Google Drive dùng URL chuẩn hóa; các URL ảnh không phải Google Drive giữ nguyên.
- D2: Đầu vào cho ảnh Google Drive là raw file ID, không phải full Google Drive URL.
- D3: URL sinh ra luôn giữ query parameter `authuser=0`.
- D4: Component/màn hình truyền giá trị `width` để dựng tham số `w`; nếu không truyền thì mặc định `1000`.
- D5: File ID rỗng hoặc không hợp lệ hiển thị placeholder và không tạo URL Google.
- D6: File ID hợp lệ nhưng ảnh không tồn tại hoặc không truy cập được hiển thị placeholder sau khi ảnh báo lỗi tải.
- D7: Quy tắc áp dụng cho tất cả component hiện có và component mới có hiển thị ảnh Google Drive.

## System Decision Impact

- Impact: none
- Decision: Không tạo System Decision mới; đây là execution rule của spec.
- Acceptance gate: Spec phải được review/approve trước khi tạo task hoặc triển khai.

## Requirements

### Functional Requirements

- FR-1: Với Google Drive file ID hợp lệ và `width` hợp lệ, web app phải tạo URL theo mẫu `https://lh3.googleusercontent.com/d/{fileId}=w{width}?authuser=0`.
- FR-2: Khi component không truyền `width`, web app phải dùng `w1000`.
- FR-3: Khi component truyền `width`, web app phải dùng đúng giá trị đó trong tham số `w`.
- FR-4: Web app phải nhận diện dữ liệu Google Drive dưới dạng raw file ID; không yêu cầu trích xuất ID từ full URL.
- FR-5: URL ảnh không phải Google Drive không được chuyển đổi bởi quy tắc này.
- FR-6: File ID rỗng hoặc không hợp lệ phải hiển thị placeholder và không phát sinh request tới URL Google.
- FR-7: Khi request ảnh Google Drive thất bại hoặc ảnh phát sinh lỗi tải, component phải chuyển sang hiển thị placeholder.
- FR-8: Quy tắc phải được áp dụng nhất quán cho mọi component hiện có và mới có hiển thị ảnh Google Drive.

### Non-Functional Requirements

- NFR-1: URL sinh ra phải giữ nguyên cấu trúc path và query parameter `authuser=0`; chỉ phần file ID và giá trị `w` được thay đổi theo đầu vào.
- NFR-2: Hành vi placeholder phải nhất quán giữa các component áp dụng quy tắc.
- NFR-3: Việc chuẩn hóa URL không được làm thay đổi hoặc làm hỏng các nguồn ảnh không phải Google Drive.

- [x] AC-1: Với file ID `1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB` và không truyền width, output là `https://lh3.googleusercontent.com/d/1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB=w1000?authuser=0`.
- [x] AC-2: Với cùng file ID và `width=600`, output chứa đúng `=w600?authuser=0`.
- [x] AC-3: Với raw file ID hợp lệ, component render ảnh bằng URL Google Drive đã dựng.
- [x] AC-4: Với file ID rỗng hoặc không hợp lệ, component render placeholder và không request URL Google.
- [x] AC-5: Với ảnh có ID hợp lệ nhưng request ảnh thất bại, component render placeholder sau lỗi tải.
- [x] AC-6: Với URL ảnh không phải Google Drive, component giữ nguyên URL đầu vào.
- [x] AC-7: Ít nhất một component hiện có và một component mới (nếu có trong phạm vi triển khai) dùng chung hành vi chuẩn hóa và placeholder.

## Scenarios

### Scenario 1: Hiển thị ảnh Google Drive với giá trị mặc định

**Given** component nhận raw file ID hợp lệ và không có width  
**When** component dựng nguồn ảnh  
**Then** nguồn ảnh là `https://lh3.googleusercontent.com/d/{fileId}=w1000?authuser=0`

### Scenario 2: Hiển thị ảnh Google Drive theo kích thước component

**Given** component nhận raw file ID hợp lệ và `width=600`  
**When** component dựng nguồn ảnh  
**Then** nguồn ảnh dùng `=w600?authuser=0`

### Scenario 3: Nguồn ảnh không phải Google Drive

**Given** component nhận một URL ảnh không phải Google Drive  
**When** component render ảnh  
**Then** URL được giữ nguyên và không áp dụng formatter Google Drive

### Scenario 4: File ID không hợp lệ

**Given** file ID rỗng hoặc không hợp lệ  
**When** component chuẩn bị render ảnh  
**Then** component hiển thị placeholder và không tạo URL Google

### Scenario 5: Ảnh không truy cập được

**Given** file ID có định dạng hợp lệ nhưng ảnh không tồn tại hoặc không có quyền truy cập  
**When** request ảnh phát sinh lỗi tải  
**Then** component hiển thị placeholder

## Technical Notes

- Công thức URL chuẩn: `https://lh3.googleusercontent.com/d/{fileId}=w{width}?authuser=0`.
- Không hỗ trợ đầu vào full Google Drive URL trong contract của feature này.
- Giá trị width mặc định là `1000`; component có thể truyền width khác theo kích thước hiển thị.
- Placeholder và xử lý lỗi tải nên được bao phủ trong test của formatter/component.
- Các spec liên quan để tham chiếu: `@doc/specs/2026-08-24/media-status-tags-by-object`, `@doc/specs/2026-08-25/web-backend-performance-optimization`.

Task `x85rvu`: [google-drive-image-url-display-01] Standardize Google Drive image URLs across web app — in-progress

## Open Questions

Không còn câu hỏi mở trong phạm vi đã chốt.
