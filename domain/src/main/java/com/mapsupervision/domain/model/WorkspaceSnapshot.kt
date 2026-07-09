package com.mapsupervision.domain.model

data class WorkspaceSnapshot(
    val projectId: String,
    val importedFiles: List<ImportedFile> = emptyList(),
    val designNodes: List<GisNode> = emptyList(),
    val designRoutes: List<GisRoute> = emptyList(),
    val constructionProgress: List<NodeProgress> = emptyList(),
    val workVolumeRows: List<WorkVolumeProgress> = emptyList(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val workCategories: List<WorkCategory> = emptyList(),
    val sitePhotos: List<SitePhoto> = emptyList(),
    val materialHandovers: List<MaterialHandover> = emptyList(),
    val materialDeclarations: List<MaterialDeclaration> = emptyList(),
    val workPlans: List<WorkPlan> = emptyList(),
    val projectTasks: List<Task> = emptyList()
)


