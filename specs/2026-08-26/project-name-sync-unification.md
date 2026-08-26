# Specification: Đồng nhất Tên và Mã Dự án giữa Local và Cloud trên Ứng dụng Android

## Overview

Tài liệu đặc tả yêu cầu và thiết kế kỹ thuật cho việc đồng nhất hiển thị Tên dự án (`projectName` / `name`) và Mã dự án (`projectCode` / `slug`) giữa dữ liệu cục bộ (Local SQLite/Room) và dữ liệu đám mây (Firebase Firestore), đồng thời nâng cấp giao diện danh mục Cloud (`FirebaseProjectCatalogScreen`) và Drawer quản lý dự án (`MapHubScreen`, `ProjectScreen`) đạt chuẩn UI/UX trực quan, hiện đại.

## Locked Decisions

- **D1 — Đồng bộ toàn diện cấu trúc định danh dự án:**
  - Tiêu đề chính hiển thị Tên dự án (`projectName` / `name`).
  - Phụ đề hiển thị `Mã: {projectCode / slug}` đồng nhất trên cả 2 màn hình (Cloud Project Catalog và Local Project Drawer).
  - Sửa triệt để logic phân giải dữ liệu Firestore (`extractCatalogEntryFromProjectDoc`, `parseFirebaseProjectCatalog`) để đọc đúng dữ liệu được bọc trong `docData["data"]` (do `SyncEnvelope` sinh ra), `docData["payload"]` và các trường root.
- **D2 — Nâng cấp UI/UX chuẩn Pro Max:**
  - Card dự án trên màn hình Cloud (`ProjectCatalogCard`) hiển thị đầy đủ: Tên dự án (Bold, 16sp), Mã dự án (`Mã: ...`, 13sp), Tag nhận diện trạng thái nội bộ ("Đang mở trên máy" / "Đã tải về máy" / Chưa tải), Ngày giờ cập nhật, Status Badge quyền truy cập và Nút hành động tương ứng.
  - Card dự án trong Drawer Local (`MapHubScreen`) và màn hình Quản lý dự án (`ProjectScreen`) đồng bộ phong cách visual hierarchy.
- **D3 — Cơ chế Tự phục hồi dữ liệu (Self-Healing) & Giải quyết xung đột (Conflict Resolution):**
  - **Self-Healing trên Firestore:** Khi nạp danh mục, nếu phát hiện bản ghi `projectCatalog` có `projectName` bị rỗng, thiếu, hoặc mang giá trị là raw UUID trùng với `projectId` trong khi tài liệu gốc `projects/{projectId}` hoặc Local có tên thực tế, hệ thống client (Admin) tự động cập nhật sửa lại bản ghi `projectCatalog` trên Firestore.
  - **Đồng bộ khi Push Local:** Khi tạo mới hoặc cập nhật dự án từ Local và thực hiện đẩy dữ liệu (`pushPending`), tự động đồng bộ cả `projects/{id}` và bản ghi `projectCatalog/{id}`.
  - **Hợp nhất khi Pull / Mở Cloud:** Áp dụng Last-Write-Wins theo `updatedAtEpochMs`; khi người dùng chọn "Mở dự án" từ Cloud, nếu dự án đã có ở Local, tự động cập nhật tên và mã từ Cloud vào Local Room DB nếu Cloud có phiên bản mới hơn.

## System Decision Impact

- **Impact:** draft new
- **Decision:** Quy chuẩn nhất quán định danh dự án trên toàn hệ thống MAPSUPERVISION (Android App & Webapp): Document `projects/{id}` và `projectCatalog/{id}` luôn đồng bộ `projectName` và `projectCode`.
- **Acceptance gate:** Đạt toàn bộ kiểm thử Unit Test cho `FirebaseAccessRepositoryImplTest`, `FirebaseProjectCatalogParserTest`, `FirebaseAccessViewModelTest`, `ProjectCatalogUiTest`, `ProjectRepositoryTest` và xác thực hiển thị đồng nhất trên giao diện.

---

## Requirements

### Functional Requirements

