# TÀI LIỆU CẤU TRÚC CƠ SỞ DỮ LIỆU & SCHEMA

Hệ thống MapSupervision sử dụng **Room Database** để quản lý cơ sở dữ liệu SQLite dưới dạng cấu trúc hướng đối tượng an toàn trên Android. Toàn bộ thông tin từ bản đồ GIS, vật tư, ảnh hiện trường, nhật ký giám sát cho tới bộ nhớ đệm AI đều được đồng bộ chặt chẽ.

- **Lớp Database chính**: `MapSupervisionDatabase` (phiên bản hiện tại: **43**).
- **Cơ chế lưu trữ**: Mỗi dự án có một file DB độc lập nằm trong thư mục lưu trữ riêng (dạng Project-scoped Database), được quản lý bởi `ProjectScopedDatabaseProvider`.

---

## 1. Danh Sách Toàn Bộ Các Bảng Dữ Liệu (Entities)

Dự án định nghĩa tổng cộng **27 thực thể (Entities)**:

### 1.1. Bảng `ai_action_log` (Lớp `AiActionLogEntity`)
Nhật ký ghi lại các hành động, lệnh điều khiển hệ thống được AI dịch và thực hiện.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `rawInput` (TEXT, NOT NULL): -
  - `actionType` (TEXT, NOT NULL): Loại hành động AI đã thực hiện
  - `draftJson` (TEXT, NOT NULL): -
  - `confidence` (INTEGER, NOT NULL): -
  - `status` (TEXT, NOT NULL): Trạng thái của đầu việc (TODO, IN_PROGRESS, DONE)
  - `timestamp` (INTEGER, NOT NULL): -

### 1.2. Bảng `ai_decision_cache` (Lớp `AiDecisionCacheEntity`)
Bộ nhớ đệm kết quả phân tích AI (mã băm payload → kết quả JSON) giúp tối ưu CPU và năng lượng.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `capability` (TEXT, NOT NULL): Tính năng hoặc kỹ năng AI được yêu cầu
  - `payloadHash` (TEXT, NOT NULL): Mã băm dữ liệu yêu cầu để đối chiếu bộ nhớ đệm
  - `resultJson` (TEXT, NOT NULL): Kết quả xử lý của AI lưu dưới dạng chuỗi JSON
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)

### 1.3. Bảng `chat_history` (Lớp `ChatHistoryEntity`)
Lịch sử hội thoại của kỹ sư với trợ lý AI Gemma cục bộ tích hợp trong ứng dụng.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `role` (TEXT, NOT NULL): Vai trò trong hội thoại AI (user: Người dùng, model: Trợ lý AI)
  - `text` (TEXT, NOT NULL): Nội dung tin nhắn hội thoại
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)

### 1.4. Bảng `daily_log` (Lớp `DailyLogEntity`)
Nhật ký thi công hàng ngày trên công trường, ghi nhận khối lượng thực tế, số lượng nhân công, thời tiết, và hình ảnh liên kết tại hiện trường.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `workItem` (TEXT, NOT NULL): Tên đầu mục công việc nhật ký thi công
  - `manpower` (INTEGER, NOT NULL): Số lượng nhân công thực hiện công việc
  - `note` (TEXT, NOT NULL): Nội dung ghi chú nhật ký hoặc bàn giao
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `weather` (TEXT, NOT NULL): Trạng thái thời tiết ghi nhận tại công trường
  - `temperature` (REAL, NOT NULL): Nhiệt độ ghi nhận tại công trường (độ C)
  - `dateEpochDay` (INTEGER, NOT NULL): Ngày thực thi tính từ Epoch (số ngày)
  - `volume` (REAL, NOT NULL): Khối lượng thực hiện của đầu việc
  - `unit` (TEXT, NOT NULL): Đơn vị đo lường (m, m3, cột, cái...)
  - `categoryName` (TEXT, NOT NULL): Tên nhóm danh mục công việc thi công
  - `batchGroupId` (TEXT, NOT NULL): ID gom nhóm công việc hàng ngày
  - `photoMatchOffsetMinutes` (INTEGER, NOT NULL): Chênh lệch múi giờ khi so khớp ảnh (phút)
  - `nodeId` (TEXT, NULL): ID nút liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `routeId` (TEXT, NULL): ID đoạn tuyến liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.5. Bảng `daily_log_nodes` (Lớp `DailyLogNodeEntity`)
