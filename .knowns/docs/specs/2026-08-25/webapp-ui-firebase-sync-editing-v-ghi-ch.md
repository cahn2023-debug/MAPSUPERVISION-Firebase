---
id: doc-fadbccd43ac27e01177e2c9c022bd7d8
title: Webapp UI, Firebase sync editing và ghi chú
description: Specification for completing the webapp UI, bidirectional Firebase sync editing, shared notes, permissions, and Vercel deployment.
createdAt: '2026-08-25T16:59:02.571Z'
updatedAt: '2026-08-25T17:23:30.623Z'
tags:
  - spec
  - approved
  - webapp
  - firebase
  - sync
  - theme
  - permissions
  - notes
---

## Overview

Hoàn thiện webapp MAP Supervision thành dashboard responsive có Dark/Light mode và hoàn chỉnh luồng dữ liệu Firebase hai chiều với Android cho nhiệm vụ, nhật ký thi công và ghi chú dùng chung. Bổ sung các thao tác chỉnh sửa/xóa an toàn theo quyền trong hai khu vực "Nhiệm vụ & Nhật ký" và "Quản trị & Cấp quyền", đồng thời deploy bản web đã kiểm chứng lên Vercel.

## Locked Decisions

- D1: Khi Android và web cùng sửa một bản ghi, bản cập nhật có `updatedAtEpochMs` mới hơn thắng. Không yêu cầu màn hình duyệt xung đột trong phạm vi này.
- D2: Ghi chú web là bản ghi dùng chung cấp dự án trong collection `note`, có tiêu đề và nội dung; tạo/sửa/xóa trên web phải được Android nhận qua sync contract hiện có.
- D3: Admin luôn có quyền chỉnh sửa. Thành viên chỉ được tạo/sửa/xóa khi request ở trạng thái APPROVED và nhóm dữ liệu cho phép thao tác tương ứng (TASKS, NOTES hoặc DEFAULT); Firestore rules là lớp thực thi cuối.
- D4: Theme mặc định theo system preference; người dùng có thể chuyển Dark/Light và lựa chọn được lưu ở trình duyệt.
- D5: Các thay đổi cloud dùng cùng envelope/sync fields với Android; xóa dùng tombstone (`isDeleted`, `deletedAtEpochMs`) để listener/pull không tái tạo bản ghi.

## System Decision Impact

- Impact: existing
- Decision: `@doc/specs/2026-08-23/firebase-project-sync-approval-approved`
- Acceptance gate: kiểm tra quyền APPROVED, data group và trạng thái REVOKED bằng rules/emulator tests trước deploy. D1 của spec này áp dụng cho các thao tác web được bổ sung; cần xác nhận không làm hỏng outbox/merge contract Android hiện có.

## Requirements

### Functional Requirements

- FR-1: Web hiển thị Dark/Light theme, khởi tạo theo system preference khi chưa có lựa chọn đã lưu, có nút chuyển theme và lưu `localStorage`.
- FR-2: Web subscribe realtime các collection `task`, `daily_log`, `note`; bản ghi từ Android xuất hiện không cần reload toàn trang.
- FR-3: Người dùng đủ quyền có thể tạo, sửa và xóa task; thay đổi giữ nguyên identity/projectId và cập nhật `updatedAtEpochMs`.
- FR-4: Người dùng đủ quyền có thể cập nhật trạng thái task TODO/IN_PROGRESS/DONE và chỉnh nội dung task.
- FR-5: Người dùng đủ quyền có thể tạo, sửa và xóa daily log; giữ các trường định dạng hiện có và đồng bộ lại Android.
- FR-6: Người dùng đủ quyền có thể tạo, sửa và xóa note cấp dự án với title/content; note dùng envelope tương thích Android.
- FR-7: Xóa nghiệp vụ phải ghi tombstone thay vì hard-delete để các thiết bị offline không đưa bản ghi cũ quay lại.
- FR-8: Admin có thể xem/chỉnh sửa phạm vi thành viên, phê duyệt/từ chối/thu hồi và cấp lại quyền; trạng thái hiển thị realtime.
- FR-9: UI chỉ hiển thị nút ghi/sửa/xóa khi quyền hiện tại cho phép, nhưng mọi write vẫn phải bị Firestore rules kiểm tra độc lập.
- FR-10: Khi lỗi permission/network, UI hiển thị trạng thái hành động được và không thông báo thành công sớm.
- FR-11: Bản build web deploy được bằng cấu hình Vercel hiện có, không commit secrets.

### Non-Functional Requirements

- NFR-1: Light mode có contrast text tối thiểu 4.5:1, border/focus state rõ; icon dùng SVG nhất quán.
- NFR-2: Responsive tối thiểu ở 375px, 768px, 1024px và 1440px; không có horizontal overflow.
- NFR-3: Nút async có loading/disabled state; touch target tối thiểu 44px; tôn trọng `prefers-reduced-motion`.
- NFR-4: Dữ liệu chỉ hiển thị trong phạm vi member hiện hành; không mở rộng quyền bằng client state.
- NFR-5: Có test cho envelope, last-write-wins, tombstone và permission-sensitive writes ở mức phù hợp với cấu trúc test hiện tại.

