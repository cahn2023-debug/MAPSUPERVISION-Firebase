# Design Document: Hoàn thiện tab Tiến độ (`progress-tab-completion`)

## Overview

Tài liệu này mô tả thiết kế kỹ thuật cho 5 thay đổi cần thực hiện để kết nối `ProgressHubScreen` với dữ liệu thực từ cơ sở dữ liệu, thay thế toàn bộ nội dung hardcode hiện tại.

Tất cả thay đổi nằm trong module `app`, không tạo ViewModel mới, không tạo module mới.

---

## Architecture

Luồng dữ liệu hiện tại và sau khi thay đổi:

```
DailyLogRepository ──┐
ProgressRepository ──┤
GisRepository ────────┤──► WorkspaceViewModel.refresh() ──► WorkspaceState ──► ProgressHubScreen
MaterialProgressRepo ─┘
```

Sau khi thay đổi, `WorkspaceState` bổ sung thêm `dailyLogs: List<DailyLog>`. `ProgressHubScreen` đọc dữ liệu từ `state` và thực hiện các tính toán thuần túy (pure computation) trực tiếp trong Composable.

---

## Components and Interfaces

### 1. WorkspaceState + WorkspaceViewModel

**Thay đổi `WorkspaceState`:**

```kotlin
data class WorkspaceState(
    // ... các trường hiện có ...
    val dailyLogs: List<DailyLog> = emptyList()  // THÊM MỚI
)
```

**Thay đổi `WorkspaceViewModel`:**

- Thêm `private val dailyLogRepository: DailyLogRepository` vào constructor `@Inject`.
- Trong `refresh()`, load `dailyLogs` song song với các dữ liệu khác bằng `async/await` trong `coroutineScope`.
- Gán `dailyLogs` vào `WorkspaceState` khi cập nhật state.
- Nếu `DailyLogRepository.byProject()` trả về `AppResult.Error`, ghi log bằng `AppLogger.d(...)` và dùng `emptyList()`.

Pattern load trong `refresh()` (nhất quán với cách load `progress`, `nodes` hiện tại):

```kotlin
val (imports, nodes, routes, progress, dailyLogs) = coroutineScope {
    val importsDeferred = async { ... }
    val nodesDeferred   = async { ... }
    val routesDeferred  = async { ... }
    val progressDeferred = async { ... }
    val dailyLogsDeferred = async {
        (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data.orEmpty()
    }
    // destructure 5 giá trị
}
```

> **Lưu ý:** Hiện tại `refresh()` dùng `Quadruple` để destructure 4 giá trị. Cần thêm `Quintuple` hoặc chuyển sang dùng `data class` nội bộ, hoặc load `dailyLogs` riêng sau `coroutineScope`. Cách đơn giản nhất là load riêng sau block `coroutineScope` hiện có (không cần thêm data class):

```kotlin
val dailyLogs = (dailyLogRepository.byProject(projectId) as? AppResult.Success)?.data
    ?: run {
        AppLogger.d("dailylog.load.error project=$projectId")
        emptyList()
    }
```

---

### 2. Critical Path Variances — Logic tính toán

Đây là pure computation trong `ProgressHubScreen`, không cần hàm riêng trong ViewModel.

**Công thức:**
```
variance(node) = node.planned - node.actual
estimatedDays(variance) = round(variance * 30 / 100f)
```

**Logic chọn top-3:**
```kotlin
val criticalNodes = state.constructionProgress
    .filter { it.planned - it.actual > 0f }
    .sortedByDescending { it.planned - it.actual }
    .take(3)
```

**Hiển thị:**
- Nếu `criticalNodes` rỗng (hoặc `constructionProgress` rỗng): hiển thị `"Chưa có dữ liệu tiến độ"`.
- Mỗi node: nhãn = `nodeDisplayName(node.nodeCode, nodesMap)`, giá trị = `"−${estimatedDays} Ngày"`.

---

### 3. ModalBottomSheet nhập tiến độ

**State cục bộ trong `ProgressHubScreen`:**

```kotlin
var selectedNodeForProgress by remember { mutableStateOf<NodeProgress?>(null) }
```

Khi `selectedNodeForProgress != null`, hiển thị `ModalBottomSheet`.

**Cấu trúc BottomSheet:**