Bảng liên kết nhiều-nhiều giữa nhật ký thi công daily_log và các điểm nút gis_node để ghi nhận tiến độ chi tiết.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `dailyLogId` (TEXT, NOT NULL): Mã nhật ký thi công liên kết (Khóa ngoại)
  - `nodeId` (TEXT, NULL): ID nút liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `nodeCodeSnapshot` (TEXT, NOT NULL): -
  - `createdAtEpochMs` (INTEGER, NOT NULL): -

### 1.6. Bảng `daily_log_photos` (Lớp `DailyLogPhotoEntity`)
Bảng liên kết nhiều-nhiều giữa nhật ký thi công daily_log và ảnh site_photos chụp tại hiện trường.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `dailyLogId` (TEXT, NOT NULL): Mã nhật ký thi công liên kết (Khóa ngoại)
  - `photoId` (TEXT, NOT NULL): Mã hình ảnh liên kết (Khóa ngoại)
  - `createdAtEpochMs` (INTEGER, NOT NULL): -

### 1.7. Bảng `event_outbox` (Lớp `EventOutboxEntity`)
Bảng lưu trữ sự kiện nghiệp vụ tạm thời (Outbox pattern) phục vụ đồng bộ dữ liệu giữa thiết bị cục bộ và máy chủ.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NULL): -
  - `eventType` (TEXT, NOT NULL): Loại sự kiện nghiệp vụ
  - `payloadJson` (TEXT, NOT NULL): -
  - `status` (TEXT, NOT NULL): Trạng thái xử lý (TODO, IN_PROGRESS, DONE, v.v.)
  - `availableAtEpochMs` (INTEGER, NOT NULL): -
  - `createdAtEpochMs` (INTEGER, NOT NULL): -
  - `dispatchedAtEpochMs` (INTEGER, NULL): -

### 1.8. Bảng `gis_node` (Lớp `GisNodeEntity`)
Lưu trữ thông tin chi tiết và tọa độ các điểm nút GIS (như cột điện, hố ga, tủ cáp) trên bản đồ giám sát thi công.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `code` (TEXT, NOT NULL): Mã hiệu duy nhất của đối tượng
  - `contractor` (TEXT, NOT NULL): Đơn vị/Nhà thầu phụ trách thi công
  - `latitude` (REAL, NOT NULL): Vĩ độ địa lý (WGS-84)
  - `longitude` (REAL, NOT NULL): Kinh độ địa lý (WGS-84)
  - `mapNumberLabel` (TEXT, NOT NULL): Nhãn số hiển thị trên bản đồ
  - `workVolumeSummary` (TEXT, NOT NULL): Tóm tắt khối lượng công việc thiết kế tại nút
  - `importedFileId` (TEXT, NULL): ID của tệp tin nhập liệu thiết kế liên kết (Khóa ngoại)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.9. Bảng `gis_route` (Lớp `GisRouteEntity`)
