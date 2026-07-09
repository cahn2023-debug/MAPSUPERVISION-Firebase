package com.mapsupervision.domain.model

data class WorkspaceMediaSnapshot(
    val projectId: String,
    val sitePhotos: List<SitePhoto> = emptyList()
)
