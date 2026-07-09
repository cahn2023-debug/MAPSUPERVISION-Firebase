package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.WorkspaceSnapshot

fun WorkspaceState.toWorkspaceSnapshot(): WorkspaceSnapshot {
    return WorkspaceSnapshot(
        projectId = activeProjectId.orEmpty(),
        importedFiles = importedFiles,
        designNodes = designNodes,
        designRoutes = designRoutes,
        constructionProgress = constructionProgress,
        workVolumeRows = workVolumeRows,
        dailyLogs = dailyLogs,
        workCategories = workCategories,
        sitePhotos = projectPhotos,
        materialHandovers = materialHandovers,
        materialDeclarations = materialDeclarations
    )
}

