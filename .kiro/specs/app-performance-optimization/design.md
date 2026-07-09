# App Performance Optimization Bugfix Design

## Overview

Ứng dụng MapSupervision bị freeze/ANR trên thiết bị cấu hình thấp do nhiều tính toán nặng đang chạy trực tiếp trên main thread. Có 4 điểm lỗi cụ thể:

1. **`getFilteredDesignNodesForMap()` / `getFilteredDesignRoutes()`** được gọi trực tiếp trong Composable (`MainActivity`) mỗi lần recomposition — tức là filter toàn bộ danh sách node/route đồng bộ trên main thread.
2. **`aiOrchestrator.execute()`** trong `refresh()` không có `withContext(Dispatchers.IO)`, có thể block coroutine trên main thread.
3. **`buildDashboard()`** trong `addConstructionProgress()` và `updateMaterialProgress()` chạy trực tiếp trên main thread (không có `withContext(Dispatchers.Default)`).
4. **`flushThreshold = 2_000`** trong `importDesignFiles()` tích lũy quá nhiều node/route trong bộ nhớ trước khi flush, gây GC pause trên thiết bị RAM thấp.

Chiến lược fix: offload tính toán sang đúng dispatcher, expose kết quả filter qua `StateFlow`, và giảm batch size khi import.

## Glossary

- **Bug_Condition (C)**: Điều kiện kích hoạt bug — tính toán nặng (filter, buildDashboard, aiOrchestrator) đang chạy trên main thread.
- **Property (P)**: Hành vi đúng — tính toán nặng phải chạy trên `Dispatchers.Default` hoặc `Dispatchers.IO`, main thread chỉ nhận kết quả đã tính xong.
- **Preservation**: Toàn bộ logic nghiệp vụ (filter chính xác, persist DB, rebuild dashboard đúng số liệu, cancel/restart refresh) phải giữ nguyên sau khi fix.
- **`getFilteredDesignNodes()`**: Hàm trong `WorkspaceViewModel` filter `designNodes` theo `searchQuery` và `filterContractor` từ `mapUi` — hiện đang là hàm đồng bộ trả về `List<GisNode>`.
- **`getFilteredDesignNodesForMap()`**: Wrapper gọi `getFilteredDesignNodes()`, được gọi trực tiếp từ Composable trong `MainActivity` — đây là điểm lỗi chính cho bug 1.1/1.2.
- **`buildDashboard()`**: Hàm private trong `WorkspaceViewModel` tổng hợp số liệu từ nodes, routes, progress, materialRows — O(n) theo số lượng node/route.
- **`aiOrchestrator.execute()`**: Gọi AI để tạo `OpsRecommendationResult` — có thể thực hiện I/O hoặc tính toán nặng.
- **`flushThreshold`**: Ngưỡng số lượng pending upserts trước khi flush xuống DB trong `importDesignFiles()` — hiện là `2_000`.
- **`WorkspaceState`**: Data class lớn chứa toàn bộ trạng thái UI — mỗi lần `_state.value = ...` kích hoạt recomposition toàn bộ Composable đang collect.
- **`MapUiState`**: Nested state trong `WorkspaceState` chứa `searchQuery`, `filterContractor`, `selectedNode`, v.v.

## Bug Details

### Bug Condition

Bug xảy ra khi tính toán nặng được thực hiện đồng bộ trên main thread. Có 4 trường hợp cụ thể:

**Formal Specification:**
```
FUNCTION isBugCondition(callSite)
  INPUT: callSite — điểm gọi hàm trong codebase
  OUTPUT: boolean

  RETURN (
    -- Bug 1: Filter chạy trên main thread trong Composable
    callSite IS "MainActivity Composable calling getFilteredDesignNodesForMap()"
    OR callSite IS "MainActivity Composable calling getFilteredDesignRoutes()"
  ) OR (
    -- Bug 2: AI orchestrator không có IO dispatcher
    callSite IS "refresh() calling aiOrchestrator.execute() without withContext(Dispatchers.IO)"
  ) OR (
    -- Bug 3: buildDashboard chạy trên main thread
    callSite IS "addConstructionProgress() calling buildDashboard() without withContext(Dispatchers.Default)"
    OR callSite IS "updateMaterialProgress() calling buildDashboard() without withContext(Dispatchers.Default)"
  ) OR (
    -- Bug 4: flushThreshold quá lớn gây peak memory
    callSite IS "importDesignFiles() with flushThreshold = 2_000 on low-RAM device"
  )
END FUNCTION
```

