---
id: wlkka3
title: "[data-mapping-import-reliability-01] Chuẩn hóa bộ đọc dữ liệu bảng và fixtures"
status: done
priority: medium
labels:
  - from-spec
  - spec:data-mapping-import-reliability
  - spec-date:2026-08-23
createdAt: '2026-08-23T06:03:18.720Z'
updatedAt: '2026-08-23T06:11:15.272Z'
completedAt: '2026-08-23T06:11:15.272Z'
timeSpent: 0
spec: specs/2026-08-23/data-mapping-import-reliability
fulfills:
  - AC-1
  - AC-2
  - AC-3
order: 10
---
# [data-mapping-import-reliability-01] Chuẩn hóa bộ đọc dữ liệu bảng và fixtures

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bổ sung và chuẩn hóa luồng xem trước cho XLS, XLSX và CSV; hỗ trợ chọn sheet, nhận diện dòng tiêu đề, bỏ qua ghi chú, tự nhận diện delimiter và UTF-8/Windows-1258.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Có fixtures kiểm soát cho XLS, XLSX và ma trận CSV delimiter/encoding.
- [x] #2 Parser chọn đúng sheet, nhận diện đúng dòng tiêu đề và bỏ qua dòng ghi chú.
- [x] #3 Kiểm thử parser xác nhận tiêu đề, dữ liệu mẫu và tiếng Việt được đọc chính xác.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Tao fixture kiem soat cho XLS, XLSX nhieu sheet kem note va ma tran CSV (comma, semicolon, tab; UTF-8, Windows-1258)
2. Chuan hoa bo doc bang (XLSX, XLS, CSV) voi kha nang chon sheet, sniffing delimiter/encoding, bo qua dong ghi chu va phat hien dong tieu de
3. Tich hop luong doc bang vao UserFileImportService (inspect va import tabular)
4. Bo sung unit test kiem chung tieu de, du lieu mau va tieng Viet
5. Chay test storage-import va validate task
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Hoan thanh: Tao TabularReader ho tro XLSX, XLS (BIFF8) va CSV voi tu dong nhan dien delimiter, UTF-8/Windows-1258, bo qua ghi chu va chon sheet; bo sung day du unit test va fixtures.
System Decision Impact: none — Bo sung bo doc du lieu bang XLSX/XLS/CSV va fixtures, khong thay doi kien truc he thong.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
<!-- SECTION:NOTES:END -->