## Acceptance Criteria

- [ ] AC-1: Người dùng mới thấy theme theo system preference; đổi theme cập nhật toàn bộ dashboard và reload vẫn giữ lựa chọn.
- [ ] AC-2: Dashboard không có lỗi build/typecheck và không xuất hiện horizontal scroll ở các breakpoint đã nêu.
- [ ] AC-3: Một task tạo/sửa/trạng thái/xóa từ web xuất hiện đúng trong Firestore và Android sync contract; bản ghi bị xóa không tái xuất hiện sau pull.
- [ ] AC-4: Một daily log tạo/sửa/xóa từ web giữ đúng trường dữ liệu và được listener/pull Android nhận.
- [ ] AC-5: Một note tạo/sửa/xóa từ web trong collection `note` được hiển thị trên web và nhận được bởi Android.
- [ ] AC-6: Thành viên APPROVED có nhóm phù hợp thao tác được; thành viên thiếu nhóm hoặc REVOKED bị chặn bởi UI và Firestore rules.
- [ ] AC-7: Admin vẫn thực hiện được luồng cấp quyền/chỉnh phạm vi/thu hồi; audit/state không bị phá vỡ.
- [ ] AC-8: Khi hai bản cập nhật có timestamp khác nhau, dữ liệu có timestamp mới hơn là dữ liệu hiển thị cuối cùng.
- [ ] AC-9: `npm run build` và bộ test web hiện có chạy đạt; nếu Vercel CLI/auth sẵn sàng, production deploy trả về URL thành công.

## Scenarios

### Scenario 1: Chuyển theme

**Given** người dùng đã đăng nhập và chưa có theme lưu
**When** mở dashboard và chuyển Dark/Light
**Then** toàn bộ layout, card, input, tab, modal và bảng dùng token theme tương ứng; reload vẫn giữ lựa chọn.

### Scenario 2: Đồng bộ task từ Android

**Given** Android ghi task vào project-scoped collection
**When** web đang mở đúng project
**Then** task xuất hiện qua listener, có thể sửa trạng thái/nội dung nếu quyền cho phép và thay đổi ghi lại envelope fields.

### Scenario 3: Ghi chú từ web

**Given** user có quyền NOTES hoặc DEFAULT
**When** tạo/sửa/xóa note với title/content
**Then** web cập nhật optimistic/loading state đúng, Firestore lưu tombstone khi xóa và Android nhận thay đổi ở lần pull/listener kế tiếp.

### Scenario 4: Thành viên bị giới hạn

**Given** member đã APPROVED nhưng không có group TASKS/NOTES hoặc đã REVOKED
**When** member cố tạo/sửa/xóa dữ liệu
**Then** nút thao tác bị ẩn/disabled phù hợp và Firestore từ chối write nếu request bị giả mạo.

### Scenario 5: Quản trị cấp quyền

**Given** Admin mở thẻ Quản trị & Cấp quyền
**When** thay đổi group/contractor scope hoặc chuyển trạng thái request
**Then** state/audit tiếp tục đồng bộ với Android và UI hiển thị trạng thái busy/error rõ ràng.

### Scenario 6: Cập nhật đồng thời

**Given** cùng một record được web và Android sửa với hai `updatedAtEpochMs`
**When** cả hai bản cập nhật được nhận
**Then** bản có timestamp lớn hơn là bản cuối cùng được áp dụng/hiển thị.

## Technical Notes

- Giữ cấu trúc envelope hiện có trong `webapp/lib/sync.ts`; bổ sung helper chung cho update/delete thay vì hard-code riêng từng component.
- Cần phân biệt quyền UI của Admin/member từ `isAdmin`, `currentMember` và access request/data group; không coi việc ẩn nút là security boundary.
- Dùng design tokens CSS cho màu nền/text/border/surface, tránh sửa rời rạc bằng inline colors.
- Không thay đổi các module Android ngoài phần contract cần thiết để tương thích; ưu tiên kiểm tra contract Android hiện có trước khi sửa.
- Vercel deploy từ thư mục `webapp`; biến `NEXT_PUBLIC_FIREBASE_*` lấy từ environment, không đưa secret vào git.

## Task Links

- @task-xc48oq [webapp-ui-firebase-sync-editing-v-ghi-ch-01] Đồng bộ CRUD và tombstone Firebase
- @task-1czvxh [webapp-ui-firebase-sync-editing-v-ghi-ch-02] Chỉnh sửa Nhiệm vụ, Nhật ký và Ghi chú
- @task-x1frpv [webapp-ui-firebase-sync-editing-v-ghi-ch-03] Hoàn chỉnh Quản trị và Cấp quyền
- @task-exnegn [webapp-ui-firebase-sync-editing-v-ghi-ch-04] Dark Light theme và responsive UX
- @task-58omyz [webapp-ui-firebase-sync-editing-v-ghi-ch-05] Verify tích hợp và deploy Vercel

## Open Questions

- [ ] Cần xác nhận Vercel project hiện tại đã có đủ biến môi trường Firebase production trước deploy.
- [ ] Cần xác nhận Android note schema thực tế (các field ngoài title/content nếu có) trong integration verification.
