# Đặc tả kỹ thuật: Xóa dự án Local độc lập & Đồng bộ khôi phục từ Cloud

## 1. Bối cảnh & Vấn đề (Context & Problem Statement)

### 1.1 Vấn đề hiện tại
- Khi người dùng xóa một dự án trên thiết bị Android (Local), hệ thống hiện tại chuyển trạng thái dự án sang `CLOUD_DECISION_PENDING` ("ĐÃ XÓA LOCAL — CHỜ QUYẾT ĐỊNH CLOUD") hoặc `DELETED` nhưng vẫn giữ lại bản ghi trong bảng `projects` của cơ sở dữ liệu SQLite máy.
- Hệ quả: Danh sách dự án tại Drawer "Quản lý dự án" vẫn tiếp tục hiển thị card dự án với trạng thái "KHÔNG HOẠT ĐỘNG", gây rối mắt, rác giao diện và làm người dùng hiểu nhầm rằng dự án chưa được xóa dứt điểm.
- Không có ranh giới rõ ràng giữa **Xóa khỏi máy (Local Deletion)** để giải phóng dung lượng và **Xóa vĩnh viễn trên Cloud (Cloud Deletion)**.

### 1.2 Mục tiêu đạt được (Goals)
1. **Xóa dứt điểm trên máy (Clean Local Purge):** Khi người dùng chọn xóa dự án trên máy, toàn bộ file SQLite DB của dự án, ảnh cục bộ và bản ghi dự án trong bảng `projects` của SQLite chính phải được dọn dẹp sạch sẽ và loại bỏ hoàn toàn khỏi danh sách Drawer.
2. **Bảo toàn dữ liệu Cloud (Cloud Safety):** Dữ liệu trên Firebase Firestore / Cloud Storage / Google Drive vẫn được bảo toàn nguyên vẹn.
3. **Khôi phục dễ dàng qua Đồng bộ (Seamless Cloud Restoration):** Nếu người dùng muốn sử dụng lại dự án, họ chỉ cần mở màn hình "Đồng bộ đám mây" (`FirebaseProjectCatalogScreen`) và bấm "Mở dự án" để tải và đồng bộ ngược lại máy.
4. **Tự động quét dọn dữ liệu kẹt cũ (Legacy Pending Deletion Self-Healing):** Các dự án đang bị kẹt ở trạng thái `CLOUD_DECISION_PENDING`, `DELETED` hoặc `isDeleted = 1` sẽ tự động được purge khỏi SQLite máy ngay khi mở app / load danh sách dự án.

---

## 2. Quyết định kỹ thuật đã khóa (Locked Architectural Decisions)

- **D1 (Quy tắc Xóa Local):** Khi người dùng xóa dự án tại máy, thực hiện dọn dẹp toàn diện (Purge): đóng DB scoped của dự án, xóa file DB vật lý, và xóa/ẩn dứt điểm khỏi bảng `projects` local (không hiển thị trên Drawer). Dữ liệu Cloud được bảo toàn nguyên vẹn.
- **D2 (Quy tắc Tự động dọn dẹp dự án cũ):** Ngay khi ứng dụng khởi tạo danh sách dự án (hoặc mở Drawer), hệ thống tự động purge tất cả các bản ghi có `deletionState IN ('CLOUD_DECISION_PENDING', 'DELETED')` hoặc `isDeleted = 1` khỏi danh sách hiển thị trên máy.
- **D3 (Quy tắc Phân định Xóa qua Dialog):** Khi bấm Xóa tại Drawer, dialog thông báo rõ: "Dự án sẽ bị xóa khỏi máy. Dữ liệu trên Cloud vẫn an toàn và bạn có thể tải lại bất cứ lúc nào." Nếu tài khoản là Admin, cung cấp thêm tùy chọn "Xóa vĩnh viễn trên Cloud" nếu Admin muốn hủy bỏ dự án ở cả 2 nơi.

---

## 3. Yêu cầu chi tiết (Requirements)

### 3.1 Functional Requirements
- **FR-1:** Khi người dùng thực hiện xóa dự án cục bộ:
  - Dự án không được là dự án đang kích hoạt (`activeProjectId`). Nếu đang kích hoạt, yêu cầu chuyển sang dự án khác trước.
  - Đóng `ProjectScopedDatabaseProvider` của dự án đó.
  - Xóa file vật lý của SQLite DB scoped và file media tạm nếu có.
  - Xóa dòng dự án khỏi bảng `projects` trên `MapSupervisionDatabase` (hoặc set `isDeleted = 1` và câu query `list()` lọc dứt điểm `isDeleted = 0 AND deletionState = 'ACTIVE'`).
  - Danh sách Drawer `MapHubScreen` cập nhật ngay lập tức và không còn xuất hiện dự án đó.
