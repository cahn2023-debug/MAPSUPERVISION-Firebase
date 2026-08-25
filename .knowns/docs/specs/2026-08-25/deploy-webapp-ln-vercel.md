---
id: doc-fec02adeef516c7bd59bd355876f6bd7
title: Deploy webapp lên Vercel
description: Specification for deploying the webapp to Vercel using the free vercel.app domain.
createdAt: '2026-08-25T16:28:55.811Z'
updatedAt: '2026-08-25T16:43:21.691Z'
tags:
  - spec
  - approved
  - vercel
  - deployment
  - webapp
---

## Overview

Triển khai webapp Next.js hiện có trong thư mục `webapp/` lên tài khoản Vercel đang đăng nhập trên máy, tạo project `mapsupervision-webapp`, sử dụng domain miễn phí do Vercel cấp dạng `*.vercel.app`.

## Locked Decisions

- D1: Dùng tài khoản Vercel hiện đang đăng nhập trên máy; tạo project mới tên `mapsupervision-webapp`; không cấu hình custom domain.
- D2: Lấy các giá trị biến môi trường từ `webapp/.env.local` để cấu hình trên Vercel; không in giá trị bí mật và không commit chúng.
- D3: Cấu hình biến môi trường cho cả Production, Preview và Development.
- D4: Deploy từ thư mục `webapp/` bằng build command hiện có `next build`; không thay đổi mã nguồn khi build đang đạt.

## System Decision Impact

- Impact: none
- Decision: N/A
- Acceptance gate: Không tạo System Decision mới; chỉ chấp nhận triển khai sau khi build production và smoke test domain đạt.

## Requirements

### Functional Requirements

- FR-1: Vercel project được tạo/liên kết với tên `mapsupervision-webapp` từ thư mục `webapp/`.
- FR-2: Vercel chạy thành công build Next.js bằng script `npm run build`.
- FR-3: Các biến môi trường cần thiết trong `webapp/.env.local` được cấu hình cho cả Production, Preview và Development.
- FR-4: Deployment Production được tạo và có URL miễn phí dạng `*.vercel.app`.
- FR-5: Trang chính và các API route hiện có phản hồi sau deployment.

### Non-Functional Requirements

- NFR-1: Không ghi secret vào git, file cấu hình Vercel công khai, log hoặc câu trả lời người dùng.
- NFR-2: Không thêm custom domain hoặc dịch vụ trả phí.
- NFR-3: Không thay đổi code không liên quan đến deployment.
- NFR-4: Firebase client configuration có thể xuất hiện trong client bundle theo mô hình hiện tại; service account chỉ được dùng ở server-side route.

## Acceptance Criteria

- [ ] AC-1: Vercel project `mapsupervision-webapp` tồn tại trong tài khoản đã xác nhận.
- [ ] AC-2: Environment Variables được cấu hình ở cả Production, Preview và Development mà không lộ giá trị.
- [ ] AC-3: Production build trên Vercel hoàn tất thành công.
- [ ] AC-4: Domain mặc định `*.vercel.app` mở được trang chính và không trả lỗi 5xx.
- [ ] AC-5: Các API route động hiện có được Vercel nhận diện và phản hồi ở mức smoke test.
- [ ] AC-6: Không có secret hoặc thay đổi ngoài phạm vi deployment được commit vào repository.

## Scenarios

### Scenario 1: Happy Path

**Given** tài khoản Vercel đã đăng nhập và biến môi trường hợp lệ trong `webapp/.env.local`

**When** project `mapsupervision-webapp` được deploy từ thư mục `webapp/`

**Then** Vercel build thành công và cung cấp URL Production dạng `*.vercel.app`

### Scenario 2: Domain Project Name Unavailable

**Given** tên miền mặc định chính xác theo project name đã được sử dụng

**When** Vercel cấp domain mặc định

**Then** deployment vẫn dùng một domain miễn phí khả dụng dạng `*.vercel.app`, không chuyển sang custom domain

### Scenario 3: Missing or Invalid Secret

**Given** một biến môi trường server-side bị thiếu hoặc không parse được

**When** route cần Firebase Admin được gọi

**Then** lỗi không làm lộ giá trị secret; deployment phải được đánh dấu chưa đạt cho đến khi biến được sửa và redeploy

## Technical Notes

- Webapp là Next.js 15 với React 19.
- Build local đã xác nhận thành công bằng `npm run build` trong `webapp/`.
- Vercel CLI chưa được cài sẵn; có thể dùng `npx vercel` để tránh thay đổi dependency của project.
- Các biến môi trường được xác định trong `webapp/.env.local.example`: Firebase public config, Firebase service account JSON/file và Google Drive config. Chỉ các giá trị thực tế cần thiết sẽ được truyền an toàn vào Vercel.
- Nếu Vercel yêu cầu xác thực lại hoặc chọn scope/team, deployment sẽ dừng để người dùng xác nhận.

## Task Links

Chưa tạo task; sẽ cập nhật sau khi spec được duyệt nếu cần.

## Open Questions

- [ ] Không còn câu hỏi phạm vi; cần user review và approve spec trước khi triển khai.


## Verification Results

- Status: implemented and deployed.
- AC-1: pass — Vercel project `mapsupervision-webapp` created and linked.
- AC-2: pass — application variables configured for Production, Preview, and Development; service-account JSON is sensitive in Production/Preview and non-sensitive in Development due Vercel scope policy.
- AC-3: pass — Vercel production build completed successfully.
- AC-4: pass — `https://mapsupervision-webapp.vercel.app` returned HTTP 200.
- AC-5: pass — dynamic API routes were recognized; smoke requests returned route-level `405/401`, with no `5xx`.
- AC-6: pass — local env and service-account files remain ignored/untracked; no source code was changed for deployment.
- Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass.
- System Decision Impact: none — this deployment does not add durable architecture or product guidance.
