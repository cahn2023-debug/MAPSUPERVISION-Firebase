package com.mapsupervision.app.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile

data class MergeRowNode(
    val id: String = java.util.UUID.randomUUID().toString(),
    val left: GisNode? = null,
    val right: GisNode? = null,
    val isMerged: Boolean = false
)

data class MergeRowRoute(
    val id: String = java.util.UUID.randomUUID().toString(),
    val left: GisRoute? = null,
    val right: GisRoute? = null,
    val isMerged: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombineFilesDialog(
    file1: ImportedFile,
    file2: ImportedFile,
    state: WorkspaceState,
    onDismiss: () -> Unit,
    onConfirm: (ImportedFile, ImportedFile, List<GisNode>, List<GisRoute>) -> Unit
) {
    val file1Nodes = remember(state.designNodes) { state.designNodes.filter { it.importedFileId == file1.id } }
    val file2Nodes = remember(state.designNodes) { state.designNodes.filter { it.importedFileId == file2.id } }
    val file1Routes = remember(state.designRoutes) { state.designRoutes.filter { it.importedFileId == file1.id } }
    val file2Routes = remember(state.designRoutes) { state.designRoutes.filter { it.importedFileId == file2.id } }

    var selectedTab by remember { mutableStateOf(0) } // 0: Vị trí (Nodes), 1: Tuyến (Routes)
    var searchQuery by remember { mutableStateOf("") }

    var nodeRows by remember(file1Nodes, file2Nodes) {
        mutableStateOf(autoAlignNodes(file1Nodes, file2Nodes))
    }
    var routeRows by remember(file1Routes, file2Routes) {
        mutableStateOf(autoAlignRoutes(file1Routes, file2Routes))
    }

    var selectedLeftNodeId by remember { mutableStateOf<String?>(null) }
    var selectedRightNodeId by remember { mutableStateOf<String?>(null) }
    var selectedLeftRouteId by remember { mutableStateOf<String?>(null) }
    var selectedRightRouteId by remember { mutableStateOf<String?>(null) }

    val darkBgColor = MaterialTheme.colorScheme.surface
    val cardBgColor = MaterialTheme.colorScheme.surfaceVariant
    val orangeColor = Color(0xFFF5A623)
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.97f)
                .clip(RoundedCornerShape(16.dp))
                .background(darkBgColor)
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gộp dữ liệu & So sánh song song",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Đang gộp: ${file1.fileName} ⇆ ${file2.fileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Đóng", tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector (Nodes vs Routes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardBgColor)
                        .padding(4.dp)
                ) {
                    val activeColor = Color(0xFF3B82F6)
                    Button(
                        onClick = { selectedTab = 0; searchQuery = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) activeColor else Color.Transparent,
                            contentColor = if (selectedTab == 0) Color.White else secondaryTextColor
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        val mergedCount = nodeRows.count { it.isMerged }
                        val totalCount = nodeRows.size
                        Text("Vị trí (Nodes) - Khớp $mergedCount/$totalCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { selectedTab = 1; searchQuery = "" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) activeColor else Color.Transparent,
                            contentColor = if (selectedTab == 1) Color.White else secondaryTextColor
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        val mergedCount = routeRows.count { it.isMerged }
                        val totalCount = routeRows.size
                        Text("Tuyến (Routes) - Khớp $mergedCount/$totalCount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm kiếm mã ký hiệu...", color = secondaryTextColor, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = secondaryTextColor) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = null, tint = secondaryTextColor)
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = cardBgColor,
                        unfocusedContainerColor = cardBgColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        cursorColor = Color(0xFF3B82F6),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Control panel for manual pairing
                if (selectedTab == 0) {
                    val showPairing = selectedLeftNodeId != null && selectedRightNodeId != null
                    if (showPairing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E3A8A).copy(alpha = 0.5f))
                                .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val leftNode = nodeRows.firstOrNull { it.id == selectedLeftNodeId }?.left
                            val rightNode = nodeRows.firstOrNull { it.id == selectedRightNodeId }?.right
                            Text(
                                text = "Chọn gộp thủ công: ${leftNode?.code ?: "?"} ⇆ ${rightNode?.code ?: "?"}",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    selectedLeftNodeId = null
                                    selectedRightNodeId = null
                                }) {
                                    Text("Bỏ chọn", color = secondaryTextColor, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val lRow = nodeRows.firstOrNull { it.id == selectedLeftNodeId }
                                        val rRow = nodeRows.firstOrNull { it.id == selectedRightNodeId }
                                        if (lRow != null && rRow != null && lRow.left != null && rRow.right != null) {
                                            val newRow = MergeRowNode(
                                                left = lRow.left,
                                                right = rRow.right,
                                                isMerged = true
                                            )
                                            nodeRows = nodeRows.toMutableList().apply {
                                                remove(lRow)
                                                remove(rRow)
                                                add(newRow)
                                            }
                                            selectedLeftNodeId = null
                                            selectedRightNodeId = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Gộp liên kết thủ công", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                } else {
                    val showPairing = selectedLeftRouteId != null && selectedRightRouteId != null
                    if (showPairing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E3A8A).copy(alpha = 0.5f))
                                .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val leftRoute = routeRows.firstOrNull { it.id == selectedLeftRouteId }?.left
                            val rightRoute = routeRows.firstOrNull { it.id == selectedRightRouteId }?.right
                            Text(
                                text = "Chọn gộp thủ công: ${leftRoute?.code ?: "?"} ⇆ ${rightRoute?.code ?: "?"}",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    selectedLeftRouteId = null
                                    selectedRightRouteId = null
                                }) {
                                    Text("Bỏ chọn", color = secondaryTextColor, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val lRow = routeRows.firstOrNull { it.id == selectedLeftRouteId }
                                        val rRow = routeRows.firstOrNull { it.id == selectedRightRouteId }
                                        if (lRow != null && rRow != null && lRow.left != null && rRow.right != null) {
                                            val newRow = MergeRowRoute(
                                                left = lRow.left,
                                                right = rRow.right,
                                                isMerged = true
                                            )
                                            routeRows = routeRows.toMutableList().apply {
                                                remove(lRow)
                                                remove(rRow)
                                                add(newRow)
                                            }
                                            selectedLeftRouteId = null
                                            selectedRightRouteId = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Gộp liên kết thủ công", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // Table Header labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File 1: ${file1.fileName}",
                        color = secondaryTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.44f)
                    )
                    Spacer(modifier = Modifier.weight(0.12f))
                    Text(
                        text = "File 2: ${file2.fileName}",
                        color = secondaryTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.44f)
                    )
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                // Main side-by-side list view
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        val filteredNodes = remember(nodeRows, searchQuery) {
                            if (searchQuery.isBlank()) nodeRows else {
                                val query = searchQuery.lowercase().trim()
                                nodeRows.filter {
                                    it.left?.code?.lowercase()?.contains(query) == true ||
                                            it.right?.code?.lowercase()?.contains(query) == true
                                }
                            }
                        }

                        if (filteredNodes.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Không tìm thấy kết quả phù hợp", color = secondaryTextColor)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredNodes, key = { index, row -> "${row.id}:$index" }) { _, row ->
                                    NodeCompareRow(
                                        row = row,
                                        selectedLeftId = selectedLeftNodeId,
                                        selectedRightId = selectedRightNodeId,
                                        onSelectLeft = { id ->
                                            selectedLeftNodeId = if (selectedLeftNodeId == id) null else id
                                        },
                                        onSelectRight = { id ->
                                            selectedRightNodeId = if (selectedRightNodeId == id) null else id
                                        },
                                        onUnlink = {
                                            if (row.isMerged && row.left != null && row.right != null) {
                                                val newL = MergeRowNode(left = row.left, right = null, isMerged = false)
                                                val newR = MergeRowNode(left = null, right = row.right, isMerged = false)
                                                nodeRows = nodeRows.toMutableList().apply {
                                                    remove(row)
                                                    add(newL)
                                                    add(newR)
                                                }
                                            }
                                        },
                                        cardBgColor = cardBgColor,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor
                                    )
                                }
                            }
                        }
                    } else {
                        val filteredRoutes = remember(routeRows, searchQuery) {
                            if (searchQuery.isBlank()) routeRows else {
                                val query = searchQuery.lowercase().trim()
                                routeRows.filter {
                                    it.left?.code?.lowercase()?.contains(query) == true ||
                                            it.right?.code?.lowercase()?.contains(query) == true
                                }
                            }
                        }

                        if (filteredRoutes.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Không tìm thấy kết quả phù hợp", color = secondaryTextColor)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(filteredRoutes, key = { index, row -> "${row.id}:$index" }) { _, row ->
                                    RouteCompareRow(
                                        row = row,
                                        selectedLeftId = selectedLeftRouteId,
                                        selectedRightId = selectedRightRouteId,
                                        onSelectLeft = { id ->
                                            selectedLeftRouteId = if (selectedLeftRouteId == id) null else id
                                        },
                                        onSelectRight = { id ->
                                            selectedRightRouteId = if (selectedRightRouteId == id) null else id
                                        },
                                        onUnlink = {
                                            if (row.isMerged && row.left != null && row.right != null) {
                                                val newL = MergeRowRoute(left = row.left, right = null, isMerged = false)
                                                val newR = MergeRowRoute(left = null, right = row.right, isMerged = false)
                                                routeRows = routeRows.toMutableList().apply {
                                                    remove(row)
                                                    add(newL)
                                                    add(newR)
                                                }
                                            }
                                        },
                                        cardBgColor = cardBgColor,
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = dividerColor, thickness = 1.dp)

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = secondaryTextColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val finalNodes = nodeRows.map { row ->
                                if (row.isMerged && row.left != null && row.right != null) {
                                    val left = row.left
                                    val right = row.right

                                    val mergedContractor = when {
                                        left.contractor.equals(right.contractor, ignoreCase = true) -> left.contractor
                                        left.contractor.isBlank() || left.contractor.uppercase() in listOf("UNKNOWN", "UPLOAD") -> right.contractor
                                        right.contractor.isBlank() || right.contractor.uppercase() in listOf("UNKNOWN", "UPLOAD") -> left.contractor
                                        else -> "${left.contractor} / ${right.contractor}"
                                    }

                                    val mergedworkVolumeSummary = mergeMaterialSummaries(left.workVolumeSummary, right.workVolumeSummary)

                                    left.copy(
                                        contractor = mergedContractor,
                                        mapNumberLabel = if (left.mapNumberLabel.isNotBlank()) left.mapNumberLabel else right.mapNumberLabel,
                                        workVolumeSummary = mergedworkVolumeSummary
                                    )
                                } else {
                                    row.left ?: row.right!!
                                }
                            }

                            val finalRoutes = routeRows.map { row ->
                                if (row.isMerged && row.left != null && row.right != null) {
                                    val left = row.left
                                    val right = row.right

                                    val mergedContractor = when {
                                        left.contractor.equals(right.contractor, ignoreCase = true) -> left.contractor
                                        left.contractor.isBlank() || left.contractor.uppercase() in listOf("UNKNOWN", "UPLOAD") -> right.contractor
                                        right.contractor.isBlank() || right.contractor.uppercase() in listOf("UNKNOWN", "UPLOAD") -> left.contractor
                                        else -> "${left.contractor} / ${right.contractor}"
                                    }

                                    left.copy(
                                        contractor = mergedContractor
                                    )
                                } else {
                                    row.left ?: row.right!!
                                }
                            }

                            onConfirm(file1, file2, finalNodes, finalRoutes)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Xác nhận gộp & Thay thế", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeCompareRow(
    row: MergeRowNode,
    selectedLeftId: String?,
    selectedRightId: String?,
    onSelectLeft: (String) -> Unit,
    onSelectRight: (String) -> Unit,
    onUnlink: () -> Unit,
    cardBgColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (row.isMerged) Color(0xFF0F172A).copy(alpha = 0.5f) else Color.Transparent)
            .border(
                1.dp,
                if (row.isMerged) Color(0xFF10B981).copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column (44%)
        Box(modifier = Modifier.weight(0.44f)) {
            if (row.left != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!row.isMerged) {
                        Checkbox(
                            checked = selectedLeftId == row.id,
                            onCheckedChange = { onSelectLeft(row.id) }
                        )
                    }
                    NodeCard(
                        node = row.left,
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Trống", color = secondaryTextColor, fontSize = 12.sp)
                }
            }
        }

        // Center Column (12%)
        Column(
            modifier = Modifier.weight(0.12f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (row.isMerged) {
                Text("🔗", fontSize = 18.sp)
                Text(
                    "Đã khớp",
                    color = Color(0xFF10B981),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onUnlink,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("Hủy", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("≠", fontSize = 20.sp, color = secondaryTextColor)
                Text("Chưa khớp", color = secondaryTextColor, fontSize = 8.sp)
            }
        }

        // Right Column (44%)
        Box(modifier = Modifier.weight(0.44f)) {
            if (row.right != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NodeCard(
                        node = row.right,
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    if (!row.isMerged) {
                        Checkbox(
                            checked = selectedRightId == row.id,
                            onCheckedChange = { onSelectRight(row.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Trống", color = secondaryTextColor, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RouteCompareRow(
    row: MergeRowRoute,
    selectedLeftId: String?,
    selectedRightId: String?,
    onSelectLeft: (String) -> Unit,
    onSelectRight: (String) -> Unit,
    onUnlink: () -> Unit,
    cardBgColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (row.isMerged) Color(0xFF0F172A).copy(alpha = 0.5f) else Color.Transparent)
            .border(
                1.dp,
                if (row.isMerged) Color(0xFF10B981).copy(alpha = 0.3f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column (44%)
        Box(modifier = Modifier.weight(0.44f)) {
            if (row.left != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!row.isMerged) {
                        Checkbox(
                            checked = selectedLeftId == row.id,
                            onCheckedChange = { onSelectLeft(row.id) }
                        )
                    }
                    RouteCard(
                        route = row.left,
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Trống", color = secondaryTextColor, fontSize = 12.sp)
                }
            }
        }

        // Center Column (12%)
        Column(
            modifier = Modifier.weight(0.12f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (row.isMerged) {
                Text("🔗", fontSize = 18.sp)
                Text(
                    "Đã khớp",
                    color = Color(0xFF10B981),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onUnlink,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("Hủy", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("≠", fontSize = 20.sp, color = secondaryTextColor)
                Text("Chưa khớp", color = secondaryTextColor, fontSize = 8.sp)
            }
        }

        // Right Column (44%)
        Box(modifier = Modifier.weight(0.44f)) {
            if (row.right != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RouteCard(
                        route = row.right,
                        cardBgColor = cardBgColor,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                    if (!row.isMerged) {
                        Checkbox(
                            checked = selectedRightId == row.id,
                            onCheckedChange = { onSelectRight(row.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Trống", color = secondaryTextColor, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: GisNode,
    cardBgColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.code,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (node.contractor.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = node.contractor,
                            color = Color(0xFF60A5FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "GPS: %.5f, %.5f".format(node.latitude, node.longitude),
                color = secondaryTextColor,
                fontSize = 10.sp
            )
            if (node.workVolumeSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Công việc: ${node.workVolumeSummary.replace("\n", ", ")}",
                    color = secondaryTextColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: GisRoute,
    cardBgColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = route.code,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (route.contractor.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = route.contractor,
                            color = Color(0xFF60A5FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tuyến: ${route.startNodeCode} ➙ ${route.endNodeCode}",
                color = secondaryTextColor,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun autoAlignNodes(leftNodes: List<GisNode>, rightNodes: List<GisNode>): List<MergeRowNode> {
    val aligned = mutableListOf<MergeRowNode>()
    val unmatchedRight = rightNodes.toMutableList()

    for (leftNode in leftNodes) {
        val codeMatch = unmatchedRight.firstOrNull {
            WorkspaceImportHelper.normalizeCode(it.code) == WorkspaceImportHelper.normalizeCode(leftNode.code)
        }
        if (codeMatch != null) {
            aligned.add(MergeRowNode(left = leftNode, right = codeMatch, isMerged = true))
            unmatchedRight.remove(codeMatch)
            continue
        }

        val coordMatch = unmatchedRight.firstOrNull {
            com.mapsupervision.domain.util.Haversine.distanceInMeters(
                leftNode.latitude, leftNode.longitude,
                it.latitude, it.longitude
            ) <= 10.0
        }
        if (coordMatch != null) {
            aligned.add(MergeRowNode(left = leftNode, right = coordMatch, isMerged = true))
            unmatchedRight.remove(coordMatch)
            continue
        }

        aligned.add(MergeRowNode(left = leftNode, right = null, isMerged = false))
    }

    for (rightNode in unmatchedRight) {
        aligned.add(MergeRowNode(left = null, right = rightNode, isMerged = false))
    }

    return aligned.sortedBy { it.left?.code ?: it.right?.code ?: "" }
}

private fun autoAlignRoutes(leftRoutes: List<GisRoute>, rightRoutes: List<GisRoute>): List<MergeRowRoute> {
    val aligned = mutableListOf<MergeRowRoute>()
    val unmatchedRight = rightRoutes.toMutableList()

    for (leftRoute in leftRoutes) {
        val codeMatch = unmatchedRight.firstOrNull {
            WorkspaceImportHelper.normalizeCode(it.code) == WorkspaceImportHelper.normalizeCode(leftRoute.code)
        }
        if (codeMatch != null) {
            aligned.add(MergeRowRoute(left = leftRoute, right = codeMatch, isMerged = true))
            unmatchedRight.remove(codeMatch)
            continue
        }

        val lStart = WorkspaceImportHelper.normalizeCode(leftRoute.startNodeCode)
        val lEnd = WorkspaceImportHelper.normalizeCode(leftRoute.endNodeCode)
        val routeMatch = unmatchedRight.firstOrNull {
            val rStart = WorkspaceImportHelper.normalizeCode(it.startNodeCode)
            val rEnd = WorkspaceImportHelper.normalizeCode(it.endNodeCode)
            (lStart == rStart && lEnd == rEnd) || (lStart == rEnd && lEnd == rStart)
        }
        if (routeMatch != null) {
            aligned.add(MergeRowRoute(left = leftRoute, right = routeMatch, isMerged = true))
            unmatchedRight.remove(routeMatch)
            continue
        }

        aligned.add(MergeRowRoute(left = leftRoute, right = null, isMerged = false))
    }

    for (rightRoute in unmatchedRight) {
        aligned.add(MergeRowRoute(left = null, right = rightRoute, isMerged = false))
    }

    return aligned.sortedBy { it.left?.code ?: it.right?.code ?: "" }
}

private fun mergeMaterialSummaries(left: String, right: String): String {
    val map = LinkedHashMap<String, Float>()
    fun parse(summary: String) {
        summary.split("\n").forEach { line ->
            val clean = line.trim()
            if (clean.isBlank()) return@forEach
            val parts = clean.split(":")
            if (parts.size >= 2) {
                val name = parts[0].trim()
                val qty = parts[1].trim().toFloatOrNull() ?: 0f
                if (name.isNotEmpty()) {
                    map[name] = (map[name] ?: 0f) + qty
                }
            } else {
                val name = clean
                map[name] = (map[name] ?: 0f) + 0f
            }
        }
    }
    parse(left)
    parse(right)
    return map.entries.joinToString("\n") { (name, qty) ->
        if (qty > 0f) "$name: ${if (qty % 1f == 0f) qty.toInt().toString() else qty.toString()}" else name
    }
}

