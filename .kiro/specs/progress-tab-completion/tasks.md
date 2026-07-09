# Implementation Plan: Hoàn thiện tab Tiến độ

## Overview

Kết nối `ProgressHubScreen` với dữ liệu thực, thay thế toàn bộ nội dung hardcode. Thứ tự task theo dependency: Task 1 (state/VM) là nền tảng cho Task 2, 3, 4, 6. Task 5 độc lập.

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1"] },
    { "wave": 2, "tasks": ["2", "3", "4", "5"] },
    { "wave": 3, "tasks": ["6"] },
    { "wave": 4, "tasks": ["7"] }
  ]
}
```

## Tasks

- [x] 1. Thêm `dailyLogs` vào `WorkspaceState` và inject `DailyLogRepository` vào `WorkspaceViewModel`
  - Thêm trường `val dailyLogs: List<DailyLog> = emptyList()` vào `data class WorkspaceState`
  - Thêm `import com.mapsupervision.domain.model.DailyLog` vào `WorkspaceViewModel.kt`
  - Thêm `private val dailyLogRepository: DailyLogRepository` vào constructor `@Inject` của `WorkspaceViewModel`
  - Trong `refresh()`, sau block `coroutineScope` hiện có, load `dailyLogs`:
    ```kotlin
    val dailyLogs = (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data
        ?: run { AppLogger.d("dailylog.load.error project=$projectId"); emptyList() }
    ```
  - Gán `dailyLogs = dailyLogs` khi tạo `WorkspaceState` trong `refresh()`
  - _Requirements: 2.1, 2.2, 2.6_

  - [ ]* 1.1 Viết unit test cho error handling của DailyLogRepository
    - Test: khi `byProject()` trả `AppResult.Error`, `dailyLogs` phải là `emptyList()` và không throw exception
    - _Requirements: 2.6_

- [x] 2. Cập nhật Critical Path Variances trong `ProgressHubScreen`
  - Thêm helper function top-level `nodeDisplayName(nodeCode: String, nodesMap: Map<String, GisNode>): String` vào `ProgressHubScreen.kt`:
    ```kotlin
    fun nodeDisplayName(nodeCode: String, nodesMap: Map<String, GisNode>): String {
        val label = nodesMap[nodeCode]?.mapNumberLabel
        return when {
            !label.isNullOrBlank() -> label
            nodeCode.isNotBlank()  -> nodeCode
            else                   -> "Node không xác định"
        }
    }
    ```
  - Thêm helper function top-level `estimatedDelayDays(variance: Float): Int`:
    ```kotlin
    fun estimatedDelayDays(variance: Float): Int = (variance * 30f / 100f).roundToInt()
    ```
  - Trong `ProgressHubScreen`, thay thế phần "CRITICAL PATH VARIANCES" hardcode bằng logic thực:
    ```kotlin
    val criticalNodes = state.constructionProgress
        .filter { it.planned - it.actual > 0f }
        .sortedByDescending { it.planned - it.actual }
        .take(3)
    ```
  - Nếu `criticalNodes` rỗng: hiển thị `Text("Chưa có dữ liệu tiến độ")`
  - Mỗi node: nhãn = `nodeDisplayName(node.nodeCode, nodesMap)`, giá trị = `"−${estimatedDelayDays(node.planned - node.actual)} Ngày"`
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

  - [ ]* 2.1 Viết property test cho logic chọn top-3 critical nodes
    - **Property 1: Top-3 critical nodes là subset hợp lệ của input**
    - Dùng `Arb.list(arbNodeProgress)`, kiểm tra: kết quả ≤ 3 phần tử, tất cả variance > 0, không bỏ sót node có variance lớn hơn
    - Tag: `// Feature: progress-tab-completion, Property 1`
    - _Requirements: 1.1, 1.2_

  - [ ]* 2.2 Viết property test cho công thức estimatedDelayDays
    - **Property 2: Công thức ước tính ngày chậm**
    - Dùng `Arb.float(0.001f, 100f)`, kiểm tra kết quả bằng `(variance * 30f / 100f).roundToInt()`
    - Tag: `// Feature: progress-tab-completion, Property 2`
    - _Requirements: 1.3_

  - [ ]* 2.3 Viết property test cho `nodeDisplayName`
    - **Property 3: nodeDisplayName ưu tiên đúng thứ tự**
    - Dùng `Arb.string()` × `Arb.map(...)`, kiểm tra 3 nhánh ưu tiên
    - Tag: `// Feature: progress-tab-completion, Property 3`
    - _Requirements: 1.6, 4.1, 4.2, 4.3_

- [x] 3. Cập nhật Activity Timeline trong `ProgressHubScreen`
  - Thêm helper function top-level `formatRelativeTime(epochMs: Long, now: Long = System.currentTimeMillis()): String`:
    - Cùng ngày: `"Hôm nay, HH:mm"`
    - Hôm qua: `"Hôm qua, HH:mm"`
    - Ngày khác: `"dd/MM/yyyy HH:mm"`
    - Dùng `Calendar` để so sánh ngày (không dùng thư viện ngoài)
  - Trong `ProgressHubScreen`, thay thế phần "Activity Timeline" hardcode bằng logic thực:
    ```kotlin
    val recentLogs = state.dailyLogs
        .sortedByDescending { it.createdAtEpochMs }
        .take(5)
    ```
  - Nếu `recentLogs` rỗng: hiển thị `Text("Chưa có nhật ký hoạt động")`
  - Mỗi `DailyLog`: hiển thị `workItem` làm tiêu đề, `note` làm nội dung, `"${manpower} người"`, `formatRelativeTime(createdAtEpochMs)`
  - _Requirements: 2.3, 2.4, 2.5_

  - [ ]* 3.1 Viết property test cho Activity Timeline sort/take
    - **Property 4: Activity Timeline lấy đúng 5 log gần nhất**
    - Dùng `Arb.list(arbDailyLog)`, kiểm tra: kết quả ≤ 5, sorted desc theo `createdAtEpochMs`, là 5 phần tử có timestamp lớn nhất
    - Tag: `// Feature: progress-tab-completion, Property 4`
    - _Requirements: 2.3_

  - [ ]* 3.2 Viết unit test cho `formatRelativeTime`
    - Test 3 cases: timestamp hôm nay, hôm qua, ngày khác
    - Truyền `now` cố định để test deterministic
    - _Requirements: 2.4_

- [x] 4. Thêm ModalBottomSheet nhập tiến độ vào `ProgressHubScreen`
  - Thêm tham số `onClick: () -> Unit` vào `InfrastructureItem` composable, bọc Card bằng `Modifier.clickable { onClick() }`
  - Thêm state cục bộ trong `ProgressHubScreen`:
    ```kotlin
    var selectedNodeForProgress by remember { mutableStateOf<NodeProgress?>(null) }
    ```
  - Khi tap `InfrastructureItem`, tìm `NodeProgress` tương ứng trong `state.constructionProgress` (fallback về `planned=0f, actual=0f` nếu không tìm thấy), gán vào `selectedNodeForProgress`
  - Thêm `ModalBottomSheet` hiển thị khi `selectedNodeForProgress != null`:
    - Tiêu đề: `nodeDisplayName(node.nodeCode, nodesMap)`
    - Phụ đề: `node.nodeCode` (mã kỹ thuật)
    - `OutlinedTextField` "Kế hoạch (%)" — giá trị mặc định `node.planned.toString()`
    - `OutlinedTextField` "Thực tế (%)" — giá trị mặc định `node.actual.toString()`
    - Validation: parse `toFloatOrNull()`, kiểm tra `in 0f..100f`; nếu không hợp lệ hiển thị `Text("Giá trị phải từ 0 đến 100")` màu đỏ
    - Nút "Lưu": gọi `onAddConstruction(nodeCode, planned, actual)` rồi `selectedNodeForProgress = null`
    - Nút "Hủy": `selectedNodeForProgress = null`
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [ ]* 4.1 Viết property test cho validation giá trị tiến độ
    - **Property 5: Validation giá trị tiến độ**
    - Dùng `Arb.float()`, kiểm tra: giá trị trong `[0f, 100f]` → valid, ngoài khoảng → invalid
    - Tag: `// Feature: progress-tab-completion, Property 5`
    - _Requirements: 3.5_

  - [ ]* 4.2 Viết unit test cho fallback NodeProgress khi node chưa có dữ liệu
    - Test: khi `constructionProgress` không chứa node được tap, `planned` và `actual` mặc định là `0f`
    - _Requirements: 3.7_

- [x] 5. Cập nhật `InfrastructureItem` hiển thị tên node thân thiện
  - Cập nhật nơi gọi `InfrastructureItem` trong `ProgressHubScreen` để truyền `title = nodeDisplayName(progress.nodeCode, nodesMap)` thay vì `progress.nodeCode`
  - Thêm tham số `subtitle: String` vào `InfrastructureItem` để hiển thị `nodeCode` dưới dạng phụ đề
  - Cập nhật nơi gọi: truyền `subtitle = progress.nodeCode`
  - Hiển thị `subtitle` bên dưới `title` trong `InfrastructureItem` (font nhỏ hơn, màu `secondaryTextColor`)
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 6. Tạo `ProgressPdfExporter` và kết nối nút Export PDF
  - Tạo file mới `ProgressPdfExporter.kt` trong package `com.mapsupervision.app.workspace`:
    ```kotlin
    object ProgressPdfExporter {
        suspend fun export(
            context: Context,
            projectId: String,
            progress: List<NodeProgress>,
            nodes: List<GisNode>
        ): Result<File>
    }
    ```
  - Implement `export()` chạy trên `withContext(Dispatchers.IO)`:
    - Tạo `PdfDocument()`, trang A4 (595 × 842 pt)
    - Vẽ header: tên dự án, ngày xuất `SimpleDateFormat("dd/MM/yyyy")`
    - Vẽ bảng tóm tắt: `avgPlanned`, `avgActual`, `delayedCount`
    - Nếu `progress` rỗng: vẽ ghi chú `"Chưa có dữ liệu tiến độ"`
    - Vẽ danh sách từng `NodeProgress`: `nodeDisplayName`, `planned%`, `actual%`, trạng thái delayed
    - Lưu vào `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` với tên `tiendo_[projectId]_[yyyyMMdd].pdf`
    - Bọc toàn bộ trong `runCatching { ... }`, trả về `Result<File>`
  - Trong `ProgressHubScreen`, thêm `val coroutineScope = rememberCoroutineScope()` và `val snackbarHostState = remember { SnackbarHostState() }`
  - Bọc `LazyColumn` trong `Scaffold` với `snackbarHost = { SnackbarHost(snackbarHostState) }`
  - Kết nối nút "Export PDF" gọi `ProgressPdfExporter.export(...)`, xử lý kết quả:
    - Thành công: mở bằng `Intent.ACTION_VIEW` với `FileProvider`; bắt `ActivityNotFoundException` → Snackbar đường dẫn
    - Thất bại: Snackbar lỗi
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8_

  - [ ]* 6.1 Viết property test cho tên file PDF
    - **Property 6: Tên file PDF đúng định dạng**
    - Dùng `Arb.string()` × `Arb.localDate()`, kiểm tra tên file match pattern `tiendo_[projectId]_[yyyyMMdd].pdf`
    - Tag: `// Feature: progress-tab-completion, Property 6`
    - _Requirements: 5.3_

  - [ ]* 6.2 Viết unit test cho export với progress rỗng
    - Test: khi `progress` rỗng, `export()` vẫn trả `Result.success(file)` và file tồn tại
    - _Requirements: 5.8_

- [x] 7. Checkpoint — Đảm bảo tất cả tests pass
  - Đảm bảo tất cả tests pass, hỏi người dùng nếu có vấn đề phát sinh.

## Notes

- Tasks đánh dấu `*` là optional, có thể bỏ qua để triển khai nhanh hơn.
- Task 1 là prerequisite của Task 2, 3, 4, 6. Task 5 độc lập, có thể làm song song.
- `nodeDisplayName` được định nghĩa ở Task 2 và tái sử dụng ở Task 4, 5, 6 — không định nghĩa lại.
- Property tests dùng thư viện `kotest-property`. Nếu chưa có trong `build.gradle.kts`, thêm: `testImplementation("io.kotest:kotest-property:5.x.x")`.
