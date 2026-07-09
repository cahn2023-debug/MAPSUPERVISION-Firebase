# Requirements Document

## Introduction

Tính năng **Hoàn thiện tab Tiến độ** (`progress-tab-completion`) nhằm kết nối `ProgressHubScreen` với dữ liệu thực từ cơ sở dữ liệu, thay thế toàn bộ nội dung hardcode hiện tại. Phạm vi gồm năm hạng mục: (1) tính toán Critical Path Variances từ `NodeProgress`, (2) hiển thị Activity Timeline từ `DailyLog`, (3) dialog nhập tiến độ cho từng node, (4) hiển thị tên node thân thiện từ `GisNode`, và (5) xuất báo cáo PDF bằng Android `PdfDocument` API.

## Glossary

- **ProgressHubScreen**: Màn hình Compose hiển thị tab Tiến độ trong ứng dụng.
- **WorkspaceViewModel**: ViewModel duy nhất quản lý trạng thái cho toàn bộ workspace, được inject qua Hilt.
- **WorkspaceState**: Data class chứa toàn bộ trạng thái UI của workspace, bao gồm `constructionProgress`, `designNodes`, v.v.
- **NodeProgress**: Model chứa tiến độ một node: `planned` (%), `actual` (%), `remain` (%), `delayed` (Boolean).
- **DailyLog**: Model nhật ký công việc hàng ngày: `workItem`, `manpower`, `note`, `createdAtEpochMs`.
- **GisNode**: Model node thiết kế: `code`, `mapNumberLabel`, `contractor`.
- **DailyLogRepository**: Interface repository để đọc/ghi `DailyLog` từ DB.
- **Variance**: Chênh lệch giữa `planned` và `actual` của một `NodeProgress` (variance = planned − actual).
- **Critical_Path_Variances**: Danh sách tối đa 3 node có variance lớn nhất (chậm nhất).
- **InfrastructureItem**: Composable hiển thị tiến độ một node trong danh sách.
- **BottomSheet_Nhap_Tien_Do**: Giao diện nhập `planned%` và `actual%` cho một node cụ thể.
- **PdfExporter**: Thành phần tạo file PDF bằng Android built-in API (`android.graphics.pdf.PdfDocument`).

---

## Requirements

### Requirement 1: Critical Path Variances từ dữ liệu thực

**User Story:** Là giám sát viên, tôi muốn xem danh sách các node bị chậm tiến độ nhất, để tôi có thể ưu tiên xử lý các hạng mục rủi ro cao.

#### Acceptance Criteria

1. WHEN `ProgressHubScreen` được hiển thị, THE `ProgressHubScreen` SHALL tính toán danh sách Critical Path Variances từ `WorkspaceState.constructionProgress` thay vì dùng dữ liệu hardcode.
2. THE `ProgressHubScreen` SHALL chọn tối đa 3 `NodeProgress` có variance lớn nhất (variance = `planned` − `actual`, chỉ lấy các node có variance > 0).
3. WHEN một `NodeProgress` có variance > 0, THE `ProgressHubScreen` SHALL hiển thị variance dưới dạng ước tính ngày chậm theo công thức: `estimatedDays = round(variance * 30 / 100f)`, hiển thị dạng "−X Ngày".
4. WHEN một `NodeProgress` có variance ≤ 0 (actual ≥ planned), THE `ProgressHubScreen` SHALL hiển thị trạng thái "Đúng tiến độ" cho node đó.
5. WHEN `WorkspaceState.constructionProgress` rỗng, THE `ProgressHubScreen` SHALL hiển thị thông báo "Chưa có dữ liệu tiến độ" trong phần Critical Path Variances.
6. THE `ProgressHubScreen` SHALL hiển thị tên node thân thiện (dùng `mapNumberLabel` nếu không rỗng, ngược lại dùng `nodeCode`) làm nhãn cho mỗi dòng variance.

### Requirement 2: Activity Timeline từ DailyLog

**User Story:** Là giám sát viên, tôi muốn xem nhật ký hoạt động thực tế từ cơ sở dữ liệu, để tôi theo dõi được tiến trình công việc hàng ngày.

#### Acceptance Criteria

1. THE `WorkspaceViewModel` SHALL inject `DailyLogRepository` và load danh sách `DailyLog` theo `projectId` hiện tại trong hàm `refresh()`.
2. THE `WorkspaceState` SHALL chứa trường `dailyLogs: List<DailyLog>` với giá trị mặc định là `emptyList()`.
3. WHEN `ProgressHubScreen` được hiển thị, THE `ProgressHubScreen` SHALL hiển thị tối đa 5 `DailyLog` gần nhất (sắp xếp giảm dần theo `createdAtEpochMs`) từ `WorkspaceState.dailyLogs`.
4. WHEN hiển thị một `DailyLog`, THE `ProgressHubScreen` SHALL hiển thị `workItem` làm tiêu đề, `note` làm nội dung, `manpower` kèm đơn vị "người", và thời gian tương đối theo quy tắc: cùng ngày hiện tại hiển thị "Hôm nay, HH:mm", ngày hôm qua hiển thị "Hôm qua, HH:mm", các ngày khác hiển thị timestamp dạng "dd/MM/yyyy HH:mm".
5. WHEN `WorkspaceState.dailyLogs` rỗng, THE `ProgressHubScreen` SHALL hiển thị thông báo "Chưa có nhật ký hoạt động" trong phần Activity Timeline.
6. IF `DailyLogRepository.byProject()` trả về lỗi, THEN THE `WorkspaceViewModel` SHALL ghi log lỗi và để `dailyLogs` là danh sách rỗng (không crash).

