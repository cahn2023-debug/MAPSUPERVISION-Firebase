package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*

@Composable
fun DashboardHubScreen(
    state: WorkspaceState,
    onRefresh: () -> Unit = {}
) {
    val d = state.dashboard
    val colors = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val darkBg = colors.background
    val cardBg = extendedColors.panelBackgroundAlt
    val orange = extendedColors.mapAccent
    val textColor = colors.onBackground
    val secondaryText = colors.onSurfaceVariant
    val green = extendedColors.success
    val red = extendedColors.danger
    val blue = colors.primary
    val dividerColor = colors.outlineVariant

    WorkspaceRefreshContainer(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Header
        item {
            Column {
                    Text(
                        "Dashboard Báo cáo",
                        color = textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tổng hợp tiến độ thi công hạ tầng kỹ thuật",
                        color = secondaryText,
                        fontSize = 14.sp
                    )
                    if (state.isRefreshing) {
                        Text(
                            "Đang đồng bộ dữ liệu...",
                            color = orange,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
        }

        if (state.activeProjectId == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Vui lòng chọn dự án để xem báo cáo",
                            color = secondaryText,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Summary stat cards — 2 per row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.LocationOn,
                    iconTint = blue,
                    label = "Tổng nút thiết kế",
                    value = "${d.totalDesignNodes}",
                    cardBg = cardBg,
                    textColor = textColor,
                    secondaryText = secondaryText
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Route,
                    iconTint = orange,
                    label = "Tổng tuyến thiết kế",
                    value = "${d.totalDesignRoutes}",
                    cardBg = cardBg,
                    textColor = textColor,
                    secondaryText = secondaryText
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = green,
                    label = "Nút đã nhập thi công",
                    value = "${d.nodesWithMaterialEntry}",
                    subLabel = "/ ${d.totalDesignNodes} nút",
                    cardBg = cardBg,
                    textColor = textColor,
                    secondaryText = secondaryText
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Warning,
                    iconTint = red,
                    label = "Chậm tiến độ",
                    value = "${d.delayedCount}",
                    subLabel = "nút",
                    cardBg = cardBg,
                    textColor = textColor,
                    secondaryText = secondaryText
                )
            }
        }

        // Material quantity progress card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Inventory,
                            contentDescription = null,
                            tint = orange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Khối lượng công việc",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Planned vs Actual bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("KL thiết kế", color = secondaryText, fontSize = 13.sp)
                            Text(
                                formatQty(d.totalPlannedQty),
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("KL thi công", color = orange, fontSize = 13.sp)
                            Text(
                                formatQty(d.totalActualQty),
                                color = orange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val materialPct = (d.materialCompletionPercent / 100f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { materialPct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = orange,
                            trackColor = dividerColor
                        )
                        Text(
                            "Hoàn thành: ${String.format("%.1f", d.materialCompletionPercent)}%",
                            color = if (d.materialCompletionPercent >= 80f) green else orange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Overall node progress card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.BarChart,
                            contentDescription = null,
                            tint = blue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tiến độ tổng thể (theo nút)",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Nút đã cập nhật", color = secondaryText, fontSize = 13.sp)
                            Text(
                                "${d.updatedConstructionNodes} / ${d.totalDesignNodes}",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val nodePct = if (d.totalDesignNodes > 0)
                            (d.updatedConstructionNodes.toFloat() / d.totalDesignNodes).coerceIn(0f, 1f)
                        else 0f
                        LinearProgressIndicator(
                            progress = { nodePct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = blue,
                            trackColor = dividerColor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Tiến độ trung bình: ${String.format("%.1f", d.completionPercent)}%",
                                color = blue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (d.delayedCount > 0) {
                                Text(
                                    "${d.delayedCount} nút chậm",
                                    color = red,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (d.updatedConstructionNodes > 0) {
                                Text(
                                    "Đúng tiến độ",
                                    color = green,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status summary row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Tóm tắt trạng thái",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    HorizontalDivider(color = dividerColor)

                    SummaryRow(
                        label = "Dự án đang hoạt động",
                        value = state.activeProjectId ?: "-",
                        valueColor = orange,
                        secondaryText = secondaryText,
                        textColor = textColor
                    )
                    SummaryRow(
                        label = "Tổng nút thiết kế",
                        value = "${d.totalDesignNodes} nút",
                        valueColor = textColor,
                        secondaryText = secondaryText,
                        textColor = textColor
                    )
                    SummaryRow(
                        label = "Nút đã nhập KL thi công",
                        value = "${d.nodesWithMaterialEntry} nút",
                        valueColor = if (d.nodesWithMaterialEntry > 0) green else secondaryText,
                        secondaryText = secondaryText,
                        textColor = textColor
                    )
                    SummaryRow(
                        label = "KL thi công / thiết kế",
                        value = "${formatQty(d.totalActualQty)} / ${formatQty(d.totalPlannedQty)}",
                        valueColor = orange,
                        secondaryText = secondaryText,
                        textColor = textColor
                    )
                    SummaryRow(
                        label = "Tỷ lệ hoàn thành công việc",
                        value = "${String.format("%.1f", d.materialCompletionPercent)}%",
                        valueColor = if (d.materialCompletionPercent >= 80f) green else orange,
                        secondaryText = secondaryText,
                        textColor = textColor
                    )
                    SummaryRow(
                        label = "Nút chậm tiến độ",
                        value = if (d.delayedCount > 0) "${d.delayedCount} nút" else "Không có",
                        valueColor = if (d.delayedCount > 0) red else green,
                        secondaryText = secondaryText,
                        textColor = textColor
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    subLabel: String = "",
    cardBg: Color,
    textColor: Color,
    secondaryText: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Text(
                text = value + if (subLabel.isNotBlank()) " $subLabel" else "",
                color = textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(label, color = secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color,
    secondaryText: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = secondaryText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatQty(qty: Float): String {
    return if (qty == qty.toLong().toFloat()) qty.toLong().toString() else String.format("%.1f", qty)
}