```
ModalBottomSheet(onDismissRequest = { selectedNodeForProgress = null }) {
    // Tiêu đề: nodeDisplayName(node.nodeCode, nodesMap)
    // OutlinedTextField "Kế hoạch (%)" — giá trị mặc định: node.planned
    // OutlinedTextField "Thực tế (%)"  — giá trị mặc định: node.actual
    // Nút "Lưu" + Nút "Hủy"
}
```

**Validation:**
- Parse `String → Float` bằng `toFloatOrNull()`.
- Kiểm tra `value in 0f..100f`.
- Nếu không hợp lệ: hiển thị `Text("Giá trị phải từ 0 đến 100")` màu đỏ, không gọi `onAddConstruction`.

**Khi tap `InfrastructureItem`:**
```kotlin
val existingProgress = state.constructionProgress
    .firstOrNull { it.nodeCode == nodeCode }
    ?: NodeProgress(id = "", projectId = "", nodeCode = nodeCode,
                    planned = 0f, actual = 0f, remain = 0f, delayed = false)
selectedNodeForProgress = existingProgress
```

`InfrastructureItem` cần thêm tham số `onClick: () -> Unit` và bọc bằng `Modifier.clickable { onClick() }`.

---

### 4. Helper function `nodeDisplayName`

Hàm thuần túy, đặt ở top-level trong file `ProgressHubScreen.kt` (hoặc file utils riêng trong cùng package):

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

`nodesMap` được tính một lần trong `ProgressHubScreen`:
```kotlin
val nodesMap = state.designNodes.associateBy { it.code }
```
(đã có sẵn trong code hiện tại)

---

### 5. ProgressPdfExporter

**Vị trí:** `com.mapsupervision.app.workspace.ProgressPdfExporter` (file mới trong cùng package).

**Chữ ký:**

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

**Luồng thực thi:**

```
withContext(Dispatchers.IO) {
    1. Tạo PdfDocument()
    2. Tạo trang A4 (595 x 842 pt)
    3. Vẽ header: tên dự án, ngày xuất (dd/MM/yyyy)
    4. Vẽ bảng tóm tắt: avgPlanned, avgActual, delayedCount
    5. Vẽ danh sách từng NodeProgress (nodeDisplayName, planned%, actual%, trạng thái)
    6. Nếu progress rỗng: vẽ ghi chú "Chưa có dữ liệu tiến độ"
    7. Lưu vào Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)
       tên file: tiendo_[projectId]_[yyyyMMdd].pdf
    8. Đóng PdfDocument
    9. Trả về Result.success(file)
}
```

**Xử lý lỗi:** Bọc toàn bộ trong `runCatching { ... }`, trả về `Result<File>`.

**Tên file:**
```kotlin
val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
val fileName = "tiendo_${projectId}_${dateStr}.pdf"
```

**Tích hợp vào `ProgressHubScreen`:**

```kotlin
val coroutineScope = rememberCoroutineScope()
val snackbarHostState = remember { SnackbarHostState() }

// Nút Export PDF:
OutlinedButton(onClick = {
    coroutineScope.launch {
        val result = ProgressPdfExporter.export(
            context, state.activeProjectId!!, state.constructionProgress, state.designNodes
        )
        result.fold(
            onSuccess = { file ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    snackbarHostState.showSnackbar("Đã lưu: ${file.absolutePath}")
                }
            },
            onFailure = { e ->
                snackbarHostState.showSnackbar("Lỗi xuất PDF: ${e.message}")
            }
        )
    }
})
```

`ProgressHubScreen` cần bọc trong `Scaffold` với `snackbarHost = { SnackbarHost(snackbarHostState) }`.

---

## Data Models

Không có model mới. Các model hiện có được sử dụng trực tiếp:

| Model | Trường dùng |
|---|---|
| `NodeProgress` | `nodeCode`, `planned`, `actual`, `delayed` |
| `DailyLog` | `workItem`, `note`, `manpower`, `createdAtEpochMs` |
| `GisNode` | `code`, `mapNumberLabel`, `contractor` |

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Top-3 critical nodes là subset hợp lệ của input

*For any* danh sách `NodeProgress`, kết quả của hàm tính critical path variances phải:
- Có ≤ 3 phần tử
- Tất cả phần tử có `planned - actual > 0`
- Không có phần tử nào trong input bị bỏ sót nếu variance của nó lớn hơn variance nhỏ nhất trong kết quả