Các tuyến đường/đoạn cáp nối giữa các điểm nút GIS, bao gồm danh sách tọa độ các điểm uốn dọc tuyến và chiều dài thiết kế.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `code` (TEXT, NOT NULL): Mã hiệu duy nhất của đối tượng
  - `contractor` (TEXT, NOT NULL): Đơn vị/Nhà thầu phụ trách thi công
  - `startNodeCode` (TEXT, NOT NULL): Mã điểm nút bắt đầu tuyến
  - `endNodeCode` (TEXT, NOT NULL): Mã điểm nút kết thúc tuyến
  - `points` (TEXT, NOT NULL): Chuỗi tọa độ điểm uốn dọc tuyến (định dạng: lat,lon;lat,lon;...)
  - `importedFileId` (TEXT, NULL): ID của tệp tin nhập liệu thiết kế liên kết (Khóa ngoại)
  - `designLength` (TEXT, NULL): Chiều dài thiết kế của tuyến (ví dụ: "150 m")
  - `startNodeId` (TEXT, NULL): ID nút bắt đầu liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `endNodeId` (TEXT, NULL): ID nút kết thúc liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.10. Bảng `import_audit` (Lớp `ImportAuditEntity`)
Nhật ký kiểm toán ghi lại các hoạt động, thao tác trong quy trình nhập liệu dữ liệu bản đồ thiết kế.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `importSessionId` (TEXT, NOT NULL): Mã phiên nhập liệu thiết kế liên kết (Khóa ngoại)
  - `action` (TEXT, NOT NULL): -
  - `actor` (TEXT, NOT NULL): -
  - `payloadJson` (TEXT, NOT NULL): -
  - `createdAtEpochMs` (INTEGER, NOT NULL): -

### 1.11. Bảng `import_conflict` (Lớp `ImportConflictEntity`)
Lưu trữ và xử lý các xung đột dữ liệu bản vẽ phát sinh khi có phiên bản thiết kế mới.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `importSessionId` (TEXT, NOT NULL): Mã phiên nhập liệu thiết kế liên kết (Khóa ngoại)
  - `featureBusinessCode` (TEXT, NOT NULL): -
  - `conflictType` (TEXT, NOT NULL): Loại xung đột dữ liệu thiết kế
  - `severity` (TEXT, NOT NULL): -
  - `details` (TEXT, NOT NULL): Chi tiết kết quả thực hiện hành động
  - `resolvedBy` (TEXT, NULL): -
  - `resolvedAtEpochMs` (INTEGER, NULL): -
  - `createdAtEpochMs` (INTEGER, NOT NULL): -

### 1.12. Bảng `import_session` (Lớp `ImportSessionEntity`)
Quản lý thông tin phiên nhập bản vẽ/dữ liệu thiết kế (import session) từ KML/Excel.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `sourceKind` (TEXT, NOT NULL): -
  - `sourceFileName` (TEXT, NOT NULL): -
  - `sourceFileType` (TEXT, NOT NULL): -
  - `sourceFilePath` (TEXT, NOT NULL): -
  - `status` (TEXT, NOT NULL): Trạng thái xử lý (TODO, IN_PROGRESS, DONE, v.v.)
  - `createdAtEpochMs` (INTEGER, NOT NULL): -
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `importedFileId` (TEXT, NULL): Mã tệp tin nhập liệu thiết kế liên kết (Khóa ngoại)
  - `featureCount` (INTEGER, NOT NULL): -
  - `conflictCount` (INTEGER, NOT NULL): -
  - `warningCount` (INTEGER, NOT NULL): -
  - `message` (TEXT, NOT NULL): -

### 1.13. Bảng `import_version` (Lớp `ImportVersionEntity`)
Quản lý thông tin phiên bản các bản vẽ thiết kế được nhập vào hệ thống.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `importSessionId` (TEXT, NOT NULL): Mã phiên nhập liệu thiết kế liên kết (Khóa ngoại)
  - `versionNumber` (INTEGER, NOT NULL): -
  - `sourceHash` (TEXT, NOT NULL): -
  - `createdAtEpochMs` (INTEGER, NOT NULL): -
  - `createdBy` (TEXT, NOT NULL): -
  - `note` (TEXT, NOT NULL): -

