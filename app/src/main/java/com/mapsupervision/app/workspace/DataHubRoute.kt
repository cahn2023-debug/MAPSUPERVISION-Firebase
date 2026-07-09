package com.mapsupervision.app.workspace

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportedFile
import com.mapsupervision.domain.model.TaskStatus
import com.mapsupervision.storage.importer.ExcelClassificationMode

@Composable
fun DataHubRoute(
    state: WorkspaceState,
    dataHubUiState: DataHubUiState,
    onOpenPicker: () -> Unit,
    onPickerEmpty: () -> Unit,
    onUploadDesign: (List<Uri>) -> Unit,
    onLoadNonExcelPreview: (Uri, String?) -> Unit,
    onUpdateImportMappingUi: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onSetImportMappingDialogVisible: (Boolean) -> Unit,
    onParseNonExcelToDesign: () -> Unit,
    onRetryFailedImports: () -> Unit,
    onLoadExcelPreview: (Uri, String?) -> Unit,
    onUpdateExcelMapping: (String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?, String?) -> Unit,
    onUpdateExcelClassificationMode: (ExcelClassificationMode) -> Unit,
    onSetExcelMappingDialogVisible: (Boolean) -> Unit,
    onUpdateExcelCoordinateMode: (Boolean) -> Unit,
    onUpdateMapVisualOptions: (Boolean?, Boolean?) -> Unit,
    onParseExcelToDesign: () -> Unit,
    onAddConstruction: (String, Float, Float) -> Unit,
    onUpdateWorkVolumeProgress: (String, String, String) -> Unit,
    onOpenNodeOnMap: (GisNode) -> Unit,
    onOpenRouteOnMap: (GisRoute) -> Unit,
    onDeleteImportedFile: (String) -> Unit,
    onRepairImportedGeometry: (ImportedFile) -> Unit,
    photoFilterNodeCode: String?,
    onClearPhotoFilter: () -> Unit,
    onLoadNotesAndTasks: (String) -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (String, String) -> Unit,
    onAddTask: (String, String) -> Unit,
    onToggleTaskStatus: (String, String, TaskStatus) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onSummarizeNotes: (String) -> Unit,
    onSuggestTasks: (String) -> Unit,
    onCombineFiles: (ImportedFile, ImportedFile, List<GisNode>, List<GisRoute>) -> Unit,
    onUpdateSelectedExcelSheet: (String) -> Unit,
    onRefresh: () -> Unit = {},
    viewModel: DataHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DataHubScreen(
        state = state,
        dataHubUiState = dataHubUiState,
        screenUiState = uiState,
        onSetDesignTab = viewModel::setDesignTab,
        onUpdateSearchQuery = viewModel::updateSearchQuery,
        onUpdateContractorFilter = viewModel::updateContractorFilter,
        onSetContractorMenuExpanded = viewModel::setContractorMenuExpanded,
        onUpdateObjectTypeFilter = viewModel::updateObjectTypeFilter,
        onUpdateSortOrder = viewModel::updateSortOrder,
        onSetSortMenuExpanded = viewModel::setSortMenuExpanded,
        onShowNotesAndTasks = viewModel::showNotesAndTasks,
        onDismissNotesAndTasks = viewModel::dismissNotesAndTasks,
        onOpenPicker = onOpenPicker,
        onPickerEmpty = onPickerEmpty,
        onUploadDesign = onUploadDesign,
        onLoadNonExcelPreview = onLoadNonExcelPreview,
        onUpdateImportMappingUi = onUpdateImportMappingUi,
        onSetImportMappingDialogVisible = onSetImportMappingDialogVisible,
        onParseNonExcelToDesign = onParseNonExcelToDesign,
        onRetryFailedImports = onRetryFailedImports,
        onLoadExcelPreview = onLoadExcelPreview,
        onUpdateExcelMapping = onUpdateExcelMapping,
        onUpdateExcelClassificationMode = onUpdateExcelClassificationMode,
        onSetExcelMappingDialogVisible = onSetExcelMappingDialogVisible,
        onUpdateExcelCoordinateMode = onUpdateExcelCoordinateMode,
        onUpdateMapVisualOptions = onUpdateMapVisualOptions,
        onParseExcelToDesign = onParseExcelToDesign,
        onAddConstruction = onAddConstruction,
        onUpdateWorkVolumeProgress = onUpdateWorkVolumeProgress,
        onOpenNodeOnMap = onOpenNodeOnMap,
        onOpenRouteOnMap = onOpenRouteOnMap,
        onDeleteImportedFile = onDeleteImportedFile,
        onRepairImportedGeometry = onRepairImportedGeometry,
        photoFilterNodeCode = photoFilterNodeCode,
        onClearPhotoFilter = onClearPhotoFilter,
        onLoadNotesAndTasks = onLoadNotesAndTasks,
        onAddNote = onAddNote,
        onDeleteNote = onDeleteNote,
        onAddTask = onAddTask,
        onToggleTaskStatus = onToggleTaskStatus,
        onDeleteTask = onDeleteTask,
        onSummarizeNotes = onSummarizeNotes,
        onSuggestTasks = onSuggestTasks,
        onCombineFiles = onCombineFiles,
        onUpdateSelectedExcelSheet = onUpdateSelectedExcelSheet,
        onRefresh = onRefresh
    )
}

