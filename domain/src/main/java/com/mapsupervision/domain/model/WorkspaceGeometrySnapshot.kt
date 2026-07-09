package com.mapsupervision.domain.model

data class WorkspaceGeometrySnapshot(
    val projectId: String,
    val importedFiles: List<ImportedFile> = emptyList(),
    val designNodes: List<GisNode> = emptyList(),
    val designRoutes: List<GisRoute> = emptyList(),
    val constructionProgress: List<NodeProgress> = emptyList()
)