### 1.14. Bảng `imported_files` (Lớp `ImportedFileEntity`)
Tệp tin thiết kế kỹ thuật (Excel, KML, KMZ, DOCX) đã được nhập vào hệ thống để trích xuất dữ liệu bản đồ.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `fileName` (TEXT, NOT NULL): Tên tệp tin gốc đã nhập
  - `fileType` (TEXT, NOT NULL): Loại tệp nhập liệu (EXCEL, KML, KMZ, DOCX)
  - `storedPath` (TEXT, NOT NULL): Đường dẫn lưu trữ file thiết kế trên thiết bị
  - `summary` (TEXT, NOT NULL): Tóm tắt kết quả nhập liệu (số lượng nút/tuyến trích xuất)
  - `importedAtEpochMs` (INTEGER, NOT NULL): Thời gian nhập tệp tin vào hệ thống
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.15. Bảng `material_declaration` (Lớp `MaterialDeclarationEntity`)
Khai báo vật tư kỹ thuật, tỷ lệ phối trộn, đơn vị đo lường và chứng chỉ kiểm định chất lượng.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `workName` (TEXT, NOT NULL): Tên hạng mục công việc thiết kế
  - `materialName` (TEXT, NOT NULL): Tên loại vật tư thi công
  - `ratio` (REAL, NOT NULL): Tỷ lệ định mức phối trộn vật tư
  - `unit` (TEXT, NOT NULL): Đơn vị đo lường (m, m3, cột, cái...)
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `batchId` (TEXT, NULL): -
  - `workCategoryId` (TEXT, NULL): Mã danh mục công việc thi công liên kết (Khóa ngoại)

### 1.16. Bảng `material_handover` (Lớp `MaterialHandoverEntity`)
Biên bản bàn giao vật tư tại hiện trường cho các nhà thầu thi công.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `nodeCode` (TEXT, NOT NULL): Mã điểm nút GIS (ví dụ: N1, G1)
  - `workName` (TEXT, NOT NULL): Tên hạng mục công việc thiết kế
  - `materialName` (TEXT, NOT NULL): -
  - `contractor` (TEXT, NOT NULL): Đơn vị/Nhà thầu phụ trách thi công
  - `quantity` (REAL, NOT NULL): Số lượng vật tư bàn giao/kế hoạch
  - `unit` (TEXT, NOT NULL): Đơn vị đo lường (m, m3, cột, cái...)
  - `handoverDateEpochDay` (INTEGER, NOT NULL): Ngày bàn giao vật tư tại hiện trường (Epoch day)
  - `note` (TEXT, NOT NULL): Nội dung ghi chú nhật ký hoặc bàn giao
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `nodeId` (TEXT, NULL): ID nút liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `materialDeclarationId` (TEXT, NULL): Mã khai báo nguồn gốc vật tư liên kết (Khóa ngoại)
  - `workCategoryId` (TEXT, NULL): Mã danh mục công việc thi công liên kết (Khóa ngoại)
  - `receiver` (TEXT, NOT NULL): Người nhận bàn giao vật tư

### 1.17. Bảng `node_progress` (Lớp `NodeProgressEntity`)
Bảng tiến độ tổng hợp của điểm nút GIS, phản ánh khối lượng kế hoạch, thực tế hoàn thành và cảnh báo trễ hạn.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `planned` (REAL, NOT NULL): Khối lượng kế hoạch thi công của điểm nút
  - `actual` (REAL, NOT NULL): Khối lượng thực tế đã hoàn thành tại điểm nút
  - `remain` (REAL, NOT NULL): Khối lượng còn lại cần thực hiện
  - `delayed` (INTEGER, NOT NULL): Cảnh báo trễ tiến độ thi công (0: Bình thường, 1: Trễ)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): Thời gian cập nhật gần nhất (Epoch ms)
  - `nodeId` (TEXT, NULL): ID nút liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.18. Bảng `note` (Lớp `NoteEntity`)
