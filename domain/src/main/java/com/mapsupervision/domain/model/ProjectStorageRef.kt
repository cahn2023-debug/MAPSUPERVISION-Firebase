package com.mapsupervision.domain.model

data class ProjectStorageRef(
    val id: String,
    val slug: String
)

val Project.storageRef: ProjectStorageRef
    get() = ProjectStorageRef(id = id, slug = slug)
