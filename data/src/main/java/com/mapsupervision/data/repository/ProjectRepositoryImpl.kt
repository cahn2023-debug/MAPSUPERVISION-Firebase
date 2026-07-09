package com.mapsupervision.data.repository

import com.mapsupervision.core.error.DatabaseException
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.Project
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.ProjectRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.util.UUID
import javax.inject.Inject

class ProjectRepositoryImpl @Inject constructor(
    private val projectDao: ProjectDao,
    private val storageManager: ProjectStorageManager
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
            isDeleted = resolved.isDeleted,
            deletedAtEpochMs = resolved.deletedAtEpochMs
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
        isDeleted = isDeleted,
        deletedAtEpochMs = deletedAtEpochMs
    )
}