### Examples

- **Bug 1.1**: Mở tab Map với 500 node → `getFilteredDesignNodesForMap()` chạy đồng bộ trên main thread, filter 500 node mỗi recomposition → UI giật/freeze.
- **Bug 1.2**: Người dùng gõ "ABC" vào ô tìm kiếm → mỗi keystroke gọi `onSearchQueryChanged` → `_state.value` thay đổi → recomposition → `getFilteredDesignNodesForMap()` chạy lại trên main thread → drop frame.
- **Bug 1.3**: `refresh()` được gọi khi mở app → `aiOrchestrator.execute()` chạy không có `withContext(Dispatchers.IO)` → nếu AI thực hiện network/disk I/O → block main thread → ANR sau 5 giây.
- **Bug 1.4**: Người dùng nhập tiến độ vật tư cho node có 200 materialRows → `buildDashboard()` chạy trực tiếp trong `viewModelScope.launch` (main thread) → jank.
- **Bug 1.5**: `addConstructionProgress()` gọi `buildDashboard()` rồi `_state.value = _state.value.copy(dashboard = ...)` → toàn bộ `WorkspaceState` được copy và emit → recomposition không cần thiết.
- **Bug 1.6**: Import 10 file KML với 200 node/file → `nodes.toMutableList()` tích lũy 2000 node trong RAM trước khi flush → GC pause → UI freeze.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Logic filter node/route (normalize Vietnamese, filter theo contractor, filter theo code/name) phải cho kết quả giống hệt hiện tại.
- `refresh()` phải tiếp tục cập nhật đầy đủ `WorkspaceState` (nodes, routes, dashboard, aiOps, materialProgress) sau khi hoàn tất.
- `addConstructionProgress()` và `updateMaterialProgress()` phải tiếp tục persist dữ liệu xuống DB và rebuild dashboard với số liệu chính xác.
- Import file Excel/KML/KMZ phải tiếp tục parse, dedup, và lưu node/route vào DB đúng như hiện tại, không mất dữ liệu.
- Cancel/restart refresh job khi chuyển tab hoặc đổi dự án phải tiếp tục hoạt động đúng, không race condition.
- Empty state (danh sách rỗng) phải tiếp tục hiển thị đúng, không crash.

**Scope:**
Tất cả input không thuộc bug condition (mouse click, navigation, project switching, photo capture) phải hoàn toàn không bị ảnh hưởng bởi fix này.

## Hypothesized Root Cause

1. **Filter function được gọi như regular function trong Composable** (`MainActivity.kt` dòng 178-179): `getFilteredDesignNodesForMap()` và `getFilteredDesignRoutes()` là hàm đồng bộ trả về `List`, được gọi trực tiếp trong lambda của `when (selected)` — tức là chạy trên main thread mỗi lần recomposition. Fix: chuyển sang `StateFlow` được tính toán trên `Dispatchers.Default` bằng `combine` + `flowOn`.

2. **`aiOrchestrator.execute()` thiếu dispatcher** (`WorkspaceViewModel.kt`, hàm `refresh()`): Lệnh gọi nằm trong `viewModelScope.launch` (mặc định `Dispatchers.Main`) mà không có `withContext(Dispatchers.IO)`. Fix: bọc trong `withContext(Dispatchers.IO)`.

3. **`buildDashboard()` chạy trên main thread** (`WorkspaceViewModel.kt`, hàm `addConstructionProgress()` và `updateMaterialProgress()`): Cả hai hàm dùng `viewModelScope.launch` (main thread) và gọi `buildDashboard()` trực tiếp trước khi `_state.value = ...`. Fix: bọc `buildDashboard()` trong `withContext(Dispatchers.Default)`.

