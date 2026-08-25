# WebApp Release Notes

Thư mục này lưu trữ tài liệu ghi chú phát hành (Release Notes) chi tiết cho từng phiên bản của WebApp (`mapsupervision-webapp`).

## Cấu trúc đặt tên file
Mỗi phiên bản được lưu theo định dạng: `v<version>.md` (Ví dụ: `v0.1.0.md`, `v0.2.0.md`).

## Quy trình phát hành
Để tạo phiên bản mới và tự động sinh release notes:
```bash
# Xem trước thay đổi
npm run release:webapp -- --dry-run

# Tự động tăng patch version (v0.1.0 -> v0.1.1)
npm run release:webapp -- --bump=patch

# Tự động tăng minor version (v0.1.0 -> v0.2.0)
npm run release:webapp -- --bump=minor

# Chế độ tương tác nhập ghi chú
npm run release:webapp -- -i
```
