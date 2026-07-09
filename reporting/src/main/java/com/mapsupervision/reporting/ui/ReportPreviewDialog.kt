package com.mapsupervision.reporting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.mapsupervision.ai.core.ReportDraftResult
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.SitePhoto
import com.mapsupervision.domain.util.PhotoMatchEvaluation
import com.mapsupervision.domain.util.evaluateSitePhotoMatch
import com.mapsupervision.core.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportPreviewDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    projectId: String,
    selectedExportFormat: String,
    isExporting: Boolean,
    onUpdatePhotoOffset: (SitePhoto, Int) -> Unit,
    onConfirmExport: (String) -> Unit,
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    photos: List<SitePhoto>,
    workVolumeRows: List<MaterialReportRow>,
    aiDraft: ReportDraftResult?
) {
    if (!showDialog) return

    var format by remember(selectedExportFormat) {
        mutableStateOf(if (selectedExportFormat.isBlank()) "PDF" else selectedExportFormat)
    }
    var showPhotos by remember { mutableStateOf(false) }
    val chunks = remember(photos) { photos.chunked(3) }
    val photoEvaluations = remember(photos, nodes, routes) {
        photos.associateWith { photo -> evaluateSitePhotoMatch(photo, nodes, routes) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.97f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Xem trước Báo cáo dự án",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isPdf = format == "PDF"
                    Button(
                        onClick = { format = "PDF" },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPdf) MaterialTheme.extendedColors.danger else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isPdf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Định dạng PDF (.pdf)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { format = "WORD" },
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPdf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isPdf) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Định dạng Word (.docx)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isExporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("THÔNG TIN BÁO CÁO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Dự án: $projectId", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tổng số ảnh thực địa: ${photos.size} ảnh", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Thời gian lập báo cáo: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    aiDraft?.let { draft ->
                        item {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("BẢN THẢO BÁO CÁO (AI DRAFT)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text("Tóm tắt tiến độ:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.executiveSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Đánh giá rủi ro:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.riskSection, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Hành động đề xuất:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(draft.recommendedActions.joinToString("\n• ", prefix = "• "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("BẢNG TỔNG HỢP CÔNG VIỆC THI CÔNG", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Nội dung", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
                                    Text("Tổng thiết kế", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                                    Text("Tổng thi công", modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
                                    Text("%", modifier = Modifier.weight(0.1f), fontWeight = FontWeight.Bold)
                                }
                                if (workVolumeRows.isEmpty()) {
                                    Text("Không có dữ liệu công việc.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                    ) {
                                        itemsIndexed(
                                            items = workVolumeRows,
                                            key = { index, row -> "${row.workName}_${index}_${row.isTotal}" }
                                        ) { _, row ->
                                            val textStyle = if (row.isTotal) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(row.workName, modifier = Modifier.weight(0.5f), style = textStyle)
                                                Text(row.totalPlannedQty.toInt().toString(), modifier = Modifier.weight(0.2f), style = textStyle)
                                                Text(row.totalActualQty.toInt().toString(), modifier = Modifier.weight(0.2f), style = textStyle)
                                                Text("${row.completionPercent.toInt()}%", modifier = Modifier.weight(0.1f), style = textStyle)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (chunks.isNotEmpty()) {
                        item {
                            Text(
                                text = "NHẬT KÝ HÌNH ẢNH ĐÃ CHÈN (${photos.size})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        itemsIndexed(
                            items = chunks,
                            key = { index, rowPhotos -> rowPhotos.joinToString(prefix = "$index:") { it.id } }
                        ) { _, rowPhotos ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                rowPhotos.forEach { photo ->
                                    PhotoItem(
                                        photo = photo,
                                        evaluation = photoEvaluations.getValue(photo),
                                        onUpdatePhotoOffset = onUpdatePhotoOffset,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowPhotos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onConfirmExport(format) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (format == "PDF") MaterialTheme.extendedColors.danger else MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (isExporting) "Đang xuất..." else "Xác nhận xuất ${if (format == "PDF") "PDF" else "Word"}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
@Composable
private fun PhotoItem(
    photo: SitePhoto,
    evaluation: PhotoMatchEvaluation,
    onUpdatePhotoOffset: (SitePhoto, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbFile = remember(photo) { java.io.File(photo.thumbnailPath.ifBlank { photo.filePath }) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val painter = rememberAsyncImagePainter(thumbFile)
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = photo.objectCode,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        val painterState = painter.state
        if (painterState is coil.compose.AsyncImagePainter.State.Error ||
            painterState is coil.compose.AsyncImagePainter.State.Empty
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xAA000000))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(photo.objectCode, color = Color.White, fontSize = 8.sp)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                if (evaluation.isMatched) "Khớp" else "Lệch",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        if (evaluation.isMatched) MaterialTheme.extendedColors.success.copy(alpha = 0.8f) else MaterialTheme.extendedColors.danger.copy(alpha = 0.8f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onUpdatePhotoOffset(photo, ((photo.matchingTimeOffsetMs / 60000) - 5).toInt()) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) { Text("-5m", fontSize = 8.sp) }
                OutlinedButton(
                    onClick = { onUpdatePhotoOffset(photo, ((photo.matchingTimeOffsetMs / 60000) + 5).toInt()) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) { Text("+5m", fontSize = 8.sp) }
            }
        }
    }
}
