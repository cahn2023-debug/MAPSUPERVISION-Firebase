# Bugfix Requirements Document

## Introduction

Ứng dụng MapSupervision (Android, Kotlin + Jetpack Compose) bị treo (freeze/ANR) khi sử dụng, đặc biệt trên các thiết bị cấu hình thấp. Các tình huống gây treo chủ yếu xảy ra khi: (1) `getFilteredDesignNodesForMap()` và `getFilteredDesignRoutes()` được gọi trực tiếp từ Composable trong `MainShell` — tức là chạy đồng bộ trên main thread mỗi lần recomposition với toàn bộ danh sách node/route; (2) `aiOrchestrator.execute()` được gọi trong `refresh()` mà không có dispatcher IO, có thể block coroutine trên main thread; (3) `buildDashboard()` được gọi trực tiếp trên main thread nhiều lần (mỗi lần `updateMaterialProgress`, `addConstructionProgress`, v.v.); (4) `_state.value = ...` cập nhật toàn bộ `WorkspaceState` lớn kích hoạt recomposition toàn bộ UI. Hậu quả là UI bị đóng băng (freeze) hoặc hệ thống báo ANR (Application Not Responding) sau 5 giây không phản hồi trên main thread.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN ứng dụng render màn hình MapHubScreen với danh sách lớn (hàng trăm node/route) THEN hệ thống gọi `getFilteredDesignNodesForMap()` và `getFilteredDesignRoutes()` đồng bộ trên main thread trong mỗi lần recomposition, khiến UI bị giật hoặc đóng băng.

1.2 WHEN người dùng nhập ký tự vào ô tìm kiếm trên MapHubScreen THEN hệ thống thực hiện filter toàn bộ danh sách node/route đồng bộ trên main thread mỗi keystroke, gây drop frame và freeze trên thiết bị cấu hình thấp.

1.3 WHEN `refresh()` được gọi (khi mở app, chuyển tab, hoặc đổi dự án) THEN hệ thống gọi `aiOrchestrator.execute()` mà không đảm bảo chạy trên Dispatchers.IO, có thể block coroutine trên main thread nếu AI orchestrator thực hiện I/O hoặc tính toán nặng.

1.4 WHEN người dùng cập nhật tiến độ vật tư (`updateMaterialProgress`) THEN hệ thống gọi `buildDashboard()` trực tiếp trên main thread (trong `viewModelScope.launch` mà không có `withContext(Dispatchers.Default)`), gây jank khi danh sách node/route lớn.

1.5 WHEN người dùng thêm tiến độ thi công (`addConstructionProgress`) THEN hệ thống cập nhật `_state.value` với toàn bộ `WorkspaceState` mới bao gồm rebuild dashboard, gây recomposition toàn bộ UI không cần thiết.

1.6 WHEN người dùng import nhiều file Excel/KML/KMZ cùng lúc trên thiết bị RAM thấp THEN hệ thống tích lũy toàn bộ node/route trong bộ nhớ (`nodes.toMutableList()`, `routes.toMutableList()`) trước khi flush, có thể gây OOM hoặc GC pause kéo dài làm treo UI.

### Expected Behavior (Correct)

2.1 WHEN ứng dụng render màn hình MapHubScreen THEN hệ thống SHALL cung cấp danh sách node/route đã được filter sẵn dưới dạng `StateFlow` (được tính toán trên `Dispatchers.Default`), để Composable chỉ collect kết quả mà không thực hiện tính toán trên main thread.

2.2 WHEN người dùng nhập ký tự vào ô tìm kiếm THEN hệ thống SHALL debounce query và thực hiện filter trên `Dispatchers.Default`, chỉ emit kết quả mới lên UI sau khi tính toán hoàn tất, không block main thread.

2.3 WHEN `refresh()` được gọi THEN hệ thống SHALL bọc `aiOrchestrator.execute()` trong `withContext(Dispatchers.IO)` để đảm bảo mọi I/O hoặc tính toán của AI orchestrator không chạy trên main thread.

2.4 WHEN người dùng cập nhật tiến độ vật tư THEN hệ thống SHALL thực hiện `buildDashboard()` trong `withContext(Dispatchers.Default)` trước khi cập nhật `_state.value`, không để tính toán nặng chạy trên main thread.

2.5 WHEN `_state.value` được cập nhật THEN hệ thống SHALL chỉ cập nhật các trường thực sự thay đổi (sử dụng `copy()` có chọn lọc), tránh kích hoạt recomposition toàn bộ UI không cần thiết.

2.6 WHEN người dùng import file trên thiết bị RAM thấp THEN hệ thống SHALL flush pending geometry xuống DB sau mỗi batch nhỏ hơn (giảm `flushThreshold` hoặc flush theo thời gian), giảm peak memory footprint trong quá trình import.

### Unchanged Behavior (Regression Prevention)

3.1 WHEN người dùng import file Excel/KML/KMZ hợp lệ THEN hệ thống SHALL CONTINUE TO parse, dedup, và lưu node/route vào DB đúng như hiện tại, không mất dữ liệu.

3.2 WHEN người dùng tìm kiếm node theo mã hoặc tên THEN hệ thống SHALL CONTINUE TO trả về kết quả filter chính xác như logic hiện tại (bao gồm normalize Vietnamese, filter theo contractor).

3.3 WHEN `refresh()` hoàn tất THEN hệ thống SHALL CONTINUE TO cập nhật đầy đủ `WorkspaceState` bao gồm nodes, routes, dashboard, aiOps, materialProgress.

3.4 WHEN người dùng cập nhật tiến độ vật tư THEN hệ thống SHALL CONTINUE TO persist dữ liệu xuống DB và rebuild dashboard với số liệu chính xác.

3.5 WHEN người dùng chuyển tab hoặc đổi dự án THEN hệ thống SHALL CONTINUE TO cancel refresh job cũ và bắt đầu refresh mới, không bị race condition.

3.6 WHEN danh sách node/route rỗng THEN hệ thống SHALL CONTINUE TO hiển thị UI đúng (empty state) mà không crash.
