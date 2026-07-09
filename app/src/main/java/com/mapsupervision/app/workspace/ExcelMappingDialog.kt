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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

@Composable
fun ExcelMappingDialog(
    state: ExcelParserUiState,
    onDismiss: () -> Unit,
    onUpdateExcelMapping: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onUpdateCoordinateMode: (Boolean) -> Unit,
    onUpdateMapVisualOptions: (Boolean?, Boolean?) -> Unit,
    onConfirmParse: () -> Unit,
    onUpdateSelectedSheet: (String) -> Unit
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anh xa cot Excel theo noi dung", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Ung dung tu phat hien cot Excel. Vui long doi soat mau truoc khi nhap.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (state.sheets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Chon worksheet", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        DropdownField(
                            selected = state.selectedSheet.ifBlank { state.sheets.firstOrNull().orEmpty() },
                            options = state.sheets,
                            onSelected = onUpdateSelectedSheet
                        )
                    }
                }

                MappingSection("Định danh & Tọa độ") {
                    ColumnSection("Cột tên đối tượng / vị trí", state.positionColumn, allHeaders, previews) { update(positionColumn = it) }
                    Text("Định dạng tọa độ trong Excel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                        ColumnSection("Cột tọa độ GPS (lat,lon)", state.coordinateColumn, allHeaders, previews) { update(coordinateColumn = it) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                ColumnSection("Cột vĩ độ (Latitude)", state.latitudeColumn, allHeaders, previews) { update(latitudeColumn = it) }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                ColumnSection("Cột kinh độ (Longitude)", state.longitudeColumn, allHeaders, previews) { update(longitudeColumn = it) }
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
        },
        confirmButton = {
            Button(
                onClick = onConfirmParse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Xác nhận nhập dữ liệu", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.97f)
            .fillMaxHeight(0.97f)
            .navigationBarsPadding()
            .imePadding(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
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
