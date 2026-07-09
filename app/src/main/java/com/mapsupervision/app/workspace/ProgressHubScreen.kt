package com.mapsupervision.app.workspace

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.ui.window.DialogProperties
import com.mapsupervision.core.ui.theme.extendedColors
import com.mapsupervision.core.ui.theme.SecondaryMint
import com.mapsupervision.core.ui.theme.PrimaryPeach
import com.mapsupervision.core.ui.components.GlassmorphicCard
import com.mapsupervision.domain.model.Task
import com.mapsupervision.domain.model.WorkPlan
import com.mapsupervision.domain.model.resolveEpochDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressHubScreen(
    activeProjectId: String?,
    constructionProgress: List<com.mapsupervision.domain.model.NodeProgress>,
    dailyLogs: List<com.mapsupervision.domain.model.DailyLog>,
    workPlans: List<WorkPlan>,
    dashboardState: com.mapsupervision.app.workspace.DashboardState,
    progressUiState: ProgressUiState,
    screenUiState: ProgressHubScreenUiState,
    workCategories: List<com.mapsupervision.domain.model.WorkCategory>,
    projectTasks: List<Task>,
    photos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    activeProjectName: String? = null,
    onSetSubTab: (ProgressHubSubTab) -> Unit,
    onUpdateGroupMode: (String) -> Unit,
    onUpdateFilterMode: (String) -> Unit,
    onSelectProgressNode: (String, String, String) -> Unit,
    onDismissProgressNodeSheet: () -> Unit,
    onUpdateCurrentMonth: (Int) -> Unit,
    onUpdateCurrentYear: (Int) -> Unit,
    onUpdateSelectedDateMillis: (Long) -> Unit,
    onUpdateWeatherSelection: (String) -> Unit,
    onUpdateCustomWeather: (String) -> Unit,
    onUpdateTemperatureInput: (String) -> Unit,
    onUpdateSelectedNodeCodeForLog: (String?) -> Unit,
    onUpdateSelectedRouteCodeForLog: (String?) -> Unit,
    onUpdateManpowerInput: (String) -> Unit,
    onUpdateWorkItemInput: (String) -> Unit,
    onUpdateNoteInput: (String) -> Unit,
    onUpdateActualProgressInput: (String) -> Unit,
    onUpdateActualProgressChecked: (Boolean) -> Unit,
    onUpdateLogFormError: (String) -> Unit,
    onSetNodeDropdownExpanded: (Boolean) -> Unit,
    onSetRouteDropdownExpanded: (Boolean) -> Unit,
    onUpdateVolumeInput: (String) -> Unit,
    onUpdateUnitInput: (String) -> Unit,
    onAddActualLine: () -> Unit,
    onRemoveActualLine: (String) -> Unit,
    onUpdateActualLineWorkName: (String, String) -> Unit,
    onUpdateActualLineCategoryName: (String, String) -> Unit,
    onUpdateActualLineQuantityInput: (String, String) -> Unit,
    onUpdateActualLineUnit: (String, String) -> Unit,
    onSelectWorkTemplate: (String, String) -> Unit,
    onUpdateSelectedCategoryName: (String) -> Unit,
    onSetCategoryDropdownExpanded: (Boolean) -> Unit,
    onSetShowAddCategoryDialog: (Boolean) -> Unit,
    onUpdateNewCategoryName: (String) -> Unit,
    onUpdateNewCategoryUnit: (String) -> Unit,
    onUpdateProgressSheetPlannedInput: (String) -> Unit,
    onUpdateProgressSheetActualInput: (String) -> Unit,
    onUpdateProgressSheetValidationError: (String) -> Unit,
    onUpdateProgressSheetNote: (String) -> Unit,
    onResetLogForm: () -> Unit,
    onAddConstruction: (String, Float, Float) -> Unit,
    onAddDailyLog: (AddDailyLogRequest) -> Unit,
    onExportDailyLogs: (ExportDailyLogRequest) -> Unit,
    onAddWorkCategory: (String, String) -> Unit,
    onEditDailyLog: (com.mapsupervision.domain.model.DailyLog) -> Unit,
    onSetShowExportDialog: (Boolean) -> Unit,
    onUpdateExportScope: (DailyLogExportScope) -> Unit,
    onUpdateExportStartDateMillis: (Long) -> Unit,
    onUpdateExportEndDateMillis: (Long) -> Unit,
    onUpdateExportFormat: (DailyLogExportFormat) -> Unit,
    onUpdateIncludePlanInExport: (Boolean) -> Unit,
    onUpdateExportValidationError: (String) -> Unit,

    // Plan Actions
    onAddWorkPlanBatch: suspend (String, List<String>, List<String>, Double, String, String, Long, String?) -> Boolean,
    onUpdateSelectedPlanWorkName: (String) -> Unit,
    onUpdatePlanUnitInput: (String) -> Unit,
    onUpdatePlanQuantityInput: (String) -> Unit,
    onUpdatePlanNoteInput: (String) -> Unit,
    onAddSelectedPlanNodeCode: (String) -> Unit,
    onRemoveSelectedPlanNodeCode: (String) -> Unit,
    onAddSelectedPlanRouteCode: (String) -> Unit,
    onRemoveSelectedPlanRouteCode: (String) -> Unit,
    onSetPlanNodeDropdownExpanded: (Boolean) -> Unit,
    onSetPlanRouteDropdownExpanded: (Boolean) -> Unit,
    onSetPlanWorkDropdownExpanded: (Boolean) -> Unit,
    onSetShowAddPlanWorkDialog: (Boolean) -> Unit,
    onUpdateNewPlanWorkName: (String) -> Unit,
    onUpdateNewPlanWorkUnit: (String) -> Unit,
    onUpdatePlanFormError: (String) -> Unit,
    onSelectPlanWorkTemplate: (String, String, String?) -> Unit,
    onApplyPlanToLog: (WorkPlan) -> Unit,
    onResetPlanForm: () -> Unit
) {
    val selectedNodeCodeForProgress = screenUiState.selectedNodeCodeForProgress
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showExportStartDatePicker by remember { mutableStateOf(false) }
    var showExportEndDatePicker by remember { mutableStateOf(false) }

    // Calendar & Diary state
    val todayCal = remember { Calendar.getInstance() }
    val currentMonth = screenUiState.currentMonth
    val currentYear = screenUiState.currentYear
    val selectedDateMillis = screenUiState.selectedDateMillis
    val selectedDate = remember(selectedDateMillis) {
        Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    }

    // Weather Form State
    val weatherSelected = screenUiState.weatherSelected
    val customWeather = screenUiState.customWeather
    val temperatureInput = screenUiState.temperatureInput

    // Daily Log Form State
    val selectedNodeCodeForLog = screenUiState.selectedNodeCodeForLog
    val selectedRouteCodeForLog = screenUiState.selectedRouteCodeForLog
    val nodeDropdownExpanded = screenUiState.nodeDropdownExpanded
    val routeDropdownExpanded = screenUiState.routeDropdownExpanded
    
    val categoryDropdownExpanded = screenUiState.categoryDropdownExpanded

    val nonStructuralNodes = progressUiState.nonStructuralNodes
    val nodesMap = progressUiState.nodesByCode
    val activeNodeCodes = progressUiState.activeNodeCodes
    val routeSelectorOptions = progressUiState.routeSelectorOptions
    val criticalNodes = progressUiState.criticalNodes
    val progressByCode = progressUiState.progressByNodeCode
    val allDisplayItems = progressUiState.allDisplayItems
    val allNodeCodes = remember(progressUiState.nonStructuralNodes) { progressUiState.nonStructuralNodes.map { it.code }.filter { it.isNotBlank() } }
    val selectedNodeForProgress = remember(selectedNodeCodeForProgress, progressByCode, activeProjectId) {
        selectedNodeCodeForProgress?.let { nodeCode ->
            progressByCode[nodeCode]
                ?: com.mapsupervision.domain.model.NodeProgress(
                    id = "",
                    projectId = activeProjectId ?: "",
                    nodeCode = nodeCode,
                    planned = 0f,
                    actual = 0f,
                    remain = 0f,
                    delayed = false,
                    updatedAtEpochMs = 0L
                )
        }
    }

    val filteredProgress = remember(allDisplayItems, screenUiState.filterMode) {
        allDisplayItems.filter {
            when (screenUiState.filterMode) {
                "Delayed" -> it.delayed
                "On-time" -> !it.delayed && it.updatedAtEpochMs > 0L
                else -> true
            }
        }
    }

    val groupedProgress = remember(filteredProgress, screenUiState.groupMode, nodesMap) {
        when (screenUiState.groupMode) {
            "Nhà thầu" -> filteredProgress.groupBy { nodesMap[it.nodeCode]?.contractor?.takeIf { c -> c.isNotBlank() } ?: "Chưa phân công" }
            "Vị trí" -> filteredProgress.groupBy { it.nodeCode }
            else -> mapOf("Tất cả hạng mục" to filteredProgress)
        }
    }

    // Task 2: summary stats
    val totalNodes = nonStructuralNodes.size
    val updatedNodes = remember(constructionProgress, activeNodeCodes) {
        constructionProgress.count { it.updatedAtEpochMs > 0L && it.nodeCode.trim().uppercase() in activeNodeCodes }
    }
    val onTrackNodes = remember(constructionProgress, activeNodeCodes) {
        constructionProgress.count { !it.delayed && it.updatedAtEpochMs > 0L && it.nodeCode.trim().uppercase() in activeNodeCodes }
    }
    val delayedNodes = dashboardState.delayedCount
    
    // Average planned/actual assuming out of 100 for percentage
    val avgStats = remember(constructionProgress, activeNodeCodes) {
        val activeProgress = constructionProgress.filter { it.nodeCode.trim().uppercase() in activeNodeCodes }
        val avgPlanned = if (activeProgress.isEmpty()) 0f else activeProgress.map { it.planned }.average().toFloat()
        val avgActual = if (activeProgress.isEmpty()) 0f else activeProgress.map { it.actual }.average().toFloat()
        val planPercent = (avgPlanned / 100f).coerceIn(0f, 1f)
        val actualPercent = (avgActual / 100f).coerceIn(0f, 1f)
        Triple(avgPlanned, avgActual, planPercent to actualPercent)
    }
    val avgPlanned = avgStats.first
    val avgActual = avgStats.second
    val planPercent = avgStats.third.first
    val actualPercent = avgStats.third.second

    // Colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val extendedColors = MaterialTheme.extendedColors
    val orangeColor = extendedColors.warning
    val darkOrangeColor = extendedColors.mapAccent
    val redColor = extendedColors.danger
    val lightRedBg = extendedColors.dangerSoft
    val darkBlueColor = MaterialTheme.colorScheme.primary
    val lightBlueColor = extendedColors.info
    val successColor = extendedColors.success
    val trackBlueColor = MaterialTheme.colorScheme.outline

    // Calendar computing helpers
    val monthCalendar = remember(currentMonth, currentYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val emptyCellsBefore = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    val calendarCells = remember(currentMonth, currentYear, emptyCellsBefore, daysInMonth) {
        buildList<Calendar?> {
            repeat(emptyCellsBefore) { add(null) }
            for (day in 1..daysInMonth) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                add(cal)
            }
        }
    }

    fun getEpochDay(cal: Calendar): Long {
        return java.time.LocalDate.of(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        ).toEpochDay()
    }

    val selectedEpoch = remember(selectedDate) { getEpochDay(selectedDate) }

    fun hasLogsOnDay(dayCal: Calendar): Boolean {
        val dayEpoch = getEpochDay(dayCal)
        return dayEpoch in progressUiState.logEpochDays
    }

    val diarySnapshot = remember(dailyLogs, photos, selectedEpoch) {
        val filteredLogs = dailyLogs.filter { log -> log.resolveEpochDay() == selectedEpoch }
            .sortedByDescending { it.createdAtEpochMs }

        val filteredPhotos = photos.filter { photo ->
            val photoCal = Calendar.getInstance().apply { timeInMillis = photo.capturedAtEpochMs }
            val photoEpoch = java.time.LocalDate.of(
                photoCal.get(Calendar.YEAR),
                photoCal.get(Calendar.MONTH) + 1,
                photoCal.get(Calendar.DAY_OF_MONTH)
            ).toEpochDay()
            photoEpoch == selectedEpoch
        }

        val matched = filteredPhotos.filter { it.matchedNodeCode != null || it.matchedRouteCode != null || it.tagCodesCsv.isNotBlank() }
        val unmatched = filteredPhotos.filterNot { it.matchedNodeCode != null || it.matchedRouteCode != null || it.tagCodesCsv.isNotBlank() }

        val totalManpower = filteredLogs.sumOf { it.manpower }
        val locations = filteredLogs.mapNotNull { it.nodeCode }.distinct()
        val volumesByCategory = filteredLogs
            .filter { it.categoryName.isNotBlank() && it.volume > 0.0 }
            .groupBy { it.categoryName }
            .mapValues { entry ->
                val sum = entry.value.sumOf { it.volume }
                val unit = entry.value.firstOrNull()?.unit.orEmpty()
                Pair(sum, unit)
            }

        DiarySelectedDateSnapshot(
            logs = filteredLogs,
            matchedPhotos = matched,
            unmatchedPhotos = unmatched,
            photos = filteredPhotos,
            totalManpower = totalManpower,
            locations = locations,
            volumesByCategory = volumesByCategory
        )
    }

    val logsForSelectedDate = diarySnapshot.logs
    val matchedPhotos = diarySnapshot.matchedPhotos
    val unmatchedPhotos = diarySnapshot.unmatchedPhotos
    val photosForSelectedDate = diarySnapshot.photos


    val selectedTemplateUnit = remember(
        screenUiState.selectedCategoryName,
        workCategories,
        progressUiState.workVolumeRows
    ) {
        resolveWorkTemplateUnit(
            screenUiState.selectedCategoryName,
            workCategories,
            progressUiState.workVolumeRows
        )
    }
    val isUnitReadOnly = selectedTemplateUnit.isNotBlank()

    val plansForDate = remember(workPlans, selectedEpoch) {
        workPlans.filter { it.plannedDateEpochDay == selectedEpoch }
    }
    val groupedPlans = remember(plansForDate) {
        plansForDate.groupBy { it.batchGroupId.ifBlank { it.id } }
    }

    Scaffold( snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        LazyColumn( modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(innerPadding)
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (activeProjectId == null) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text( "Vui lòng tạo hoặc chọn một dự án để tiếp tục", color = primaryTextColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text( text = "Tiến độ & Nhật ký", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                        )
                        Text( text = activeProjectName ?: "Dự án hiện tại", fontSize = 13.sp, color = secondaryTextColor
                        )
                    }
                    OutlinedButton( onClick = {
                            onSetShowExportDialog(true)
                        }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), enabled = !screenUiState.isExporting
                    ) {
                        Icon( imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryTextColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xuất nhật ký", color = primaryTextColor, fontSize = 14.sp)
                    }
                }
            }

            // Tab Selector
            item {
                Row( modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isProgressSelected = screenUiState.subTab == ProgressHubSubTab.PROGRESS
                    val isPlanSelected = screenUiState.subTab == ProgressHubSubTab.PLAN
                    val isDiarySelected = screenUiState.subTab == ProgressHubSubTab.DIARY

                    Button( onClick = { onSetSubTab(ProgressHubSubTab.PROGRESS) }, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors( containerColor = if (isProgressSelected) MaterialTheme.colorScheme.surface else Color.Transparent, contentColor = if (isProgressSelected) primaryTextColor else secondaryTextColor
                        ), elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isProgressSelected) 2.dp else 0.dp), shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tiến độ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button( onClick = { onSetSubTab(ProgressHubSubTab.PLAN) }, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors( containerColor = if (isPlanSelected) MaterialTheme.colorScheme.surface else Color.Transparent, contentColor = if (isPlanSelected) primaryTextColor else secondaryTextColor
                        ), elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isPlanSelected) 2.dp else 0.dp), shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kế hoạch", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button( onClick = { onSetSubTab(ProgressHubSubTab.DIARY) }, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors( containerColor = if (isDiarySelected) MaterialTheme.colorScheme.surface else Color.Transparent, contentColor = if (isDiarySelected) primaryTextColor else secondaryTextColor
                        ), elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isDiarySelected) 2.dp else 0.dp), shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nhật ký", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            fun hasPlansOnDay(dayCal: Calendar): Boolean {
                val dayEpoch = getEpochDay(dayCal)
                return workPlans.any { it.plannedDateEpochDay == dayEpoch }
            }

            if (screenUiState.subTab == ProgressHubSubTab.PROGRESS) {
                // ========================== PROGRESS VIEW ==========================
                item {
                    GlassmorphicCard( modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    ) {
                        val statusColor = if (dashboardState.delayedCount > 0) orangeColor else successColor
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(statusColor))
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text( text = "TRẠNG THÁI TIẾN ĐỘ DỰ ÁN", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp
                                    )
                                    if (dashboardState.delayedCount > 0) {
                                        Row( modifier = Modifier
                                                .background(lightRedBg, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = redColor, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${dashboardState.delayedCount} Chậm hạn", color = redColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Row( modifier = Modifier
                                                .background(successColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successColor, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Đúng tiến độ", color = successColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Planned
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Benchmark kế hoạch", color = secondaryTextColor, fontSize = 14.sp)
                                    Text("${String.format("%.1f", avgPlanned)}%", color = secondaryTextColor, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator( progress = { planPercent }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = SecondaryMint, trackColor = MaterialTheme.colorScheme.surface
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Actual
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Hoàn thành thực tế", color = PrimaryPeach, fontSize = 14.sp)
                                    Text("${String.format("%.1f", avgActual)}%", color = PrimaryPeach, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator( progress = { actualPercent }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = PrimaryPeach, trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                }

                item {
                    SummaryStatsRow( totalNodes = totalNodes, updatedNodes = updatedNodes, onTrackNodes = onTrackNodes, delayedNodes = delayedNodes, primaryTextColor = primaryTextColor, secondaryTextColor = secondaryTextColor, successColor = successColor, orangeColor = orangeColor
                    )
                }

                item {
                    GlassmorphicCard( shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text( "CRITICAL PATH VARIANCES (ĐỘ LỆCH TIẾN ĐỘ)", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 12.dp)
                            )
                            if (criticalNodes.isEmpty()) {
                                Text( "Chưa có dữ liệu trễ hạn", color = secondaryTextColor, fontSize = 14.sp
                                )
                            } else {
                                criticalNodes.forEachIndexed { index, node ->
                                    val variance = node.planned - node.actual
                                    val days = estimatedDelayDays(variance)
                                    val label = nodeDisplayName(node.nodeCode, nodesMap)
                                    Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(label, color = primaryTextColor, fontSize = 14.sp)
                                        Text("-$days Ngày", color = darkOrangeColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (index < criticalNodes.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Danh Sách\nHạng Mục",
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                fontSize = 20.sp,
                                lineHeight = 24.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip( selected = screenUiState.filterMode == "All", onClick = { onUpdateFilterMode("All") }, label = { Text("Tất cả", color = if (screenUiState.filterMode == "All") MaterialTheme.colorScheme.onPrimary else primaryTextColor) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = trackBlueColor, containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)
                                 )
                                FilterChip( selected = screenUiState.filterMode == "Delayed", onClick = { onUpdateFilterMode("Delayed") }, label = { Text("Chậm trễ", color = if (screenUiState.filterMode == "Delayed") MaterialTheme.colorScheme.onPrimary else primaryTextColor) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = orangeColor, containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)
                                 )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nhóm theo: ", color = secondaryTextColor, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            listOf("Không nhóm", "Nhà thầu", "Vị trí").forEach { mode ->
                                FilterChip( selected = screenUiState.groupMode == mode, onClick = { onUpdateGroupMode(mode) }, label = { Text(mode, fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp), shape = RoundedCornerShape(16.dp)
                                 )
                            }
                        }
                    }
                }

                if (nonStructuralNodes.isEmpty()) {
                    items(5) { index ->
                        ShimmerItem( modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .padding(vertical = 4.dp)
                        )
                    }
                } else {
                    groupedProgress.forEach { (groupName, items) ->
                        item(key = "group_header_$groupName") {
                            Text( text = groupName.uppercase(), fontWeight = FontWeight.Bold, color = secondaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        
                        if (items.isEmpty()) {
                            item(key = "group_empty_$groupName") {
                                Text("Không có dữ liệu", color = secondaryTextColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        } else {
                            items( items = items, key = { it.nodeCode }
                            ) { progress ->
                                val pPlan = remember(progress.planned) { (progress.planned / 100f).coerceIn(0f, 1f) }
                                val pActual = remember(progress.actual) { (progress.actual / 100f).coerceIn(0f, 1f) }
                                InfrastructureItem( title = nodeDisplayName(progress.nodeCode, nodesMap), subtitle = progress.nodeCode, id = progress.id.take(8).uppercase(), supervisor = nodesMap[progress.nodeCode]?.contractor ?: "Chưa phân thầu", planProgress = pPlan, actualProgress = pActual, borderColor = if (progress.delayed) darkOrangeColor else successColor, planColor = if (progress.delayed) darkBlueColor else trackBlueColor, actualColor = if (progress.delayed) darkOrangeColor else lightBlueColor, isWarning = progress.delayed, isNew = progress.id.isBlank(), onClick = {
                                        val existing = progressByCode[progress.nodeCode]
                                            ?: com.mapsupervision.domain.model.NodeProgress( id = "", projectId = activeProjectId ?: "", nodeCode = progress.nodeCode, planned = 0f, actual = 0f, remain = 0f, delayed = false, updatedAtEpochMs = 0L
                                            )
                                        onSelectProgressNode(existing.nodeCode, existing.planned.toString(), existing.actual.toString())
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (screenUiState.subTab == ProgressHubSubTab.PLAN) {
                // ========================== PLAN VIEW ==========================
                item {
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text( text = "Lịch tháng ${currentMonth + 1}, $currentYear", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                )
                                Row {
                                    IconButton(onClick = {
                                        if (currentMonth == 0) {
                                            onUpdateCurrentMonth(11)
                                            onUpdateCurrentYear(currentYear - 1)
                                        } else {
                                            onUpdateCurrentMonth(currentMonth - 1)
                                        }
                                    }) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước")
                                    }
                                    IconButton(onClick = {
                                        if (currentMonth == 11) {
                                            onUpdateCurrentMonth(0)
                                            onUpdateCurrentYear(currentYear + 1)
                                        } else {
                                            onUpdateCurrentMonth(currentMonth + 1)
                                        }
                                    }) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { dayName ->
                                    Text( text = dayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val chunks = calendarCells.chunked(7)
                            chunks.forEach { rowDays ->
                                Row( modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    rowDays.forEach { cellCal ->
                                        if (cellCal == null) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        } else {
                                            val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                                    cellCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                                    cellCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                                            
                                            val isSelected = getEpochDay(cellCal) == selectedEpoch
                                            val hasLogs = hasLogsOnDay(cellCal)
                                            val hasPlans = hasPlansOnDay(cellCal)

                                            Box( modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(CircleShape)
                                                    .background( when {
                                                            isSelected -> orangeColor
                                                            isToday -> orangeColor.copy(alpha = 0.2f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .clickable { onUpdateSelectedDateMillis(cellCal.timeInMillis) }
                                                    .padding(4.dp), contentAlignment = Alignment.Center
                                            ) {
                                                Column( horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text( text = cellCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 14.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal, color = when {
                                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                                            isToday -> orangeColor
                                                            else -> primaryTextColor
                                                        }
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        if (hasLogs) {
                                                            Box( modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else successColor)
                                                            )
                                                        }
                                                        if (hasPlans) {
                                                            if (hasLogs) Spacer(modifier = Modifier.width(2.dp))
                                                            Box( modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (rowDays.size < 7) {
                                        repeat(7 - rowDays.size) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time)
                    val selectedPlanTemplateUnit = remember(
                        screenUiState.selectedPlanWorkName,
                        workCategories,
                        progressUiState.workVolumeRows
                    ) {
                        resolveWorkTemplateUnit(
                            screenUiState.selectedPlanWorkName,
                            workCategories,
                            progressUiState.workVolumeRows
                        )
                    }
                    val isPlanUnitReadOnly = selectedPlanTemplateUnit.isNotBlank()

                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text( text = "Lập kế hoạch: $dateFormatted", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("CÔNG VIỆC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton( onClick = { onSetPlanWorkDropdownExpanded(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = screenUiState.selectedPlanWorkName.ifBlank { "Chọn hoặc thêm công việc..." },
                                        color = if (screenUiState.selectedPlanWorkName.isNotBlank()) primaryTextColor else secondaryTextColor,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = screenUiState.planWorkDropdownExpanded,
                                    onDismissRequest = { onSetPlanWorkDropdownExpanded(false) },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Thêm công việc mới...", fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        onClick = {
                                            onSetPlanWorkDropdownExpanded(false)
                                            onSetShowAddPlanWorkDialog(true)
                                        }
                                    )
                                    val materialOptions = progressUiState.templateOptions.filter {
                                        it.source == com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_WORK_VOLUME
                                    }
                                    val generalWorkOptions = progressUiState.templateOptions.filter {
                                        it.source == com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_GENERAL
                                    }
                                    if (materialOptions.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Công việc / khối lượng công việc:",
                                                    color = secondaryTextColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {},
                                            enabled = false
                                        )
                                        materialOptions.forEach { option ->
                                            val displayUnit = if (option.unit.isNotBlank()) " (${option.unit})" else ""
                                            DropdownMenuItem(
                                                text = { Text("${option.name}$displayUnit", color = primaryTextColor) },
                                                onClick = {
                                                    onSelectPlanWorkTemplate(
                                                        option.name,
                                                        resolveWorkTemplateUnit(option.name, workCategories, progressUiState.workVolumeRows)
                                                            .ifBlank { option.unit },
                                                        null
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    if (generalWorkOptions.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "Hạng mục công việc chung:",
                                                    color = secondaryTextColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {},
                                            enabled = false
                                        )
                                        generalWorkOptions.forEach { option ->
                                            val displayUnit = if (option.unit.isNotBlank()) " (${option.unit})" else ""
                                            DropdownMenuItem(
                                                text = { Text("${option.name}$displayUnit", color = primaryTextColor) },
                                                onClick = {
                                                    onSelectPlanWorkTemplate(
                                                        option.name,
                                                        resolveWorkTemplateUnit(option.name, workCategories, progressUiState.workVolumeRows)
                                                            .ifBlank { option.unit },
                                                        null
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = screenUiState.planQuantityInput,
                                    onValueChange = { onUpdatePlanQuantityInput(it) },
                                    label = { Text("Khối lượng") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = if (isPlanUnitReadOnly) selectedPlanTemplateUnit else screenUiState.planUnitInput,
                                    onValueChange = { if (!isPlanUnitReadOnly) onUpdatePlanUnitInput(it) },
                                    label = { Text("Đơn vị tính") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !isPlanUnitReadOnly,
                                    readOnly = isPlanUnitReadOnly
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("LIÊN KẾT VỊ TRÍ (ĐIỂM NÚT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onSetPlanNodeDropdownExpanded(true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Chọn vị trí...", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = secondaryTextColor)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = screenUiState.planNodeDropdownExpanded,
                                    onDismissRequest = { onSetPlanNodeDropdownExpanded(false) },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    progressUiState.nodeSelectorOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                onAddSelectedPlanNodeCode(option.key)
                                                onSetPlanNodeDropdownExpanded(false)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("LIÊN KẾT TUYẾN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onSetPlanRouteDropdownExpanded(true) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Chọn tuyến...", modifier = Modifier.weight(1f), textAlign = TextAlign.Start, color = secondaryTextColor)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = screenUiState.planRouteDropdownExpanded,
                                    onDismissRequest = { onSetPlanRouteDropdownExpanded(false) },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    routeSelectorOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.label) },
                                            onClick = {
                                                onAddSelectedPlanRouteCode(opt.key)
                                                onSetPlanRouteDropdownExpanded(false)
                                            }
                                        )
                                    }
                                }
                            }

                            if (screenUiState.selectedPlanNodeCodes.isNotEmpty() || screenUiState.selectedPlanRouteCodes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Đã chọn:", fontSize = 12.sp, color = secondaryTextColor)
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(screenUiState.selectedPlanNodeCodes) { code ->
                                            InputChip(
                                                selected = true,
                                                onClick = { onRemoveSelectedPlanNodeCode(code) },
                                                label = { Text(code, fontSize = 11.sp) },
                                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                            )
                                        }
                                        items(screenUiState.selectedPlanRouteCodes) { code ->
                                            val label = routeSelectorOptions.firstOrNull { it.key == code }?.label ?: code
                                            InputChip(
                                                selected = true,
                                                onClick = { onRemoveSelectedPlanRouteCode(code) },
                                                label = { Text(label, fontSize = 11.sp) },
                                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = screenUiState.planNoteInput,
                                onValueChange = { onUpdatePlanNoteInput(it) },
                                label = { Text("Ghi chú kế hoạch") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4
                            )

                            if (screenUiState.planFormError.isNotBlank()) {
                                Text(
                                    text = screenUiState.planFormError,
                                    color = redColor,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val qty = screenUiState.planQuantityInput.toDoubleOrNull()
                                    if (screenUiState.selectedPlanWorkName.isBlank()) {
                                        onUpdatePlanFormError("Vui lòng chọn hoặc thêm công việc")
                                    } else if (qty == null || qty <= 0.0) {
                                        onUpdatePlanFormError("Khối lượng phải là số dương hợp lệ")
                                    } else if (
                                        screenUiState.selectedPlanNodeCodes.isEmpty() &&
                                        screenUiState.selectedPlanRouteCodes.isEmpty()
                                    ) {
                                        onUpdatePlanFormError("Vui lòng chọn ít nhất 1 vị trí hoặc tuyến")
                                    } else {
                                        coroutineScope.launch {
                                            val saved = onAddWorkPlanBatch(
                                                screenUiState.selectedPlanWorkName,
                                                screenUiState.selectedPlanNodeCodes,
                                                screenUiState.selectedPlanRouteCodes,
                                                qty,
                                                if (isPlanUnitReadOnly) selectedPlanTemplateUnit else screenUiState.planUnitInput,
                                                screenUiState.planNoteInput,
                                                selectedEpoch,
                                                screenUiState.selectedPlanTaskId
                                            )
                                            if (saved) {
                                                onResetPlanForm()
                                            } else if (screenUiState.planFormError.isBlank()) {
                                                onUpdatePlanFormError("Không lưu được kế hoạch, vui lòng thử lại")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Lưu Kế Hoạch")
                            }
                        }
                    }
                }

                item {
                    Text( "Kế hoạch đã lập trong ngày", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (groupedPlans.isEmpty()) {
                    item {
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Chưa có kế hoạch thi công nào được lập cho ngày này", color = secondaryTextColor, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    groupedPlans.forEach { (batchGroupId, plans) ->
                        val firstPlan = plans.first()
                        item(key = batchGroupId) {
                            Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = firstPlan.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = primaryTextColor
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Khối lượng: ${"%.2f".format(firstPlan.quantity)} ${firstPlan.unit}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = successColor
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Vị trí áp dụng:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Flexbox row using FlowRow style or a scrollable LazyRow for locations
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(plans) { plan ->
                                            if (!plan.nodeCode.isNullOrBlank()) {
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(nodeDisplayName(plan.nodeCode!!, nodesMap)) }
                                                )
                                            }
                                            if (!plan.routeCode.isNullOrBlank()) {
                                                val label = routeSelectorOptions.firstOrNull { it.key == plan.routeCode }?.label ?: plan.routeCode
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(label ?: "") }
                                                )
                                            }
                                        }
                                    }

                                    if (firstPlan.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box( modifier = Modifier
                                                 .fillMaxWidth()
                                                 .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                                 .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                                 .padding(10.dp)
                                         ) {
                                            Text(firstPlan.description, color = secondaryTextColor, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ========================== DIARY / CALENDAR VIEW ==========================
                
                item {
                    val context = LocalContext.current
                    var isSyncChecked by remember {
                        mutableStateOf(
                            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                .getBoolean("calendar_sync_enabled", false)
                        )
                    }
                    val launcher = rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                    ) { results ->
                        val granted = results.values.all { it }
                        if (granted) {
                            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("calendar_sync_enabled", true).apply()
                            isSyncChecked = true
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Đồng bộ lịch hệ thống", color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = isSyncChecked,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    launcher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                } else {
                                    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("calendar_sync_enabled", false).apply()
                                    isSyncChecked = false
                                }
                            }
                        )
                    }
                }

                // Month Header & Navigation
                item {
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text( text = "Lịch tháng ${currentMonth + 1}, $currentYear", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                )
                                Row {
                                    IconButton(onClick = {
                                        if (currentMonth == 0) {
                                            onUpdateCurrentMonth(11)
                                            onUpdateCurrentYear(currentYear - 1)
                                        } else {
                                            onUpdateCurrentMonth(currentMonth - 1)
                                        }
                                    }) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước")
                                    }
                                    IconButton(onClick = {
                                        if (currentMonth == 11) {
                                            onUpdateCurrentMonth(0)
                                            onUpdateCurrentYear(currentYear + 1)
                                        } else {
                                            onUpdateCurrentMonth(currentMonth + 1)
                                        }
                                    }) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Day Names Header
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { dayName ->
                                    Text( text = dayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Calendar Grid View (rendered as rows of 7)
                            val chunks = calendarCells.chunked(7)
                            chunks.forEach { rowDays ->
                                Row( modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    rowDays.forEach { cellCal ->
                                        if (cellCal == null) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        } else {
                                            val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                                    cellCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                                    cellCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                                            
                                            val isSelected = getEpochDay(cellCal) == selectedEpoch
                                            val hasLogs = hasLogsOnDay(cellCal)
                                            val hasPlans = hasPlansOnDay(cellCal)

                                            Box( modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(CircleShape)
                                                    .background( when {
                                                            isSelected -> orangeColor
                                                            isToday -> orangeColor.copy(alpha = 0.2f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .clickable { onUpdateSelectedDateMillis(cellCal.timeInMillis) }
                                                    .padding(4.dp), contentAlignment = Alignment.Center
                                            ) {
                                                Column( horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text( text = cellCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 14.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal, color = when {
                                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                                            isToday -> orangeColor
                                                            else -> primaryTextColor
                                                        }
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        if (hasLogs) {
                                                            Box( modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else successColor)
                                                            )
                                                        }
                                                        if (hasPlans) {
                                                            if (hasLogs) Spacer(modifier = Modifier.width(2.dp))
                                                            Box( modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // If last row has less than 7 elements, pad it
                                    if (rowDays.size < 7) {
                                        repeat(7 - rowDays.size) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected Day Info & Weather Widget
                item {
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time)
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text( text = "Thông tin ngày: $dateFormatted", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                )
                                val isSelectedToday = selectedEpoch == getEpochDay(todayCal)
                                if (isSelectedToday) {
                                    Text( "Hôm nay", color = orangeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier
                                            .background(orangeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Weather section
                            Text("THỜI TIẾT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Weather condition chips
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Nắng", "Mưa", "Nhiều mây", "Giông bão").forEach { cond ->
                                    val isSelected = weatherSelected == cond
                                    Box( modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) orangeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                            .border(1.dp, if (isSelected) orangeColor else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { onUpdateWeatherSelection(cond) }
                                            .padding(vertical = 8.dp), contentAlignment = Alignment.Center
                                    ) {
                                        Text( cond, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) orangeColor else primaryTextColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField( value = temperatureInput, onValueChange = { onUpdateTemperatureInput(it) }, label = { Text("Nhiệt độ (°C)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true
                                 )
                                OutlinedTextField( value = customWeather, onValueChange = { onUpdateCustomWeather(it) }, label = { Text("Mô tả chi tiết (nếu có)") }, modifier = Modifier.weight(2f), singleLine = true
                                 )
                            }
                        }
                    }
                }

                // Add log submission form for selected day
                item {
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text( text = "Điền nhật ký & tiến độ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (plansForDate.isNotEmpty()) {
                                Text("KẾ HOẠCH TRONG NGÀY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                                Spacer(modifier = Modifier.height(6.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(plansForDate, key = { it.id }) { plan ->
                                        FilterChip(
                                            selected = screenUiState.selectedPlanSnapshot?.linkedWorkPlanId == plan.id,
                                            onClick = { onApplyPlanToLog(plan) },
                                            label = {
                                                Text(
                                                    "${plan.title} (${String.format("%.2f", plan.quantity)} ${plan.unit})",
                                                    maxLines = 1
                                                )
                                            }
                                        )
                                    }
                                }
                                screenUiState.selectedPlanSnapshot?.let { snapshot ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Kế hoạch gốc", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(snapshot.plannedWorkName, color = primaryTextColor, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Khối lượng kế hoạch: ${String.format("%.2f", snapshot.plannedQuantity)} ${snapshot.plannedUnit}",
                                                color = successColor,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Associated node selector
                            Text("LIÊN KẾT VỊ TRÍ (ĐIỂM NÚT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton( onClick = { onSetNodeDropdownExpanded(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                                 ) {
                                    Text( text = selectedNodeCodeForLog?.let { code ->
                                            nodeDisplayName(code, nodesMap) + " ($code)"
                                         } ?: "Chọn vị trí liên kết (không bắt buộc)", color = if (selectedNodeCodeForLog != null) primaryTextColor else secondaryTextColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                     )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                }
                                DropdownMenu(
                                    expanded = nodeDropdownExpanded,
                                    onDismissRequest = { onSetNodeDropdownExpanded(false) },
                                    modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Không liên kết vị trí", color = secondaryTextColor) },
                                        onClick = {
                                            onUpdateSelectedNodeCodeForLog(null)
                                            onSetNodeDropdownExpanded(false)
                                        }
                                    )
                                    progressUiState.nodeSelectorOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label, color = primaryTextColor) },
                                            onClick = {
                                                onUpdateSelectedNodeCodeForLog(option.key)
                                                onSetNodeDropdownExpanded(false)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Associated route selector
                            Text("LIÊN KẾT TUYẾN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton( onClick = { onSetRouteDropdownExpanded(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                                 ) {
                                    Text( text = selectedRouteCodeForLog?.let { code ->
                                            val opt = routeSelectorOptions.find { it.key == code }
                                            opt?.label ?: code
                                         } ?: "Chọn tuyến liên kết (không bắt buộc)", color = if (selectedRouteCodeForLog != null) primaryTextColor else secondaryTextColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                     )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                }
                                DropdownMenu(
                                    expanded = routeDropdownExpanded,
                                    onDismissRequest = { onSetRouteDropdownExpanded(false) },
                                    modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Không liên kết tuyến", color = secondaryTextColor) },
                                        onClick = {
                                            onUpdateSelectedRouteCodeForLog(null)
                                            onSetRouteDropdownExpanded(false)
                                        }
                                    )
                                    routeSelectorOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label, color = primaryTextColor) },
                                            onClick = {
                                                onUpdateSelectedRouteCodeForLog(option.key)
                                                onSetRouteDropdownExpanded(false)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // General category work item selector
                            Text("DANH MỤC CÔNG VIỆC CHUNG (TÙY CHỌN)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton( onClick = { onSetCategoryDropdownExpanded(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                                     ) {
                                        Text(
                                            text = screenUiState.selectedCategoryName.ifBlank { "Chọn hạng mục mẫu" },
                                            color = if (screenUiState.selectedCategoryName.isNotBlank()) primaryTextColor else secondaryTextColor,
                                            modifier = Modifier.weight(1f),
                                            textAlign = TextAlign.Start,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                    }
                                    DropdownMenu( expanded = categoryDropdownExpanded, onDismissRequest = { onSetCategoryDropdownExpanded(false) }, modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surfaceVariant)
                                     ) {
                                        DropdownMenuItem( text = { Text("Không dùng mẫu", color = secondaryTextColor) }, onClick = {
                                                 onSelectWorkTemplate("", "")
                                                 onSetCategoryDropdownExpanded(false)
                                             }
                                         )

                                         val allMaterials = progressUiState.templateOptions.filter {
                                             it.source == com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_WORK_VOLUME
                                         }
                                         val materials = if (selectedNodeCodeForLog != null) {
                                             val parsedNames = progressUiState.materialOptionsByNodeCode[selectedNodeCodeForLog].orEmpty()
                                                 .map { it.key.lowercase().trim() }
                                                 .toSet()
                                             val dbNames = progressUiState.workVolumeRows
                                                 .filter { it.nodeCode.trim().lowercase() == selectedNodeCodeForLog.trim().lowercase() }
                                                 .map { it.workName.trim().lowercase() }
                                                 .toSet()
                                             allMaterials.filter { option ->
                                                 val optKey = option.name.trim().lowercase()
                                                 optKey in parsedNames || optKey in dbNames
                                             }
                                         } else {
                                             allMaterials
                                         }
                                         val generalWorks = progressUiState.templateOptions.filter {
                                             it.source == com.mapsupervision.domain.model.WORK_TEMPLATE_SOURCE_GENERAL
                                         }

                                         if (materials.isNotEmpty()) {
                                             DropdownMenuItem(
                                                 text = { Text("Công việc / khối lượng công việc:", color = secondaryTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                 onClick = {},
                                                 enabled = false
                                             )
                                             materials.forEach { option ->
                                                val displayUnit = if (option.unit.isNotBlank()) " (${option.unit})" else ""
                                                DropdownMenuItem(
                                                    text = { Text("${option.name}$displayUnit", color = primaryTextColor) },
                                                    onClick = {
                                                        onSelectWorkTemplate(
                                                            option.name,
                                                            resolveWorkTemplateUnit(option.name, workCategories, progressUiState.workVolumeRows)
                                                                .ifBlank { option.unit }
                                                        )
                                                        onSetCategoryDropdownExpanded(false)
                                                    }
                                                )
                                            }
                                         }

                                         if (generalWorks.isNotEmpty()) {
                                             DropdownMenuItem(
                                                 text = { Text("Hạng mục công việc chung:", color = secondaryTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                 onClick = {},
                                                 enabled = false
                                             )
                                             generalWorks.forEach { option ->
                                                val displayUnit = if (option.unit.isNotBlank()) " (${option.unit})" else ""
                                                DropdownMenuItem(
                                                    text = { Text("${option.name}$displayUnit", color = primaryTextColor) },
                                                    onClick = {
                                                        onSelectWorkTemplate(
                                                            option.name,
                                                            resolveWorkTemplateUnit(option.name, workCategories, progressUiState.workVolumeRows)
                                                                .ifBlank { option.unit }
                                                        )
                                                        onSetCategoryDropdownExpanded(false)
                                                    }
                                                )
                                            }
                                         }
                                    }
                                }
                                
                                IconButton(
                                    onClick = { onSetShowAddCategoryDialog(true) },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Thêm hạng mục mẫu", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField( value = screenUiState.workItemInput, onValueChange = { onUpdateWorkItemInput(it); onUpdateLogFormError("") }, label = { Text("Nội dung công việc *") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5
                             )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField( value = screenUiState.volumeInput, onValueChange = { onUpdateVolumeInput(it) }, label = { Text("Khối lượng công việc thực hiện lũy kế:") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                                 )
                                OutlinedTextField( value = screenUiState.unitInput, onValueChange = { onUpdateUnitInput(it) }, label = { Text("Đơn vị tính") }, modifier = Modifier.weight(1f), singleLine = true, enabled = !isUnitReadOnly
                                 )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField( value = screenUiState.manpowerInput, onValueChange = { onUpdateManpowerInput(it) }, label = { Text("Số nhân công: ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true
                                 )
                                
                                if (selectedNodeCodeForLog != null) {
                                    OutlinedTextField( value = screenUiState.actualProgressInput, onValueChange = { onUpdateActualProgressInput(it) }, label = { Text("Tiến độ thực tế (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                                     )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField( value = screenUiState.noteInput, onValueChange = { onUpdateNoteInput(it) }, label = { Text("Báo cáo khó khăn / ghi chú khác") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
                             )

                            if (screenUiState.logFormError.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(screenUiState.logFormError, color = redColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 if (screenUiState.editingDailyLogId != null) {
                                     OutlinedButton(
                                         onClick = {
                                             onResetLogForm()
                                             onSetSubTab(ProgressHubSubTab.DIARY)
                                         },
                                         modifier = Modifier.weight(1f).height(48.dp),
                                         shape = RoundedCornerShape(8.dp),
                                         border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                         colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                     ) {
                                         Text("Hủy", fontWeight = FontWeight.Bold)
                                     }
                                 }

                                 Button(
                                     onClick = {
                                         val workItem = screenUiState.workItemInput.trim()
                                         if (workItem.isBlank()) {
                                             onUpdateLogFormError("Vui lòng nhập nội dung công việc")
                                             return@Button
                                         }
                                         val manpower = screenUiState.manpowerInput.toIntOrNull()
                                         if (manpower == null || manpower < 0) {
                                             onUpdateLogFormError("Số nhân công phải là số nguyên không âm")
                                             return@Button
                                         }
                                         val progressPercent = if (selectedNodeCodeForLog != null && screenUiState.actualProgressInput.isNotBlank()) {
                                             val p = screenUiState.actualProgressInput.toFloatOrNull()
                                             if (p == null || p < 0f || p > 100f) {
                                                 onUpdateLogFormError("Tiến độ thực tế phải từ 0 đến 100")
                                                 return@Button
                                             }
                                             p
                                         } else null

                                         val volumeText = screenUiState.volumeInput.trim()
                                         val volume = if (volumeText.isNotBlank()) {
                                             val v = volumeText.toDoubleOrNull()
                                             if (v == null || v < 0.0) {
                                                 onUpdateLogFormError("Khối lượng công việc phải là số không âm")
                                                 return@Button
                                             }
                                             v
                                         } else 0.0

                                         val temp = screenUiState.temperatureInput.toDoubleOrNull() ?: 30.0
                                         val weatherDesc = customWeather.trim().ifBlank { weatherSelected }

                                         val wasEditing = screenUiState.editingDailyLogId != null

                                         onAddDailyLog(
                                             AddDailyLogRequest(
                                                 workItem = workItem,
                                                 manpower = manpower,
                                                 note = screenUiState.noteInput.trim(),
                                                 weather = weatherDesc,
                                                 temperature = temp,
                                                 nodeCode = selectedNodeCodeForLog,
                                                 routeCode = selectedRouteCodeForLog,
                                                 dateEpochDay = selectedEpoch,
                                                 planSnapshot = screenUiState.selectedPlanSnapshot,
                                                 actualLines = listOf(
                                                     DailyLogDraftLine(
                                                         id = screenUiState.editingDailyLogId ?: java.util.UUID.randomUUID().toString(),
                                                         workName = workItem,
                                                         categoryName = screenUiState.selectedCategoryName.trim(),
                                                         quantityInput = volume.toString(),
                                                         unit = screenUiState.unitInput.trim(),
                                                         nodeCode = selectedNodeCodeForLog,
                                                         routeCode = selectedRouteCodeForLog
                                                     )
                                                 ),
                                                 existingId = screenUiState.editingDailyLogId,
                                                 actualProgressPercent = progressPercent
                                             )
                                         )

                                         // Clear fields after saving
                                         onResetLogForm()
                                         if (wasEditing) {
                                             onSetSubTab(ProgressHubSubTab.DIARY)
                                         }
                                         coroutineScope.launch {
                                             snackbarHostState.showSnackbar(if (!wasEditing) "Đã thêm nhật ký thi công mới" else "Đã cập nhật nhật ký")
                                         }
                                     },
                                     modifier = Modifier.weight(1f).height(48.dp),
                                     shape = RoundedCornerShape(8.dp),
                                     colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = MaterialTheme.colorScheme.onPrimary)
                                 ) {
                                     Text(if (screenUiState.editingDailyLogId == null) "Lưu nhật ký & đồng bộ tiến độ" else "Cập nhật nhật ký", fontWeight = FontWeight.Bold)
                                 }
                             }
                        }
                    }
                }

                // Daily Logs list for selected day
                item {
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time)
                    Text( text = "NHẬT KÝ NGÀY $dateFormatted", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Ảnh đối chiếu", fontWeight = FontWeight.Bold, color = primaryTextColor)
                            Text("Khớp: ${matchedPhotos.size}", color = successColor)
                            Text("Chưa khớp: ${unmatchedPhotos.size}", color = redColor)
                        }
                    }
                }

                if (photos.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Ảnh nhật ký gần nhất", fontWeight = FontWeight.Bold, color = primaryTextColor)
                                photos.take(8).forEach { photo ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            PhotoStatusBadge(photo = photo)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "${photo.objectCode} - ${photo.filePath.substringAfterLast('/')}",
                                                fontSize = 12.sp,
                                                color = secondaryTextColor,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            "${photo.matchingTimeOffsetMs / 60000}m",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (photo.matchedNodeCode != null || photo.matchedRouteCode != null || photo.tagCodesCsv.isNotBlank()) successColor else redColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (logsForSelectedDate.isNotEmpty()) {
                    item {
                        val totalManpower = logsForSelectedDate.sumOf { it.manpower }
                        val locations = logsForSelectedDate.mapNotNull { it.nodeCode }.distinct()
                        val volumesByCategory = logsForSelectedDate
                            .filter { it.categoryName.isNotBlank() && it.volume > 0.0 }
                            .groupBy { it.categoryName }
                            .mapValues { entry ->
                                val sum = entry.value.sumOf { it.volume }
                                val unit = entry.value.firstOrNull()?.unit.orEmpty()
                                Pair(sum, unit)
                            }
                        
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, orangeColor.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text( "TỔNG HỢP NHẬT KÝ TRONG NGÀY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = orangeColor, letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Tổng nhân công", fontSize = 11.sp, color = secondaryTextColor)
                                        Text("$totalManpower người", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Vị trí thi công", fontSize = 11.sp, color = secondaryTextColor)
                                        Text( if (locations.isNotEmpty()) locations.joinToString(", ") else "Không liên kết", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                         )
                                    }
                                }
                                
                                if (volumesByCategory.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Khối lượng công việc thực hiện lũy kế:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    volumesByCategory.forEach { (category, value) ->
                                        Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(orangeColor))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(category, fontSize = 13.sp, color = primaryTextColor)
                                            }
                                            Text( "${"%.2f".format(value.first)} ${value.second}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = successColor
                                             )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (logsForSelectedDate.isEmpty()) {
                    item {
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Chưa có nhật ký hoạt động nào ghi nhận trong ngày này", color = secondaryTextColor, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    items(logsForSelectedDate, key = { it.id }) { log ->
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.createdAtEpochMs))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text( text = "Thời gian: $timeStr", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = secondaryTextColor
                                         )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { onEditDailyLog(log) }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = secondaryTextColor)
                                        }
                                    }
                                    if (log.weather.isNotBlank()) {
                                        Text( text = "${log.weather} - ${log.temperature.roundToInt()}°C", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = orangeColor, modifier = Modifier
                                                 .background(orangeColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                 .padding(horizontal = 8.dp, vertical = 2.dp)
                                         )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text( text = log.workItem, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryTextColor
                                 )
                                if (log.categoryName.isNotBlank() && log.volume > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text( text = "Hạng mục: ${log.categoryName} - Khối lượng: ${"%.2f".format(log.volume)} ${log.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = successColor
                                     )
                                }
                                if (!log.nodeCode.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Link, contentDescription = null, tint = successColor, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text( text = "Liên kết: ${nodeDisplayName(log.nodeCode!!, nodesMap)} (${log.nodeCode})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = successColor
                                         )
                                    }
                                }
                                if (!log.routeCode.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tuyến: ${progressUiState.routeSelectorOptions.firstOrNull { it.key == log.routeCode }?.label ?: log.routeCode}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = successColor
                                    )
                                }
                                if (log.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box( modifier = Modifier
                                             .fillMaxWidth()
                                             .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                             .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                             .padding(10.dp)
                                     ) {
                                        Text(log.note, color = secondaryTextColor, fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Số nhân công: ${log.manpower} người", color = secondaryTextColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (screenUiState.showExportDialog) {
            AlertDialog(
                onDismissRequest = { if (!screenUiState.isExporting) onSetShowExportDialog(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                modifier = Modifier
                    .fillMaxWidth(0.97f)
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Xuất nhật ký", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Phạm vi xuất", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = screenUiState.exportScope == DailyLogExportScope.ALL,
                                onClick = { onUpdateExportScope(DailyLogExportScope.ALL) },
                                label = { Text("Toàn bộ") }
                            )
                            FilterChip(
                                selected = screenUiState.exportScope == DailyLogExportScope.DATE_RANGE,
                                onClick = { onUpdateExportScope(DailyLogExportScope.DATE_RANGE) },
                                label = { Text("Từ ngày...đến ngày...") }
                            )
                        }

                        if (screenUiState.exportScope == DailyLogExportScope.DATE_RANGE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { showExportStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                                    Text("Từ: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(screenUiState.exportStartDateMillis))}")
                                }
                                OutlinedButton(onClick = { showExportEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                                    Text("Đến: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(screenUiState.exportEndDateMillis))}")
                                }
                            }
                        }

                        Text("Định dạng", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = screenUiState.exportFormat == DailyLogExportFormat.PDF,
                                onClick = { onUpdateExportFormat(DailyLogExportFormat.PDF) },
                                label = { Text("PDF") }
                            )
                            FilterChip(
                                selected = screenUiState.exportFormat == DailyLogExportFormat.DOCX,
                                onClick = { onUpdateExportFormat(DailyLogExportFormat.DOCX) },
                                label = { Text("Word") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kèm kế hoạch", fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = screenUiState.includePlanInExport,
                                onCheckedChange = { onUpdateIncludePlanInExport(it) }
                            )
                        }

                        if (screenUiState.isExporting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (screenUiState.exportValidationError.isNotBlank()) {
                            Text(
                                screenUiState.exportValidationError,
                                color = redColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val startEpochDay = java.time.Instant.ofEpochMilli(screenUiState.exportStartDateMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .toEpochDay()
                            val endEpochDay = java.time.Instant.ofEpochMilli(screenUiState.exportEndDateMillis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .toEpochDay()
                            if (screenUiState.exportScope == DailyLogExportScope.DATE_RANGE && startEpochDay > endEpochDay) {
                                onUpdateExportValidationError("Ngày bắt đầu không được lớn hơn ngày kết thúc")
                            } else {
                                onUpdateExportValidationError("")
                                onExportDailyLogs(
                                    ExportDailyLogRequest(
                                        projectId = activeProjectId ?: return@Button,
                                        scope = screenUiState.exportScope,
                                        startEpochDay = if (screenUiState.exportScope == DailyLogExportScope.DATE_RANGE) startEpochDay else null,
                                        endEpochDay = if (screenUiState.exportScope == DailyLogExportScope.DATE_RANGE) endEpochDay else null,
                                        format = screenUiState.exportFormat,
                                        includePlan = screenUiState.includePlanInExport
                                    )
                                )
                            }
                        },
                        enabled = !screenUiState.isExporting
                    ) {
                        Text("Xuất")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onSetShowExportDialog(false) }, enabled = !screenUiState.isExporting) {
                        Text("Hủy")
                    }
                }
            )
        }

        if (showExportStartDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = screenUiState.exportStartDateMillis
            )
            DatePickerDialog(
                onDismissRequest = { showExportStartDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        onUpdateExportStartDateMillis(datePickerState.selectedDateMillis ?: screenUiState.exportStartDateMillis)
                        showExportStartDatePicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportStartDatePicker = false }) {
                        Text("Hủy")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showExportEndDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = screenUiState.exportEndDateMillis
            )
            DatePickerDialog(
                onDismissRequest = { showExportEndDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        onUpdateExportEndDateMillis(datePickerState.selectedDateMillis ?: screenUiState.exportEndDateMillis)
                        showExportEndDatePicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportEndDatePicker = false }) {
                        Text("Hủy")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Add Construction progress Sheet (From Progress view item selection click)
        if (selectedNodeForProgress != null) {
            val node = selectedNodeForProgress
            val plannedInput = screenUiState.progressSheetPlannedInput.ifBlank { node.planned.toString() }
            val actualInput = screenUiState.progressSheetActualInput.ifBlank { node.actual.toString() }
            val validationError = screenUiState.progressSheetValidationError
            val progressNote = screenUiState.progressSheetNote

            ModalBottomSheet( onDismissRequest = { onDismissProgressNodeSheet() }, sheetState = bottomSheetState
             ) {
                Column( modifier = Modifier
                         .fillMaxWidth()
                         .padding(horizontal = 24.dp)
                         .padding(bottom = 32.dp)
                 ) {
                    Text( text = nodeDisplayName(node.nodeCode, nodesMap), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryTextColor
                     )
                    Text( text = "Mã: ${node.nodeCode}", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.padding(bottom = 8.dp)
                     )

                    if (node.updatedAtEpochMs > 0L) {
                        Text( "Cập nhật lần cuối: ${formatRelativeTime(node.updatedAtEpochMs)}", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.padding(bottom = 8.dp)
                         )
                    }

                    OutlinedTextField( value = plannedInput, onValueChange = { onUpdateProgressSheetPlannedInput(it); onUpdateProgressSheetValidationError("") }, label = { Text("Kế hoạch (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true
                     )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField( value = actualInput, onValueChange = { onUpdateProgressSheetActualInput(it); onUpdateProgressSheetValidationError("") }, label = { Text("Thực tế (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true
                     )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField( value = progressNote, onValueChange = { onUpdateProgressSheetNote(it) }, label = { Text("Ghi chú (tùy chọn)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
                     )

                    if (validationError.isNotBlank()) {
                        Text( text = validationError, color = redColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                         )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                     ) {
                        OutlinedButton( onClick = { onDismissProgressNodeSheet() }, modifier = Modifier.weight(1f)
                         ) {
                            Text("Hủy")
                        }
                        Button( onClick = {
                                val planned = plannedInput.toFloatOrNull()
                                val actual = actualInput.toFloatOrNull()
                                if (planned == null || actual == null || planned !in 0f..100f || actual !in 0f..100f) {
                                    onUpdateProgressSheetValidationError("Giá trị phải từ 0 đến 100")
                                } else {
                                    onAddConstruction(node.nodeCode, planned, actual)
                                    onDismissProgressNodeSheet()
                                }
                            }, modifier = Modifier.weight(1f)
                         ) {
                            Text("Lưu")
                        }
                    }
                }
            }
        }

        if (screenUiState.showAddCategoryDialog) {
            AlertDialog(
                onDismissRequest = { onSetShowAddCategoryDialog(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
                modifier = Modifier
                    .fillMaxWidth(0.97f)
                    .wrapContentHeight()
                    .navigationBarsPadding()
                    .imePadding(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("HẠNG MỤC CÔNG VIỆC", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                text = {
                     Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField( value = screenUiState.newCategoryName, onValueChange = { onUpdateNewCategoryName(it) }, label = { Text("Tên hạng mục (ví dụ: đổ bê tông)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                         )
                        OutlinedTextField( value = screenUiState.newCategoryUnit, onValueChange = { onUpdateNewCategoryUnit(it) }, label = { Text("Đơn vị tính (ví dụ: m3)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                         )
                    }
                }, confirmButton = {
                    Button( onClick = {
                             if (screenUiState.newCategoryName.isNotBlank() && screenUiState.newCategoryUnit.isNotBlank()) {
                                 onAddWorkCategory(screenUiState.newCategoryName.trim(), screenUiState.newCategoryUnit.trim())
                                 onUpdateSelectedCategoryName(screenUiState.newCategoryName.trim())
                                 onUpdateUnitInput(screenUiState.newCategoryUnit.trim())
                                 onUpdateNewCategoryName("")
                                 onUpdateNewCategoryUnit("")
                                 onSetShowAddCategoryDialog(false)
                             }
                         }
                     ) {
                        Text("Thêm")
                    }
                }, dismissButton = {
                    OutlinedButton(onClick = { onSetShowAddCategoryDialog(false) }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

@Composable
private fun PhotoStatusBadge(photo: com.mapsupervision.domain.model.SitePhoto) {
    val isMatched = photo.matchedNodeCode != null || photo.matchedRouteCode != null || photo.tagCodesCsv.isNotBlank()
    val bg = if (isMatched) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
    val fg = if (isMatched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            if (isMatched) "Khớp" else "Lệch",
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

data class DiarySelectedDateSnapshot(
    val logs: List<com.mapsupervision.domain.model.DailyLog>,
    val matchedPhotos: List<com.mapsupervision.domain.model.SitePhoto>,
    val unmatchedPhotos: List<com.mapsupervision.domain.model.SitePhoto>,
    val photos: List<com.mapsupervision.domain.model.SitePhoto>,
    val totalManpower: Int,
    val locations: List<String>,
    val volumesByCategory: Map<String, Pair<Double, String>>
)