**Validates: Requirements 1.1, 1.2**

### Property 2: Công thức ước tính ngày chậm

*For any* `variance` trong khoảng `(0f, 100f]`, `estimatedDays(variance)` phải bằng `(variance * 30f / 100f).roundToInt()`.

**Validates: Requirements 1.3**

### Property 3: nodeDisplayName ưu tiên đúng thứ tự

*For any* `nodeCode: String` và `nodesMap: Map<String, GisNode>`:
- Nếu `nodesMap[nodeCode]?.mapNumberLabel` không rỗng → kết quả là `mapNumberLabel`
- Nếu `mapNumberLabel` rỗng nhưng `nodeCode` không rỗng → kết quả là `nodeCode`
- Nếu cả hai đều rỗng → kết quả là `"Node không xác định"`

**Validates: Requirements 1.6, 3.2, 4.1, 4.2, 4.3**

### Property 4: Activity Timeline lấy đúng 5 log gần nhất

*For any* danh sách `DailyLog` có kích thước bất kỳ, kết quả hiển thị phải:
- Có ≤ 5 phần tử
- Được sắp xếp giảm dần theo `createdAtEpochMs`
- Là 5 phần tử có `createdAtEpochMs` lớn nhất trong input

**Validates: Requirements 2.3**

### Property 5: Validation giá trị tiến độ

*For any* `Float` nằm ngoài khoảng `[0f, 100f]`, hàm validation phải trả về `false` (không hợp lệ). *For any* `Float` trong khoảng `[0f, 100f]`, hàm validation phải trả về `true`.

**Validates: Requirements 3.5**

### Property 6: Tên file PDF đúng định dạng

*For any* `projectId: String` và `date: Date`, tên file được tạo ra phải match pattern `tiendo_[projectId]_[yyyyMMdd].pdf`.

**Validates: Requirements 5.3**

---

## Error Handling

| Tình huống | Xử lý |
|---|---|
| `DailyLogRepository.byProject()` trả lỗi | Log bằng `AppLogger.d`, dùng `emptyList()`, không crash |
| Giá trị nhập ngoài [0, 100] | Hiển thị lỗi inline trong BottomSheet, không gọi `onAddConstruction` |
| Tạo PDF thất bại (IO, quyền) | `Result.failure(e)`, hiển thị Snackbar lỗi |
| Không có app mở PDF | Bắt `ActivityNotFoundException`, hiển thị Snackbar đường dẫn |
| `constructionProgress` rỗng khi export | Tạo PDF với ghi chú "Chưa có dữ liệu tiến độ" |

---

## Testing Strategy

### Unit tests (example-based)

Tập trung vào các hàm thuần túy và edge cases:

- `nodeDisplayName`: 3 cases (có label, không có label, cả hai rỗng)
- `estimatedDays`: các giá trị biên (0, 50, 100)
- Critical path filter: list rỗng, list có < 3 node, list có > 3 node
- Activity timeline sort: list rỗng, list có > 5 phần tử
- Validation: giá trị biên (0, 100, -1, 101, NaN)
- Tên file PDF: projectId có ký tự đặc biệt, ngày cụ thể

### Property-based tests

Dùng thư viện **[kotest-property](https://kotest.io/docs/proptest/property-based-testing.html)** (đã phổ biến trong Android/Kotlin, không cần thêm dependency nặng).

Mỗi property test chạy tối thiểu 100 iterations.

- **Property 1** — `Arb.list(arbNodeProgress)` → kiểm tra kết quả ≤ 3, tất cả variance > 0, không bỏ sót node có variance lớn hơn
- **Property 2** — `Arb.float(0.001f, 100f)` → kiểm tra công thức
- **Property 3** — `Arb.string()` × `Arb.map(...)` → kiểm tra ưu tiên label
- **Property 4** — `Arb.list(arbDailyLog)` → kiểm tra ≤ 5, sorted desc
- **Property 5** — `Arb.float()` → kiểm tra validation boundary
- **Property 6** — `Arb.string()` × `Arb.localDate()` → kiểm tra pattern tên file

Tag format cho mỗi test: `// Feature: progress-tab-completion, Property N: <mô tả>`
