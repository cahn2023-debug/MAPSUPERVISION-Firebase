# Android App Release Notes

Thư mục này lưu trữ tài liệu ghi chú phát hành (Release Notes) chi tiết cho từng phiên bản của ứng dụng Android (`com.mapsupervision`).

## Cấu trúc đặt tên file
Mỗi phiên bản được lưu theo định dạng: `v<versionName>.md` (Ví dụ: `v1.1.0.md`, `v1.2.0.md`).

## Quy trình phát hành
Để tạo phiên bản mới và tự động sinh release notes:
```bash
# Xem trước thay đổi
npm run release:android -- --dry-run

# Tự động tăng patch version (v1.1.0 -> v1.1.1)
npm run release:android -- --bump=patch

# Tự động tăng minor version (v1.1.0 -> v1.2.0)
npm run release:android -- --bump=minor

# Chế độ tương tác nhập ghi chú
npm run release:android -- -i
```