Ghi chú tự do của kỹ sư giám sát hiện trường liên kết với các đối tượng cụ thể (nút hoặc tuyến).
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `content` (TEXT, NOT NULL): Nội dung chi tiết của ghi chú hoặc tài liệu RAG
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `objectNodeId` (TEXT, NULL): ID điểm nút liên kết với ghi chú/công việc (Khóa ngoại)
  - `objectRouteId` (TEXT, NULL): ID đoạn tuyến liên kết với ghi chú/công việc (Khóa ngoại)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.19. Bảng `photo_tags` (Lớp `PhotoTagEntity`)
Bảng liên kết nhiều-nhiều giữa ảnh chụp site_photos và các thẻ từ khóa gắn kèm để phục vụ tra cứu phân loại.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): -
  - `projectId` (TEXT, NOT NULL): -
  - `photoId` (TEXT, NOT NULL): Mã hình ảnh liên kết (Khóa ngoại)
  - `tagCode` (TEXT, NOT NULL): -
  - `createdAtEpochMs` (INTEGER, NOT NULL): -

### 1.20. Bảng `projects` (Lớp `ProjectEntity`)
Danh sách dự án trong hệ thống. Quản lý thông tin chung về các dự án và đường dẫn cơ sở dữ liệu riêng biệt của từng dự án.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `name` (TEXT, NOT NULL): Tên đối tượng hoặc tên danh mục
  - `slug` (TEXT, NOT NULL): Đường dẫn rút gọn của dự án (URL-friendly)
  - `isArchived` (INTEGER, NOT NULL): Trạng thái đã đóng băng/lưu trữ (0: Hoạt động, 1: Lưu trữ)
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `metadataVersion` (INTEGER, NOT NULL): Phiên bản cấu trúc metadata thiết kế
  - `updatedAtEpochMs` (INTEGER, NOT NULL): Thời gian cập nhật gần nhất (Epoch ms)
  - `storageMode` (TEXT, NOT NULL): Chế độ lưu trữ tệp tin CSDL (LEGACY_SHARED/PROJECT_SCOPED)
  - `projectDbPath` (TEXT, NOT NULL): Đường dẫn vật lý đến file CSDL độc lập của dự án
  - `projectCode` (TEXT, NULL): Mã ký hiệu viết tắt của dự án
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.21. Bảng `rag_document_embedding` (Lớp `RagDocumentEmbeddingEntity`)
Lưu trữ các đoạn văn bản tài liệu kỹ thuật đã nhúng vector (embeddings) để phục vụ tra cứu thông minh RAG.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `docType` (TEXT, NOT NULL): Phân loại tài liệu RAG
  - `sourceId` (TEXT, NOT NULL): ID nguồn tài liệu RAG gốc
  - `text` (TEXT, NOT NULL): Nội dung tin nhắn hội thoại
  - `contentHash` (TEXT, NOT NULL): Mã băm nội dung của đoạn tài liệu
  - `embeddingBlob` (BLOB, NOT NULL): Dữ liệu vector nhúng (Binary)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): Thời gian cập nhật gần nhất (Epoch ms)
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.22. Bảng `report_draft` (Lớp `ReportDraftEntity`)
Dự thảo báo cáo giám sát thi công, tổng hợp các vấn đề rủi ro và khuyến nghị hành động tại hiện trường.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `title` (TEXT, NOT NULL): Tiêu đề của đầu việc hoặc dự thảo báo cáo
  - `executiveSummary` (TEXT, NOT NULL): Bản tóm tắt tổng quan báo cáo giám sát
  - `riskSection` (TEXT, NOT NULL): Đánh giá các yếu tố rủi ro tại hiện trường
  - `recommendedActionsCsv` (TEXT, NOT NULL): Đề xuất hành động khuyến nghị khắc phục (CSV)
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)

