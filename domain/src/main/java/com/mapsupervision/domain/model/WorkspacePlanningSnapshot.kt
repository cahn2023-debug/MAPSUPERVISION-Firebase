package com.mapsupervision.domain.model

data class WorkspacePlanningSnapshot(
    val projectId: String,
    val materialHandovers: List<MaterialHandover> = emptyList(),
    val materialDeclarations: List<MaterialDeclaration> = emptyList(),
    val workPlans: List<WorkPlan> = emptyList(),
    val projectTasks: List<Task> = emptyList()
)