4. **`flushThreshold = 2_000` quá lớn** (`WorkspaceViewModel.kt`, hàm `importDesignFiles()`): Với 2000 node pending trong RAM cùng lúc, trên thiết bị 2GB RAM có thể gây GC pressure. Fix: giảm xuống `500` hoặc thêm time-based flush mỗi 200ms.

## Correctness Properties

Property 1: Bug Condition — Filter Nodes/Routes Không Chạy Trên Main Thread

_For any_ recomposition của `MapHubScreen` (bao gồm khi `searchQuery` thay đổi hoặc `filterContractor` thay đổi), hàm fixed SHALL cung cấp danh sách node/route đã được filter sẵn dưới dạng `StateFlow` được tính toán trên `Dispatchers.Default`, sao cho Composable chỉ collect kết quả — không thực hiện bất kỳ tính toán filter nào trên main thread.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition — `buildDashboard()` Không Chạy Trên Main Thread

_For any_ lần gọi `addConstructionProgress()` hoặc `updateMaterialProgress()`, hàm fixed SHALL thực hiện `buildDashboard()` trong `withContext(Dispatchers.Default)` trước khi cập nhật `_state.value`, sao cho main thread không bị block bởi tính toán dashboard.

**Validates: Requirements 2.4**

Property 3: Bug Condition — `aiOrchestrator.execute()` Chạy Trên IO Dispatcher

_For any_ lần gọi `refresh()`, hàm fixed SHALL bọc `aiOrchestrator.execute()` trong `withContext(Dispatchers.IO)`, sao cho mọi I/O hoặc tính toán của AI orchestrator không chạy trên main thread.

**Validates: Requirements 2.3**

Property 4: Preservation — Kết Quả Filter Giữ Nguyên

_For any_ cặp (`searchQuery`, `filterContractor`) và danh sách `designNodes`/`designRoutes`, hàm fixed SHALL trả về kết quả filter giống hệt với hàm gốc `getFilteredDesignNodes()` / `getFilteredDesignRoutes()` — bao gồm normalize Vietnamese, filter theo contractor, filter theo code/name.

**Validates: Requirements 3.2**

Property 5: Preservation — Dashboard Số Liệu Chính Xác

_For any_ lần gọi `addConstructionProgress()` hoặc `updateMaterialProgress()`, hàm fixed SHALL tạo ra `DashboardState` với số liệu giống hệt kết quả của `buildDashboard()` gốc với cùng input (nodes, routes, progress, materialRows).

**Validates: Requirements 3.3, 3.4**

## Fix Implementation

### Changes Required

**File**: `app/src/main/java/com/mapsupervision/app/workspace/WorkspaceViewModel.kt`

**Change 1 — Expose filtered lists as StateFlow**

Thêm hai `StateFlow` mới được tính toán bằng `combine` + `flowOn(Dispatchers.Default)`:

```kotlin
val filteredNodesForMap: StateFlow<List<GisNode>> = _state
    .map { s ->
        val mapUi = s.mapUi
        val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeVietnamese(mapUi.searchQuery)
        s.designNodes.filter { node ->
            val byContractor = mapUi.filterContractor.isNullOrBlank() ||
                node.contractor.equals(mapUi.filterContractor, ignoreCase = true)
            val byQuery = mapUi.searchQuery.isBlank() ||
                nodeMatchesQuery(node, mapUi.searchQuery, normalizedQuery)
            byContractor && byQuery
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

val filteredRoutes: StateFlow<List<GisRoute>> = _state
    .map { s ->
        val mapUi = s.mapUi
        val normalizedQuery = if (mapUi.searchQuery.isBlank()) "" else normalizeVietnamese(mapUi.searchQuery)
        s.designRoutes.filter { route ->
            val byContractor = mapUi.filterContractor.isNullOrBlank() ||
                route.contractor.equals(mapUi.filterContractor, ignoreCase = true)
            val byQuery = mapUi.searchQuery.isBlank() ||
                routeMatchesQuery(route, mapUi.searchQuery, normalizedQuery)
            byContractor && byQuery
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

Giữ nguyên `getFilteredDesignNodesForMap()` và `getFilteredDesignRoutes()` để không break các caller khác (hoặc deprecate dần).

**Change 2 — Cập nhật `MainActivity.kt` để collect StateFlow**

Thay thế lời gọi hàm đồng bộ bằng `collectAsState()`:

```kotlin
// Trước:
designNodes = workspaceViewModel.getFilteredDesignNodesForMap(),
designRoutes = workspaceViewModel.getFilteredDesignRoutes(),

