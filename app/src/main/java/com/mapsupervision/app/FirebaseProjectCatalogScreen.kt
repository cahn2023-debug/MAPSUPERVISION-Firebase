package com.mapsupervision.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapsupervision.domain.model.FirebaseAccessAdminAction
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import com.mapsupervision.domain.model.FirebaseCatalogMigrationReport
import java.text.DateFormat
import java.util.Date

@Composable
fun FirebaseProjectCatalogScreen(
    entries: List<FirebaseProjectCatalogEntry>,
    statusFor: (String) -> FirebaseAccessRequestStatus,
    isLoading: Boolean,
    error: String,
    migrationReport: FirebaseCatalogMigrationReport? = null,
    requestingProjectId: String?,
    message: String,
    isAdmin: Boolean,
    adminRequests: List<FirebaseProjectAccessRequest>,
    adminLoading: Boolean,
    adminError: String,
    adminBusyRequestId: String?,
    onRefresh: () -> Unit,
    onMigrationRefresh: () -> Unit = {},
    onRequestAccess: (String) -> Unit,
    onAdminRefresh: () -> Unit,
    onAdminTransition: (FirebaseProjectAccessRequest, FirebaseAccessAdminAction) -> Unit,
    onOpenProject: (FirebaseProjectCatalogEntry) -> Unit = {},
    onCreateCloudProject: ((String, String?) -> Unit)? = null,
    onContinueToWorkspace: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectCode by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Tạo dự án Cloud mới", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Tên dự án") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProjectCode,
                        onValueChange = { newProjectCode = it },
                        label = { Text("Mã dự án (tùy chọn)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            onCreateCloudProject?.invoke(newProjectName, newProjectCode.takeIf { it.isNotBlank() })
                            showCreateDialog = false
                            newProjectName = ""
                            newProjectCode = ""
                        }
                    },
                    enabled = newProjectName.isNotBlank()
                ) {
                    Text("Tạo dự án")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Dự án trên Cloud",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isAdmin) "Chọn dự án để mở hoặc quản lý quyền truy cập"
                    else "Chọn dự án đã duyệt để mở, hoặc gửi yêu cầu truy cập",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAdmin && onCreateCloudProject != null) {
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tạo mới")
                }
            }
        }

        if (error.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (message.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (isAdmin && migrationReport?.status == "COMPLETED_WITH_WARNINGS") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Migration catalog cần rà soát", fontWeight = FontWeight.Bold)
                    Text("${migrationReport.warningCount} cảnh báo, ${migrationReport.discrepancyCount} sai lệch. Người dùng thường không thấy báo cáo này.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onMigrationRefresh) { Text("Cập nhật báo cáo") }
                }
            }
        }

        // Admin Access Queue (if Admin)
        if (isAdmin && adminRequests.isNotEmpty()) {
            AdminAccessQueue(
                requests = adminRequests,
                isLoading = adminLoading,
                error = adminError,
                busyRequestId = adminBusyRequestId,
                onRefresh = onAdminRefresh,
                onTransition = onAdminTransition
            )
        }

        // Project Catalog List / Empty State
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Đang tải danh sách dự án Cloud...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (error.isNotBlank()) {
            ElevatedCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Không thể tải danh sách dự án Cloud", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRefresh) { Text("Thử lại") }
                }
            }
        } else if (entries.isEmpty()) {
            EmptyCatalogView(
                isAdmin = isAdmin,
                onCreateProject = { showCreateDialog = true },
                onContinueOffline = onContinueToWorkspace,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.projectId }) { entry ->
                    val status = statusFor(entry.projectId)
                    ProjectCatalogCard(
                        entry = entry,
                        status = status,
                        isAdmin = isAdmin,
                        isRequesting = requestingProjectId == entry.projectId,
                        onRequestAccess = { onRequestAccess(entry.projectId) },
                        onOpenProject = { onOpenProject(entry) }
                    )
                }
            }
        }

        // Footer Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Làm mới")
            }
            Button(
                onClick = onContinueToWorkspace,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mở dữ liệu cục bộ")
            }
        }
    }
}

