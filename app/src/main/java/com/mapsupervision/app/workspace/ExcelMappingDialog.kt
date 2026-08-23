package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.domain.model.DuplicateImportPolicy
import com.mapsupervision.domain.model.DuplicateBusinessKey

@Composable
fun ExcelMappingDialog(
    state: ExcelParserUiState,
    onDismiss: () -> Unit,
    onUpdateExcelMapping: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onUpdateCoordinateMode: (Boolean) -> Unit,
    onUpdateMapVisualOptions: (Boolean?, Boolean?) -> Unit,
    onConfirmParse: () -> Unit,
    onUpdateSelectedSheet: (String) -> Unit,
    onToggleAllowPartialImport: (Boolean) -> Unit = {},
    onToggleConfirmedCustomColumn: (String) -> Unit = {},
    onUpdateDuplicatePolicy: (DuplicateImportPolicy) -> Unit = {},
    onUpdateDeduplicationKey: (DuplicateBusinessKey) -> Unit = {}
) {
    val previews = remember(state.headers, state.sampleRows) {
        state.headers.map { header ->
            val samples = state.sampleRows.mapNotNull { row -> row[header]?.trim()?.takeIf { it.isNotBlank() } }.take(3)
            header to samples
        }
    }
    val allHeaders = state.headers
    val selectedMaterials = remember(state.workVolumeColumnsCsv) {
        mutableStateListOf<String>().apply {
            addAll(state.workVolumeColumnsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    fun update(
        positionColumn: String? = null,
        coordinateColumn: String? = null,
        latitudeColumn: String? = null,
        longitudeColumn: String? = null,
        contractorColumn: String? = null,
        mapNumberColumn: String? = null,
        objectTypeColumn: String? = null,
        ipAddressColumn: String? = null,
        subnetColumn: String? = null,
        gatewayColumn: String? = null,
        signalStatusColumn: String? = null,
        fiberCoreCountColumn: String? = null,
        fiberConnectionColumn: String? = null,
        workVolumeColumnsCsv: String? = null
    ) {
        onUpdateExcelMapping(
            positionColumn,
            coordinateColumn,
            latitudeColumn,
            longitudeColumn,
            contractorColumn,
            mapNumberColumn,
            objectTypeColumn,
            ipAddressColumn,
            subnetColumn,
            gatewayColumn,
            signalStatusColumn,
            fiberCoreCountColumn,
            fiberConnectionColumn,
            workVolumeColumnsCsv
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text("Ánh xạ cột dữ liệu bảng", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Hệ thống tự động phát hiện cấu trúc cột. Vui lòng đối soát và xác nhận trước khi nhập.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

                    // Validation Summary Card
                    if (state.validationErrors.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Chưa thể xác nhận nhập:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                state.validationErrors.forEach { err ->
                                    Text("• $err", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                                }
                            }
                        }
                    } else if (state.invalidRowCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Phát hiện ${state.invalidRowCount} dòng lỗi (${state.validRowCount} dòng hợp lệ)",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontSize = 13.sp
                                )
                                if (state.invalidRowSamples.isNotEmpty()) {
                                    Text("Chi tiết lỗi mẫu:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    state.invalidRowSamples.forEach { sample ->
                                        Text("• $sample", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleAllowPartialImport(!state.allowPartialImport) }
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = state.allowPartialImport,
                                        onCheckedChange = { onToggleAllowPartialImport(it) },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Cho phép chỉ nhập ${state.validRowCount} dòng hợp lệ (bỏ qua dòng lỗi)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    } else if (state.validRowCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓ Toàn bộ ${state.validRowCount} dòng dữ liệu hợp lệ và sẵn sàng nhập.", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                            }
                        }
                    }

                    if (state.message.isNotBlank() && state.validationErrors.isEmpty() && state.invalidRowCount == 0) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Đang xử lý nhập dữ liệu, vui lòng chờ...", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }

                    if (state.sheets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Chọn worksheet", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            DropdownField(
                                selected = state.selectedSheet.ifBlank { state.sheets.firstOrNull().orEmpty() },
                                options = state.sheets,
                                onSelected = onUpdateSelectedSheet
                            )
                        }
                    }

                    MappingSection("Định danh & Tọa độ") {
                        ColumnSection("Cột tên đối tượng / vị trí (*)", state.positionColumn, allHeaders, previews) { update(positionColumn = it) }
                        Text("Định dạng tọa độ trong bảng dữ liệu (*)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onUpdateCoordinateMode(false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!state.useTwoColumnCoordinates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (!state.useTwoColumnCoordinates) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("1 cột (lat,lon)", fontSize = 12.sp) }
                            Button(
                                onClick = { onUpdateCoordinateMode(true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.useTwoColumnCoordinates) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.useTwoColumnCoordinates) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("2 cột (vĩ/kinh)", fontSize = 12.sp) }
                        }

                        if (!state.useTwoColumnCoordinates) {
                            ColumnSection("Cột tọa độ GPS (lat,lon) (*)", state.coordinateColumn, allHeaders, previews) { update(coordinateColumn = it) }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    ColumnSection("Cột vĩ độ (Latitude) (*)", state.latitudeColumn, allHeaders, previews) { update(latitudeColumn = it) }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    ColumnSection("Cột kinh độ (Longitude) (*)", state.longitudeColumn, allHeaders, previews) { update(longitudeColumn = it) }
                                }
                            }
                        }
                    }

                    MappingSection("Nhà thầu & Bản đồ") {
                        ColumnSection("Cột nhà thầu", state.contractorColumn, allHeaders, previews) { update(contractorColumn = it) }
                        ColumnSection("Cột số hiển thị trên bản đồ", state.mapNumberColumn, allHeaders, previews) { update(mapNumberColumn = it) }
                    }

                    MappingSection("Thông tin mạng (Nút)") {
                        ColumnSection("Cột IP", state.ipAddressColumn, allHeaders, previews) { update(ipAddressColumn = it) }
                        ColumnSection("Cột Subnet", state.subnetColumn, allHeaders, previews) { update(subnetColumn = it) }
                        ColumnSection("Cột Gateway", state.gatewayColumn, allHeaders, previews) { update(gatewayColumn = it) }
                        ColumnSection("Cột trạng thái tín hiệu", state.signalStatusColumn, allHeaders, previews) { update(signalStatusColumn = it) }
                    }

                    MappingSection("Thông tin mạng (Tuyến)") {
                        ColumnSection("Cột số core quang", state.fiberCoreCountColumn, allHeaders, previews) { update(fiberCoreCountColumn = it) }
                        ColumnSection("Cột sợi kết nối", state.fiberConnectionColumn, allHeaders, previews) { update(fiberConnectionColumn = it) }
                    }

                    val excluded = setOf(
                        state.positionColumn,
                        state.contractorColumn,
                        state.mapNumberColumn,
                        state.ipAddressColumn,
                        state.subnetColumn,
                        state.gatewayColumn,
                        state.signalStatusColumn,
                        state.fiberCoreCountColumn,
                        state.fiberConnectionColumn,
                        if (state.useTwoColumnCoordinates) state.latitudeColumn else state.coordinateColumn,
                        if (state.useTwoColumnCoordinates) state.longitudeColumn else ""
                    )
                    val availableHeaders = allHeaders.filter { it !in excluded }

                    MappingSection("Vật tư & Khối lượng thiết kế") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chọn cột công việc / khối lượng", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Chọn tất cả",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            selectedMaterials.clear()
                                            selectedMaterials.addAll(availableHeaders)
                                            update(workVolumeColumnsCsv = selectedMaterials.joinToString(","))
                                        }
                                        .padding(4.dp)
                                )
                                Text(
                                    text = "Bỏ chọn",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            selectedMaterials.clear()
                                            update(workVolumeColumnsCsv = "")
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            availableHeaders.forEach { header ->
                                val checked = selectedMaterials.contains(header)
                                val sample = previews.firstOrNull { it.first == header }?.second?.joinToString(", ").orEmpty()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) selectedMaterials.remove(header) else selectedMaterials.add(header)
                                            update(workVolumeColumnsCsv = selectedMaterials.joinToString(","))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selectedMaterials.add(header) else selectedMaterials.remove(header)
                                            update(workVolumeColumnsCsv = selectedMaterials.joinToString(","))
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(header, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                        if (sample.isNotBlank()) Text("Mẫu: $sample", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Decision D4: Custom Metadata / Unmapped Columns
                    val unmappedHeaders = availableHeaders.filter { !selectedMaterials.contains(it) }
                    if (unmappedHeaders.isNotEmpty()) {
                        MappingSection("Cột mở rộng (Metadata lưu trữ thêm)") {
                            Text("Chọn các cột chưa ánh xạ cần lưu vào trường mở rộng của đối tượng:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                unmappedHeaders.forEach { header ->
                                    val isConfirmed = state.confirmedCustomColumns.contains(header)
                                    val sample = previews.firstOrNull { it.first == header }?.second?.joinToString(", ").orEmpty()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleConfirmedCustomColumn(header) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isConfirmed,
                                            onCheckedChange = { onToggleConfirmedCustomColumn(header) },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(header, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                            if (sample.isNotBlank()) Text("Mẫu: $sample", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MappingSection("Xử lý dữ liệu trùng & Khóa nghiệp vụ") {
                        Text(
                            "Chọn khóa nghiệp vụ để nhận diện bản ghi trùng. Hệ thống cho phép Bỏ qua (SKIP) hoặc Cập nhật/Ghi đè (UPDATE).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Khóa nhận diện trùng:",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onUpdateDeduplicationKey(DuplicateBusinessKey.CODE) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.deduplicationKey == DuplicateBusinessKey.CODE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.deduplicationKey == DuplicateBusinessKey.CODE) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) {
                                Text("Mã vị trí" + if (state.suggestedBusinessKey == DuplicateBusinessKey.CODE) " ★" else "", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onUpdateDeduplicationKey(DuplicateBusinessKey.COORDINATES) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.deduplicationKey == DuplicateBusinessKey.COORDINATES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.deduplicationKey == DuplicateBusinessKey.COORDINATES) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) {
                                Text("Tọa độ" + if (state.suggestedBusinessKey == DuplicateBusinessKey.COORDINATES) " ★" else "", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onUpdateDeduplicationKey(DuplicateBusinessKey.COMPOSITE_CODE_COORD) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.deduplicationKey == DuplicateBusinessKey.COMPOSITE_CODE_COORD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.deduplicationKey == DuplicateBusinessKey.COMPOSITE_CODE_COORD) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) {
                                Text("Mã + Tọa độ" + if (state.suggestedBusinessKey == DuplicateBusinessKey.COMPOSITE_CODE_COORD) " ★" else "", fontSize = 11.sp)
                            }
                        }

                        if (state.duplicateRowCount > 0) {
                            Surface(
                                color = Color(0xFFFFF3CD),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Phát hiện ${state.duplicateRowCount} bản ghi trùng theo khóa đã chọn.",
                                    color = Color(0xFF856404),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Chính sách xử lý khi trùng:",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onUpdateDuplicatePolicy(DuplicateImportPolicy.SKIP) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.duplicatePolicy == DuplicateImportPolicy.SKIP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.duplicatePolicy == DuplicateImportPolicy.SKIP) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("Bỏ qua (SKIP)", fontSize = 12.sp) }
                            Button(
                                onClick = { onUpdateDuplicatePolicy(DuplicateImportPolicy.UPDATE) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.duplicatePolicy == DuplicateImportPolicy.UPDATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.duplicatePolicy == DuplicateImportPolicy.UPDATE) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("Cập nhật (UPDATE)", fontSize = 12.sp) }
                        }
                    }

                    MappingSection("Cấu hình hiển thị bản đồ") {
                        Text("Hiển thị số trên bản đồ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onUpdateMapVisualOptions(false, null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!state.showNumberOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (!state.showNumberOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("Ẩn số", fontSize = 12.sp) }
                            Button(
                                onClick = { onUpdateMapVisualOptions(true, null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.showNumberOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.showNumberOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("Hiện số", fontSize = 12.sp) }
                        }

                        Text("Màu đối tượng trên bản đồ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onUpdateMapVisualOptions(null, false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!state.colorByContractorOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (!state.colorByContractorOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("Đơn sắc", fontSize = 12.sp) }
                            Button(
                                onClick = { onUpdateMapVisualOptions(null, true) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.colorByContractorOnMap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (state.colorByContractorOnMap) MaterialTheme.colorScheme.onPrimary else Color.White
                                )
                            ) { Text("Theo nhà thầu", fontSize = 12.sp) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.isLoading) {
                        Text("Hủy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            AppLogger.d(
                                "import.mapping.flow confirm.click file=${state.sourceFileName} " +
                                    "headers=${state.headers.size} loading=${state.isLoading} canConfirm=${state.canConfirm}"
                            )
                            onConfirmParse()
                        },
                        enabled = !state.isLoading && state.canConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            if (state.isLoading) "Đang nhập dữ liệu..."
                            else when {
                                !state.canConfirm && state.validationErrors.isNotEmpty() -> "Thiếu cột bắt buộc"
                                !state.canConfirm && state.invalidRowCount > 0 -> "Có dòng dữ liệu lỗi"
                                state.canConfirm && state.invalidRowCount > 0 -> "Nhập ${state.validRowCount} dòng hợp lệ"
                                else -> "Xác nhận nhập dữ liệu (${state.validRowCount})"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnSection(
    label: String,
    selected: String,
    options: List<String>,
    previews: List<Pair<String, List<String>>>,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        DropdownField(selected = selected, options = options, onSelected = onSelected)
        val sample = previews.firstOrNull { it.first == selected }?.second?.joinToString(", ").orEmpty()
        if (sample.isNotBlank()) Text("Nội dung mẫu: $sample", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
    }
}

@Composable
private fun DropdownField(selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.ifBlank { "Chưa cấu hình" },
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Mở danh sách")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MappingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(
                text = title.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            content()
        }
    )
}