// Sau:
designNodes = workspaceViewModel.filteredNodesForMap.collectAsState().value,
designRoutes = workspaceViewModel.filteredRoutes.collectAsState().value,
```

**Change 3 — Bọc `aiOrchestrator.execute()` trong `withContext(Dispatchers.IO)`**

Trong hàm `refresh()`:

```kotlin
// Trước:
val aiOps = runCatching {
    aiOrchestrator.execute<OpsRecommendationResult>(...)
}.getOrNull()

// Sau:
val aiOps = runCatching {
    withContext(Dispatchers.IO) {
        aiOrchestrator.execute<OpsRecommendationResult>(...)
    }
}.getOrNull()
```

**Change 4 — Bọc `buildDashboard()` trong `withContext(Dispatchers.Default)`**

Trong `addConstructionProgress()`:

```kotlin
// Trước:
_state.value = _state.value.copy(
    constructionProgress = current,
    dashboard = buildDashboard(_state.value.designNodes, _state.value.designRoutes, current, emptyList()),
    ...
)

// Sau:
val newDashboard = withContext(Dispatchers.Default) {
    buildDashboard(_state.value.designNodes, _state.value.designRoutes, current, emptyList())
}
_state.value = _state.value.copy(
    constructionProgress = current,
    dashboard = newDashboard,
    ...
)
```

Áp dụng tương tự trong `updateMaterialProgress()`.

**Change 5 — Giảm `flushThreshold` trong `importDesignFiles()`**

```kotlin
// Trước:
val flushThreshold = 2_000

// Sau:
val flushThreshold = 500
```

## Testing Strategy

### Validation Approach

Chiến lược hai giai đoạn: (1) chạy test trên code **chưa fix** để xác nhận bug tồn tại và hiểu root cause; (2) sau khi fix, chạy lại để xác nhận bug đã được giải quyết và behavior cũ được bảo toàn.

### Exploratory Bug Condition Checking

**Goal**: Xác nhận rằng các hàm filter và `buildDashboard` thực sự chạy trên main thread trước khi fix. Nếu test không fail trên code chưa fix, cần re-examine root cause.

**Test Plan**: Viết unit test dùng `TestCoroutineDispatcher` / `UnconfinedTestDispatcher` để kiểm tra thread nào đang thực thi các hàm tính toán. Chạy trên code **chưa fix** để quan sát failure.

**Test Cases**:
1. **Filter On Main Thread Test**: Gọi `getFilteredDesignNodesForMap()` với 1000 node và đo thời gian thực thi trên main thread — sẽ block > 16ms trên thiết bị chậm (fail trên code chưa fix).
2. **AI Dispatcher Test**: Spy `aiOrchestrator.execute()` và assert nó được gọi trên `Dispatchers.IO` — sẽ fail trên code chưa fix vì không có `withContext`.
3. **buildDashboard Thread Test**: Trong `addConstructionProgress()`, assert `buildDashboard()` không chạy trên `Dispatchers.Main` — sẽ fail trên code chưa fix.
4. **Memory Accumulation Test**: Import 5 file với 400 node/file và assert `pendingNodeUpserts.size` không vượt quá `flushThreshold` tại bất kỳ thời điểm nào — sẽ fail với `flushThreshold = 2_000`.

**Expected Counterexamples**:
- `getFilteredDesignNodesForMap()` chạy trên `Thread.currentThread().name == "main"`.
- `aiOrchestrator.execute()` không được gọi trên IO thread.
- `buildDashboard()` chạy trên main thread trong `addConstructionProgress()`.

### Fix Checking

**Goal**: Xác nhận rằng sau khi fix, tất cả tính toán nặng đã được offload đúng dispatcher.

**Pseudocode:**
```
FOR ALL callSite WHERE isBugCondition(callSite) DO
  result := executeFixed(callSite)
  ASSERT result.executionThread != "main"
  ASSERT result.completedWithoutBlockingMainThread == true
