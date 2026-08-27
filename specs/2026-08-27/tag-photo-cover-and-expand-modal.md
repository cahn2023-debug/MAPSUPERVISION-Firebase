# Specification: Thẻ Tag Ảnh Đại Diện & Modal Mở Rộng Bảng Ảnh Thực Địa

## Overview

Tài liệu đặc tả kỹ thuật giải quyết triệt để lỗi co ngắn/bẹp hình ảnh trong các thẻ Tag tại tab **"HÌNH ẢNH & GHI CHÚ"** trên webapp MapSupervision và nâng cấp trải nghiệm hiển thị hình ảnh theo cấu trúc thư mục Tag trực quan, hiện đại:
1. **Khắc phục lỗi co hẹp hình ảnh:** Loại bỏ cơ chế flex co rúm khi thư mục có nhiều ảnh (38+ ảnh).
2. **Cấu trúc Tag Cover Card:** Mỗi thẻ Tag trong Node hiển thị gọn gàng dưới dạng 1 Card thư mục với 1 ảnh đại diện (ảnh chụp mới nhất), tên thẻ Tag, số lượng ảnh trong Tag, nút mở Lightbox nhanh và nút biểu tượng **"Mở rộng" (Expand)**.
3. **Modal Mở Rộng Bảng Ảnh (Tag Photos Grid Modal):** Khi bấm nút "Mở rộng", hệ thống mở Modal popup hiển thị toàn bộ hình ảnh trong Tag dưới dạng lưới (Responsive Grid) sắc nét, đầy đủ thông tin (Kỹ sư, thời gian chụp, trạng thái Sync Google Drive), hỗ trợ click xem Lightbox toàn màn hình và quyền gỡ ảnh cho Admin.
4. **Đồng bộ đa tuyến (Multi-route parity):** Áp dụng đồng bộ cho cả trang Webapp chính (`app/page.tsx`) và trang dự án công khai (`app/269-2026/page.tsx`).
5. **Kiểm thử & Triển khai Vercel:** Chạy toàn bộ test, build xác thực Next.js không lỗi và deploy lên Vercel Production.

---

## Locked Decisions

- **D1 (Tag Cover Card):** Mỗi thẻ Tag trong một Node thư mục sẽ hiển thị 1 thẻ Card đại diện gồm: 1 ảnh đại diện mới nhất (Cover thumbnail), tiêu đề thẻ Tag, badge số lượng ảnh (ví dụ: `38`), nút xem nhanh Lightbox và nút biểu tượng "Mở rộng" (Expand icon: ⛶ / ↗ / ⤢).
- **D2 (Tag Photos Grid Modal):** Bấm nút "Mở rộng" sẽ mở Modal chuyên dụng với tiêu đề `[Tên Node] > [Tên Tag] ([Số lượng] ảnh)`, hiển thị toàn bộ ảnh theo Grid layout rộng rãi, cuộn mượt mà, không bị co ngắn. Mỗi ảnh trong modal cho phép click mở Lightbox chi tiết hoặc gỡ ảnh (nếu là Admin).
- **D3 (Sửa triệt để Flex Shrink & Layout CSS):** Đặt `flex-shrink: 0`, chiều cao và tỉ lệ ảnh cố định (`aspect-ratio: 4/3` hoặc `aspect-ratio: 16/9`), overflow hợp lý giúp hình ảnh luôn giữ đúng tỉ lệ không bị biến dạng.
- **D4 (Áp dụng toàn diện):** Cập nhật đồng bộ trên trang Webapp quản trị chính (`/app/page.tsx`) và trang xem nhanh Dự án (`/app/269-2026/page.tsx`).
- **D5 (Xác thực & Deploy Vercel):** Chạy `npm test` trong webapp, kiểm tra `npm run build` thành công 100%, sau đó triển khai lên Vercel production.

---

## System Decision Impact

- **Impact:** none (Cải tiến UI/UX webapp, không thay đổi cấu trúc Firestore hay Android schema).
- **Acceptance gate:** Giao diện thẻ Tag không còn bị bẹp ảnh, hiển thị 1 ảnh cover đẹp mắt, modal mở rộng mượt mà và build/deploy Vercel thành công.

---

## Requirements

### Functional Requirements

- **FR-1:** Tại tab "HÌNH ẢNH & GHI CHÚ" trong Node có thẻ Tag:
  - Hiển thị danh sách các thẻ Tag dưới dạng các Folder Cover Card (Card Thư mục Tag).
  - Ảnh đại diện lấy từ ảnh có `capturedAtEpochMs` mới nhất trong Tag đó.
  - Hiển thị nhãn thẻ tag (🏷️ Tên Tag), số lượng ảnh (`N ảnh`).
  - Có nút biểu tượng "Mở rộng" rõ ràng, nổi bật với tooltip giải thích ("Mở rộng xem toàn bộ N ảnh trong thẻ Tag này").
  - Khi click vào ảnh đại diện: Mở Lightbox xem ảnh trực tiếp.
  - Khi click vào nút "Mở rộng": Mở Tag Photos Grid Modal.
- **FR-2:** Xử lý thẻ ảnh "Chưa gắn tag" (Untagged photos):
  - Tương tự như thẻ Tag thông thường: hiển thị 1 ảnh đại diện chưa gắn tag mới nhất + nút "Mở rộng" xem toàn bộ ảnh chưa gắn tag trong Node.
