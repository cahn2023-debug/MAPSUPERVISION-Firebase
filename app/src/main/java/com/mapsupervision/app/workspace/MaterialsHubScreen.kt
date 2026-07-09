package com.mapsupervision.app.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.components.*
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.MaterialHandover
import com.mapsupervision.domain.model.MaterialDeclaration
import com.mapsupervision.domain.model.WorkVolumeProgress
import com.mapsupervision.domain.model.NodeProgress
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsHubScreen(
    state: WorkspaceState,
    viewModel: WorkspaceViewModel,
    onRefresh: () -> Unit = { viewModel.refresh() }
) {
    val colors = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val darkBg = colors.background
    val cardBg = extendedColors.panelBackgroundAlt
    val orange = extendedColors.mapAccent
    val green = extendedColors.success
    val blue = colors.primary
    val dividerColor = colors.outlineVariant
    val textColor = colors.onBackground
    val secondaryText = colors.onSurfaceVariant

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Kê khai, 1: Giao nhận, 2: Thống kê

    // General Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedContractor by remember { mutableStateOf("Tất cả nhà thầu") }
    var selectedNodeFilter by remember { mutableStateOf("Tất cả vị trí") }
    var selectedMonthFilter by remember { mutableStateOf("Tất cả tháng") }
    val currentMonthKey = remember { SimpleDateFormat("MM/yyyy", Locale("vi", "VN")).format(Date()) }

    var contractorDropdownExpanded by remember { mutableStateOf(false) }
    var nodeDropdownExpanded by remember { mutableStateOf(false) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }
    var expandedHandoverNotes by remember { mutableStateOf(setOf<String>()) }

    // Dialogs States
    var showAddDeclarationDialog by remember { mutableStateOf(false) }
    var showAddHandoverDialog by remember { mutableStateOf(false) }
    var showQuickAddHandoverDialog by remember { mutableStateOf(false) }
    var prefilledNodeCode by remember { mutableStateOf<String?>(null) }
    var prefilledWorkName by remember { mutableStateOf<String?>(null) }
    var prefilledMaterialName by remember { mutableStateOf<String?>(null) }
    var prefilledUnit by remember { mutableStateOf<String?>(null) }
    var prefilledContractor by remember { mutableStateOf<String?>(null) }

    // Distinct filter lists
    val allContractors = remember(state.designNodes) {
        listOf("Tất cả nhà thầu") + state.designNodes
            .map { it.contractor }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val allNodeCodes = remember(state.designNodes) {
        listOf("Tất cả vị trí") + state.designNodes
            .map { it.code }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    if (state.activeProjectId == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Vui lòng tạo hoặc chọn một dự án để tiếp tục",
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
        return
    }

    val indexes = remember(state.designNodes, state.designRoutes, state.constructionProgress, state.workVolumeRows, state.dailyLogs) {
        viewModel.ensureIndexes(state)
    }

    val distinctWorkNames = remember(indexes, state.workCategories, state.workVolumeRows, state.designNodes) {
        val fromCategories = state.workCategories.map { it.name }
        val fromVolumeRows = state.workVolumeRows.map { it.workName }
        val fromNodes = state.designNodes.flatMap { node ->
            indexes.parsedMaterialsByNodeKey[node.id].orEmpty().mapNotNull { parsed ->
                val key = parsed.itemName.trim()
                if (
                    key.equals("Công việc", ignoreCase = true) ||
                    key.equals("Thuộc tính khác", ignoreCase = true) ||
                    parsed.plannedText.isBlank()
                ) {
                    null
                } else {
                    key
                }
            }
        }
        (fromCategories + fromVolumeRows + fromNodes)
            .distinct()
            .sorted()
    }

    val filteredNodes = remember(state.designNodes, searchQuery, selectedContractor, selectedNodeFilter) {
        state.designNodes.filter { node ->
            val matchesSearch = node.code.contains(searchQuery, ignoreCase = true)
            val matchesContractor = selectedContractor == "Tất cả nhà thầu" || node.contractor == selectedContractor
            val matchesNode = selectedNodeFilter == "Tất cả vị trí" || node.code == selectedNodeFilter
            matchesSearch && matchesContractor && matchesNode
        }
    }

    val activeNodes = remember(state.designNodes, selectedContractor) {
        state.designNodes.filter { selectedContractor == "Tất cả nhà thầu" || it.contractor == selectedContractor }
    }
    val activeNodeCodes = remember(activeNodes) {
        activeNodes.map { it.code.trim().uppercase() }.toSet()
    }

    val aggregatedStatsList = remember(state.materialDeclarations, state.workVolumeRows, state.materialHandovers, activeNodeCodes) {
        buildMaterialProjectSummary(
            declarations = state.materialDeclarations,
            workVolumeRows = state.workVolumeRows,
            materialHandovers = state.materialHandovers,
            nodeCodes = activeNodeCodes
        )
    }

    val filteredHandovers = remember(state.materialHandovers, activeNodeCodes) {
        state.materialHandovers.filter {
            it.nodeCode.trim().uppercase() in activeNodeCodes
        }.sortedByDescending { it.handoverDateEpochDay }
    }

    val handoverMonthOptions = remember(filteredHandovers) {
        listOf("Tất cả tháng") + filteredHandovers
            .map { formatMonthKey(it.handoverDateEpochDay) }
            .distinct()
    }

    val visibleHandovers = remember(filteredHandovers, selectedMonthFilter) {
        if (selectedMonthFilter == "Tất cả tháng") {
            filteredHandovers
        } else {
            filteredHandovers.filter { formatMonthKey(it.handoverDateEpochDay) == selectedMonthFilter }
        }
    }
    val isCompactHandoverTable = LocalConfiguration.current.screenWidthDp < 360
    val showUnitColumn = !isCompactHandoverTable || visibleHandovers.any { it.unit.isNotBlank() }
    val handoverMonthSummaries = remember(visibleHandovers) {
        visibleHandovers
            .groupBy { formatMonthKey(it.handoverDateEpochDay) }
            .map { (monthKey, monthHandovers) ->
                HandoverMonthSummary(
                    monthKey = monthKey,
                    totalCount = monthHandovers.size,
                    totalQuantity = monthHandovers.sumOf { it.quantity.toDouble() }.toFloat(),
                    daySummaries = monthHandovers
                        .groupBy { it.handoverDateEpochDay }
                        .map { (epochDay, dayHandovers) ->
                            HandoverDaySummary(
                                epochDay = epochDay,
                                totalCount = dayHandovers.size,
                                totalQuantity = dayHandovers.sumOf { it.quantity.toDouble() }.toFloat(),
                                materialSummaries = dayHandovers
                                    .groupBy { materialProjectSummaryKey(resolveHandoverWorkName(it), resolveHandoverMaterialName(it)) }
                                    .map { (_, materialHandovers) ->
                                        val firstHandover = materialHandovers.first()
                                        HandoverMaterialSummary(
                                            workLabel = resolveHandoverWorkName(firstHandover),
                                            materialLabel = resolveHandoverMaterialName(firstHandover),
                                            totalCount = materialHandovers.size,
                                            totalQuantity = materialHandovers.sumOf { it.quantity.toDouble() }.toFloat()
                                        )
                                    }
                                    .sortedWith(
                                        compareByDescending<HandoverMaterialSummary> { it.totalQuantity }
                                            .thenBy { it.materialLabel.lowercase(Locale("vi", "VN")) }
                                            .thenBy { it.workLabel.lowercase(Locale("vi", "VN")) }
                                    )
                            )
                        }
                        .sortedByDescending { it.epochDay }
                )
            }
            .sortedByDescending { monthKeyToEpochMillis(it.monthKey) }
    }

    val projectContractorOptions = remember(state.designNodes, state.designRoutes) {
        (state.designNodes.map { it.contractor.trim() } + state.designRoutes.map { it.contractor.trim() })
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val projectHandoverCandidatesMatched = remember(state.designNodes, state.workVolumeRows, state.materialDeclarations) {
        val nodesByCode = state.designNodes.associateBy { it.code.trim().uppercase() }
        state.workVolumeRows.flatMap { row ->
            val matchedNode = nodesByCode[row.nodeCode.trim().uppercase()]
                ?: state.designNodes.firstOrNull { handoverTextMatches(it.code, row.nodeCode) }
            state.materialDeclarations
                .filter { declaration -> handoverTextMatches(declaration.workName, row.workName) }
                .map { declaration ->
                    HandoverCandidate(
                        nodeCode = matchedNode?.code ?: row.nodeCode,
                        contractor = matchedNode?.contractor.orEmpty(),
                        workName = declaration.workName,
                        materialName = declaration.materialName,
                        unit = declaration.unit
                    )
                }
        }.filter { it.materialName.isNotBlank() && it.workName.isNotBlank() }
    }

    val projectHandoverCandidatesFallback = remember(state.designNodes, state.designRoutes, state.materialDeclarations) {
        val candidatesFromNodes = state.designNodes.flatMap { node ->
            state.materialDeclarations.map { declaration ->
                HandoverCandidate(
                    nodeCode = node.code,
                    contractor = node.contractor.trim(),
                    workName = declaration.workName,
                    materialName = declaration.materialName,
                    unit = declaration.unit
                )
            }
        }
        val candidatesFromRoutes = state.designRoutes.flatMap { route ->
            val nodes = listOfNotNull(route.startNodeCode.takeIf { it.isNotBlank() }, route.endNodeCode.takeIf { it.isNotBlank() })
            nodes.flatMap { nodeCode ->
                state.materialDeclarations.map { declaration ->
                    HandoverCandidate(
                        nodeCode = nodeCode,
                        contractor = route.contractor.trim(),
                        workName = declaration.workName,
                        materialName = declaration.materialName,
                        unit = declaration.unit
                    )
                }
            }
        }
        (candidatesFromNodes + candidatesFromRoutes)
            .filter { it.contractor.isNotBlank() && it.materialName.isNotBlank() && it.workName.isNotBlank() }
    }

    val projectHandoverCandidates = remember(
        projectContractorOptions,
        projectHandoverCandidatesMatched,
        projectHandoverCandidatesFallback
    ) {
        (projectHandoverCandidatesMatched + projectHandoverCandidatesFallback)
            .distinctBy {
                listOf(
                    it.nodeCode.trim().uppercase(),
                    it.contractor.trim().uppercase(),
                    it.workName.trim().uppercase(),
                    it.materialName.trim().uppercase(),
                    it.unit.trim().uppercase()
                ).joinToString("|")
            }
            .sortedWith(
                compareBy<HandoverCandidate> { it.contractor.lowercase(Locale("vi", "VN")) }
                    .thenBy { it.nodeCode.lowercase(Locale("vi", "VN")) }
                    .thenBy { it.workName.lowercase(Locale("vi", "VN")) }
                    .thenBy { it.materialName.lowercase(Locale("vi", "VN")) }
            )
    }

    WorkspaceRefreshContainer(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = darkBg
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Header
                item {
                    Column {
                        Text(
                            "Hồ sơ Vật tư",
                            color = textColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Kê khai định mức, theo dõi giao nhận và thống kê chi tiết",
                            color = secondaryText,
                            fontSize = 14.sp
                        )
                    }
                }

                // Horizontal Tab Selector
                item {
                    TabRow(
                        selectedTabIndex = selectedSubTab,
                        containerColor = cardBg,
                        contentColor = blue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, dividerColor, RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedSubTab == 0,
                            onClick = { selectedSubTab = 0 },
                            text = { Text("Kê khai", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Outlined.EditNote, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedSubTab == 1,
                            onClick = { selectedSubTab = 1 },
                            text = { Text("Giao nhận", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Outlined.LocalShipping, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedSubTab == 2,
                            onClick = { selectedSubTab = 2 },
                            text = { Text("Thống kê", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            icon = { Icon(Icons.Outlined.BarChart, contentDescription = null) }
                        )
                    }
                }

                // Tab Content Render
                when (selectedSubTab) {
                    0 -> {
                        // TAB 1: KÊ KHAI (DECLARATION)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ĐỊNH MỨC VẬT TƯ DỰ ÁN",
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Button(
                                    onClick = { showAddDeclarationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = blue),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Thêm vật tư", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Check if declarations are empty
                        if (state.materialDeclarations.isEmpty()) {
                            item {
                                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.AssignmentLate,
                                            contentDescription = null,
                                            tint = secondaryText,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Chưa có định mức vật tư nào được kê khai.",
                                            color = textColor,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Nhấn 'Thêm vật tư' để thiết lập định mức.",
                                            color = secondaryText,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            // Group declarations by workName (could be mapped work category or historical work name)
                            val groupedDeclarations = state.materialDeclarations.groupBy { it.workName }
                            items(groupedDeclarations.keys.toList()) { workCategory ->
                                val declarations = groupedDeclarations[workCategory].orEmpty()
                                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = workCategory,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = dividerColor)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        declarations.forEach { decl ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        decl.materialName,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = textColor,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                    "Định mức: ${formatQty(decl.ratio)} ${decl.unit} / đơn vị công việc",
                                                        fontSize = 12.sp,
                                                        color = secondaryText
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteMaterialDeclaration(decl) }
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Xóa",
                                                        tint = Color.Red.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // TAB 2: GIAO NHẬN (HANDOVER)
                        // If no mapped works exist in workVolumeRows
                        if (state.workVolumeRows.isEmpty()) {
                            item {
                                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Vật tư chỉ hoạt động sau khi các công việc được ánh xạ thiết kế.",
                                            color = secondaryText,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "DANH SÁCH GIAO NHẬN VẬT TƯ",
                                        color = textColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Button(
                                        onClick = { showQuickAddHandoverDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = blue),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Search and Filters Bar
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Tìm theo mã nút...") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Xóa")
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Contractor dropdown
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { contractorDropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    selectedContractor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 12.sp,
                                                    color = textColor
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                            DropdownMenu(
                                                expanded = contractorDropdownExpanded,
                                                onDismissRequest = { contractorDropdownExpanded = false }
                                            ) {
                                                allContractors.forEach { contractor ->
                                                    DropdownMenuItem(
                                                        text = { Text(contractor) },
                                                        onClick = {
                                                            selectedContractor = contractor
                                                            contractorDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Node dropdown
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { nodeDropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    selectedNodeFilter,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 12.sp,
                                                    color = textColor
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                            DropdownMenu(
                                                expanded = nodeDropdownExpanded,
                                                onDismissRequest = { nodeDropdownExpanded = false }
                                            ) {
                                                allNodeCodes.forEach { code ->
                                                    DropdownMenuItem(
                                                        text = { Text(code) },
                                                        onClick = {
                                                            selectedNodeFilter = code
                                                            nodeDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { monthDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                selectedMonthFilter,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp,
                                                color = textColor
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = monthDropdownExpanded,
                                            onDismissRequest = { monthDropdownExpanded = false }
                                        ) {
                                            handoverMonthOptions.forEach { month ->
                                                DropdownMenuItem(
                                                    text = { Text(month) },
                                                    onClick = {
                                                        selectedMonthFilter = month
                                                        monthDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { selectedMonthFilter = currentMonthKey },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Tháng hiện tại",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { selectedMonthFilter = "Tất cả tháng" },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = secondaryText.copy(alpha = 0.72f)
                                            ),
                                            border = BorderStroke(1.dp, secondaryText.copy(alpha = 0.22f))
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = secondaryText.copy(alpha = 0.68f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Xóa lọc",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                    }

                                    // Node details list
                                    item {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                            "BẢNG THỐNG KÊ GIAO NHẬN",
                                                color = textColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            HandoverStatisticsSection(
                                                visibleHandovers = visibleHandovers,
                                                monthSummaries = handoverMonthSummaries
                                            )
                                        }
                                    }

                                    if (filteredNodes.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Không tìm thấy nút kỹ thuật phù hợp", color = secondaryText)
                                    }
                                }
                            } else {
                                items(filteredNodes) { node ->
                                    // Get work items at this node
                                    val nodeWorks = state.workVolumeRows.filter { it.nodeCode == node.code }
                                    if (nodeWorks.isNotEmpty()) {
                                        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            "Nút: ${node.code}",
                                                            fontWeight = FontWeight.Bold,
                                                            color = textColor,
                                                            fontSize = 16.sp
                                                        )
                                                        Text(
                                                            "Nhà thầu: ${node.contractor.ifBlank { "Chưa phân công" }}",
                                                            fontSize = 12.sp,
                                                            color = secondaryText
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = dividerColor)
                                                Spacer(modifier = Modifier.height(8.dp))

                                                // List declared materials for each work category at this node
                                                var hasMaterials = false
                                                nodeWorks.forEach { workVolumeRow ->
                                                    val declarations = state.materialDeclarations.filter { it.workName.equals(workVolumeRow.workName, ignoreCase = true) }
                                                    declarations.forEach { decl ->
                                                        hasMaterials = true
                                                        val balance = calculateMaterialBalance(
                                                            declaration = decl,
                                                            workVolumeRows = listOf(workVolumeRow),
                                                            materialHandovers = state.materialHandovers,
                                                            nodeCodes = setOf(node.code.trim().uppercase())
                                                        )

                                                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        decl.materialName,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = textColor,
                                                                        fontSize = 14.sp
                                                                    )
                                                                    Text(
                                                                        "Công việc: ${decl.workName}",
                                                                        fontSize = 11.sp,
                                                                        color = secondaryText
                                                                    )
                                                                }
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Column(
                                                                        horizontalAlignment = Alignment.End,
                                                                        modifier = Modifier.padding(end = 8.dp)
                                                                    ) {
                                                                                    Text(
                                                                                        "Giao: ${formatQty(balance.delivered)} / ${formatQty(balance.planned)} ${decl.unit}",
                                                                                        fontSize = 12.sp,
                                                                                        color = textColor
                                                                                    )
                                                                                    if (balance.remaining > 0f) {
                                                                                        Text(
                                                                                         "Thiếu: ${formatQty(balance.remaining)}",
                                                                                            fontSize = 11.sp,
                                                                                            color = orange,
                                                                                            fontWeight = FontWeight.Bold
                                                                            )
                                                                        } else {
                                                                            Text(
                                                                                "Đủ",
                                                                                fontSize = 11.sp,
                                                                                color = green,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                        }
                                                                    }
                                                                    IconButton(
                                                                        onClick = {
                                                                            prefilledNodeCode = node.code
                                                                            prefilledWorkName = decl.workName
                                                                            prefilledMaterialName = decl.materialName
                                                                            prefilledUnit = decl.unit
                                                                            prefilledContractor = node.contractor
                                                                            showAddHandoverDialog = true
                                                                        },
                                                                        modifier = Modifier.size(36.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Outlined.LocalShipping,
                                                                             contentDescription = "Giao nhận",
                                                                            tint = blue,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if (!hasMaterials) {
                                                    Text(
                                                         "Chưa kê khai vật tư nào cho các hạng mục tại nút này.",
                                                        fontSize = 12.sp,
                                                        color = secondaryText,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // TAB 3: THỐNG KÊ (STATISTICS)
                        if (state.workVolumeRows.isEmpty()) {
                            item {
                                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Chưa có dữ liệu công việc. Không thể tạo thống kê.",
                                            color = secondaryText,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            // Contractor filter dropdown
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Lọc theo nhà thầu:", fontSize = 13.sp, color = secondaryText)
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedButton(
                                            onClick = { contractorDropdownExpanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                selectedContractor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp,
                                                color = textColor
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = contractorDropdownExpanded,
                                            onDismissRequest = { contractorDropdownExpanded = false }
                                        ) {
                                            allContractors.forEach { contractor ->
                                                DropdownMenuItem(
                                                    text = { Text(contractor) },
                                                    onClick = {
                                                        selectedContractor = contractor
                                                        contractorDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Component A: Aggregated Table
                            item {
                                Text(
                                    "BẢNG TỔNG HỢP VẬT TƯ DỰ ÁN",
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            if (aggregatedStatsList.isEmpty()) {
                                item {
                                    Text("Không có dữ liệu định mức phù hợp.", color = secondaryText, fontSize = 13.sp)
                                }
                            } else {
                                items(aggregatedStatsList) { (decl, balance) ->
                                    val progressFraction = if (balance.planned > 0f) (balance.delivered / balance.planned).coerceIn(0f, 1f) else 0f
                                    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        decl.materialName,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textColor,
                                                        fontSize = 15.sp
                                                    )
                                                    Text(
                                                        "Công việc: ${decl.workName}",
                                                        fontSize = 11.sp,
                                                        color = secondaryText
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        "Giao: ${formatQty(balance.delivered)} / ${formatQty(balance.planned)} ${decl.unit}",
                                                        fontSize = 12.sp,
                                                        color = textColor
                                                    )
                                                    if (balance.remaining > 0f) {
                                                        Text(
                                                             "Tồn thiếu: ${formatQty(balance.remaining)}",
                                                            fontSize = 12.sp,
                                                            color = orange,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    } else {
                                                        Text(
                                                             "Đủ kế hoạch",
                                                            fontSize = 12.sp,
                                                            color = green,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                                LinearProgressIndicator(
                                                    progress = { progressFraction },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp)),
                                                    color = if (balance.remaining > 0f) blue else green,
                                                    trackColor = dividerColor
                                                )
                                        }
                                    }
                                }
                            }

                            // Component B: Timeline log
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "LỊCH SỬ GIAO NHẬN VẬT TƯ THEO NGÀY",
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            if (visibleHandovers.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (selectedMonthFilter == "Tất cả tháng") {
                                                "Không có lịch sử giao nhận"
                                            } else {
                                                "Không có lịch sử giao nhận trong $selectedMonthFilter"
                                            },
                                            color = secondaryText,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                val groupedHandoversByMonth = visibleHandovers.groupBy { formatMonthKey(it.handoverDateEpochDay) }
                                val monthKeys = groupedHandoversByMonth.keys.sortedByDescending { monthKeyToEpochMillis(it) }
                                items(monthKeys) { monthKey ->
                                    val monthHandovers = groupedHandoversByMonth[monthKey].orEmpty()
                                    val groupedHandoversByDay = monthHandovers.groupBy { it.handoverDateEpochDay }
                                    val dayKeys = groupedHandoversByDay.keys.sortedByDescending { it }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.3f)),
                                        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                "Tháng: $monthKey",
                                                fontWeight = FontWeight.Bold,
                                                color = blue,
                                                fontSize = 13.sp
                                            )
                                            HorizontalDivider(color = dividerColor.copy(alpha = 0.3f))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                TableHeaderCell("Vật tư", modifier = Modifier.weight(1.3f))
                                                TableHeaderCell("Nhà thầu", modifier = Modifier.weight(0.7f))
                                                TableHeaderCell("SL", modifier = Modifier.weight(0.45f), textAlign = TextAlign.End)
                                                if (showUnitColumn) {
                                                TableHeaderCell("ĐV", modifier = Modifier.weight(0.4f), textAlign = TextAlign.End)
                                                }
                                                TableHeaderCell("Người nhận", modifier = Modifier.weight(0.8f))
                                            }
                                            HorizontalDivider(color = dividerColor.copy(alpha = 0.25f))
                                            dayKeys.forEach { epochDay ->
                                                val dayHandovers = groupedHandoversByDay[epochDay].orEmpty()
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(88.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(blue.copy(alpha = 0.08f))
                                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            Text(
                                                                "Ngày",
                                                                fontSize = 10.sp,
                                                                color = secondaryText
                                                            )
                                                            Text(
                                                                formatEpochDay(epochDay),
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = blue,
                                                                fontSize = 11.sp
                                                            )
                                                            Text(
                                                                "${dayHandovers.size} phiếu",
                                                                fontSize = 10.sp,
                                                                color = secondaryText
                                                            )
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        dayHandovers.forEachIndexed { index, ho ->
                                                            val displayName = resolveHandoverMaterialName(ho)
                                                            val receiver = resolveHandoverReceiver(ho)
                                                            val noteBody = resolveHandoverNoteBody(ho)
                                                            val isNoteExpanded = ho.id in expandedHandoverNotes
                                                            val rowBackground = if (index % 2 == 0) {
                                                                cardBg.copy(alpha = 0.18f)
                                                            } else {
                                                                cardBg.copy(alpha = 0.34f)
                                                            }

                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .background(rowBackground, RoundedCornerShape(6.dp))
                                                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.Top
                                                            ) {
                                                                TableCell(
                                                                    text = displayName,
                                                                    modifier = Modifier.weight(1.3f),
                                                                    color = textColor,
                                                                    maxLines = 2
                                                                )
                                                                TableCell(
                                                                    text = ho.contractor,
                                                                    modifier = Modifier.weight(0.7f),
                                                                    color = textColor,
                                                                    maxLines = 1
                                                                )
                                                                TableCell(
                                                                    text = formatQty(ho.quantity),
                                                                    modifier = Modifier.weight(0.45f),
                                                                    color = green,
                                                                    textAlign = TextAlign.End
                                                                )
                                                                if (showUnitColumn) {
                                                                    TableCell(
                                                                        text = ho.unit.ifBlank { "-" },
                                                                        modifier = Modifier.weight(0.4f),
                                                                        color = secondaryText,
                                                                        textAlign = TextAlign.End
                                                                    )
                                                                }
                                                                TableCell(
                                                                    text = receiver.ifBlank { "-" },
                                                                    modifier = Modifier.weight(0.8f),
                                                                    color = secondaryText,
                                                                    maxLines = 2
                                                                )
                                                                Row(
                                                                    modifier = Modifier.weight(0.45f),
                                                                    horizontalArrangement = Arrangement.End,
                                                                    verticalAlignment = Alignment.Top
                                                                ) {
                                                                    if (noteBody.isNotBlank()) {
                                                                        IconButton(
                                                                            onClick = {
                                                                                expandedHandoverNotes =
                                                                                    if (isNoteExpanded) {
                                                                                        expandedHandoverNotes - ho.id
                                                                                    } else {
                                                                                        expandedHandoverNotes + ho.id
                                                                                    }
                                                                            },
                                                                            modifier = Modifier
                                                                                .size(22.dp)
                                                                                .clip(CircleShape)
                                                                                .background(secondaryText.copy(alpha = 0.10f))
                                                                        ) {
                                                                            Icon(
                                                                                if (isNoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                                contentDescription = null,
                                                                                tint = secondaryText,
                                                                                modifier = Modifier.size(12.dp)
                                                                            )
                                                                        }
                                                                    } else {
                                                                        Text(
                                                                            "-",
                                                                            color = secondaryText,
                                                                            fontSize = 12.sp,
                                                                            modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                                                                        )
                                                                    }
                                                                    IconButton(
                                                                        onClick = { viewModel.deleteMaterialHandover(ho) },
                                                                        modifier = Modifier.size(22.dp)
                                                                    ) {
                                                                        Icon(
                                                                            Icons.Default.Delete,
                                                                             contentDescription = "Xóa",
                                                                            tint = Color.Red.copy(alpha = 0.8f),
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            AnimatedVisibility(
                                                                visible = isNoteExpanded && noteBody.isNotBlank()
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(start = 6.dp, end = 6.dp, top = 0.dp, bottom = 0.dp)
                                                                ) {
                                                                    Text(
                                                                        "Ghi chú: $noteBody",
                                                                        fontSize = 10.sp,
                                                                        color = secondaryText,
                                                                        maxLines = 2,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                        }
                                                    }
                                                }
                                                if (dayKeys.lastOrNull() != epochDay) {
                                                    HorizontalDivider(color = dividerColor.copy(alpha = 0.15f))
                                                    Spacer(modifier = Modifier.height(4.dp))
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
        }
    }

    if (showAddDeclarationDialog) {
        AddDeclarationDialog(
            distinctWorkNames = distinctWorkNames,
            onDismiss = { showAddDeclarationDialog = false },
            onConfirm = { workCategory, material, ratio, unit ->
                viewModel.addMaterialDeclaration(workCategory, material, ratio, unit)
                showAddDeclarationDialog = false
            }
        )
    }

    if (showAddHandoverDialog) {
        val prefilledQty = remember(
            prefilledNodeCode,
            prefilledWorkName,
            prefilledMaterialName,
            state.materialDeclarations,
            state.designNodes,
            state.workVolumeRows,
            indexes.parsedMaterialsByNodeKey
        ) {
            val nodeCode = prefilledNodeCode.orEmpty()
            val workName = prefilledWorkName.orEmpty()
            val materialName = prefilledMaterialName.orEmpty()
            resolveMaterialQuantitySuggestion(
                nodeCode = nodeCode,
                workName = workName,
                materialName = materialName,
                allDeclarations = state.materialDeclarations,
                workVolumeRows = state.workVolumeRows,
                parsedMaterialsByNodeKey = indexes.parsedMaterialsByNodeKey
            )?.plannedMaterialQty?.takeIf { it > 0f }?.let(::formatQty).orEmpty()
        }
        AddHandoverDialog(
            prefilledNodeCode = prefilledNodeCode.orEmpty(),
            prefilledWorkName = prefilledWorkName.orEmpty(),
            prefilledMaterialName = prefilledMaterialName.orEmpty(),
            prefilledUnit = prefilledUnit.orEmpty(),
            prefilledContractor = prefilledContractor.orEmpty(),
            prefilledQtyText = prefilledQty,
            onDismiss = { showAddHandoverDialog = false },
            onConfirm = { node, work, material, qty, unit, contractor, date, receiver, note ->
                viewModel.addMaterialHandover(
                    nodeCode = node,
                    workName = work,
                    materialName = material,
                    contractor = contractor,
                    quantity = qty,
                    unit = unit,
                    handoverDateEpochDay = date,
                    note = note,
                    receiver = receiver
                )
                showAddHandoverDialog = false
            }
        )
    }

    if (showQuickAddHandoverDialog) {
        QuickAddHandoverDialog(
            candidates = projectHandoverCandidates,
            handovers = state.materialHandovers,
            allContractors = projectContractorOptions,
            allDeclarations = state.materialDeclarations,
            designNodes = state.designNodes,
            designRoutes = state.designRoutes,
            workVolumeRows = state.workVolumeRows,
            parsedMaterialsByNodeKey = indexes.parsedMaterialsByNodeKey,
            onDismiss = { showQuickAddHandoverDialog = false },
            onConfirm = { node, work, material, qty, unit, contractor, date, receiver, note ->
                viewModel.addMaterialHandover(
                    nodeCode = node,
                    workName = work,
                    materialName = material,
                    contractor = contractor,
                    quantity = qty,
                    unit = unit,
                    handoverDateEpochDay = date,
                    note = note,
                    receiver = receiver
                )
                showQuickAddHandoverDialog = false
            }
        )
    }
}

@Composable
private fun HandoverStatisticsSection(
    visibleHandovers: List<MaterialHandover>,
    monthSummaries: List<HandoverMonthSummary>
) {
    val colors = MaterialTheme.colorScheme
    val extendedColors = MaterialTheme.extendedColors
    val cardBg = extendedColors.panelBackgroundAlt
    val blue = colors.primary
    val green = extendedColors.success
    val orange = extendedColors.mapAccent
    val dividerColor = colors.outlineVariant
    val textColor = colors.onBackground
    val secondaryText = colors.onSurfaceVariant

    if (visibleHandovers.isEmpty()) {
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Chưa có phiếu giao nhận để thống kê.",
                    color = secondaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${visibleHandovers.size} phiếu • ${formatQty(visibleHandovers.sumOf { it.quantity.toDouble() }.toFloat())} tổng SL",
                    color = secondaryText,
                    fontSize = 11.sp
                )

                HorizontalDivider(color = dividerColor.copy(alpha = 0.2f))

                monthSummaries.forEachIndexed { monthIndex, monthSummary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardBg.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    monthSummary.monthKey,
                                    color = blue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                     "${monthSummary.totalCount} phiếu • ${formatQty(monthSummary.totalQuantity)}",
                                    color = secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TableHeaderCell("Ngày", modifier = Modifier.weight(1.0f))
                            TableHeaderCell("Vật tư", modifier = Modifier.weight(1.35f))
                            TableHeaderCell("Phiếu", modifier = Modifier.weight(0.45f), textAlign = TextAlign.End)
                            TableHeaderCell("SL", modifier = Modifier.weight(0.55f), textAlign = TextAlign.End)
                        }
                        HorizontalDivider(color = dividerColor.copy(alpha = 0.18f))

                        monthSummary.daySummaries.forEachIndexed { dayIndex, daySummary ->
                            val rowBackground = if (dayIndex % 2 == 0) {
                                cardBg.copy(alpha = 0.10f)
                            } else {
                                cardBg.copy(alpha = 0.18f)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBackground, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                daySummary.materialSummaries.forEachIndexed { materialIndex, materialSummary ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TableCell(
                                            text = if (materialIndex == 0) formatEpochDay(daySummary.epochDay) else "",
                                            modifier = Modifier.weight(1.0f),
                                            color = textColor
                                        )
                                        Column(modifier = Modifier.weight(1.35f)) {
                                            Text(
                                                materialSummary.materialLabel,
                                                color = textColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (materialSummary.workLabel.isNotBlank()) {
                                                Text(
                                                    materialSummary.workLabel,
                                                    color = secondaryText,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        TableCell(
                                            text = materialSummary.totalCount.toString(),
                                            modifier = Modifier.weight(0.45f),
                                            color = orange,
                                            textAlign = TextAlign.End
                                        )
                                        TableCell(
                                            text = formatQty(materialSummary.totalQuantity),
                                            modifier = Modifier.weight(0.55f),
                                            color = green,
                                            textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (monthIndex != monthSummaries.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeclarationDialog(
    distinctWorkNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (workCategory: String, material: String, ratio: Float, unit: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onBackground

    var selectedWorkCategory by remember { mutableStateOf("") }
    var materialName by remember { mutableStateOf("") }
    var ratioString by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    var workDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.97f)
            .wrapContentHeight()
            .navigationBarsPadding()
            .imePadding(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
                Text(
                    "KÊ KHAI ĐỊNH MỨC VẬT TƯ",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Work Category selection (either dropdown or free text if no work categories mapped)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedWorkCategory,
                        onValueChange = {
                            selectedWorkCategory = it
                            workDropdownExpanded = true
                        },
                        label = { Text("Tên công việc thiết kế") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (distinctWorkNames.isNotEmpty()) {
                                IconButton(onClick = { workDropdownExpanded = !workDropdownExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true
                    )
                    if (distinctWorkNames.isNotEmpty()) {
                        DropdownMenu(
                            expanded = workDropdownExpanded,
                            onDismissRequest = { workDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            distinctWorkNames.forEach { work ->
                                DropdownMenuItem(
                                    text = { Text(work) },
                                    onClick = {
                                        selectedWorkCategory = work
                                        workDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = materialName,
                    onValueChange = { materialName = it },
                    label = { Text("Tên vật tư") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = ratioString,
                    onValueChange = { ratioString = it },
                    label = { Text("Hệ số / Định mức") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Đơn vị tính") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ratio = if (ratioString.isBlank()) 1f else parseMaterialNumberInput(ratioString)
                    if (selectedWorkCategory.isBlank()) {
                        errorMessage = "Vui lòng nhập/chọn công việc thiết kế"
                        return@Button
                    }
                    if (materialName.isBlank()) {
                        errorMessage = "Vui lòng nhập tên vật tư"
                        return@Button
                    }
                    if (ratio == null || ratio <= 0f) {
                        errorMessage = "Hệ số định mức phải là số lớn hơn 0"
                        return@Button
                    }
                    if (unit.isBlank()) {
                        errorMessage = "Vui lòng nhập đơn vị tính"
                        return@Button
                    }
                    errorMessage = ""
                    onConfirm(selectedWorkCategory, materialName, ratio, unit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Lưu định mức")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHandoverDialog(
    prefilledNodeCode: String,
    prefilledWorkName: String,
    prefilledMaterialName: String,
    prefilledUnit: String,
    prefilledContractor: String,
    prefilledQtyText: String,
    onDismiss: () -> Unit,
    onConfirm: (node: String, work: String, material: String, qty: Float, unit: String, contractor: String, date: Long, receiver: String, note: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onBackground

    var qtyString by remember(prefilledQtyText) { mutableStateOf(prefilledQtyText) }
    var receiver by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val calendar = remember { Calendar.getInstance() }
    var selectedDateMillis by remember { mutableStateOf(calendar.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val formattedDate = remember(selectedDateMillis) {
        val date = Date(selectedDateMillis)
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(date)
    }

    val dateEpochDay = remember(selectedDateMillis) {
        selectedDateMillis / (24 * 60 * 60 * 1000)
    }

    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.97f)
            .wrapContentHeight()
            .navigationBarsPadding()
            .imePadding(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "GHI NHẬN PHIẾU GIAO NHẬN VẬT TƯ",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Thông tin bàn giao:",
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    fontSize = 14.sp
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vị trí / Nút:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(prefilledNodeCode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Công việc:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(prefilledWorkName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vật tư:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(prefilledMaterialName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Nhà thầu:", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            Text(prefilledContractor, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = qtyString,
                        onValueChange = { qtyString = it },
                        label = { Text("Số lượng giao nhận (${prefilledUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ngày giao: $formattedDate",
                        color = textColor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }

                OutlinedTextField(
                    value = receiver,
                    onValueChange = { receiver = it },
                    label = { Text("Người nhận") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Ghi chú") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = parseMaterialNumberInput(qtyString)
                    if (qty == null || qty <= 0f) {
                        errorMessage = "Số lượng phải là số lớn hơn 0"
                        return@Button
                    }
                    if (receiver.isBlank()) {
                        errorMessage = "Vui lòng nhập người nhận"
                        return@Button
                    }
                    errorMessage = ""
                    onConfirm(
                        prefilledNodeCode,
                        prefilledWorkName,
                        prefilledMaterialName,
                        qty,
                        prefilledUnit,
                        prefilledContractor,
                        dateEpochDay,
                        receiver.trim(),
                        note
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Lưu phiếu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis ?: selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Hủy")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private data class HandoverCandidate(
    val nodeCode: String,
    val contractor: String,
    val workName: String,
    val materialName: String,
    val unit: String
) {
    val materialKey: String
        get() = "$materialName|$unit"

    val workKey: String
        get() = "$workName|$nodeCode"

    val label: String
        get() = "$materialName - $workName"

    val workLabel: String
        get() = workName
}

internal data class MaterialQuantitySuggestion(
    val matchedDeclaration: MaterialDeclaration,
    val plannedWorkQty: Float,
    val plannedMaterialQty: Float
)

internal fun resolveMaterialQuantitySuggestion(
    nodeCode: String,
    workName: String,
    materialName: String,
    allDeclarations: List<MaterialDeclaration>,
    workVolumeRows: List<WorkVolumeProgress>,
    parsedMaterialsByNodeKey: Map<String, List<PreparedMaterialLine>>
): MaterialQuantitySuggestion? {
    val matchedDeclaration = allDeclarations.firstOrNull { declaration ->
        normalizeMatchText(declaration.materialName) == normalizeMatchText(materialName) &&
            normalizeMatchText(declaration.workName) == normalizeMatchText(workName)
    } ?: return null

    val plannedWorkQty = resolvePlannedWorkQty(
        nodeCode = nodeCode,
        workName = workName,
        workVolumeRows = workVolumeRows,
        parsedMaterialsByNodeKey = parsedMaterialsByNodeKey
    )
    if (plannedWorkQty <= 0f) return null

    return MaterialQuantitySuggestion(
        matchedDeclaration = matchedDeclaration,
        plannedWorkQty = plannedWorkQty,
        plannedMaterialQty = plannedWorkQty * matchedDeclaration.ratio
    )
}

internal fun materialQuantityDefaultText(suggestion: MaterialQuantitySuggestion?): String {
    return suggestion?.plannedMaterialQty?.takeIf { it > 0f }?.let(::formatQty).orEmpty()
}

internal fun parseMaterialNumberInput(value: String): Float? {
    val compact = value.trim().replace(" ", "")
    if (compact.isBlank()) return null
    val normalized = if (compact.contains(',') && compact.contains('.')) {
        compact.replace(".", "").replace(',', '.')
    } else {
        compact.replace(',', '.')
    }
    return normalized.toFloatOrNull()
}

internal fun shouldRefreshMaterialQuantity(
    currentSelectionKey: String,
    previousSelectionKey: String
): Boolean = currentSelectionKey != previousSelectionKey

internal fun resolvePlannedWorkQty(
    nodeCode: String,
    workName: String,
    workVolumeRows: List<WorkVolumeProgress>,
    parsedMaterialsByNodeKey: Map<String, List<PreparedMaterialLine>>
): Float {
    val normalizedWorkName = normalizeMatchText(workName)
    
    // 1. Try to find in parsed materials from AutoCAD/Excel design summary of the node
    val parsedLines = parsedMaterialsByNodeKey[nodeCode].orEmpty()
    val parsedPlanned = parsedLines.firstOrNull { normalizeMatchText(it.itemName) == normalizedWorkName }?.plannedQty
    if (parsedPlanned != null && parsedPlanned > 0f) {
        return parsedPlanned
    }
    
    // 2. Fallback to workVolumeRows matching nodeCode and workName
    val normalizedNodeCode = nodeCode.trim().uppercase()
    val matchingRows = workVolumeRows.filter {
        normalizeMatchText(it.workName) == normalizedWorkName &&
                it.nodeCode.trim().uppercase() == normalizedNodeCode
    }
    if (matchingRows.isNotEmpty()) {
        return matchingRows.sumOf { it.plannedQty.toDouble() }.toFloat()
    }

    // 3. Global fallback to any workVolumeRows with matching workName if nodeCode-specific rows aren't found
    return workVolumeRows
        .filter { normalizeMatchText(it.workName) == normalizedWorkName }
        .sumOf { it.plannedQty.toDouble() }
        .toFloat()
}

internal fun resolveContractorPlannedWorkQty(
    contractor: String,
    workName: String,
    designNodes: List<GisNode>,
    workVolumeRows: List<WorkVolumeProgress>,
    parsedMaterialsByNodeKey: Map<String, List<PreparedMaterialLine>>
): Float {
    val normalizedWorkName = normalizeMatchText(workName)
    val trimmedContractor = contractor.trim()
    
    val filteredNodes = if (trimmedContractor.isBlank()) {
        designNodes
    } else {
        designNodes.filter { it.contractor.trim().equals(trimmedContractor, ignoreCase = true) }
    }
    
    var totalPlanned = 0f
    var hasDesignOrNodeRowMatch = false
    
    filteredNodes.forEach { node ->
        val parsedLines = parsedMaterialsByNodeKey[node.id].orEmpty().takeIf { it.isNotEmpty() }
            ?: parsedMaterialsByNodeKey[node.code].orEmpty()
            
        val parsedPlanned = parsedLines.firstOrNull { normalizeMatchText(it.itemName) == normalizedWorkName }?.plannedQty
        if (parsedPlanned != null && parsedPlanned > 0f) {
            totalPlanned += parsedPlanned
            hasDesignOrNodeRowMatch = true
        } else {
            val nodeRows = workVolumeRows.filter { row ->
                normalizeMatchText(row.workName) == normalizedWorkName &&
                        (row.nodeCode.trim().equals(node.id, ignoreCase = true) ||
                         row.nodeCode.trim().equals(node.code, ignoreCase = true))
            }
            if (nodeRows.isNotEmpty()) {
                totalPlanned += nodeRows.sumOf { it.plannedQty.toDouble() }.toFloat()
                hasDesignOrNodeRowMatch = true
            }
        }
    }
    
    if (!hasDesignOrNodeRowMatch) {
        val contractorNodeCodes = filteredNodes.flatMap { listOf(it.id.trim().uppercase(), it.code.trim().uppercase()) }.toSet()
        val matchingRows = workVolumeRows.filter { row ->
            normalizeMatchText(row.workName) == normalizedWorkName &&
                    (trimmedContractor.isBlank() || row.nodeCode.trim().uppercase() in contractorNodeCodes)
        }
        if (matchingRows.isNotEmpty()) {
            totalPlanned = matchingRows.sumOf { it.plannedQty.toDouble() }.toFloat()
        } else if (trimmedContractor.isNotBlank()) {
            val globalMatchingRows = workVolumeRows.filter { normalizeMatchText(it.workName) == normalizedWorkName }
            if (globalMatchingRows.isNotEmpty()) {
                totalPlanned = globalMatchingRows.sumOf { it.plannedQty.toDouble() }.toFloat()
            }
        }
    }
    
    return totalPlanned
}

internal fun normalizeMatchText(value: String): String {
    val stripped = Normalizer.normalize(value.lowercase(Locale("vi", "VN")), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')
    return stripped
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

internal fun handoverTextMatches(left: String, right: String): Boolean {
    val leftVariants = handoverTextVariants(left)
    val rightVariants = handoverTextVariants(right)
    if (leftVariants.isEmpty() || rightVariants.isEmpty()) return false

    val leftNormalized = leftVariants.map { normalizeMatchText(it) }.filter { it.isNotBlank() }
    val rightNormalized = rightVariants.map { normalizeMatchText(it) }.filter { it.isNotBlank() }
    if (leftNormalized.any { it in rightNormalized } || rightNormalized.any { it in leftNormalized }) return true

    return leftNormalized.any { leftCandidate ->
        rightNormalized.any { rightCandidate ->
            leftCandidate.contains(rightCandidate) || rightCandidate.contains(leftCandidate)
        }
    }
}

private fun handoverTextVariants(value: String): List<String> {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return emptyList()

    return buildList {
        add(trimmed)
        listOf(">", ":", "/").forEach { separator ->
            if (trimmed.contains(separator)) {
                add(trimmed.substringAfterLast(separator).trim())
                add(trimmed.substringBefore(separator).trim())
            }
        }
        if (trimmed.contains(" - ")) {
            add(trimmed.substringAfterLast(" - ").trim())
            add(trimmed.substringBefore(" - ").trim())
        }
    }.filter { it.isNotBlank() }.distinct()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddHandoverDialog(
    candidates: List<HandoverCandidate>,
    handovers: List<MaterialHandover>,
    allContractors: List<String>,
    allDeclarations: List<MaterialDeclaration>,
    designNodes: List<GisNode>,
    designRoutes: List<GisRoute>,
    workVolumeRows: List<WorkVolumeProgress>,
    parsedMaterialsByNodeKey: Map<String, List<PreparedMaterialLine>>,
    onDismiss: () -> Unit,
    onConfirm: (node: String, work: String, material: String, qty: Float, unit: String, contractor: String, date: Long, receiver: String, note: String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onBackground
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 360 || configuration.screenHeightDp < 700
    val dropdownMaxHeight = if (isCompactScreen) 180.dp else 260.dp

    val contractorOptions = remember(candidates, allContractors) {
        (candidates.map { it.contractor } + allContractors)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    var selectedContractor by remember(contractorOptions) {
        mutableStateOf(contractorOptions.firstOrNull().orEmpty())
    }
    var selectedMaterialKey by remember(candidates) { mutableStateOf("") }
    var selectedWorkKey by remember(candidates) { mutableStateOf("") }
    var qtyString by remember { mutableStateOf("") }
    var qtySelectionKey by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var contractorExpanded by remember { mutableStateOf(false) }
    var materialExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val calendar = remember { Calendar.getInstance() }
    var selectedDateMillis by remember { mutableStateOf(calendar.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val materialOptions = remember(candidates, selectedContractor, allDeclarations, designNodes) {
        val matchedMaterials = candidates
            .filter { selectedContractor.isBlank() || it.contractor == selectedContractor }
            .map { it.materialKey }
            .toSet()

        val matchedCandidates = candidates.filter { selectedContractor.isBlank() || it.contractor == selectedContractor }
        val fallbackCandidates = allDeclarations
            .filter { decl -> "${decl.materialName}|${decl.unit}" !in matchedMaterials }
            .map { decl ->
                val defaultNode = candidates.firstOrNull { it.contractor == selectedContractor }?.nodeCode
                    ?: designNodes.firstOrNull { it.contractor == selectedContractor }?.code
                    ?: designNodes.firstOrNull()?.code
                    ?: ""
                HandoverCandidate(
                    nodeCode = defaultNode,
                    contractor = selectedContractor,
                    workName = decl.workName,
                    materialName = decl.materialName,
                    unit = decl.unit
                )
            }

        (matchedCandidates + fallbackCandidates).distinctBy { it.materialKey }
    }

    val workOptions = remember(candidates, selectedContractor, selectedMaterialKey) {
        candidates
            .filter {
                (selectedContractor.isBlank() || it.contractor == selectedContractor) &&
                    (selectedMaterialKey.isBlank() || it.materialKey == selectedMaterialKey)
            }
            .distinctBy { it.workKey }
    }

    LaunchedEffect(materialOptions) {
        if (materialOptions.none { it.materialKey == selectedMaterialKey }) {
            selectedMaterialKey = materialOptions.firstOrNull()?.materialKey.orEmpty()
        }
    }

    LaunchedEffect(workOptions) {
        if (workOptions.none { it.workKey == selectedWorkKey }) {
            selectedWorkKey = workOptions.firstOrNull()?.workKey.orEmpty()
        }
    }

    val selectedCandidate = remember(candidates, selectedContractor, selectedMaterialKey, selectedWorkKey) {
        candidates.firstOrNull {
            it.contractor == selectedContractor &&
                it.materialKey == selectedMaterialKey &&
                it.workKey == selectedWorkKey
        }
    }

    val plannedWorkQtyForSelection = remember(
        selectedContractor,
        selectedCandidate,
        workVolumeRows,
        parsedMaterialsByNodeKey,
        designNodes
    ) {
        if (selectedCandidate == null) {
            0f
        } else {
            resolveContractorPlannedWorkQty(
                contractor = selectedContractor,
                workName = selectedCandidate.workName,
                designNodes = designNodes,
                workVolumeRows = workVolumeRows,
                parsedMaterialsByNodeKey = parsedMaterialsByNodeKey
            )
        }
    }

    val selectedQuantitySuggestion = remember(
        selectedCandidate,
        allDeclarations,
        plannedWorkQtyForSelection
    ) {
        val candidate = selectedCandidate ?: return@remember null
        val matchedDeclaration = allDeclarations.firstOrNull { declaration ->
            normalizeMatchText(declaration.materialName) == normalizeMatchText(candidate.materialName) &&
                    normalizeMatchText(declaration.workName) == normalizeMatchText(candidate.workName)
        }
        if (matchedDeclaration != null && plannedWorkQtyForSelection > 0f) {
            MaterialQuantitySuggestion(
                matchedDeclaration = matchedDeclaration,
                plannedWorkQty = plannedWorkQtyForSelection,
                plannedMaterialQty = plannedWorkQtyForSelection * matchedDeclaration.ratio
            )
        } else {
            null
        }
    }

    val selectedSelectionKey = selectedCandidate?.let {
        listOf(it.nodeCode, it.workKey, it.materialKey, it.contractor).joinToString("|")
    }.orEmpty()

    LaunchedEffect(selectedSelectionKey) {
        if (shouldRefreshMaterialQuantity(selectedSelectionKey, qtySelectionKey)) {
            qtyString = materialQuantityDefaultText(selectedQuantitySuggestion)
            qtySelectionKey = selectedSelectionKey
        }
    }

    val formattedDate = remember(selectedDateMillis) {
        val date = Date(selectedDateMillis)
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(date)
    }

    val dateEpochDay = remember(selectedDateMillis) {
        selectedDateMillis / (24 * 60 * 60 * 1000)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.97f)
            .wrapContentHeight()
            .navigationBarsPadding()
            .imePadding(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "THÊM GIAO NHẬN VẬT TƯ",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (candidates.isEmpty()) {
                    Text(
                        "Chưa có dữ liệu vật tư để tạo giao nhận.",
                        color = colors.error,
                        fontSize = 13.sp
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = contractorExpanded,
                        onExpandedChange = { contractorExpanded = !contractorExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (selectedContractor.isBlank()) "Chọn nhà thầu" else selectedContractor,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            label = { Text("Nhà thầu") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = contractorExpanded) },
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = contractorExpanded,
                            onDismissRequest = { contractorExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight)
                        ) {
                            contractorOptions.forEach { contractor ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            contractor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp
                                        )
                                    },
                                    onClick = {
                                        selectedContractor = contractor
                                        contractorExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = materialExpanded,
                        onExpandedChange = { materialExpanded = !materialExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = materialOptions.firstOrNull { it.materialKey == selectedMaterialKey }?.materialName
                                ?: "Chọn vật tư",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            label = { Text("Vật tư") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = materialExpanded) },
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = materialExpanded,
                            onDismissRequest = { materialExpanded = false },
                            modifier = Modifier.heightIn(max = dropdownMaxHeight)
                        ) {
                            materialOptions.forEach { candidate ->
                                DropdownMenuItem(
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                candidate.materialName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                candidate.workName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 10.sp,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedMaterialKey = candidate.materialKey
                                        materialExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = workOptions.firstOrNull { it.workKey == selectedWorkKey }?.workLabel ?: "",
                        onValueChange = {},
                        label = { Text("Hạng mục liên quan") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        singleLine = true
                    )

                    selectedCandidate?.let { candidate ->
                        Text(
                            "Đơn vị: ${candidate.unit}",
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = qtyString,
                        onValueChange = { qtyString = it },
                        label = {
                            Text(
                                selectedCandidate?.unit?.takeIf { it.isNotBlank() }?.let { "Số lượng ($it)" } ?: "Số lượng"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            selectedCandidate?.let { candidate ->
                                Column {
                    Text("Khối lượng công việc: ${formatQty(plannedWorkQtyForSelection)}")
                                    selectedQuantitySuggestion?.let { suggestion ->
                                        Text(
                                            "Định mức vật tư: ${formatQty(suggestion.plannedMaterialQty)} ${candidate.unit} (Khối lượng CV: ${formatQty(suggestion.plannedWorkQty)} x Hệ số: ${formatQty(suggestion.matchedDeclaration.ratio)})",
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true
                    )

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(
                            Icons.Filled.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ngày giao: $formattedDate",
                            color = textColor,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                    }

                    OutlinedTextField(
                        value = receiver,
                        onValueChange = { receiver = it },
                        label = { Text("Người nhận") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Ghi chú") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val candidate = selectedCandidate
                    val qty = parseMaterialNumberInput(qtyString)
                    when {
                        candidates.isEmpty() -> errorMessage = "Chưa có dữ liệu vật tư để thêm giao nhận"
                        selectedContractor.isBlank() -> errorMessage = "Vui lòng chọn nhà thầu"
                        candidate == null -> errorMessage = "Vui lòng chọn vật tư"
                        qty == null || qty <= 0f -> errorMessage = "Số lượng phải là số lớn hơn 0"
                        receiver.isBlank() -> errorMessage = "Vui lòng nhập người nhận"
                        else -> {
                            errorMessage = ""
                            onConfirm(
                                candidate.nodeCode,
                                candidate.workName,
                                candidate.materialName,
                                qty,
                                candidate.unit,
                                candidate.contractor,
                                dateEpochDay,
                                receiver.trim(),
                                note
                            )
                        }
                    }
                },
                enabled = candidates.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text("Lưu phiếu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMillis = datePickerState.selectedDateMillis ?: selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Hủy")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private data class HandoverMonthSummary(
    val monthKey: String,
    val totalCount: Int,
    val totalQuantity: Float,
    val daySummaries: List<HandoverDaySummary>
)

private data class HandoverDaySummary(
    val epochDay: Long,
    val totalCount: Int,
    val totalQuantity: Float,
    val materialSummaries: List<HandoverMaterialSummary>
)

private data class HandoverMaterialSummary(
    val workLabel: String,
    val materialLabel: String,
    val totalCount: Int,
    val totalQuantity: Float
)

internal data class MaterialBalanceSummary(
    val planned: Float,
    val delivered: Float,
    val remaining: Float
)

internal data class MaterialProjectSummary(
    val declaration: MaterialDeclaration,
    val balance: MaterialBalanceSummary
)

internal fun buildMaterialProjectSummary(
    declarations: List<MaterialDeclaration>,
    workVolumeRows: List<WorkVolumeProgress>,
    materialHandovers: List<MaterialHandover>,
    nodeCodes: Set<String>
): List<Pair<MaterialDeclaration, MaterialBalanceSummary>> {
    val normalizedNodeCodes = nodeCodes.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    if (declarations.isEmpty()) return emptyList()

    data class SummaryAccumulator(
        val declaration: MaterialDeclaration,
        var planned: Float = 0f,
        var delivered: Float = 0f
    )

    val declarationsByKey = LinkedHashMap<String, SummaryAccumulator>()
    declarations.forEach { declaration ->
        val key = materialProjectSummaryKey(declaration.workName, declaration.materialName)
        declarationsByKey.putIfAbsent(key, SummaryAccumulator(declaration))
    }

    val workRowsByKey = workVolumeRows
        .asSequence()
        .filter { row -> row.nodeCode.trim().uppercase() in normalizedNodeCodes }
        .groupBy { normalizeMatchText(it.workName) }

    declarationsByKey.forEach { (_, accumulator) ->
        val workKey = normalizeMatchText(accumulator.declaration.workName)
        val matchingRows = workRowsByKey[workKey].orEmpty()
        val plannedWorkQty = matchingRows.sumOf { it.plannedQty.toDouble() }.toFloat()
        accumulator.planned = plannedWorkQty * accumulator.declaration.ratio
    }

    materialHandovers
        .asSequence()
        .filter { it.nodeCode.trim().uppercase() in normalizedNodeCodes }
        .forEach { handover ->
            val workName = resolveHandoverWorkName(handover)
            val materialName = resolveHandoverMaterialName(handover)
            if (workName.isBlank() || materialName.isBlank()) return@forEach
            val key = materialProjectSummaryKey(workName, materialName)
            declarationsByKey[key]?.let { summary ->
                summary.delivered += handover.quantity
            }
        }

    return declarationsByKey.values
        .map { summary ->
            summary.declaration to MaterialBalanceSummary(
                planned = summary.planned,
                delivered = summary.delivered,
                remaining = (summary.planned - summary.delivered).coerceAtLeast(0f)
            )
        }
        .filter { it.second.planned > 0f || it.second.delivered > 0f }
        .sortedWith(
            compareBy<Pair<MaterialDeclaration, MaterialBalanceSummary>> {
                normalizeMatchText(it.first.materialName)
            }.thenBy {
                normalizeMatchText(it.first.workName)
            }
        )
}

internal fun materialProjectSummaryKey(workName: String, materialName: String): String {
    return "${normalizeMatchText(workName)}|${normalizeMatchText(materialName)}"
}

internal fun calculateMaterialBalance(
    declaration: MaterialDeclaration,
    workVolumeRows: List<WorkVolumeProgress>,
    materialHandovers: List<MaterialHandover>,
    nodeCodes: Set<String>
): MaterialBalanceSummary {
    val normalizedNodeCodes = nodeCodes.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    val planned = workVolumeRows.fold(0f) { sum, row ->
        if (normalizeMatchText(row.workName) == normalizeMatchText(declaration.workName) && row.nodeCode.trim().uppercase() in normalizedNodeCodes) {
            sum + (row.plannedQty * declaration.ratio)
        } else {
            sum
        }
    }
    val delivered = materialHandovers
        .filter { handover ->
            val workName = resolveHandoverWorkName(handover)
            val materialName = resolveHandoverMaterialName(handover)
            normalizeMatchText(workName) == normalizeMatchText(declaration.workName) &&
                    normalizeMatchText(materialName) == normalizeMatchText(declaration.materialName) &&
                    handover.nodeCode.trim().uppercase() in normalizedNodeCodes
        }
        .sumOf { it.quantity.toDouble() }
        .toFloat()
    return MaterialBalanceSummary(
        planned = planned,
        delivered = delivered,
        remaining = (planned - delivered).coerceAtLeast(0f)
    )
}

internal fun resolveHandoverWorkName(handover: MaterialHandover): String {
    val cleanWorkName = handover.workName.trim()
    val parts = cleanWorkName.split(":", limit = 2)
    if (parts.size >= 2) return parts.firstOrNull()?.trim().orEmpty()
    return cleanWorkName
}

internal fun resolveHandoverMaterialName(handover: MaterialHandover): String {
    val materialName = handover.materialName.trim()
    if (materialName.isNotBlank()) return materialName
    val parts = handover.workName.split(":", limit = 2)
    return if (parts.size >= 2) parts[1].trim() else handover.workName.trim()
}

internal fun extractMaterialName(workName: String): String {
    val parts = workName.split(":")
    return if (parts.size >= 2) parts[1].trim() else workName.trim()
}

internal fun buildHandoverNote(receiver: String, note: String): String {
    val trimmedReceiver = receiver.trim()
    val trimmedNote = note.trim()
    return listOfNotNull(
        trimmedReceiver.takeIf { it.isNotBlank() }?.let { "receiver=$it" },
        trimmedNote.takeIf { it.isNotBlank() }?.let { "note=$it" }
    ).joinToString("\n")
}

internal fun extractHandoverReceiver(note: String): String {
    return note.lineSequence()
        .firstOrNull { it.startsWith("receiver=") }
        ?.removePrefix("receiver=")
        ?.trim()
        .orEmpty()
}

internal fun extractHandoverNoteBody(note: String): String {
    val extracted = note.lineSequence()
        .firstOrNull { it.startsWith("note=") }
        ?.removePrefix("note=")
        ?.trim()
        .orEmpty()
    return if (extracted.isNotBlank()) extracted else if (note.contains("=")) "" else note.trim()
}

internal fun resolveHandoverReceiver(handover: MaterialHandover): String {
    return handover.receiver.trim().ifBlank { extractHandoverReceiver(handover.note) }
}

internal fun resolveHandoverNoteBody(handover: MaterialHandover): String {
    return if (handover.receiver.isNotBlank()) handover.note.trim() else extractHandoverNoteBody(handover.note)
}

private fun formatQty(qty: Float): String {
    return if (qty == qty.toLong().toFloat()) qty.toLong().toString() else String.format(Locale.US, "%.1f", qty)
}

private fun formatEpochDay(epochDay: Long): String {
    val date = Date(epochDay * 24 * 60 * 60 * 1000)
    return SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(date)
}

private fun formatMonthKey(epochDay: Long): String {
    val date = Date(epochDay * 24 * 60 * 60 * 1000)
    return SimpleDateFormat("MM/yyyy", Locale("vi", "VN")).format(date)
}

private fun monthKeyToEpochMillis(monthKey: String): Long {
    return runCatching {
        SimpleDateFormat("MM/yyyy", Locale("vi", "VN")).parse(monthKey)?.time ?: 0L
    }.getOrDefault(0L)
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

