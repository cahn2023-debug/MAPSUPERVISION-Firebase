---
id: doc-0d5cbeea345b160498365b0db3f50583
title: Data Mapping Import Reliability
description: Specification for fixing data-mapping confirmation and validating tabular imports across Excel and CSV files.
createdAt: '2026-08-23T05:48:18.322Z'
updatedAt: '2026-08-23T05:48:18.322Z'
tags:
  - spec
  - draft
  - data-import
  - excel
  - csv
  - database
---

## Overview

Đặc tả việc rà soát và sửa luồng ánh xạ dữ liệu bảng vào database của DataHub, tập trung vào lỗi người dùng không thể xác nhận ánh xạ. Phạm vi bao gồm đọc file, xem trước, xác nhận ánh xạ, kiểm tra dữ liệu, xử lý trùng và ghi nguyên tử vào database theo project.

Phạm vi chỉ bao gồm các đích dữ liệu importer hiện hỗ trợ: node/tuyến GIS, khối lượng công việc và thuộc tính mạng. Không mở rộng thành cơ chế ghi tùy ý vào toàn bộ bảng database.

Tài liệu liên quan: @doc/specs/2026-08-22/full-features-specification, @doc/specs/2026-08-22/data-architecture-specification và @doc/patterns/project-scoped-database.

## Locked Decisions

- D1: Kiểm thử bằng cả fixture của repository và file thực tế; hỗ trợ `.xls`, `.xlsx`, `.csv`; cho phép chọn sheet, tự nhận diện dòng tiêu đề và bỏ qua dòng ghi chú; CSV tự nhận diện dấu phẩy, chấm phẩy hoặc tab, hỗ trợ UTF-8 và Windows-1258.
- D2: Kiểm tra toàn bộ dữ liệu trước xác nhận và hiển thị số dòng hợp lệ/lỗi. Thiếu trường bắt buộc hoặc cột không hợp lệ phải chặn xác nhận, chỉ rõ nguyên nhân và cách khắc phục. Khi có lỗi theo dòng, người dùng được chọn sửa để nhập toàn bộ hoặc chỉ nhập các dòng hợp lệ kèm báo cáo.
- D3: Hệ thống đề xuất khóa nghiệp vụ để nhận diện trùng và cho phép người dùng thay đổi. Trước khi nhập phải hiển thị số bản ghi trùng và cho chọn bỏ qua hoặc cập nhật. Việc ghi database phải nguyên tử; lỗi ghi phải hoàn tác toàn bộ lần nhập.
- D4: Chỉ sửa và kiểm thử các đích dữ liệu importer hiện hỗ trợ. Với cột ngoài mô hình chuẩn, người dùng chọn riêng từng cột; cột được xác nhận lưu dưới dạng metadata mở rộng, cột không được xác nhận bị loại bỏ.

## System Decision Impact

- Impact: none
- Decision: Không thay đổi kiến trúc database toàn hệ thống; thay đổi bị giới hạn trong hợp đồng import hiện có và vẫn tuân theo database tách biệt theo project.
- Acceptance gate: Không áp dụng.

## Requirements

### Functional Requirements

