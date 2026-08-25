# Changelog

All notable changes to the MAPSUPERVISION-Firebase project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.1.0] - 2026-08-25 (Android App)

### Highlights
- Quản lý vòng đời xóa dự án local-first kèm cơ chế xác thực Admin trên Cloud.
- Tính năng Camera Overlay và đồng bộ hóa thư viện ảnh thực địa theo cấu trúc Node / Thẻ khảo sát.
- Hiển thị Minimap định vị và tối ưu hóa zoom bản đồ.

### Added
- Local-first project deletion lifecycle with admin authorization and checkpointed cloud reconciliation.
- Photo gallery node folder and survey status tag columns.
- Minimap location overlay with standard zoom level.

## [0.1.0] - 2026-08-25 (WebApp)

### Highlights
- Phiên bản WebApp quản trị bản đồ và duyệt yêu cầu truy cập dự án.
- Tích hợp Firebase Auth, Firestore access approval, và Google Drive media integration.

### Added
- Web dashboard with MapLibre GL map integration.
- Access approval workflow and role claim bootstrap scripts.
- Benchmark and project catalog migration tools.
