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
fun NonExcelMappingDialog(
    state: ImportMappingUiState,
    onDismiss: () -> Unit,
    onUpdateMapping: (
        String?, String?, String?, String?, String?, String?, String?,
        String?, String?, String?, String?, String?, String?,
        Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?,
        Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?
    ) -> Unit,
    onConfirmParse: () -> Unit
) {
    val candidates = state.candidates
    val selectedItems = remember(state.workVolumeFieldsCsv) {
        mutableStateListOf<String>().apply {
            addAll(state.workVolumeFieldsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
    }

    fun update(
        positionField: String? = null,
        coordinateField: String? = null,
        contractorField: String? = null,
        mapNumberField: String? = null,
        objectTypeField: String? = null,
        workVolumeFieldsCsv: String? = null,
        routeLengthField: String? = null,
        ipAddressField: String? = null,
        subnetField: String? = null,
        gatewayField: String? = null,
        signalStatusField: String? = null,
        fiberCoreCountField: String? = null,
        fiberConnectionField: String? = null,
        confirmedPositionField: Boolean? = null,
        confirmedCoordinateField: Boolean? = null,
        confirmedContractorField: Boolean? = null,
        confirmedMapNumberField: Boolean? = null,
        confirmedObjectTypeField: Boolean? = null,
        confirmedWorkVolumeFields: Boolean? = null,
        confirmedRouteLengthField: Boolean? = null,
        confirmedIpAddressField: Boolean? = null,
        confirmedSubnetField: Boolean? = null,
        confirmedGatewayField: Boolean? = null,
        confirmedSignalStatusField: Boolean? = null,
        confirmedFiberCoreCountField: Boolean? = null,
        confirmedFiberConnectionField: Boolean? = null
    ) {
        onUpdateMapping(
            positionField,
            coordinateField,
            contractorField,
            mapNumberField,
            objectTypeField,
            workVolumeFieldsCsv,
            routeLengthField,
            ipAddressField,
            subnetField,
            gatewayField,
            signalStatusField,
            fiberCoreCountField,
            fiberConnectionField,
            confirmedPositionField,
            confirmedCoordinateField,
            confirmedContractorField,
            confirmedMapNumberField,
            confirmedObjectTypeField,
            confirmedWorkVolumeFields,
            confirmedRouteLengthField,
            confirmedIpAddressField,
            confirmedSubnetField,
            confirmedGatewayField,
            confirmedSignalStatusField,
            confirmedFiberCoreCountField,
            confirmedFiberConnectionField
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Ánh xạ dữ liệu bản vẽ",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Chọn các thuộc tính cần dùng để nhập dữ liệu GIS. Chỉ những trường đã xác nhận mới được áp dụng.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                if (state.message.isNotBlank()) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .fillMaxWidth()
                    )
                }

                MappingConfirmSection(title = "Định danh & Tọa độ") {
                    ColumnSectionWithConfirm(
                        label = "Tên đối tượng / vị trí trạm",
                        selected = state.positionField,
                        options = candidates.positionOptions,
                        confirmed = state.confirmedPositionField,
                        onSelected = { update(positionField = it) },
                        onConfirmedChange = { update(confirmedPositionField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Tọa độ hình học",
                        selected = state.coordinateField,
                        options = candidates.coordinateOptions,
                        confirmed = state.confirmedCoordinateField,
                        onSelected = { update(coordinateField = it) },
                        onConfirmedChange = { update(confirmedCoordinateField = it) }
                    )
                }

                MappingConfirmSection(title = "Nhà thầu & Bản đồ") {
                    ColumnSectionWithConfirm(
                        label = "Nhà thầu",
                        selected = state.contractorField,
                        options = candidates.contractorOptions,
                        confirmed = state.confirmedContractorField,
                        onSelected = { update(contractorField = it) },
                        onConfirmedChange = { update(confirmedContractorField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Số hiển thị trên bản đồ",
                        selected = state.mapNumberField,
                        options = candidates.mapNumberOptions,
                        confirmed = state.confirmedMapNumberField,
                        onSelected = { update(mapNumberField = it) },
                        onConfirmedChange = { update(confirmedMapNumberField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Loại đối tượng",
                        selected = state.objectTypeField,
                        options = candidates.objectTypeOptions,
                        confirmed = state.confirmedObjectTypeField,
                        onSelected = { update(objectTypeField = it) },
                        onConfirmedChange = { update(confirmedObjectTypeField = it) }
                    )
                }

                MappingConfirmSection(title = "Thông tin mạng (Nút)") {
                    ColumnSectionWithConfirm(
                        label = "IP",
                        selected = state.ipAddressField,
                        options = candidates.ipAddressOptions,
                        confirmed = state.confirmedIpAddressField,
                        onSelected = { update(ipAddressField = it) },
                        onConfirmedChange = { update(confirmedIpAddressField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Subnet",
                        selected = state.subnetField,
                        options = candidates.subnetOptions,
                        confirmed = state.confirmedSubnetField,
                        onSelected = { update(subnetField = it) },
                        onConfirmedChange = { update(confirmedSubnetField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Gateway",
                        selected = state.gatewayField,
                        options = candidates.gatewayOptions,
                        confirmed = state.confirmedGatewayField,
                        onSelected = { update(gatewayField = it) },
                        onConfirmedChange = { update(confirmedGatewayField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Trạng thái tín hiệu",
                        selected = state.signalStatusField,
                        options = candidates.signalStatusOptions,
                        confirmed = state.confirmedSignalStatusField,
                        onSelected = { update(signalStatusField = it) },
                        onConfirmedChange = { update(confirmedSignalStatusField = it) }
                    )
                }

                MappingConfirmSection(title = "Thông tin mạng (Tuyến)") {
                    ColumnSectionWithConfirm(
                        label = "Chiều dài tuyến",
                        selected = state.routeLengthField,
                        options = candidates.routeLengthOptions,
                        confirmed = state.confirmedRouteLengthField,
                        onSelected = { update(routeLengthField = it) },
                        onConfirmedChange = { update(confirmedRouteLengthField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Số core quang",
                        selected = state.fiberCoreCountField,
                        options = candidates.fiberCoreCountOptions,
                        confirmed = state.confirmedFiberCoreCountField,
                        onSelected = { update(fiberCoreCountField = it) },
                        onConfirmedChange = { update(confirmedFiberCoreCountField = it) }
                    )
                    ColumnSectionWithConfirm(
                        label = "Sợi kết nối",
                        selected = state.fiberConnectionField,
                        options = candidates.fiberConnectionOptions,
                        confirmed = state.confirmedFiberConnectionField,
                        onSelected = { update(fiberConnectionField = it) },
                        onConfirmedChange = { update(confirmedFiberConnectionField = it) }
                    )
                }

                if (candidates.itemOptions.isNotEmpty()) {
                    MappingConfirmSection(title = "Vật tư & Khối lượng thiết kế") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Chọn các trường đi kèm",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Tất cả",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        selectedItems.clear()
                                        selectedItems.addAll(candidates.itemOptions)
                                        update(workVolumeFieldsCsv = selectedItems.joinToString(","))
                                    }
                                )
                                Text(
                                    text = "Bỏ chọn",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        selectedItems.clear()
                                        update(workVolumeFieldsCsv = "")
                                    }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            candidates.itemOptions.forEach { option ->
                                val checked = selectedItems.contains(option)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (checked) selectedItems.remove(option) else selectedItems.add(option)
                                            update(workVolumeFieldsCsv = selectedItems.joinToString(","))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            if (it) selectedItems.add(option) else selectedItems.remove(option)
                                            update(workVolumeFieldsCsv = selectedItems.joinToString(","))
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(option, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.confirmedWorkVolumeFields,
                                onCheckedChange = { update(confirmedWorkVolumeFields = it) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Xác nhận nhập thông tin vật tư đi kèm", color = Color.LightGray, fontSize = 12.sp)
                        }
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
                Text("Xác nhận nhập bản vẽ", fontWeight = FontWeight.Bold)
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
private fun MappingConfirmSection(
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

@Composable
private fun ColumnSectionWithConfirm(
    label: String,
    selected: String,
    options: List<String>,
    confirmed: Boolean,
    onSelected: (String) -> Unit,
    onConfirmedChange: (Boolean) -> Unit
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = selected.ifBlank { "Chưa cấu hình" },
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = confirmed,
                onCheckedChange = onConfirmedChange,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Xác nhận dùng trường này", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}
