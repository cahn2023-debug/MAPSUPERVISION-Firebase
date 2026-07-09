package com.mapsupervision.domain.service

import com.mapsupervision.domain.model.Project

interface ProjectStorageMigrationService {
    suspend fun migrateProjectIfNeeded(project: Project): ProjectStorageMigrationStatus
}