@Composable
private fun EmptyCatalogView(
    isAdmin: Boolean,
    onCreateProject: () -> Unit,
    onContinueOffline: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Chưa có dự án nào trên Cloud",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isAdmin) "Bạn có thể tạo một dự án mới trên Cloud ngay bây giờ, hoặc làm việc với dữ liệu cục bộ."
                else "Hiện chưa có dự án nào được chia sẻ trên Cloud. Bạn có thể làm việc với dữ liệu cục bộ trên máy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isAdmin) {
                    Button(onClick = onCreateProject, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tạo dự án Cloud mới")
                    }
                }
                OutlinedButton(onClick = onContinueOffline, shape = RoundedCornerShape(12.dp)) {
                    Text("Làm việc với dự án cục bộ")
                }
                TextButton(onClick = onRefresh) {
                    Text("Kiểm tra lại danh sách")
                }
            }
        }
    }
}

@Composable
private fun AdminAccessQueue(
    requests: List<FirebaseProjectAccessRequest>,
    isLoading: Boolean,
    error: String,
    busyRequestId: String?,
    onRefresh: () -> Unit,
    onTransition: (FirebaseProjectAccessRequest, FirebaseAccessAdminAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Yêu cầu cấp quyền chờ xử lý (${requests.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh Requests", modifier = Modifier.size(16.dp))
                }
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            requests.take(3).forEach { request ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = request.userId,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Dự án: ${request.projectId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when (request.status) {
                        FirebaseAccessRequestStatus.PENDING -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { onTransition(request, FirebaseAccessAdminAction.APPROVE) },
                                    enabled = busyRequestId != request.requestId,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("Duyệt", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { onTransition(request, FirebaseAccessAdminAction.REJECT) },
                                    enabled = busyRequestId != request.requestId,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text("Từ chối", fontSize = 12.sp)
                                }
                            }
                        }
                        FirebaseAccessRequestStatus.APPROVED -> {
                            OutlinedButton(
                                onClick = { onTransition(request, FirebaseAccessAdminAction.REVOKE) },
                                enabled = busyRequestId != request.requestId,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text("Thu hồi", fontSize = 12.sp)
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCatalogCard(
    entry: FirebaseProjectCatalogEntry,
    status: FirebaseAccessRequestStatus,
    isAdmin: Boolean,
    isRequesting: Boolean,
    onRequestAccess: () -> Unit,
    onOpenProject: () -> Unit
) {
    val canDirectOpen = isAdmin || status == FirebaseAccessRequestStatus.APPROVED

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (canDirectOpen) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.projectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mã dự án: ${entry.projectCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = if (isAdmin) FirebaseAccessRequestStatus.APPROVED else status)
            }

            Text(
                text = "Cập nhật: ${DateFormat.getDateTimeInstance().format(Date(entry.updatedAtEpochMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canDirectOpen) {
                    Button(
                        onClick = onOpenProject,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Outlined.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mở dự án")
                    }
                } else {
                    when (status) {
                        FirebaseAccessRequestStatus.NOT_REQUESTED -> {
                            Button(
                                onClick = onRequestAccess,
                                enabled = !isRequesting,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isRequesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Đang gửi...")
                                } else {
                                    Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Yêu cầu cấp quyền")
                                }
                            }
                        }
                        FirebaseAccessRequestStatus.PENDING -> {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Đang chờ Admin duyệt")
                            }
                        }
                        FirebaseAccessRequestStatus.REJECTED -> {
                            Button(
                                onClick = onRequestAccess,
                                enabled = !isRequesting,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isRequesting) "Đang gửi..." else "Gửi lại yêu cầu")
                            }
                        }
                        FirebaseAccessRequestStatus.REVOKED -> {
                            Button(
                                onClick = onRequestAccess,
                                enabled = !isRequesting,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isRequesting) "Đang gửi..." else "Yêu cầu lại quyền")
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: FirebaseAccessRequestStatus) {
    val (label, bgColor, textColor) = when (status) {
        FirebaseAccessRequestStatus.APPROVED -> Triple("ĐÃ DUYỆT", Color(0xFF1B5E20), Color(0xFFC8E6C9))
        FirebaseAccessRequestStatus.PENDING -> Triple("CHỜ DUYỆT", Color(0xFFE65100), Color(0xFFFFE0B2))
        FirebaseAccessRequestStatus.REJECTED -> Triple("TỪ CHỐI", Color(0xFFB71C1C), Color(0xFFFFCDD2))
        FirebaseAccessRequestStatus.REVOKED -> Triple("THU HỒI", Color(0xFF424242), Color(0xFFE0E0E0))
        FirebaseAccessRequestStatus.NOT_REQUESTED -> Triple("CHƯA YÊU CẦU", Color(0xFF37474F), Color(0xFFCFD8DC))
    }

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
