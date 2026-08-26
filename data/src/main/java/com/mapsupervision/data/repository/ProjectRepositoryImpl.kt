package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.EventOutboxDao
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectDeletionState
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.ActiveProjectRepository
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.util.UUID
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val storageManager: ProjectStorageManager,
    private val eventOutboxDao: EventOutboxDao,
    private val sitePhotoDao: SitePhotoDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider,
    private val activeProjectRepository: ActiveProjectRepository
) : ProjectRepository {
    private companion object {
        const val CURRENT_METADATA_VERSION = 3
    }

    override suspend fun create(name: String, customPath: String?): AppResult<Project> = runCatching {
        val entity = newProject(name, customPath)
        if (!customPath.isNullOrBlank()) {
            storageManager.setCustomPath(entity.slug, customPath)
        }
        projectDao.upsert(entity)
        entity.toDomain()
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to create project", it)) }
    )

    override suspend fun list(includeArchived: Boolean): AppResult<List<Project>> = runCatching {
        runCatching { projectDao.purgeDeletedProjects() }
        projectDao.list(includeArchived).map {
            if (it.storageMode == ProjectStorageMode.PROJECT_DB && it.projectDbPath.isNotBlank()) {
                val dbFile = java.io.File(it.projectDbPath)
                val parent = dbFile.parentFile
                val projectRoot = parent?.parentFile
                if (projectRoot != null &&
                    !storageManager.isScopedProjectDbPath(it.projectDbPath) &&
                    !it.projectDbPath.contains("/Download/MapSupervision/Projects/")
                ) {
                    storageManager.setCustomPath(it.slug, projectRoot.absolutePath)
                }
            }
            it.toDomain()
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to load projects", it)) }
    )

    override suspend fun clone(projectId: String, newName: String): AppResult<Project> = runCatching {
        val source = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        val cloned = newProject(newName).copy(isArchived = false)
        projectDao.upsert(cloned)
        cloned.toDomain().copy(createdAtEpochMs = System.currentTimeMillis(), isArchived = false, name = cloned.name, slug = cloned.slug)
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to clone project", it)) }
    )

    override suspend fun archive(projectId: String): AppResult<Unit> = runCatching {
        projectDao.archive(projectId)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to archive project", it)) }
    )

    override suspend fun importProject(project: Project): AppResult<Unit> = runCatching {
        val resolved = if (project.storageMode == ProjectStorageMode.PROJECT_DB) {
            project.copy(projectDbPath = resolveProjectDbPath(project.slug))
        } else if (project.storageMode == ProjectStorageMode.LEGACY_SHARED && project.projectDbPath.isBlank()) {
            project.copy(storageMode = ProjectStorageMode.PROJECT_DB, projectDbPath = resolveProjectDbPath(project.slug))
        } else {
            project
        }
        val entity = ProjectEntity(
            id = resolved.id,
            name = resolved.name,
            slug = resolved.slug,
            isArchived = resolved.isArchived,
            createdAtEpochMs = resolved.createdAtEpochMs,
            metadataVersion = resolved.metadataVersion,
            updatedAtEpochMs = resolved.updatedAtEpochMs,
            storageMode = resolved.storageMode,
            projectDbPath = resolved.projectDbPath,
            mediaStorageProvider = resolved.mediaStorageProvider,
            mediaStorageFolderId = resolved.mediaStorageFolderId,
            mediaStorageFolderUrl = resolved.mediaStorageFolderUrl,
            mediaStorageUpdatedAtEpochMs = resolved.mediaStorageUpdatedAtEpochMs,
            isDeleted = false,
            deletedAtEpochMs = null,
            deletionState = ProjectDeletionState.ACTIVE,
            deletionRequestId = null,
            deletionErrorCode = null,
            cloudDeletionCompletedAtEpochMs = resolved.cloudDeletionCompletedAtEpochMs,
            cloudDataConfirmed = resolved.cloudDataConfirmed,
            cloudDecisionRequestId = resolved.cloudDecisionRequestId,
            localDeletionErrorCode = resolved.localDeletionErrorCode
        )
        projectDao.upsert(entity)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to import project", it)) }
    )

    override suspend fun clearProject(projectId: String): AppResult<Unit> = runCatching {
        val now = System.currentTimeMillis()
        projectDao.clearProjectData(projectId, now, now)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to clear project data", it)) }
    )

    override suspend fun pendingDeletionWork(projectId: String): AppResult<Int> = runCatching {
        val project = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        val pendingOutbox = eventOutboxDao.pendingCountByProject(projectId)
        val photos = if (project.storageMode == ProjectStorageMode.PROJECT_DB) {
            projectScopedDatabaseProvider.databaseFor(projectId)?.sitePhotoDao()
        } else {
            sitePhotoDao
        }
        val pendingPhotos = photos?.hasPendingUploads(projectId) == true
        pendingOutbox + if (pendingPhotos) 1 else 0
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to inspect pending project work", it)) }
    )

    override suspend fun requestDeletion(projectId: String, requestId: String): AppResult<ProjectDeletionState> = runCatching {
        val activeProjectId = when (val active = activeProjectRepository.getActive()) {
            is AppResult.Success -> active.data
            is AppResult.Error -> throw active.throwable
        }
        check(activeProjectId != projectId) { "Active project must be switched before deletion" }
        val project = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        check(!project.isDeleted) { "Project is already deleted" }
        if (project.deletionState in setOf(
                ProjectDeletionState.CLOUD_DECISION_PENDING,
                ProjectDeletionState.CLOUD_RETAINED,
                ProjectDeletionState.RESTORE_PENDING,
                ProjectDeletionState.DELETING
            )) {
            check(project.deletionRequestId == requestId) { "Project deletion is already owned by another request" }
            return@runCatching project.deletionState
        }
        if (project.deletionState in setOf(ProjectDeletionState.DELETE_FAILED, ProjectDeletionState.LOCAL_DELETE_FAILED)) {
            check(project.deletionRequestId == requestId) { "Retry must reuse the existing deletion request" }
        }
        if (project.deletionState == ProjectDeletionState.ACTIVE || project.deletionState == ProjectDeletionState.LOCAL_DELETE_FAILED) {
            check(projectDao.requestDeletion(projectId, requestId, System.currentTimeMillis()) > 0) {
                "Project deletion request could not be claimed"
            }
        }
        try {
            deleteLocalProjectData(project)
            val now = System.currentTimeMillis()
            projectDao.completeLocalOnlyDeletion(projectId, requestId, now, now)
            ProjectDeletionState.DELETED
        } catch (error: Throwable) {
            projectDao.markLocalDeletionFailed(
                projectId,
                requestId,
                error::class.simpleName ?: "LOCAL_DELETE_FAILED",
                System.currentTimeMillis()
            )
            throw error
        }
    }.fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(DatabaseException("Failed to request project deletion", it)) }
    )

    private suspend fun deleteLocalProjectData(project: ProjectEntity) {
        check(projectDao.countActiveBySlug(project.slug) == 1) {
            "Project storage root is shared by another active project"
        }
        projectDao.purgeProjectRows(project.id)
        check(projectScopedDatabaseProvider.closeProjectDatabase(project.id)) {
            "Project database was not found"
        }
        check(storageManager.deleteProjectStorage(project.slug, project.id, project.projectDbPath)) {
            "Project storage could not be removed"
        }
    }

    override suspend fun markDeletionFailed(projectId: String, requestId: String, errorCode: String): AppResult<Unit> = runCatching {
        check(projectDao.markDeletionFailed(projectId, requestId, errorCode, System.currentTimeMillis()) > 0) {
            "Project deletion request is no longer active"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to mark project deletion failure", it)) }
    )

    override suspend fun markCloudRetained(projectId: String, requestId: String): AppResult<Unit> = runCatching {
        check(projectDao.markCloudRetained(projectId, requestId, System.currentTimeMillis()) > 0) {
            "Cloud retention decision is stale"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to mark Cloud project as retained", it)) }
    )

    override suspend fun markRestorePending(projectId: String, requestId: String, errorCode: String): AppResult<Unit> = runCatching {
        check(projectDao.markRestorePending(projectId, requestId, errorCode, System.currentTimeMillis()) > 0) {
            "Project restore decision is stale"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to mark project restore pending", it)) }
    )

    override suspend fun markRestoreCompleted(projectId: String, requestId: String): AppResult<Unit> = runCatching {
        check(projectDao.markRestoreCompleted(projectId, requestId, System.currentTimeMillis()) > 0) {
            "Project restore completion is stale"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to complete project restore", it)) }
    )

    override suspend fun markCloudDeletionCompleted(projectId: String, requestId: String): AppResult<Unit> = runCatching {
        check(projectDao.markCloudDeletionCompleted(projectId, requestId, System.currentTimeMillis(), System.currentTimeMillis()) > 0) {
            "Project deletion request is not active"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to record cloud project deletion", it)) }
    )

    override suspend fun completeLocalDeletion(projectId: String, requestId: String): AppResult<Unit> = runCatching {
        val project = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        check(project.deletionRequestId == requestId) { "Project deletion request does not match" }
        check(project.deletionState == ProjectDeletionState.DELETING) { "Project is not pending deletion" }
        check(project.cloudDeletionCompletedAtEpochMs != null) { "Cloud deletion has not completed" }
        check(projectDao.countActiveBySlug(project.slug) == 1) {
            "Project storage root is shared by another active project"
        }
        projectDao.purgeProjectRows(projectId)
        check(projectScopedDatabaseProvider.closeProjectDatabase(projectId)) { "Project database was not found" }
        check(storageManager.deleteProjectStorage(project.slug, project.id, project.projectDbPath)) {
            "Project storage could not be removed"
        }
        check(projectDao.completeLocalDeletion(projectId, requestId, System.currentTimeMillis(), System.currentTimeMillis()) > 0) {
            "Project deletion request is no longer active"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to complete local project deletion", it)) }
    )

    override suspend fun acknowledgeRemoteDeletion(projectId: String, deleteLocal: Boolean): AppResult<Unit> = runCatching {
        val project = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        check(project.deletionState == ProjectDeletionState.DELETED && !project.isDeleted) {
            "Remote project deletion is not awaiting acknowledgement"
        }
        if (deleteLocal) {
            check(projectDao.countActiveBySlug(project.slug) == 1) {
                "Project storage root is shared by another active project"
            }
            projectDao.purgeProjectRows(projectId)
            check(projectScopedDatabaseProvider.closeProjectDatabase(projectId)) { "Project database was not found" }
            check(storageManager.deleteProjectStorage(project.slug, project.id, project.projectDbPath)) {
                "Project storage could not be removed"
            }
            check(projectDao.completeRemoteLocalDeletion(projectId, System.currentTimeMillis(), System.currentTimeMillis()) > 0) {
                "Remote project deletion acknowledgement is stale"
            }
        } else {
            projectScopedDatabaseProvider.databaseFor(projectId)
                ?.projectDao()
                ?.markRemoteDeletion(projectId, project.deletionRequestId.orEmpty(), project.deletedAtEpochMs ?: System.currentTimeMillis(), System.currentTimeMillis())
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to acknowledge remote project deletion", it)) }
    )

    override suspend fun forcePurgeLocalProject(projectId: String): AppResult<Unit> = runCatching {
        val project = projectDao.get(projectId) ?: return@runCatching Unit
        projectDao.purgeProjectRows(projectId)
        runCatching { projectScopedDatabaseProvider.closeProjectDatabase(projectId) }
        runCatching { storageManager.deleteProjectStorage(project.slug, project.id, project.projectDbPath) }
        val now = System.currentTimeMillis()
        val requestId = project.deletionRequestId ?: project.cloudDecisionRequestId ?: java.util.UUID.randomUUID().toString()
        projectDao.completeLocalOnlyDeletion(projectId, requestId, now, now)
        projectDao.completeRemoteLocalDeletion(projectId, now, now)
        projectDao.markProjectDeletedForce(projectId, now, now)
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to force purge local project", it)) }
    )

    override suspend fun touch(projectId: String): AppResult<Unit> = runCatching {
        projectDao.touch(
            projectId = projectId,
            metadataVersion = CURRENT_METADATA_VERSION,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to update project metadata", it)) }
    )

    private fun newProject(name: String, customPath: String? = null): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val slug = slugify(name)
        val dbPath = resolveProjectDbPath(slug)
        return ProjectEntity(
            id = id,
            name = name,
            slug = slug,
            isArchived = false,
            createdAtEpochMs = System.currentTimeMillis(),
            metadataVersion = CURRENT_METADATA_VERSION,
            updatedAtEpochMs = System.currentTimeMillis(),
            storageMode = ProjectStorageMode.PROJECT_DB,
            projectDbPath = dbPath,
            isDeleted = false,
            deletedAtEpochMs = null
        )
    }

    override suspend fun markCloudDeletionStarted(projectId: String, requestId: String): AppResult<Unit> = runCatching {
        check(projectDao.markCloudDeletionStarted(projectId, requestId, System.currentTimeMillis()) > 0) {
            "Cloud deletion decision is stale"
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to mark Cloud deletion started", it)) }
    )

    override suspend fun updateStoragePath(projectId: String, newPath: String): AppResult<Unit> = runCatching {
        val project = projectDao.get(projectId) ?: throw IllegalArgumentException("Project not found")
        val slug = project.slug
        val oldDbFile = java.io.File(project.projectDbPath)
        val newDbFile = java.io.File(resolveProjectDbPath(slug))

        if (newPath.isNotBlank()) {
            val oldRoot = storageManager.getCustomPath(slug)?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                ?: if (storageManager.isScopedProjectDbPath(project.projectDbPath)) {
                    storageManager.projectRootDirectory(slug)
                } else {
                    oldDbFile.parentFile?.parentFile
                }
            val newRoot = java.io.File(newPath)
            
            // 1. Copy directory recursively if source exists and is different from destination
            if (oldRoot != null && oldRoot.exists() && oldRoot.absolutePath != newRoot.absolutePath) {
                copyDirectory(oldRoot, newRoot)
            }

            // 2. Set the custom path so future storage references resolve to newRoot
            storageManager.setCustomPath(slug, newPath)

            val newDbPath = newDbFile.absolutePath

            // 3. Update paths in shared database
            projectDao.updateProjectDbPath(projectId, newDbPath)

            // 4. Update paths in project-scoped database
            if (newDbFile.exists()) {
                val oldPrefix = oldRoot?.absolutePath.orEmpty()
                val newPrefix = newRoot.absolutePath
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    newDbPath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE or android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
                ).use { db ->
                    db.execSQL("UPDATE projects SET projectDbPath = ? WHERE id = ?", arrayOf(newDbPath, projectId))
                    db.execSQL("UPDATE imported_files SET storedPath = REPLACE(storedPath, ?, ?) WHERE projectId = ?", arrayOf(oldPrefix, newPrefix, projectId))
                    db.execSQL("UPDATE site_photos SET filePath = REPLACE(filePath, ?, ?), thumbnailPath = REPLACE(thumbnailPath, ?, ?) WHERE projectId = ?", arrayOf(oldPrefix, newPrefix, projectId))
                }
            }

            // 5. Delete old directory safely after verification
            if (oldRoot != null && oldRoot.exists() && oldRoot.absolutePath != newRoot.absolutePath) {
                if (newDbFile.exists() && newDbFile.length() > 0L) {
                    oldRoot.deleteRecursively()
                }
            }
        }
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to update storage path", it)) }
    )

    override suspend fun updateMediaStorage(projectId: String, folderId: String, folderUrl: String): AppResult<Unit> = runCatching {
        val normalizedFolderId = folderId.trim()
        val normalizedFolderUrl = folderUrl.trim()
        if (normalizedFolderId.isBlank()) {
            throw IllegalArgumentException("Google Drive folder ID is required")
        }
        projectDao.updateMediaStorage(
            projectId = projectId,
            folderId = normalizedFolderId,
            folderUrl = normalizedFolderUrl,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }.fold(
        onSuccess = { AppResult.Success(Unit) },
        onFailure = { AppResult.Error(DatabaseException("Failed to update media storage", it)) }
    )

    private fun copyDirectory(source: java.io.File, destination: java.io.File) {
        if (!source.exists()) return
        if (!destination.exists()) {
            destination.mkdirs()
        }
        source.listFiles()?.forEach { file ->
            val targetFile = java.io.File(destination, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                file.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }

    private fun resolveProjectDbPath(slug: String): String =
        storageManager.projectDbFile(slug).absolutePath

    private fun slugify(name: String): String =
        name.lowercase().trim().replace(Regex("\\s+"), "-").replace(Regex("[^a-z0-9-]"), "")

    private fun ProjectEntity.toDomain() = Project(
        id = id,
        name = name,
        slug = slug,
        isArchived = isArchived,
        createdAtEpochMs = createdAtEpochMs,
        metadataVersion = metadataVersion,
        updatedAtEpochMs = updatedAtEpochMs,
        storageMode = storageMode,
        projectDbPath = projectDbPath,
        mediaStorageProvider = mediaStorageProvider,
        mediaStorageFolderId = mediaStorageFolderId,
        mediaStorageFolderUrl = mediaStorageFolderUrl,
        mediaStorageUpdatedAtEpochMs = mediaStorageUpdatedAtEpochMs,
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs,
        deletionState = deletionState,
        deletionRequestId = deletionRequestId,
        deletionErrorCode = deletionErrorCode,
        cloudDeletionCompletedAtEpochMs = cloudDeletionCompletedAtEpochMs,
        cloudDataConfirmed = cloudDataConfirmed,
        cloudDecisionRequestId = cloudDecisionRequestId,
        localDeletionErrorCode = localDeletionErrorCode
    )
}