- **FR-1 — Trích xuất chính xác Metadata từ Firestore `projects` và `projectCatalog`:**
  - `extractCatalogEntryFromProjectDoc` phải tìm kiếm trường `name` / `projectName` và `slug` / `projectCode` theo thứ tự: `docData["data"]` -> `docData["payload"]` -> root `docData`.
  - Nếu `projectName` vẫn trống hoặc là UUID, fallback sang `slug` hoặc tên dự án mặc định thay vì để hiển thị UUID thô.
- **FR-2 — Tự động chữa lành (Self-Healing) `projectCatalog`:**
  - Trong quá trình `listProjectCatalog()` (khi Admin tải danh mục), hệ thống đối chiếu và tự động cập nhật lại các document `projectCatalog/{id}` bị lưu sai tên trước đây.
  - Khi client thực hiện sync dự án qua `FirebaseSyncRepositoryImpl.pushPending()`, nếu bảng `projects` có bản ghi cập nhật, cập nhật đồng thời sang `projectCatalog/{id}`.
- **FR-3 — Nâng cấp UI/UX màn hình `FirebaseProjectCatalogScreen`:**
  - Mỗi `ProjectCatalogCard` hiển thị:
    1. Tên dự án (Text Title bold, 16sp).
    2. Mã dự án (`Mã: <projectCode>`).
    3. Tag nhận biết trạng thái trên máy (ví dụ: Badge `ĐANG MỞ` màu cam nổi bật nếu `activeProjectId == projectId`, Badge `ĐÃ CÓ TRÊN MÁY` màu lam nhạt nếu đã lưu trong Room DB).
    4. Dấu mốc thời gian cập nhật.
    5. Badge trạng thái duyệt quyền Cloud (`ĐÃ DUYỆT`, `CHỜ DUYỆT`, `TỪ CHỐI`, ...).
    6. Nút thao tác ("Mở dự án" / "Yêu cầu cấp quyền" / "Đang chờ duyệt").
- **FR-4 — Đồng bộ thông tin khi mở dự án từ Cloud vào Local:**
  - Trong `openOrDownloadProject()`, nếu dự án đã tồn tại ở Local nhưng tên/mã ở Cloud được cập nhật mới hơn (hoặc tên Local đang trống), cập nhật lại `ProjectEntity` trong Local Room DB.

### Non-Functional Requirements

- **NFR-1 — Hiệu năng & Băng thông:** Việc tự phục hồi (self-heal) chỉ thực hiện theo lô (batch write) cho các bản ghi thực sự có sai lệch tên, không phát sinh query thừa.
- **NFR-2 — Trải nghiệm người dùng mượt mà:** Không gây giật lag (frame drop) trên LazyColumn danh sách dự án; tuân thủ bảng màu theme hệ thống (Cam thương hiệu, Dark background, không vi phạm màu tím/purple).
- **NFR-3 — Tính toàn vẹn dữ liệu:** Không làm mất dữ liệu SQLite cục bộ khi đồng bộ thông tin metadata dự án.

---

## Acceptance Criteria

- [ ] **AC-1:** Danh sách dự án trên Cloud (`FirebaseProjectCatalogScreen`) hiển thị đúng tên dự án (ví dụ: "Dự án 269 – 2026") thay vì hiển thị mã UUID (ví dụ: `6874375a-3366-4457-a978-b8ee71c4e461`).
- [ ] **AC-2:** Mã dự án (`Mã: ...`) hiển thị rõ ràng bên dưới tên dự án trên cả màn hình Cloud và Drawer Local.
- [ ] **AC-3:** Các dự án đã có sẵn trên máy Local được gắn nhãn chỉ báo ("Đang mở trên máy" / "Đã tải về máy") ngay trên giao diện Cloud.
- [ ] **AC-4:** Khi Admin tải danh sách Cloud, các bản ghi cũ trên Firestore `projectCatalog` bị lỗi tên được tự động sửa đúng theo tài liệu `projects/{id}` hoặc Local DB.
- [ ] **AC-5:** Thao tác tạo dự án mới hoặc đổi tên dự án từ Local/Cloud phản ánh đồng bộ tên và mã trên cả hai phía sau khi sync.
- [ ] **AC-6:** 100% các bài kiểm thử tự động (Unit Tests) liên quan đến Parser, Repository, ViewModel và UI State đều vượt qua thành công.

