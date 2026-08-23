package com.mapsupervision.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseAccessAdminAction
import com.mapsupervision.domain.model.FirebaseProjectAccessRequest
import com.mapsupervision.domain.model.FirebaseProjectCatalogEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun FirebaseProjectCatalogScreen(
    entries: List<FirebaseProjectCatalogEntry>,
    statusFor: (String) -> FirebaseAccessRequestStatus,
    isLoading: Boolean,
    error: String,
    requestingProjectId: String?,
    message: String,
    isAdmin: Boolean,
    adminRequests: List<FirebaseProjectAccessRequest>,
    adminLoading: Boolean,
    adminError: String,
    adminBusyRequestId: String?,
    onRefresh: () -> Unit,
    onRequestAccess: (String) -> Unit,
    onAdminRefresh: () -> Unit,
    onAdminTransition: (FirebaseProjectAccessRequest, FirebaseAccessAdminAction) -> Unit,
    onContinueToWorkspace: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dự án trên Firebase", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Danh mục chỉ hiển thị thông tin cơ bản. Bạn cần được Admin phê duyệt trước khi tải dữ liệu.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        if (isAdmin) {
            AdminAccessQueue(
                requests = adminRequests,
                isLoading = adminLoading,
                error = adminError,
                busyRequestId = adminBusyRequestId,
                onRefresh = onAdminRefresh,
                onTransition = onAdminTransition
            )
        }
        if (isLoading) {
            CircularProgressIndicator()
        } else if (entries.isEmpty()) {
            Text("Chưa có dự án Firebase khả dụng.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.projectId }) { entry ->
                    ProjectCatalogCard(
                        entry = entry,
                        status = statusFor(entry.projectId),
                        isRequesting = requestingProjectId == entry.projectId,
                        onRequestAccess = { onRequestAccess(entry.projectId) }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !isLoading) { Text("Làm mới") }
            Button(onClick = onContinueToWorkspace) { Text("Mở dữ liệu cục bộ") }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hàng đợi phê duyệt Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onRefresh, enabled = !isLoading) { Text("Làm mới") }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (isLoading) CircularProgressIndicator()
            requests.forEach { request ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${request.projectId} · ${request.userId}", fontWeight = FontWeight.SemiBold)
                        Text("${request.status.name} · scope ${request.contractorScope.name}")
                    }
                    when (request.status) {
                        FirebaseAccessRequestStatus.PENDING -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { onTransition(request, FirebaseAccessAdminAction.APPROVE) },
                                    enabled = busyRequestId != request.requestId
                                ) { Text("Duyệt") }
                                OutlinedButton(
                                    onClick = { onTransition(request, FirebaseAccessAdminAction.REJECT) },
                                    enabled = busyRequestId != request.requestId
                                ) { Text("Từ chối") }
                            }
                        }
                        FirebaseAccessRequestStatus.APPROVED -> OutlinedButton(
                            onClick = { onTransition(request, FirebaseAccessAdminAction.REVOKE) },
                            enabled = busyRequestId != request.requestId
                        ) { Text("Thu hồi") }
                        else -> Unit
                    }
                }
            }
            if (!isLoading && requests.isEmpty()) Text("Không có yêu cầu cần xử lý.")
        }
    }
}

@Composable
private fun ProjectCatalogCard(
    entry: FirebaseProjectCatalogEntry,
    status: FirebaseAccessRequestStatus,
    isRequesting: Boolean,
    onRequestAccess: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(entry.projectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Mã dự án: ${entry.projectCode}")
            Text("Cập nhật: ${DateFormat.getDateTimeInstance().format(Date(entry.updatedAtEpochMs))}")
            Text("Trạng thái dự án: ${entry.status.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Quyền: ${status.displayLabel()}")
                when (status) {
                    FirebaseAccessRequestStatus.NOT_REQUESTED,
                    FirebaseAccessRequestStatus.REJECTED,
                    FirebaseAccessRequestStatus.REVOKED -> {
                        Button(onClick = onRequestAccess, enabled = !isRequesting) {
                            Text(if (isRequesting) "Đang gửi..." else if (status == FirebaseAccessRequestStatus.NOT_REQUESTED) "Yêu cầu tải" else "Gửi lại yêu cầu")
                        }
                    }
                    FirebaseAccessRequestStatus.PENDING -> Text("Đang chờ Admin xử lý")
                    FirebaseAccessRequestStatus.APPROVED -> Text("Đã duyệt · tải dữ liệu theo phạm vi")
                }
            }
        }
    }
}

private fun FirebaseAccessRequestStatus.displayLabel(): String = when (this) {
    FirebaseAccessRequestStatus.NOT_REQUESTED -> "Chưa yêu cầu"
    FirebaseAccessRequestStatus.PENDING -> "Đang chờ duyệt"
    FirebaseAccessRequestStatus.APPROVED -> "Đã duyệt"
    FirebaseAccessRequestStatus.REJECTED -> "Đã từ chối"
    FirebaseAccessRequestStatus.REVOKED -> "Đã thu hồi · chỉ đọc cục bộ"
}
