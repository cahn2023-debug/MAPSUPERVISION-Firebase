package com.mapsupervision.project.ui

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapsupervision.domain.model.FirebaseAccessRequestStatus
import com.mapsupervision.domain.model.FirebaseProjectCatalogStatus

@Composable
fun ProjectScreen(viewModel: ProjectViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var customPath by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val path = getPathFromTreeUri(context, uri)
            if (path != null) {
                customPath = path
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { importLauncher.launch("*/*") }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, contentDescription = "Nhập tệp")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Quản lý dự án", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tạo dự án mới", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tên dự án") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customPath,
                            onValueChange = { customPath = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Thư mục lưu trữ (tùy chọn)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text("Chọn...")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { 
                                if (name.isNotBlank()) {
                                    viewModel.createProject(name, customPath.takeIf { it.isNotBlank() })
                                    name = ""
                                    customPath = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) { 
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tạo mới") 
                        }
                        OutlinedButton(onClick = { viewModel.refresh() }, shape = MaterialTheme.shapes.medium) { 
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Làm mới") 
                        }
                    }
                }
            }

            if (state.importMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(state.importMessage, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            if (state.message.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(state.message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }

            if (state.catalogItems.isNotEmpty() || state.isCatalogLoading || state.catalogError.isNotBlank()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Danh mục Firebase đám mây",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { viewModel.loadCatalog() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Tải lại danh mục", modifier = Modifier.size(20.dp))
                            }
                        }

                        if (state.isCatalogLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (state.catalogError.isNotBlank()) {
                            Text(state.catalogError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        state.catalogItems.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (item.isRevokedReadOnly) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.projectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Mã: ${item.projectCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        CatalogStatusBadge(item.accessStatus)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        when (item.accessStatus) {
                                            FirebaseAccessRequestStatus.APPROVED -> {
                                                if (item.isLocalAvailable) {
                                                    Button(
                                                        onClick = { viewModel.switchProject(item.projectId) },
                                                        shape = MaterialTheme.shapes.small
                                                    ) {
                                                        Text("Mở dự án")
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = { },
                                                        enabled = false,
                                                        shape = MaterialTheme.shapes.small
                                                    ) {
                                                        Text("Đã phê duyệt")
                                                    }
                                                }
                                            }
                                            FirebaseAccessRequestStatus.PENDING -> {
                                                OutlinedButton(
                                                    onClick = {},
                                                    enabled = false,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text("Đang chờ duyệt")
                                                }
                                            }
                                            FirebaseAccessRequestStatus.REJECTED -> {
                                                Button(
                                                    onClick = { viewModel.requestAccess(item.projectId) },
                                                    enabled = !item.isActionBusy,
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(if (item.isActionBusy) "Đang gửi..." else "Gửi lại yêu cầu")
                                                }
                                            }
                                            FirebaseAccessRequestStatus.REVOKED -> {
                                                Button(
                                                    onClick = { viewModel.requestAccess(item.projectId) },
                                                    enabled = !item.isActionBusy,
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(if (item.isActionBusy) "Đang gửi..." else "Yêu cầu lại quyền")
                                                }
                                            }
                                            FirebaseAccessRequestStatus.NOT_REQUESTED -> {
                                                Button(
                                                    onClick = { viewModel.requestAccess(item.projectId) },
                                                    enabled = !item.isActionBusy,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(if (item.isActionBusy) "Đang gửi..." else "Yêu cầu quyền")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text("Danh sách dự án", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(state.projects) { p ->
                    val isActive = state.activeProjectId == p.id
                    val isRevoked = p.id in state.revokedReadOnlyProjectIds
                    val projectRootDir = p.projectDbPath.substringBeforeLast("/db/")
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(p.id) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (!isActive) {
                                            viewModel.switchProject(p.id)
                                        }
                                    }
                                )
                            },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when {
                                isActive -> MaterialTheme.colorScheme.primaryContainer
                                isRevoked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium, color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    Text("Slug: ${p.slug}", style = MaterialTheme.typography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Vị trí lưu: $projectRootDir", style = MaterialTheme.typography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isActive) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("ĐANG HOẠT ĐỘNG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                } else if (isRevoked) {
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("CHỈ ĐỌC (REVOKED)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isActive) {
                                    Button(onClick = { viewModel.switchProject(p.id) }, shape = MaterialTheme.shapes.small) { Text("Mở dự án") }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cloneProject(p.id, p.name + " (Bản sao)") },
                                    enabled = !isRevoked,
                                    shape = MaterialTheme.shapes.small
                                ) { Text("Nhân bản") }
                                OutlinedButton(
                                    onClick = { viewModel.archiveProject(p.id) },
                                    enabled = !isRevoked,
                                    shape = MaterialTheme.shapes.small
                                ) { Text("Lưu trữ") }
                            }
                        }
                    }
                }

                if (state.importedFiles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tệp đã nhập", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    items(state.importedFiles) { f ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${f.fileName} (.${f.fileType})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(f.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun CatalogStatusBadge(status: FirebaseAccessRequestStatus) {
    val (bgColor, textColor, label) = when (status) {
        FirebaseAccessRequestStatus.APPROVED -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "ĐÃ PHÊ DUYỆT")
        FirebaseAccessRequestStatus.PENDING -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "CHỜ DUYỆT")
        FirebaseAccessRequestStatus.REJECTED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "TỪ CHỐI")
        FirebaseAccessRequestStatus.REVOKED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "ĐÃ THU HỒI")
        FirebaseAccessRequestStatus.NOT_REQUESTED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "CHƯA YÊU CẦU")
    }

    Box(
        modifier = Modifier
            .background(bgColor, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, fontWeight = FontWeight.Bold)
    }
}

private fun getPathFromTreeUri(context: android.content.Context, uri: Uri): String? {
    try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        val type = split[0]
        val relativePath = if (split.size > 1) split[1] else ""
        
        return if ("primary".equals(type, ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
        } else {
            "/storage/$type/$relativePath"
        }
    } catch (e: Exception) {
        return uri.path
    }
}