### 1.23. Bảng `site_photos` (Lớp `SitePhotoEntity`)
Hình ảnh và video định vị hiện trường được chụp bởi kỹ sư giám sát. Lưu giữ tọa độ, độ chính xác và thông tin chống giả lập vị trí GPS.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `objectCode` (TEXT, NOT NULL): Mã ký hiệu của đối tượng liên quan (Hố ga/Tuyến cáp)
  - `tagCodesCsv` (TEXT, NOT NULL): Danh sách thẻ tag ảnh chụp (phân tách bởi dấu phẩy)
  - `filePath` (TEXT, NOT NULL): Đường dẫn vật lý đến file ảnh gốc trên thiết bị
  - `thumbnailPath` (TEXT, NOT NULL): Đường dẫn vật lý đến file ảnh thu nhỏ (thumbnail)
  - `latitude` (REAL, NULL): Vĩ độ địa lý (WGS-84)
  - `longitude` (REAL, NULL): Kinh độ địa lý (WGS-84)
  - `locationAccuracyM` (REAL, NULL): Độ chính xác của định vị GPS (mét)
  - `isGpsMocked` (INTEGER, NOT NULL): Đánh dấu nếu phát hiện sử dụng vị trí GPS giả lập (0: Thật, 1: Giả lập)
  - `locationStatus` (TEXT, NOT NULL): Trạng thái tín hiệu GPS (AVAILABLE, MISSING, MOCKED)
  - `engineer` (TEXT, NOT NULL): Kỹ sư thực hiện chụp ảnh/giám sát
  - `capturedAtEpochMs` (INTEGER, NOT NULL): Thời điểm chụp ảnh/video hiện trường
  - `matchedAtEpochMs` (INTEGER, NOT NULL): Thời điểm thực hiện so khớp ảnh tự động
  - `matchingTimeOffsetMs` (INTEGER, NOT NULL): -
  - `mediaType` (TEXT, NOT NULL): Loại phương tiện truyền thông (IMAGE: Ảnh, VIDEO: Video)
  - `mimeType` (TEXT, NOT NULL): Định dạng MIME của tệp (ví dụ: image/jpeg)
  - `durationMs` (INTEGER, NOT NULL): Thời lượng của tệp video (miligiây)
  - `address` (TEXT, NULL): Địa chỉ thực tế giải mã từ tọa độ địa lý (Geocoded address)
  - `captureNote` (TEXT, NULL): Ghi chú nhanh của kỹ sư khi chụp ảnh
  - `matchedNodeId` (TEXT, NULL): -
  - `matchedRouteId` (TEXT, NULL): -
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `syncStatus` (TEXT, NOT NULL): -
  - `remoteUrl` (TEXT, NULL): -
  - `lastSyncAttemptEpochMs` (INTEGER, NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.24. Bảng `task` (Lớp `TaskEntity`)
Các đầu việc cần thực hiện tại hiện trường, quản lý trạng thái, mô tả và thời điểm hoàn thành.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `title` (TEXT, NOT NULL): Tiêu đề của đầu việc hoặc dự thảo báo cáo
  - `description` (TEXT, NOT NULL): Mô tả chi tiết nội dung
  - `status` (TEXT, NOT NULL): Trạng thái của đầu việc (TODO, IN_PROGRESS, DONE)
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `completedAtEpochMs` (INTEGER, NULL): Thời gian hoàn thành đầu việc
  - `objectNodeId` (TEXT, NULL): ID điểm nút liên kết với ghi chú/công việc (Khóa ngoại)
  - `objectRouteId` (TEXT, NULL): ID đoạn tuyến liên kết với ghi chú/công việc (Khóa ngoại)
  - `updatedAtEpochMs` (INTEGER, NOT NULL): -
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

### 1.25. Bảng `work_categories` (Lớp `WorkCategoryEntity`)
Danh mục phân loại công việc thi công (ví dụ: đào móng, dựng cột, kéo cáp) của dự án.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `name` (TEXT, NOT NULL): Tên đối tượng hoặc tên danh mục
  - `unit` (TEXT, NOT NULL): Đơn vị đo lường (m, m3, cột, cái...)
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)