- FR-1: DataHub phải tiếp nhận `.xls`, `.xlsx` và `.csv` trong cùng luồng xem trước và ánh xạ dữ liệu bảng.
- FR-2: Với Excel, người dùng phải chọn được sheet; hệ thống phải tự nhận diện dòng tiêu đề và bỏ qua các dòng ghi chú nằm trước tiêu đề.
- FR-3: Với CSV, hệ thống phải tự nhận diện dấu phân cách dấu phẩy, chấm phẩy hoặc tab và giải mã được UTF-8 hoặc Windows-1258.
- FR-4: Sau khi đọc file, hệ thống phải hiển thị các cột, dữ liệu xem trước, ánh xạ được đề xuất và trạng thái xác nhận rõ ràng. Ánh xạ tự động/AI chỉ là gợi ý và người dùng có thể sửa.
- FR-5: Một ánh xạ hợp lệ phải cho phép thực hiện hành động xác nhận. Trạng thái loading, lỗi từ gợi ý AI hoặc lỗi đọc xem trước không được khiến thao tác xác nhận bị kẹt không có thông báo.
- FR-6: Hệ thống phải kiểm tra các trường bắt buộc và toàn bộ các dòng trước khi ghi database, hiển thị tổng số dòng hợp lệ, dòng lỗi và lỗi gắn với trường/cột/dòng tương ứng.
- FR-7: Khi dữ liệu có dòng lỗi, hệ thống phải cho người dùng chọn quay lại sửa để nhập toàn bộ hoặc chỉ nhập các dòng hợp lệ; lựa chọn nhập phần hợp lệ phải tạo báo cáo các dòng bị bỏ qua.
- FR-8: Hệ thống chỉ ghi vào các đích importer hiện hỗ trợ: node/tuyến GIS, khối lượng công việc và thuộc tính mạng.
- FR-9: Với cột ngoài mô hình chuẩn, giao diện phải cho chọn lưu từng cột vào metadata mở rộng. Cột không được chọn không được lưu.
- FR-10: Hệ thống phải đề xuất khóa nghiệp vụ nhận diện trùng, cho phép người dùng đổi tập cột khóa, và hiển thị số bản ghi trùng trước xác nhận.
- FR-11: Người dùng phải chọn được bỏ qua hoặc cập nhật các bản ghi trùng; kết quả phải phản ánh đúng lựa chọn.
- FR-12: Toàn bộ lần nhập phải được ghi nguyên tử vào database của project hiện hoạt. Bất kỳ lỗi ghi nào cũng phải hoàn tác toàn bộ thay đổi của lần nhập.
- FR-13: Bộ kiểm thử phải bao gồm fixture có kiểm soát và các file dữ liệu thực tế do người dùng cung cấp; kết quả phải kiểm chứng cả dữ liệu lưu trong database, không chỉ trạng thái UI.

### Non-Functional Requirements

- NFR-1: Không được ghi hoặc cập nhật dữ liệu của project khác project hiện hoạt.
- NFR-2: Cùng một file, sheet, ánh xạ, khóa trùng và lựa chọn xử lý phải cho kết quả xác định.
- NFR-3: Thông báo lỗi phải có thể hành động được, không chỉ hiển thị lỗi chung chung.
- NFR-4: Không được để lại dữ liệu nhập dở dang hoặc metadata mồ côi khi giao dịch thất bại.
- NFR-5: Các luồng import không phải dữ liệu bảng đang hoạt động phải tiếp tục vượt qua kiểm thử hồi quy.

## Acceptance Criteria

- [ ] AC-1: Fixture hợp lệ cho từng định dạng `.xls`, `.xlsx`, `.csv` mở được màn hình ánh xạ, hiển thị đúng tiêu đề và dữ liệu xem trước.
- [ ] AC-2: Một workbook có nhiều sheet và các dòng ghi chú trước tiêu đề cho phép chọn sheet và nhận diện đúng dòng tiêu đề.
- [ ] AC-3: CSV dùng dấu phẩy, chấm phẩy và tab ở UTF-8 hoặc Windows-1258 được đọc đúng tiếng Việt và số cột.
- [ ] AC-4: Với ánh xạ hợp lệ, hành động xác nhận khả dụng, hoàn tất và ghi đúng dữ liệu vào database của project hiện hoạt.
- [ ] AC-5: Thiếu trường bắt buộc hoặc chọn cột không có dữ liệu hợp lệ sẽ chặn xác nhận và hiển thị chính xác trường, cột cùng hướng khắc phục.
- [ ] AC-6: Với file chứa cả dòng hợp lệ và lỗi, tổng số hai nhóm được tính đúng; chế độ nhập phần hợp lệ chỉ ghi dòng hợp lệ và tạo báo cáo cho mọi dòng bị bỏ qua.
- [ ] AC-7: Với dữ liệu trùng, hệ thống đề xuất khóa, cho phép đổi khóa, hiển thị đúng số trùng; lựa chọn bỏ qua không thay đổi bản ghi cũ và lựa chọn cập nhật thay đổi đúng bản ghi cũ.
- [ ] AC-8: Mỗi cột mở rộng được chọn được lưu nguyên vẹn vào metadata của đúng bản ghi; cột không được chọn không xuất hiện trong dữ liệu lưu.
- [ ] AC-9: Khi chủ động gây lỗi ở giữa quá trình ghi, database sau lỗi giống trạng thái trước lần nhập và không có bản ghi/metadata nhập dở.
- [ ] AC-10: Import vào một project không làm thay đổi dữ liệu của project khác.
- [ ] AC-11: Các file dữ liệu thực tế do người dùng cung cấp vượt qua luồng xem trước, xác nhận và đối chiếu database theo các tiêu chí trên; mọi ngoại lệ được chuyển thành fixture hồi quy đã ẩn dữ liệu nhạy cảm.
- [ ] AC-12: Các kiểm thử import hiện có, bao gồm luồng non-Excel liên quan, tiếp tục thành công.