### Requirement 3: Dialog nhập tiến độ node

**User Story:** Là giám sát viên, tôi muốn nhập tiến độ planned% và actual% cho từng node trực tiếp từ tab Tiến độ, để cập nhật dữ liệu thi công nhanh chóng.

#### Acceptance Criteria

1. WHEN người dùng tap vào một `InfrastructureItem`, THE `ProgressHubScreen` SHALL mở một `ModalBottomSheet` cho phép nhập tiến độ của node đó.
2. THE `ModalBottomSheet` SHALL hiển thị tên node (dùng `mapNumberLabel` nếu không rỗng, ngược lại dùng `nodeCode`) làm tiêu đề.
3. THE `ModalBottomSheet` SHALL chứa hai trường nhập số: "Kế hoạch (%)" với giá trị mặc định là `NodeProgress.planned` hiện tại, và "Thực tế (%)" với giá trị mặc định là `NodeProgress.actual` hiện tại.
4. WHEN người dùng nhấn nút "Lưu", THE `ProgressHubScreen` SHALL gọi `onAddConstruction(nodeCode, planned, actual)` với giá trị đã nhập và đóng BottomSheet.
5. IF giá trị nhập vào nằm ngoài khoảng [0, 100], THEN THE `ModalBottomSheet` SHALL hiển thị thông báo lỗi "Giá trị phải từ 0 đến 100" và không gọi `onAddConstruction`.
6. WHEN người dùng nhấn nút "Hủy" hoặc vuốt đóng BottomSheet, THE `ProgressHubScreen` SHALL đóng BottomSheet mà không thay đổi dữ liệu.
7. WHEN `WorkspaceState.constructionProgress` không chứa `NodeProgress` cho node được tap, THE `ModalBottomSheet` SHALL dùng giá trị mặc định 0f cho cả `planned` và `actual`.

### Requirement 4: Hiển thị tên node thân thiện trong InfrastructureItem

**User Story:** Là giám sát viên, tôi muốn thấy tên/nhãn bản đồ của node thay vì mã code thô, để dễ nhận biết hạng mục thi công.

#### Acceptance Criteria

1. WHEN `InfrastructureItem` được hiển thị, THE `InfrastructureItem` SHALL dùng `GisNode.mapNumberLabel` làm title nếu `mapNumberLabel` không rỗng.
2. WHEN `GisNode.mapNumberLabel` rỗng hoặc không tìm thấy `GisNode` tương ứng, THE `InfrastructureItem` SHALL dùng `NodeProgress.nodeCode` làm title.
3. IF cả `mapNumberLabel` lẫn `nodeCode` đều rỗng, THEN THE `InfrastructureItem` SHALL hiển thị chuỗi placeholder "Node không xác định" làm title.
4. THE `InfrastructureItem` SHALL vẫn hiển thị `NodeProgress.nodeCode` dưới dạng phụ đề (subtitle) để người dùng có thể tra cứu mã kỹ thuật.

### Requirement 5: Xuất báo cáo PDF

**User Story:** Là giám sát viên, tôi muốn xuất báo cáo tiến độ dạng PDF, để chia sẻ với các bên liên quan mà không cần kết nối mạng.

#### Acceptance Criteria

1. WHEN người dùng nhấn nút "Export PDF", THE `ProgressHubScreen` SHALL kích hoạt luồng tạo PDF.
2. THE `PdfExporter` SHALL tạo file PDF bằng `android.graphics.pdf.PdfDocument` (không dùng thư viện bên ngoài) chứa các thông tin: tên dự án (`activeProjectId`), ngày xuất (định dạng "dd/MM/yyyy"), bảng tóm tắt (planned% trung bình, actual% trung bình, số node bị chậm), và danh sách tất cả `InfrastructureItem` với tên node, planned%, actual%, trạng thái delayed.
3. THE `PdfExporter` SHALL lưu file PDF vào thư mục `Downloads` của thiết bị với tên file dạng `tiendo_[projectId]_[yyyyMMdd].pdf`.
4. WHEN file PDF được tạo thành công, THE `ProgressHubScreen` SHALL mở file bằng `Intent.ACTION_VIEW` với MIME type `application/pdf` để người dùng có thể xem hoặc chia sẻ.
5. IF không có ứng dụng nào xử lý được Intent mở PDF, THEN THE `ProgressHubScreen` SHALL hiển thị Snackbar thông báo đường dẫn file đã lưu.
6. IF quá trình tạo PDF thất bại (lỗi IO, thiếu quyền), THEN THE `ProgressHubScreen` SHALL hiển thị Snackbar thông báo lỗi cụ thể.
7. THE `PdfExporter` SHALL được thực thi trên `Dispatchers.IO` để không block UI thread.
8. WHEN `WorkspaceState.constructionProgress` rỗng, THE `PdfExporter` SHALL vẫn tạo PDF với bảng tóm tắt rỗng và ghi chú "Chưa có dữ liệu tiến độ".