---

## Scenarios

### Scenario 1: Hiển thị danh mục dự án Cloud khi dữ liệu gốc nằm trong `data` envelope
**Given** Firestore có document `projects/6874375a-...` chứa `{ data: { name: "Dự án 269 – 2026", slug: "d-n-269---2026" } }`  
**When** Người dùng mở màn hình "Dự án trên Cloud"  
**Then** Card dự án hiển thị Tiêu đề "Dự án 269 – 2026", phụ đề "Mã: d-n-269---2026", không hiển thị chuỗi UUID làm tiêu đề.

### Scenario 2: Nhận diện dự án đã tồn tại trên máy
**Given** Thiết bị đang mở dự án có id `6874375a-...`  
**When** Người dùng vào màn hình "Dự án trên Cloud"  
**Then** Card dự án tương ứng hiển thị Tag "Đang mở trên máy" và nút "Mở dự án".

### Scenario 3: Tự phục hồi bản ghi `projectCatalog` bị lỗi tên
**Given** Collection `projectCatalog/6874375a-...` có `projectName` bị rỗng hoặc là UUID do đợt sync cũ  
**When** Tài khoản Admin mở danh mục Cloud  
**Then** Hệ thống tự động trích xuất tên đúng từ `projects/6874375a-...` và ghi đè cập nhật lại `projectCatalog/6874375a-...` với `projectName = "Dự án 269 – 2026"`.

---

## Technical Notes

### Các tệp nguồn trọng tâm cần cập nhật:
1. `data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt`:
   - Cập nhật `extractCatalogEntryFromProjectDoc` và `parseFirebaseProjectCatalog` để bóc tách từ `docData["data"]`, `docData["payload"]`, và `docData`.
   - Bổ sung logic so khớp và heal `missingEntries` / `corruptedEntries` khi Admin query danh mục.
2. `data/src/main/java/com/mapsupervision/data/sync/FirebaseSyncRepositoryImpl.kt`:
   - Bổ sung logic đồng bộ `projectCatalog` khi `pushPending` bảng `projects`.
3. `app/src/main/java/com/mapsupervision/app/FirebaseProjectCatalogScreen.kt`:
   - Nâng cấp `ProjectCatalogCard` hiển thị: `entry.projectName`, `Mã: ${entry.projectCode}`, Local presence tag (`localProjects`), `updatedAt`, `StatusBadge`, `Action Button`.
4. `app/src/main/java/com/mapsupervision/app/auth/FirebaseAccessViewModel.kt`:
   - Bổ sung danh sách `localProjects` vào state để `FirebaseProjectCatalogScreen` biết dự án nào đang có / đang active trên máy.
   - Cập nhật `openOrDownloadProject` để đồng bộ metadata mới nhất xuống Room DB.
5. Unit Test Suites:
   - `data/src/test/java/com/mapsupervision/data/sync/FirebaseProjectCatalogParserTest.kt`
   - `app/src/test/java/com/mapsupervision/app/auth/FirebaseAccessViewModelTest.kt`
   - `project/src/test/java/com/mapsupervision/project/ui/ProjectCatalogUiTest.kt`

---

## Task Links

- [zaftn2] @task/zaftn2: [proj-name-01] Parser & Repository Self-Healing (FirebaseAccessRepository & FirebaseSyncRepository) (open)
- [r6tubb] @task/r6tubb: [proj-name-02] ViewModel & Local State Orchestration (FirebaseAccessViewModel & FirebaseAccessGate) (open)
- [q5mriu] @task/q5mriu: [proj-name-03] UI/UX Refinement for Project Cards (FirebaseProjectCatalogScreen & MapHubScreen) (open)
- [hrmc2f] @task/hrmc2f: [proj-name-04] Unit Testing & Verification Suite (Parser, ViewModel, UI) (open)

## Open Questions

*Không còn câu hỏi mở nào. Toàn bộ các quyết định thiết kế đã được thống nhất qua phiên phỏng vấn Socratic.*
