package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.WorkspaceGeometrySnapshot
import com.mapsupervision.domain.model.WorkspaceMediaSnapshot
import com.mapsupervision.domain.model.WorkspacePlanningSnapshot
import com.mapsupervision.domain.model.WorkspaceProgressSnapshot

internal fun mergeWorkspaceGeometrySnapshot(
    current: WorkspaceState,
    snapshot: WorkspaceGeometrySnapshot
): WorkspaceState {
    val dashboard = WorkspaceProgressHelper.buildDashboard(
        snapshot.designNodes,
        snapshot.designRoutes,
        snapshot.constructionProgress,
        current.workVolumeRows
    )
    val selectedNodeCode = current.mapUi.selectedNode?.code
    val nextSelectedPhotos = if (selectedNodeCode != null && current.projectPhotos.isNotEmpty()) {
        current.projectPhotos.filter { it.objectCode == selectedNodeCode }
    } else {
        current.selectedNodePhotos
    }

    return current.copy(
        activeProjectId = snapshot.projectId,
        importedFiles = snapshot.importedFiles,
        designNodes = snapshot.designNodes,
        designRoutes = snapshot.designRoutes,
        constructionProgress = snapshot.constructionProgress,
        dashboard = dashboard,
        selectedNodePhotos = nextSelectedPhotos,
        isRefreshing = false,
        lastRefreshedAtEpochMs = System.currentTimeMillis()
    )
}

internal fun mergeWorkspaceProgressSnapshot(
    current: WorkspaceState,
    snapshot: WorkspaceProgressSnapshot
): WorkspaceState {
    val dashboard = WorkspaceProgressHelper.buildDashboard(
        current.designNodes,
        current.designRoutes,
        current.constructionProgress,
        snapshot.workVolumeRows
    )
    return current.copy(
        activeProjectId = snapshot.projectId,
        workVolumeRows = snapshot.workVolumeRows,
        dailyLogs = snapshot.dailyLogs,
        workCategories = snapshot.workCategories,
        dashboard = dashboard,
        isRefreshing = false,
        lastRefreshedAtEpochMs = System.currentTimeMillis()
    )
}

internal fun mergeWorkspaceMediaSnapshot(
    current: WorkspaceState,
    snapshot: WorkspaceMediaSnapshot
): WorkspaceState {
    val selectedNodeCode = current.mapUi.selectedNode?.code
    val nextSelectedPhotos = if (selectedNodeCode != null) {
        snapshot.sitePhotos.filter { it.objectCode == selectedNodeCode }
    } else {
        current.selectedNodePhotos
    }
    return current.copy(
        activeProjectId = snapshot.projectId,
        projectPhotos = snapshot.sitePhotos,
        selectedNodePhotos = nextSelectedPhotos
    )
}

internal fun mergeWorkspacePlanningSnapshot(
    current: WorkspaceState,
    snapshot: WorkspacePlanningSnapshot
): WorkspaceState {
    return current.copy(
        activeProjectId = snapshot.projectId,
        materialHandovers = snapshot.materialHandovers,
        materialDeclarations = snapshot.materialDeclarations,
        workPlans = snapshot.workPlans,
        projectTasks = snapshot.projectTasks
    )
}