- **FR-2:** Tự động dọn dẹp (Self-Healing Purge):
  - Trong `ProjectRepositoryImpl.list()` hoặc DAO, chỉ trả về các dự án có `deletionState == 'ACTIVE'` và `isDeleted == 0`.
  - Thực hiện purge các bản ghi mồ côi/rác thuộc các dự án đã xóa.
- **FR-3:** Khôi phục dự án từ Cloud (`FirebaseAccessViewModel.openOrDownloadProject`):
  - Khi người dùng vào màn hình Cloud Catalog, dự án vẫn xuất hiện đầy đủ với trạng thái `APPROVED` / `ACTIVE`.
  - Người dùng bấm "Mở dự án", hệ thống tải metadata, tạo lại `ProjectEntity` trong SQLite với `deletionState = 'ACTIVE'`, tải dữ liệu từ Firestore và mở dự án bình thường.
- **FR-4:** Dialog xác nhận xóa trên Drawer:
  - Hiển thị rõ ràng nội dung cảnh báo xóa cục bộ.
  - Hỗ trợ tùy chọn nâng cao cho Admin nếu muốn xóa Cloud.

### 3.2 Non-Functional Requirements
- **NFR-1 (UI Snappiness):** Thao tác xóa và làm mới danh sách Drawer phải diễn ra mượt mà, phản hồi dưới 300ms.
- **NFR-2 (Data Safety):** Thao tác xóa local không bao giờ vô tình gửi lệnh xóa dữ liệu trên Cloud trừ khi Admin xác nhận rõ ràng tùy chọn xóa Cloud.
- **NFR-3 (Offline Resilience):** Xóa local hoạt động độc lập không phụ thuộc vào kết nối mạng.

---

## 4. Tiêu chí nghiệm thu (Acceptance Criteria)

- **AC-1:** Khi xóa dự án trên Drawer, card dự án biến mất hoàn toàn khỏi màn hình Drawer, không còn hiển thị card "ĐÃ XÓA LOCAL — CHỜ QUYẾT ĐỊNH CLOUD".
- **AC-2:** Các dự án cũ đang kẹt trạng thái `CLOUD_DECISION_PENDING` (như "nha thau") bị loại bỏ sạch khỏi Drawer ngay sau khi áp dụng bản vá.
- **AC-3:** Dữ liệu dự án trên Firestore và Cloud Storage không bị ảnh hưởng khi xóa local.
- **AC-4:** Khi vào màn hình "Đồng bộ đám mây" và bấm "Mở dự án" cho dự án đã xóa local, dự án được tải lại thành công và xuất hiện trở lại trên Drawer ở trạng thái hoạt động bình thường.
- **AC-5:** 100% các Unit Tests và Regression Tests cho `ProjectRepository`, `ProjectDao`, `MapHubScreen` và `FirebaseAccessViewModel` chạy thành công.

---

## 5. Kế hoạch triển khai (Work Breakdown Structure)

1. **Task `[proj-del-01]` DAO & Query Filtering:** Cập nhật `ProjectDao.list` và `ProjectRepositoryImpl.list` để chỉ truy vấn các dự án `deletionState = 'ACTIVE'` và `isDeleted = 0`; thêm hàm `purgeDeletedProjects()` dọn dẹp triệt để các dự án đã xóa.
2. **Task `[proj-del-02]` Local Deletion Execution:** Cập nhật hàm xóa dự án local trong `ProjectRepositoryImpl` để xóa sạch file và bản ghi local ngay lập tức mà không chuyển sang trạng thái treo `CLOUD_DECISION_PENDING` nếu chỉ xóa local.
3. **Task `[proj-del-03]` Drawer UI & Deletion Dialog:** Cập nhật dialog xác nhận xóa trong `MapHubScreen.kt`, loại bỏ UI card chờ quyết định cloud ở drawer, đảm bảo UI danh sách dự án sạch sẽ và phản ánh đúng trạng thái.
4. **Task `[proj-del-04]` Cloud Re-download & Verification:** Kiểm tra và đảm bảo quy trình tải lại từ `FirebaseProjectCatalogScreen` tái tạo `ProjectEntity` sạch với `deletionState = 'ACTIVE'`, chạy toàn bộ bộ Unit Tests và nghiệm thu.
