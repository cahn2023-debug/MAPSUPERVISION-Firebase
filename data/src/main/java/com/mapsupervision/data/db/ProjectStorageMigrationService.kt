package com.mapsupervision.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.DailyLogLineEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.ReportDraftEntity
import com.mapsupervision.domain.model.Project
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.domain.service.ProjectStorageMigrationService
import com.mapsupervision.domain.service.ProjectStorageMigrationStatus
import com.mapsupervision.storage.ProjectStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ProjectStorageMigrationServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedDatabase: MapSupervisionDatabase,
    private val storageManager: ProjectStorageManager
) : ProjectStorageMigrationService {

    private val migrationMutex = Mutex()
    private val verificationTables = listOf(
        "projects",
        "imported_files",
        "gis_node",
        "gis_route",
        "node_progress",
        "work_volume_progress",
        "daily_log",
        "daily_log_line",
        "work_categories",
        "work_plan",
        "note",
        "task",
        "site_photos",
        "report_draft",
        "material_declaration",
        "material_handover"
    )

    override suspend fun migrateProjectIfNeeded(project: Project): ProjectStorageMigrationStatus {
        val entity = ProjectEntity(
            id = project.id,
            name = project.name,
            slug = project.slug,
            isArchived = project.isArchived,
            createdAtEpochMs = project.createdAtEpochMs,
            metadataVersion = project.metadataVersion,
            updatedAtEpochMs = project.updatedAtEpochMs,
            storageMode = project.storageMode,
            projectDbPath = project.projectDbPath
        )
        return migrateProjectEntityIfNeeded(entity)
    }

    suspend fun migrateProjectEntityIfNeeded(project: ProjectEntity): ProjectStorageMigrationStatus = migrationMutex.withLock {
        withContext(Dispatchers.IO) {
            val slug = project.slug
            val projectId = project.id
            val newRoot = storageManager.projectRootDirectory(slug)
            val scopedDbFile = storageManager.scopedProjectDbFile(slug)
            val inferredLegacyRoot = project.projectDbPath.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.parentFile
                ?.parentFile

            val legacyRoots = listOf(
                inferredLegacyRoot,
                storageManager.privateProjectRootDirectory(slug),
                storageManager.privateProjectRootDirectory(projectId),
                File(storageManager.publicBaseDirDirectory(), "Projects/$projectId"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MapSupervision/Projects/$projectId")
            ).filterNotNull().distinctBy { it.absolutePath }
                .filter {
                    it.exists() &&
                        it.absolutePath != newRoot.absolutePath &&
                        it.absolutePath != scopedDbFile.parentFile?.parent &&
                        hasLegacyProjectContent(it)
                }

            if (legacyRoots.isEmpty()) {
                val hasCurrentProjectContent = hasLegacyProjectContent(newRoot)
                val message = if (hasCurrentProjectContent) {
                    "No legacy storage detected; standardized current scoped storage state."
                } else {
                    "No legacy storage detected; verified current scoped storage state."
                }
                AppLogger.d("ProjectStorageMigration: No legacy directory found for $slug. Skipping migration. Running standardization/verification.")
                standardizeMediaAndCleanupThumbs(projectId, slug)
                sharedDatabase.projectDao().updateProjectDbPath(projectId, scopedDbFile.absolutePath)
                ensureScopedProjectDataCopied(project, scopedDbFile.absolutePath)
                return@withContext verifyScopedMigration(
                    projectId = projectId,
                    slug = slug,
                    migrated = false,
                    defaultMessage = message
                )
            }

            AppLogger.d("ProjectStorageMigration: Migrating project $slug to public storage and restructuring media layout...")

            var migrationThrowable: Throwable? = null
            val success = runCatching {
                newRoot.mkdirs()

                // Ensure database exists in public root
                val newDbFile = scopedDbFile
                if (!newDbFile.exists() || newDbFile.length() == 0L) {
                    val dbSource = legacyRoots.map { File(it, "db/project.sqlite") }.firstOrNull { it.exists() && it.length() > 0L }
                    if (dbSource != null) {
                        newDbFile.parentFile?.mkdirs()
                        dbSource.copyTo(newDbFile, overwrite = true)
                    }
                }

                val newDbPath = newDbFile.absolutePath
                sharedDatabase.projectDao().updateProjectDbPath(projectId, newDbPath)
                ensureScopedProjectDataCopied(project, newDbPath)

                val nodeMap = sharedDatabase.gisNodeDao().byProject(projectId).associate { it.id to it.code }
                val routeMap = sharedDatabase.gisRouteDao().byProject(projectId).associate { it.id to it.code }
                var sitePhotos = sharedDatabase.sitePhotoDao().byProject(projectId)
                var importedFiles = sharedDatabase.importedFileDao().byProject(projectId)

                legacyRoots.forEach { legacyRoot ->
                    val oldPrefix = legacyRoot.absolutePath
                    val newPrefix = newRoot.absolutePath

                    copyDirectory(legacyRoot, newRoot)

                    val updatedPhotos = sitePhotos.map { photo ->
                        val matchedNodeCode = photo.matchedNodeId?.let { nodeMap[it] }
                        val matchedRouteCode = photo.matchedRouteId?.let { routeMap[it] }
                        val isRoute = matchedRouteCode != null
                        val objectCode = matchedNodeCode ?: matchedRouteCode ?: ""
                        val targetFolder = storageManager.resolveObjectFolder(slug, isRoute, objectCode)
                        val sourcePhoto = pickExistingFile(
                            File(photo.filePath),
                            File(legacyRoot, "photos/${File(photo.filePath).name}"),
                            File(legacyRoot, "media/${File(photo.filePath).name}"),
                            File(newRoot, "photos/${File(photo.filePath).name}"),
                            File(newRoot, "media/${File(photo.filePath).name}")
                        )

                        val locLabel = photo.address.takeIf { !it.isNullOrBlank() }
                            ?: if (photo.latitude != null && photo.longitude != null) "${photo.latitude}_${photo.longitude}" else null
                        val extension = sourcePhoto?.extension?.ifBlank { "jpg" } ?: "jpg"
                        val finalFileName = storageManager.buildMediaFileName(photo.capturedAtEpochMs, locLabel, photo.captureNote, extension)
                        val newFile = storageManager.generateUniqueFile(targetFolder, finalFileName.substringBeforeLast("."), extension)

                        if (sourcePhoto != null && sourcePhoto.absolutePath != newFile.absolutePath) {
                            newFile.parentFile?.mkdirs()
                            newFile.writeBytes(sourcePhoto.readBytes())
                        }

                        photo.copy(
                            filePath = newFile.absolutePath,
                            thumbnailPath = newFile.absolutePath
                        )
                    }
                    sitePhotos = updatedPhotos

                    val updatedFiles = importedFiles.map { file ->
                        file.copy(storedPath = file.storedPath.replace(oldPrefix, newPrefix))
                    }
                    importedFiles = updatedFiles

                    legacyRoot.deleteRecursively()
                }

                sitePhotos.forEach { photo -> sharedDatabase.sitePhotoDao().upsert(photo) }
                sharedDatabase.importedFileDao().upsertAll(importedFiles)

                if (newDbFile.exists() && newDbFile.length() > 0L) {
                    SQLiteDatabase.openDatabase(newDbPath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { db ->
                        db.execSQL("UPDATE projects SET projectDbPath = ? WHERE id = ?", arrayOf(newDbPath, projectId))
                        legacyRoots.forEach { legacyRoot ->
                            val oldPrefix = legacyRoot.absolutePath
                            val newPrefix = newRoot.absolutePath
                            db.execSQL("UPDATE imported_files SET storedPath = REPLACE(storedPath, ?, ?) WHERE projectId = ?", arrayOf(oldPrefix, newPrefix, projectId))
                            db.execSQL("UPDATE site_photos SET filePath = REPLACE(filePath, ?, ?), thumbnailPath = REPLACE(thumbnailPath, ?, ?) WHERE projectId = ?", arrayOf(oldPrefix, newPrefix, projectId))
                        }
                    }
                }

                // Clean up old media directories under newRoot if they are empty or obsolete
                val oldPhotosDir = File(newRoot, "photos")
                val oldMediaDir = File(newRoot, "media")
                val oldThumbsDir = File(newRoot, "thumbs")
                if (oldPhotosDir.exists()) oldPhotosDir.deleteRecursively()
                if (!oldMediaDir.absolutePath.equals(File(newRoot, "Media").absolutePath, ignoreCase = true) && oldMediaDir.exists()) {
                    oldMediaDir.deleteRecursively()
                }
                if (oldThumbsDir.exists()) oldThumbsDir.deleteRecursively()

                standardizeMediaAndCleanupThumbs(projectId, slug)
                ensureScopedProjectDataCopied(project, newDbPath)

            }.onFailure {
                migrationThrowable = it
            }.isSuccess

            if (success) {
                AppLogger.d("ProjectStorageMigration: Project $slug successfully migrated and restructured.")
                verifyScopedMigration(
                    projectId = projectId,
                    slug = slug,
                    migrated = true,
                    defaultMessage = "Project storage migrated and verified."
                )
            } else {
                AppLogger.e(migrationThrowable ?: RuntimeException("Unknown migration error"), "ProjectStorageMigration: Migration/Restructuring failed for project $slug.")
                ProjectStorageMigrationStatus(
                    projectId = projectId,
                    migrated = true,
                    verified = false,
                    verificationMessage = migrationThrowable?.message ?: "Unknown migration error",
                    projectDbPath = scopedDbFile.absolutePath
                )
            }
        }
    }

    private suspend fun verifyScopedMigration(
        projectId: String,
        slug: String,
        migrated: Boolean,
        defaultMessage: String
    ): ProjectStorageMigrationStatus {
        val newDbFile = storageManager.scopedProjectDbFile(slug)
        val newDbPath = newDbFile.absolutePath
        if (!newDbFile.exists() || newDbFile.length() == 0L) {
            return ProjectStorageMigrationStatus(
                projectId = projectId,
                migrated = migrated,
                verified = false,
                verificationMessage = "Scoped database file missing after migration.",
                projectDbPath = newDbPath
            )
        }

        val sharedProject = sharedDatabase.projectDao().get(projectId)
        if (sharedProject?.projectDbPath != newDbPath) {
            return ProjectStorageMigrationStatus(
                projectId = projectId,
                migrated = migrated,
                verified = false,
                verificationMessage = "Shared project metadata still points to an unexpected database path.",
                projectDbPath = newDbPath
            )
        }

        val sharedCounts = countRows(sharedDatabase.openHelper.writableDatabase, projectId)
        val scopedCounts = runCatching {
            SQLiteDatabase.openDatabase(newDbPath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { db ->
                countRows(db, projectId)
            }
        }.getOrElse { throwable ->
            return ProjectStorageMigrationStatus(
                projectId = projectId,
                migrated = migrated,
                verified = false,
                verificationMessage = "Unable to open scoped database for verification: ${throwable.message}",
                projectDbPath = newDbPath,
                sharedRowCounts = sharedCounts
            )
        }

        val mismatchedTables = verificationTables.filter { sharedCounts[it] != scopedCounts[it] }
        val verificationMessage = if (mismatchedTables.isEmpty()) {
            defaultMessage
        } else {
            "Row count mismatch for ${mismatchedTables.joinToString(", ")}."
        }

        return ProjectStorageMigrationStatus(
            projectId = projectId,
            migrated = migrated,
            verified = mismatchedTables.isEmpty(),
            verificationMessage = verificationMessage,
            projectDbPath = newDbPath,
            sharedRowCounts = sharedCounts,
            scopedRowCounts = scopedCounts
        )
    }

    private suspend fun standardizeMediaAndCleanupThumbs(projectId: String, slug: String) {
        val newDbFile = storageManager.scopedProjectDbFile(slug)
        val newDbPath = newDbFile.absolutePath

        // 1. Standardize and repair site_photos in sharedDatabase
        runCatching {
            sharedDatabase.sitePhotoDao().byProject(projectId).forEach { photo ->
                val isVideo = photo.mediaType == com.mapsupervision.domain.model.MediaType.VIDEO ||
                    photo.filePath.endsWith(".mp4", ignoreCase = true)
                val currentThumb = photo.thumbnailPath
                val hasThumbPattern = currentThumb.endsWith("_thumb.jpg", ignoreCase = true)

                if (isVideo || hasThumbPattern) {
                    if (currentThumb.isNotBlank() && currentThumb != photo.filePath) {
                        val thumbFile = File(currentThumb)
                        if (thumbFile.exists() && thumbFile.absolutePath != File(photo.filePath).absolutePath) {
                            runCatching { thumbFile.delete() }
                        }
                    }
                    val updated = photo.copy(thumbnailPath = photo.filePath)
                    sharedDatabase.sitePhotoDao().upsert(updated)
                }
            }
        }

        // 2. Also update the project-scoped DB if it exists
        if (newDbFile.exists() && newDbFile.length() > 0L) {
            runCatching {
                SQLiteDatabase.openDatabase(newDbPath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { db ->
                    db.execSQL("UPDATE site_photos SET thumbnailPath = filePath WHERE mediaType = 'VIDEO' OR thumbnailPath LIKE '%_thumb.jpg'")
                }
            }
        }
    }

    private suspend fun ensureScopedProjectDataCopied(
        project: ProjectEntity,
        newDbPath: String
    ) {
        val scopedDatabase = openScopedDatabase(newDbPath)
        try {
            val corePayload = corePayload(sharedDatabase, project.id)
            val auxPayload = auxPayload(sharedDatabase, project.id)

            scopedDatabase.withTransaction {
                scopedDatabase.projectDao().upsert(project.copy(projectDbPath = newDbPath))
                corePayload?.let { payload ->
                    if (payload.importedFiles.isNotEmpty()) scopedDatabase.importedFileDao().upsertAll(payload.importedFiles)
                    if (payload.nodes.isNotEmpty()) scopedDatabase.gisNodeDao().upsertAll(payload.nodes)
                    val lookupBeforeRoutes = buildProjectBridgeLookup(
                        sourceDatabase = sharedDatabase,
                        targetDatabase = scopedDatabase,
                        projectId = project.id
                    )
                    if (payload.routes.isNotEmpty()) {
                        scopedDatabase.gisRouteDao().upsertAll(payload.routes.map { it.normalizeForBridge(lookupBeforeRoutes) })
                    }
                    val lookupAfterRoutes = buildProjectBridgeLookup(
                        sourceDatabase = sharedDatabase,
                        targetDatabase = scopedDatabase,
                        projectId = project.id
                    )
                    for (entity in payload.nodeProgress.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                        scopedDatabase.nodeProgressDao().upsert(entity)
                    }
                    for (entity in payload.workVolumeProgress) scopedDatabase.workVolumeProgressDao().upsert(entity)
                    for (entity in payload.dailyLogs.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                        scopedDatabase.dailyLogDao().upsert(entity)
                    }
                    if (payload.dailyLogLines.isNotEmpty()) {
                        scopedDatabase.dailyLogLineDao().upsertAll(payload.dailyLogLines.map { it.normalizeForBridge(lookupAfterRoutes) })
                    }
                    for (entity in payload.workCategories) scopedDatabase.workCategoryDao().upsert(entity)
                    for (entity in payload.workPlans.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                        scopedDatabase.workPlanDao().insert(entity)
                    }
                }
                auxPayload?.let { payload ->
                    val auxLookup = buildProjectBridgeLookup(
                        sourceDatabase = sharedDatabase,
                        targetDatabase = scopedDatabase,
                        projectId = project.id
                    )
                    for (entity in payload.notes.map { it.normalizeForBridge(auxLookup) }) {
                        scopedDatabase.noteDao().insert(entity)
                    }
                    for (entity in payload.tasks.map { it.normalizeForBridge(auxLookup) }) {
                        scopedDatabase.taskDao().upsert(entity)
                    }
                    for (entity in payload.sitePhotos.map { it.normalizeForBridge(auxLookup) }) {
                        scopedDatabase.sitePhotoDao().upsert(entity)
                    }
                    for (entity in payload.reportDrafts) scopedDatabase.reportDraftDao().upsert(entity)
                    for (entity in payload.materialDeclarations.map { it.normalizeForBridge(auxLookup) }) {
                        scopedDatabase.materialDeclarationDao().insert(entity)
                    }
                    val materialLookup = buildProjectBridgeLookup(
                        sourceDatabase = sharedDatabase,
                        targetDatabase = scopedDatabase,
                        projectId = project.id
                    )
                    for (entity in payload.materialHandovers.map { it.normalizeForBridge(materialLookup) }) {
                        scopedDatabase.materialHandoverDao().upsert(entity)
                    }
                }
            }
        } finally {
            scopedDatabase.close()
        }
    }

    private fun openScopedDatabase(newDbPath: String): MapSupervisionDatabase {
        return androidx.room.Room.databaseBuilder(context, MapSupervisionDatabase::class.java, newDbPath)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .build()
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!source.exists()) return
        if (!destination.exists()) {
            destination.mkdirs()
        }
        source.listFiles()?.forEach { file ->
            val targetFile = File(destination, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                file.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
            }
        }
    }

    private fun pickExistingFile(vararg candidates: File): File? {
        return candidates.firstOrNull { it.exists() }
    }

    private fun hasLegacyProjectContent(root: File): Boolean {
        return LEGACY_CONTENT_DIRECTORIES.any { relativePath ->
            val target = File(root, relativePath)
            target.exists() && target.walkTopDown().any { it.isFile && it.length() > 0L }
        }
    }

    private suspend fun corePayload(
        database: MapSupervisionDatabase,
        projectId: String
    ): CoreProjectPayload? {
        val importedFiles = database.importedFileDao().byProject(projectId)
        val nodes = database.gisNodeDao().byProject(projectId)
        val routes = database.gisRouteDao().byProject(projectId)
        val nodeProgress = database.nodeProgressDao().byProject(projectId)
        val workVolumeProgress = database.workVolumeProgressDao().byProject(projectId)
        val dailyLogs = database.dailyLogDao().byProject(projectId)
        val dailyLogLines = database.dailyLogLineDao().byLogIds(projectId, dailyLogs.map { it.id })
        val workCategories = database.workCategoryDao().byProject(projectId)
        val workPlans = database.workPlanDao().byProject(projectId)
        if (
            importedFiles.isEmpty() &&
            nodes.isEmpty() &&
            routes.isEmpty() &&
            nodeProgress.isEmpty() &&
            workVolumeProgress.isEmpty() &&
            dailyLogs.isEmpty() &&
            dailyLogLines.isEmpty() &&
            workCategories.isEmpty() &&
            workPlans.isEmpty()
        ) {
            return null
        }
        return CoreProjectPayload(
            importedFiles = importedFiles,
            nodes = nodes,
            routes = routes,
            nodeProgress = nodeProgress,
            workVolumeProgress = workVolumeProgress,
            dailyLogs = dailyLogs,
            dailyLogLines = dailyLogLines,
            workCategories = workCategories,
            workPlans = workPlans
        )
    }

    private suspend fun auxPayload(
        database: MapSupervisionDatabase,
        projectId: String
    ): AuxProjectPayload? {
        val notes = database.noteDao().byProject(projectId)
        val tasks = database.taskDao().byProject(projectId)
        val sitePhotos = database.sitePhotoDao().byProject(projectId)
        val reportDrafts = database.reportDraftDao().byProject(projectId)
        val materialHandovers = database.materialHandoverDao().byProject(projectId)
        val materialDeclarations = database.materialDeclarationDao().getByProject(projectId)
        if (
            notes.isEmpty() &&
            tasks.isEmpty() &&
            sitePhotos.isEmpty() &&
            reportDrafts.isEmpty() &&
            materialHandovers.isEmpty() &&
            materialDeclarations.isEmpty()
        ) {
            return null
        }
        return AuxProjectPayload(
            notes = notes,
            tasks = tasks,
            sitePhotos = sitePhotos,
            reportDrafts = reportDrafts,
            materialHandovers = materialHandovers,
            materialDeclarations = materialDeclarations
        )
    }

    private fun countRows(database: androidx.sqlite.db.SupportSQLiteDatabase, projectId: String): Map<String, Int> {
        return verificationTables.associateWith { table ->
            val sql = if (table == "projects") {
                "SELECT COUNT(*) FROM $table WHERE id = ?"
            } else {
                "SELECT COUNT(*) FROM $table WHERE projectId = ?"
            }
            database.query(sql, arrayOf(projectId)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private fun countRows(database: SQLiteDatabase, projectId: String): Map<String, Int> {
        return verificationTables.associateWith { table ->
            val sql = if (table == "projects") {
                "SELECT COUNT(*) FROM $table WHERE id = ?"
            } else {
                "SELECT COUNT(*) FROM $table WHERE projectId = ?"
            }
            database.rawQuery(sql, arrayOf(projectId)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private data class CoreProjectPayload(
        val importedFiles: List<ImportedFileEntity>,
        val nodes: List<GisNodeEntity>,
        val routes: List<GisRouteEntity>,
        val nodeProgress: List<NodeProgressEntity>,
        val workVolumeProgress: List<MaterialProgressEntity>,
        val dailyLogs: List<DailyLogEntity>,
        val dailyLogLines: List<DailyLogLineEntity>,
        val workCategories: List<WorkCategoryEntity>,
        val workPlans: List<WorkPlanEntity>
    )

    private data class AuxProjectPayload(
        val notes: List<NoteEntity>,
        val tasks: List<TaskEntity>,
        val sitePhotos: List<SitePhotoEntity>,
        val reportDrafts: List<ReportDraftEntity>,
        val materialHandovers: List<MaterialHandoverEntity>,
        val materialDeclarations: List<MaterialDeclarationEntity>
    )
}

private val LEGACY_CONTENT_DIRECTORIES = listOf(
    "db",
    "imports",
    "photos",
    "media",
    "thumbs",
    "Media"
)