### 1.26. Bảng `work_plan` (Lớp `WorkPlanEntity`)
Kế hoạch thi công chi tiết theo ngày cho các nút/tuyến cụ thể, bao gồm khối lượng kế hoạch và trạng thái.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `title` (TEXT, NOT NULL): Tiêu đề của đầu việc hoặc dự thảo báo cáo
  - `description` (TEXT, NOT NULL): Mô tả chi tiết nội dung
  - `plannedDateEpochDay` (INTEGER, NOT NULL): Ngày thực hiện kế hoạch (Epoch day)
  - `nodeCode` (TEXT, NULL): Mã điểm nút GIS (ví dụ: N1, G1)
  - `routeCode` (TEXT, NULL): Mã tuyến đường/đoạn cáp GIS (ví dụ: LINE_A)
  - `taskId` (TEXT, NULL): ID đầu việc liên quan trong kế hoạch (Khóa ngoại)
  - `sourceRawInput` (TEXT, NOT NULL): Dữ liệu đầu vào thô của kế hoạch
  - `createdAtEpochMs` (INTEGER, NOT NULL): Thời gian tạo (Miligiây từ Epoch)
  - `quantity` (REAL, NOT NULL): Số lượng vật tư bàn giao/kế hoạch
  - `unit` (TEXT, NOT NULL): Đơn vị đo lường (m, m3, cột, cái...)
  - `batchGroupId` (TEXT, NOT NULL): ID gom nhóm công việc hàng ngày
  - `nodeId` (TEXT, NULL): ID nút liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `routeId` (TEXT, NULL): ID đoạn tuyến liên kết trực tiếp trong CSDL (Khóa ngoại)

### 1.27. Bảng `work_volume_progress` (Lớp `MaterialProgressEntity`)
Tiến độ chi tiết cho từng loại vật tư tại điểm nút hoặc tuyến đường, phục vụ quản lý cấp phát vật tư thi công.
* **Cấu trúc các cột**:
  - `id` (TEXT, NOT NULL): Mã định danh duy nhất (UUID)
  - `projectId` (TEXT, NOT NULL): ID dự án liên kết (Khóa ngoại)
  - `nodeCode` (TEXT, NOT NULL): Mã điểm nút GIS (ví dụ: N1, G1)
  - `workName` (TEXT, NOT NULL): -
  - `plannedQty` (REAL, NOT NULL): Số lượng vật tư kế hoạch thiết kế
  - `actualQty` (REAL, NOT NULL): Số lượng vật tư thực tế đã sử dụng
  - `updatedAtEpochMs` (INTEGER, NOT NULL): Thời gian cập nhật gần nhất (Epoch ms)
  - `unit` (TEXT, NOT NULL): Đơn vị đo lường (m, m3, cột, cái...)
  - `nodeId` (TEXT, NULL): ID nút liên kết trực tiếp trong CSDL (Khóa ngoại)
  - `isDeleted` (INTEGER, NOT NULL): Đánh dấu trạng thái xóa logic (0: Hoạt động, 1: Đã xóa logic)
  - `deletedAtEpochMs` (INTEGER, NULL): Thời điểm xóa logic (Epoch Milliseconds)

---

## 2. Lịch Sử Nâng Cấp Schema Nổi Bật (Migrations)

Quy trình phát triển cơ sở dữ liệu đã trải qua nhiều đợt nâng cấp quan trọng, đảm bảo không làm mất dữ liệu của người dùng khi cài đè ứng dụng:

- **Migration 8 lên 9**: Tối ưu tốc độ tìm kiếm bằng việc thêm chỉ mục (`Index`) cho các trường `projectId` và `code` trên các bảng `gis_node`, `gis_route`, `task`, `note`.
- **Migration 9 lên 10**: Bổ sung thông tin thời tiết (`weather`, `temperature`) và ngày cụ thể (`dateEpochDay`) vào nhật ký `daily_log`. Tạo mới bảng danh mục công việc `work_categories`.
- **Migration 11 lên 12**: Bổ sung cơ chế lưu trữ phân đoạn dự án (Project-scoped DB): cột `storageMode` và `projectDbPath` được thêm vào bảng dự án để định cấu hình file DB độc lập.
- **Migration 12 lên 13**: Nâng cấp bảo mật máy ảnh hiện trường: thêm các cột độ chính xác GPS `locationAccuracyM`, trạng thái giả lập GPS `isGpsMocked`, trạng thái vị trí `locationStatus`.
- **Migration 16 lên 17**: Bổ sung các cột so khớp định vị tự động: thêm trường `matchedNodeCode`, `matchedRouteCode` và `matchingTimeOffsetMs` vào bảng `site_photos` giúp tự động gắn ảnh vào đúng hố ga/tuyến cáp theo khoảng cách địa lý.
- **Migration 20 lên 21 (Hợp nhất tuyến phân đoạn)**: Chuyển đổi dữ liệu lớn: Đọc toàn bộ các tuyến rời rạc dạng segment (LINE_A_S1, LINE_A_S2) trong SQLite, tính toán khoảng cách và kết nối thành một chuỗi tọa độ liên tục `points`, sau đó lưu lại thành tuyến chính `LINE_A` duy nhất, đồng thời tự động dọn dẹp các điểm nút trung gian cũ để tránh rác cơ sở dữ liệu.
- **Migration 32 lên 33**: Mở rộng liên kết định vị và đối tượng: Bổ sung các liên kết trực tiếp bằng ID khóa ngoại (`nodeId`, `routeId`, `matchedNodeId`, `matchedRouteId`) trên các thực thể như nhật ký, ảnh chụp, tiến độ vật tư, ghi chú, kế hoạch và đầu việc để tối ưu tốc độ so khớp thay vì so khớp chuỗi code.
- **Migration 33 lên 34**: Bổ sung cột xóa logic (`isDeleted`, `deletedAtEpochMs`) trên toàn bộ các thực thể cốt lõi. Tạo các bảng trung gian liên kết nhiều-nhiều (`daily_log_nodes`, `daily_log_photos`, `photo_tags`) để chuẩn hóa thiết kế cơ sở dữ liệu.
- **Migration 34 lên 35**: Thiết kế lại cơ chế bàn giao vật tư: Bổ sung các trường `receiver`, khóa ngoại liên kết tới danh mục và tờ khai vật tư trong bảng bàn giao vật tư (`material_handover`) và tờ khai xuất xứ (`material_declaration`).
- **Migration 35 lên 40**: Tái thiết cấu trúc toàn bộ khóa ngoại trong cơ sở dữ liệu (Foreign Key constraints) ràng buộc trực tiếp tới thực thể địa lý `gis_node(id)` và `gis_route(id)` ON DELETE SET NULL để đảm bảo an toàn toàn vẹn dữ liệu khi thay đổi file thiết kế. Thay đổi kiểu lưu trữ và trường định danh trong RAG.
- **Migration 40 lên 41**: Bổ sung trường tên vật tư thi công `materialName` vào bảng bàn giao vật tư `material_handover` và tự động di chuyển tách tách ghép dữ liệu dạng chuỗi phân tách cũ.
- **Migration 41 lên 42**: Bổ sung cơ chế lưu trữ phiên bản thiết kế và kiểm toán đồng bộ dữ liệu: Tạo mới các bảng `import_session`, `import_version`, `import_conflict`, `import_audit` và bảng hàng đợi sự kiện nghiệp vụ `event_outbox`.
- **Migration 42 lên 43**: Khôi phục và tự động điền lại trường mã đối tượng `objectCode` trong bảng `site_photos` từ mã điểm nút/đoạn tuyến liên kết để duy trì khả năng tương thích ngược và tra cứu nhanh.