## Scenarios

### Scenario 1: Import file bảng hợp lệ

**Given** người dùng chọn file thuộc định dạng được hỗ trợ và ánh xạ đủ trường bắt buộc  
**When** người dùng xem trước rồi xác nhận  
**Then** thao tác hoàn tất, dữ liệu được ghi đúng vào database của project hiện hoạt và UI hiển thị kết quả nhập.

### Scenario 2: File có ghi chú và nhiều sheet

**Given** workbook có nhiều sheet và tiêu đề không nằm ở dòng đầu  
**When** người dùng chọn sheet cần nhập  
**Then** hệ thống nhận diện đúng tiêu đề, bỏ qua phần ghi chú và tạo dữ liệu xem trước đúng.

### Scenario 3: Ánh xạ không hợp lệ

**Given** một trường bắt buộc chưa được ánh xạ hoặc cột được chọn không có giá trị hợp lệ  
**When** người dùng chuẩn bị xác nhận  
**Then** xác nhận bị chặn và lỗi chỉ rõ trường/cột cần sửa.

### Scenario 4: File có dòng lỗi

**Given** kết quả kiểm tra có cả dòng hợp lệ và dòng lỗi  
**When** người dùng chọn nhập phần hợp lệ  
**Then** chỉ dòng hợp lệ được ghi và một báo cáo liệt kê đầy đủ dòng lỗi được tạo.

### Scenario 5: Dữ liệu trùng và metadata mở rộng

**Given** file có bản ghi trùng và các cột ngoài mô hình  
**When** người dùng chọn khóa nhận diện, chọn cập nhật bản ghi trùng và xác nhận một số cột metadata  
**Then** đúng bản ghi được cập nhật, metadata được chọn được lưu và các cột không chọn bị loại bỏ.

### Scenario 6: Ghi database thất bại

**Given** dữ liệu đã hợp lệ nhưng phát sinh lỗi trong quá trình ghi  
**When** giao dịch import thất bại  
**Then** toàn bộ thay đổi của lần nhập được hoàn tác và người dùng nhận thông báo có thể hành động.

## Technical Notes

- Điểm khảo sát hiện tại gồm `ExcelColumnMapping`, `ExcelPreview`, `ExcelParserUiState`, `loadExcelPreview`, `commitExcelImportDraft`, `ImportLifecycleRepositoryImpl` và `UserFileImportService`.
- Cần truy nguyên điều kiện enable/confirm và vòng đời `isLoading`/error trước khi quyết định nguyên nhân lỗi; spec không giả định sẵn lỗi nằm ở UI hay repository.
- Việc ghi phải sử dụng database của project hiện hoạt thông qua cơ chế project-scoped hiện có.
- Repository hiện không có fixture `.xls`, `.xlsx` hoặc `.csv` được phát hiện ngoài thư mục build; kế hoạch triển khai cần bổ sung fixture tối thiểu cho ma trận AC-1 đến AC-3.

## Task Links

Chưa tạo task.

## Open Questions

- [ ] Người dùng cần cung cấp hoặc chỉ rõ vị trí các file dữ liệu thực tế dùng cho AC-11 trước giai đoạn verification cuối.