END FOR
```

### Preservation Checking

**Goal**: Xác nhận rằng kết quả nghiệp vụ (filter output, dashboard numbers, DB persistence) giống hệt trước khi fix.

**Pseudocode:**
```
FOR ALL (nodes, routes, searchQuery, filterContractor) DO
  original := getFilteredDesignNodes_original(nodes, searchQuery, filterContractor)
  fixed    := filteredNodesForMap_fixed.value  -- collected from StateFlow
  ASSERT original == fixed  -- same elements, same order
END FOR

FOR ALL (nodes, routes, progress, materialRows) DO
  original := buildDashboard_original(nodes, routes, progress, materialRows)
  fixed    := buildDashboard_fixed(nodes, routes, progress, materialRows)
  ASSERT original == fixed
END FOR
```

**Testing Approach**: Property-based testing phù hợp cho preservation checking vì:
- Tự động sinh nhiều bộ (nodes, searchQuery, filterContractor) khác nhau.
- Bắt được edge case như query rỗng, contractor null, danh sách rỗng.
- Đảm bảo mạnh hơn rằng logic filter không thay đổi.

**Test Cases**:
1. **Filter Result Preservation**: Với cùng input, `filteredNodesForMap` StateFlow phải emit kết quả giống hệt `getFilteredDesignNodes()` gốc.
2. **Dashboard Accuracy Preservation**: `buildDashboard()` sau khi bọc `withContext` phải trả về `DashboardState` giống hệt kết quả gốc.
3. **DB Persistence Preservation**: `addConstructionProgress()` và `updateMaterialProgress()` phải tiếp tục gọi `progressRepository.upsert()` / `materialProgressRepository.upsert()` với đúng dữ liệu.
4. **Refresh Completeness Preservation**: `refresh()` sau khi fix phải tiếp tục set đầy đủ tất cả fields của `WorkspaceState`.

### Unit Tests

- Test `filteredNodesForMap` StateFlow emit đúng kết quả khi `searchQuery` thay đổi.
- Test `filteredRoutes` StateFlow emit đúng kết quả khi `filterContractor` thay đổi.
- Test `addConstructionProgress()` gọi `buildDashboard()` trên `Dispatchers.Default` (dùng `TestCoroutineScheduler`).
- Test `updateMaterialProgress()` gọi `buildDashboard()` trên `Dispatchers.Default`.
- Test `refresh()` gọi `aiOrchestrator.execute()` trên `Dispatchers.IO`.
- Test `importDesignFiles()` flush khi `pendingNodeUpserts.size >= 500`.
- Test empty state: `designNodes = emptyList()` → `filteredNodesForMap` emit `emptyList()`, không crash.

### Property-Based Tests

- Với bất kỳ `List<GisNode>` và `searchQuery: String` nào, `filteredNodesForMap` phải trả về tập con của `designNodes` thỏa mãn điều kiện filter — giống hệt kết quả của `getFilteredDesignNodes()` gốc.
- Với bất kỳ `List<GisNode>`, `List<NodeProgress>`, `List<MaterialProgress>` nào, `buildDashboard()` phải trả về `DashboardState` với `totalDesignNodes == nodes.size` và `totalActualQty == sum(materialRows.actualQty)`.
- Với bất kỳ batch import nào, `pendingNodeUpserts.size` tại mọi thời điểm phải `<= flushThreshold`.

### Integration Tests

- Test full flow: import file → refresh → gõ search query → `filteredNodesForMap` emit kết quả đúng mà không block main thread.
- Test chuyển tab: switch project → `refreshJob` cũ bị cancel → refresh mới bắt đầu → `WorkspaceState` được cập nhật đầy đủ.
- Test `addConstructionProgress()` end-to-end: gọi hàm → DB được persist → `dashboard.delayedCount` được cập nhật đúng → UI nhận state mới.