- **FR-3:** Tag Photos Grid Modal:
  - Header: Hiển thị icon thư mục, tên Node, tên Thẻ Tag, số lượng ảnh, nút đóng (✕ hoặc phím Esc).
  - Body: Lưới ảnh responsive (2 cột trên mobile, 3-4 cột trên tablet/desktop). Mỗi ảnh hiển thị thumbnail sắc nét, nhãn trạng thái Sync, thời gian chụp, tên kỹ sư và nút gỡ ảnh (nếu có quyền Admin).
  - Tương tác: Click vào bất kỳ ảnh nào trong Modal sẽ mở Lightbox toàn màn hình với danh sách phát (playlist) là toàn bộ ảnh trong Tag đó.
- **FR-4:** Đồng bộ hóa trên trang Public `/269-2026`:
  - Áp dụng cấu trúc Tag Cover Card và Modal mở rộng tương đương để người xem công khai có trải nghiệm đồng nhất.

### Non-Functional Requirements

- **NFR-1 (UI/UX Pro Max):** Giao diện chuẩn Dark/Light mode, hiệu ứng hover mượt mà (scale nhẹ, glow viền khi hover), bo góc tinh tế, font số liệu rõ nét, tuân thủ nguyên tắc không sử dụng màu tím sến, dùng accent color theo design system của dự án.
- **NFR-2 (Performance):** Tối ưu hóa ảnh với kích thước phù hợp (`imageUrlForPhoto(photo, 600)`), lazy loading mượt mà, không giật lag khi mở Modal nhiều ảnh.
- **NFR-3 (Deploy Reliability):** Next.js build không có lỗi TypeScript, lint hoặc runtime error; triển khai Vercel thành công.

---

## Acceptance Criteria

- [ ] **AC-1:** Trong tab "HÌNH ẢNH & GHI CHÚ", các cột Tag không còn bị lỗi co dẹp thành các dải mỏng 10px; mỗi Tag hiển thị dạng 1 Cover Card đẹp mắt với 1 ảnh đại diện.
- [ ] **AC-2:** Thẻ Tag hiển thị đúng tên Tag, số lượng ảnh và icon "Mở rộng".
- [ ] **AC-3:** Bấm vào icon "Mở rộng" trên thẻ Tag (ví dụ: `Thi công (38)`) sẽ mở Modal hiển thị đủ 38 ảnh trong lưới dạng bảng rõ ràng, đẹp mắt.
- [ ] **AC-4:** Bấm vào một ảnh bất kỳ trong Modal sẽ mở Lightbox xem chi tiết ảnh đó và có thể bấm nút Trước/Sau (hoặc mũi tên bàn phím) để duyệt qua toàn bộ ảnh của Tag.
- [ ] **AC-5:** Trang `/269-2026` hoạt động đồng bộ với tính năng Cover Card và Modal mở rộng.
- [ ] **AC-6:** Chạy test và `npm run build` trong webapp đạt kết quả 0 lỗi và hoàn tất deploy Vercel.

---

## Scenarios

### Scenario 1: Xem thư mục Tag "Thi công" có 38 ảnh
**Given** Người dùng đăng nhập vào webapp và mở tab "HÌNH ẢNH & GHI CHÚ", mở Node "NGÃ 3 PHÙNG".
**When** Quan sát khu vực thẻ Tag của Node này.
**Then** Thấy 1 Card Tag "Thi công" hiển thị ảnh chụp mới nhất làm ảnh đại diện, kèm badge số "38" và biểu tượng nút "Mở rộng".

### Scenario 2: Mở rộng xem toàn bộ 38 ảnh trong Tag
**Given** Người dùng đang ở Card Tag "Thi công".
**When** Bấm vào nút biểu tượng "Mở rộng".
**Then** Một Modal popup mở ra với tiêu đề "NGÃ 3 PHÙNG > Thi công (38 ảnh)" và hiển thị lưới 38 ảnh với đầy đủ thông tin kỹ sư, ngày chụp và trạng thái sync.

### Scenario 3: Bấm xem ảnh trong Modal
**Given** Modal xem 38 ảnh của Tag "Thi công" đang mở.
**When** Người dùng click vào ảnh thứ 5 trong lưới.
**Then** Trình xem phóng to Lightbox mở ra với ảnh thứ 5, góc trên hiển thị "5 / 38", người dùng có thể nhấn phím mũi tên trái/phải để duyệt tiếp.

---

## Technical Notes

- Files cần chỉnh sửa:
  - `webapp/app/page.tsx`: Cập nhật component `TagFolderCard`, `TagPhotosModal`, tích hợp vào `photoNodeGroups` rendering logic.
  - `webapp/app/269-2026/page.tsx`: Cập nhật tương ứng cho trang public.
  - `webapp/app/globals.css`: Bổ sung styles cho `.tag-folder-card`, `.tag-folder-cover`, `.tag-expand-btn`, `.tag-photos-modal`, `.tag-photos-grid` và sửa flex shrink.
- Deploy:
  - Lệnh kiểm tra: `npm test` & `npm run build` trong thư mục `webapp`.
  - Lệnh deploy: `npx vercel --prod --yes` trong thư mục `webapp`.

---

## Open Questions

- Không còn câu hỏi tồn đọng (đã giải quyết qua Socratic Phase 0).
