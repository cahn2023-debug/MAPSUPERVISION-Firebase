package com.mapsupervision.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mapsupervision.core.logging.AppLogger
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.data.db.entity.ReportDraftEntity
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.storage.ProjectStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ProjectScopedDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedDatabase: MapSupervisionDatabase,
    private val storageManager: ProjectStorageManager
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holders = LinkedHashMap<String, DatabaseHolder>()
    private val hydrationJobs = LinkedHashMap<String, Job>()
    private val idleTimeoutMs = 5 * 60 * 1000L
    @Volatile
    private var cleanupJob: Job? = null

    suspend fun databaseFor(projectId: String): MapSupervisionDatabase? {
        val project = sharedDatabase.projectDao().get(projectId) ?: return null
        if (project.storageMode != ProjectStorageMode.PROJECT_DB || project.projectDbPath.isBlank()) {
            return null
        }
        return openProjectDb(project)
    }

    suspend fun vacuumProjectDb(projectId: String) {
        databaseFor(projectId)?.openHelper?.writableDatabase?.execSQL("VACUUM")
    }

    private suspend fun openProjectDb(project: ProjectEntity): MapSupervisionDatabase = mutex.withLock {
        val resolvedProject = resolveProjectDbPath(project)
        val dbPath = resolvedProject.projectDbPath

        val existing = holders[dbPath]
        if (existing != null) {
            existing.lastAccessEpochMs = System.currentTimeMillis()
            ensureCleanupSchedulerLocked()
            return existing.database
        }

        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()
        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbFile.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                }
            })
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .build()
        AppLogger.d("project.db.open path=${dbFile.absolutePath}")
        val holder = DatabaseHolder(database)
        holders[dbPath] = holder
        try {
            ensureCleanupSchedulerLocked()
            prepareOpenedProjectDatabase(resolvedProject, dbPath, holder, "open")
        } catch (throwable: Throwable) {
            holders.remove(dbPath)
            runCatching { database.close() }
            throw throwable
        }
        database
    }

    private suspend fun resolveProjectDbPath(project: ProjectEntity): ProjectEntity {
        val scopedDbFile = storageManager.scopedProjectDbFile(project.slug)
        val scopedDbPath = scopedDbFile.absolutePath
        if (project.projectDbPath == scopedDbPath) {
            return project
        }

        val legacyDbFile = project.projectDbPath.takeIf { it.isNotBlank() }?.let(::File)
        if ((!scopedDbFile.exists() || scopedDbFile.length() == 0L) &&
            legacyDbFile != null &&
            legacyDbFile.exists() &&
            legacyDbFile.absolutePath != scopedDbPath
        ) {
            scopedDbFile.parentFile?.mkdirs()
            legacyDbFile.copyTo(scopedDbFile, overwrite = true)
        }

        sharedDatabase.projectDao().updateProjectDbPath(project.id, scopedDbPath)
        return project.copy(projectDbPath = scopedDbPath)
    }

    private suspend fun prepareOpenedProjectDatabase(
        project: ProjectEntity,
        dbPath: String,
        holder: DatabaseHolder,
        source: String
    ) {
        if (holder.isPrepared) {
            return
        }

        val database = holder.database
        ensureProjectRow(project, database, source)
        if (shouldUseLegacyBridge(project, database)) {
            runLegacyBridge(project, dbPath, database)
        } else {
            AppLogger.d("project.db.bridge_skip projectId=${project.id} reason=cutover_complete")
        }
        holder.isPrepared = true
    }

    private suspend fun runLegacyBridge(
        project: ProjectEntity,
        dbPath: String,
        projectDatabase: MapSupervisionDatabase
    ) {
        ensureCoreHydrated(project, projectDatabase)
        syncSharedFromScoped(project, projectDatabase)
        hydrateMaterialTables(project, projectDatabase)
        scheduleAuxHydration(project, dbPath, projectDatabase)
    }

    private suspend fun ensureProjectRow(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase,
        source: String
    ) {
        if (projectDatabase.projectDao().get(project.id) == null) {
            projectDatabase.projectDao().upsert(project)
            AppLogger.d("project.db.seed.ensure_project_row projectId=${project.id} source=$source")
        } else {
            AppLogger.d("project.db.seed.keep_project_row projectId=${project.id} source=$source")
        }
    }

    private fun isTableEmpty(
        database: MapSupervisionDatabase,
        projectId: String,
        table: String
    ): Boolean {
        val query = SimpleSQLiteQuery("SELECT EXISTS(SELECT 1 FROM `$table` WHERE `projectId` = ? LIMIT 1)", arrayOf(projectId))
        return database.openHelper.readableDatabase.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0) == 0
            } else {
                true
            }
        }
    }

    private suspend fun ensureCoreHydrated(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ) {
        ensureProjectRow(project, projectDatabase, "open")
        val projectId = project.id

        projectDatabase.withTransaction {
            projectDatabase.projectDao().upsert(project)

            if (isTableEmpty(projectDatabase, projectId, "imported_files")) {
                val list = sharedDatabase.importedFileDao().byProject(projectId)
                if (list.isNotEmpty()) projectDatabase.importedFileDao().upsertAll(list)
            }
            if (isTableEmpty(projectDatabase, projectId, "gis_node")) {
                val list = sharedDatabase.gisNodeDao().byProject(projectId)
                if (list.isNotEmpty()) projectDatabase.gisNodeDao().upsertAll(list)
            }
            val lookupBeforeRoutes = buildProjectBridgeLookup(
                sourceDatabase = sharedDatabase,
                targetDatabase = projectDatabase,
                projectId = projectId
            )
            if (isTableEmpty(projectDatabase, projectId, "gis_route")) {
                val list = sharedDatabase.gisRouteDao().byProject(projectId)
                if (list.isNotEmpty()) {
                    projectDatabase.gisRouteDao().upsertAll(list.map { it.normalizeForBridge(lookupBeforeRoutes) })
                }
            }
            val lookupAfterRoutes = buildProjectBridgeLookup(
                sourceDatabase = sharedDatabase,
                targetDatabase = projectDatabase,
                projectId = projectId
            )
            if (isTableEmpty(projectDatabase, projectId, "node_progress")) {
                val list = sharedDatabase.nodeProgressDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    projectDatabase.nodeProgressDao().upsert(entity)
                }
            }
            if (isTableEmpty(projectDatabase, projectId, "work_volume_progress")) {
                val list = sharedDatabase.workVolumeProgressDao().byProject(projectId)
                for (entity in list) projectDatabase.workVolumeProgressDao().upsert(entity)
            }
            if (isTableEmpty(projectDatabase, projectId, "daily_log")) {
                val list = sharedDatabase.dailyLogDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    projectDatabase.dailyLogDao().upsert(entity)
                }
            }
            if (isTableEmpty(projectDatabase, projectId, "daily_log_line")) {
                val list = sharedDatabase.dailyLogLineDao().byLogIds(
                    projectId,
                    sharedDatabase.dailyLogDao().byProject(projectId).map { it.id }
                )
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    projectDatabase.dailyLogLineDao().upsertAll(listOf(entity))
                }
            }
            if (isTableEmpty(projectDatabase, projectId, "work_categories")) {
                val list = sharedDatabase.workCategoryDao().byProject(projectId)
                for (entity in list) projectDatabase.workCategoryDao().upsert(entity)
            }
            if (isTableEmpty(projectDatabase, projectId, "work_plan")) {
                val list = sharedDatabase.workPlanDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    projectDatabase.workPlanDao().insert(entity)
                }
            }
        }
    }

    private suspend fun syncSharedFromScoped(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ) {
        val projectId = project.id
        sharedDatabase.withTransaction {
            val p = projectDatabase.projectDao().get(project.id) ?: project
            if (sharedDatabase.projectDao().get(p.id) == null) {
                sharedDatabase.projectDao().upsert(p)
            }

            // Core tables
            if (isTableEmpty(sharedDatabase, projectId, "imported_files")) {
                val list = projectDatabase.importedFileDao().byProject(projectId)
                if (list.isNotEmpty()) sharedDatabase.importedFileDao().upsertAll(list)
            }
            if (isTableEmpty(sharedDatabase, projectId, "gis_node")) {
                val list = projectDatabase.gisNodeDao().byProject(projectId)
                if (list.isNotEmpty()) sharedDatabase.gisNodeDao().upsertAll(list)
            }
            val lookupBeforeRoutes = buildProjectBridgeLookup(
                sourceDatabase = projectDatabase,
                targetDatabase = sharedDatabase,
                projectId = projectId
            )
            if (isTableEmpty(sharedDatabase, projectId, "gis_route")) {
                val list = projectDatabase.gisRouteDao().byProject(projectId)
                if (list.isNotEmpty()) {
                    sharedDatabase.gisRouteDao().upsertAll(list.map { it.normalizeForBridge(lookupBeforeRoutes) })
                }
            }
            val lookupAfterRoutes = buildProjectBridgeLookup(
                sourceDatabase = projectDatabase,
                targetDatabase = sharedDatabase,
                projectId = projectId
            )
            if (isTableEmpty(sharedDatabase, projectId, "node_progress")) {
                val list = projectDatabase.nodeProgressDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    sharedDatabase.nodeProgressDao().upsert(entity)
                }
            }
            if (isTableEmpty(sharedDatabase, projectId, "work_volume_progress")) {
                val list = projectDatabase.workVolumeProgressDao().byProject(projectId)
                for (entity in list) sharedDatabase.workVolumeProgressDao().upsert(entity)
            }
            if (isTableEmpty(sharedDatabase, projectId, "daily_log")) {
                val list = projectDatabase.dailyLogDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    sharedDatabase.dailyLogDao().upsert(entity)
                }
            }
            if (isTableEmpty(sharedDatabase, projectId, "daily_log_line")) {
                val list = projectDatabase.dailyLogLineDao().byLogIds(
                    projectId,
                    projectDatabase.dailyLogDao().byProject(projectId).map { it.id }
                )
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    sharedDatabase.dailyLogLineDao().upsertAll(listOf(entity))
                }
            }
            if (isTableEmpty(sharedDatabase, projectId, "work_categories")) {
                val list = projectDatabase.workCategoryDao().byProject(projectId)
                for (entity in list) sharedDatabase.workCategoryDao().upsert(entity)
            }
            if (isTableEmpty(sharedDatabase, projectId, "work_plan")) {
                val list = projectDatabase.workPlanDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(lookupAfterRoutes) }) {
                    sharedDatabase.workPlanDao().insert(entity)
                }
            }

            // Aux tables
            val auxLookup = buildProjectBridgeLookup(
                sourceDatabase = projectDatabase,
                targetDatabase = sharedDatabase,
                projectId = projectId
            )
            if (isTableEmpty(sharedDatabase, projectId, "note")) {
                val list = projectDatabase.noteDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    sharedDatabase.noteDao().insert(entity)
                }
            }
            if (isTableEmpty(sharedDatabase, projectId, "task")) {
                val list = projectDatabase.taskDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    sharedDatabase.taskDao().upsert(entity)
                }
            }
            if (isTableEmpty(sharedDatabase, projectId, "site_photos")) {
                val list = projectDatabase.sitePhotoDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    sharedDatabase.sitePhotoDao().upsert(entity)
                }
            }
            if (isTableEmpty(sharedDatabase, projectId, "report_draft")) {
                val list = projectDatabase.reportDraftDao().byProject(projectId)
                for (entity in list) sharedDatabase.reportDraftDao().upsert(entity)
            }
            if (isTableEmpty(sharedDatabase, projectId, "material_declaration")) {
                val list = projectDatabase.materialDeclarationDao().getByProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    sharedDatabase.materialDeclarationDao().insert(entity)
                }
            }
            val materialLookup = buildProjectBridgeLookup(
                sourceDatabase = projectDatabase,
                targetDatabase = sharedDatabase,
                projectId = projectId
            )
            if (isTableEmpty(sharedDatabase, projectId, "material_handover")) {
                val list = projectDatabase.materialHandoverDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(materialLookup) }) {
                    sharedDatabase.materialHandoverDao().upsert(entity)
                }
            }
        }
    }

    private suspend fun scheduleAuxHydration(
        project: ProjectEntity,
        dbPath: String,
        projectDatabase: MapSupervisionDatabase
    ) {
        val existingJob = hydrationJobs[dbPath]
        if (existingJob?.isActive == true) return
        if (existingJob != null) {
            hydrationJobs.remove(dbPath)
        }

        val needsHydration = AUX_TABLES.any { table ->
            isTableEmpty(projectDatabase, project.id, table) && !isTableEmpty(sharedDatabase, project.id, table)
        }
        if (!needsHydration) {
            AppLogger.d("project.db.seed.aux_skip projectId=${project.id} reason=all_tables_hydrated")
            return
        }

        AppLogger.d("project.db.seed.aux_schedule projectId=${project.id}")
        val job = scope.launch {
            try {
                delay(100L)
                hydrateAuxiliaryTables(project, projectDatabase)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mutex.withLock {
                    if (hydrationJobs[dbPath] === coroutineContext[Job]) {
                        hydrationJobs.remove(dbPath)
                    }
                }
            }
        }
        hydrationJobs[dbPath] = job
    }

    private suspend fun hydrateAuxiliaryTables(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ) {
        val projectId = project.id
        AppLogger.d("project.db.seed.aux_start projectId=${projectId}")
        projectDatabase.withTransaction {
            val auxLookup = buildProjectBridgeLookup(
                sourceDatabase = sharedDatabase,
                targetDatabase = projectDatabase,
                projectId = projectId
            )
            if (isTableEmpty(projectDatabase, projectId, "note")) {
                val list = sharedDatabase.noteDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    projectDatabase.noteDao().insert(entity)
                }
            }
            if (isTableEmpty(projectDatabase, projectId, "task")) {
                val list = sharedDatabase.taskDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    projectDatabase.taskDao().upsert(entity)
                }
            }
            if (isTableEmpty(projectDatabase, projectId, "site_photos")) {
                val list = sharedDatabase.sitePhotoDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    projectDatabase.sitePhotoDao().upsert(entity)
                }
            }
            if (isTableEmpty(projectDatabase, projectId, "report_draft")) {
                val list = sharedDatabase.reportDraftDao().byProject(projectId)
                for (entity in list) projectDatabase.reportDraftDao().upsert(entity)
            }
            if (isTableEmpty(projectDatabase, projectId, "material_declaration")) {
                val list = sharedDatabase.materialDeclarationDao().getByProject(projectId)
                for (entity in list.map { it.normalizeForBridge(auxLookup) }) {
                    projectDatabase.materialDeclarationDao().insert(entity)
                }
            }
            val materialLookup = buildProjectBridgeLookup(
                sourceDatabase = sharedDatabase,
                targetDatabase = projectDatabase,
                projectId = projectId
            )
            if (isTableEmpty(projectDatabase, projectId, "material_handover")) {
                val list = sharedDatabase.materialHandoverDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(materialLookup) }) {
                    projectDatabase.materialHandoverDao().upsert(entity)
                }
            }
        }
    }

    private suspend fun hydrateMaterialTables(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ) {
        val projectId = project.id
        projectDatabase.withTransaction {
            val materialLookup = buildProjectBridgeLookup(
                sourceDatabase = sharedDatabase,
                targetDatabase = projectDatabase,
                projectId = projectId
            )
            if (isTableEmpty(projectDatabase, projectId, "material_declaration")) {
                val list = sharedDatabase.materialDeclarationDao().getByProject(projectId)
                for (entity in list.map { it.normalizeForBridge(materialLookup) }) {
                    projectDatabase.materialDeclarationDao().insert(entity)
                }
            }
            val handoverLookup = buildProjectBridgeLookup(
                sourceDatabase = sharedDatabase,
                targetDatabase = projectDatabase,
                projectId = projectId
            )
            if (isTableEmpty(projectDatabase, projectId, "material_handover")) {
                val list = sharedDatabase.materialHandoverDao().byProject(projectId)
                for (entity in list.map { it.normalizeForBridge(handoverLookup) }) {
                    projectDatabase.materialHandoverDao().upsert(entity)
                }
            }
        }
    }

    private suspend fun shouldUseLegacyBridge(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ): Boolean {
        if (!isCutoverComplete(project, projectDatabase)) {
            return true
        }
        return false
    }

    private suspend fun isCutoverComplete(
        project: ProjectEntity,
        projectDatabase: MapSupervisionDatabase
    ): Boolean {
        if (projectDatabase.projectDao().get(project.id) == null) {
            return false
        }
        val sharedCounts = countRows(sharedDatabase, project.id, CUTOVER_TABLES)
        val scopedCounts = countRows(projectDatabase, project.id, CUTOVER_TABLES)
        return CUTOVER_TABLES.all { table -> sharedCounts[table] == scopedCounts[table] }
    }

    private fun countRows(
        database: MapSupervisionDatabase,
        projectId: String,
        tables: List<String>
    ): Map<String, Int> {
        return tables.associateWith { table ->
            val sql = if (table == "projects") {
                "SELECT COUNT(*) FROM `$table` WHERE `id` = ?"
            } else {
                "SELECT COUNT(*) FROM `$table` WHERE `projectId` = ?"
            }
            database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, arrayOf(projectId))).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private suspend fun closeIdleDatabases(nowEpochMs: Long = System.currentTimeMillis()): Boolean = mutex.withLock {
        val iterator = holders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowEpochMs - entry.value.lastAccessEpochMs >= idleTimeoutMs) {
                runCatching {
                    val db = entry.value.database.openHelper.writableDatabase
                    db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                }
                runCatching { entry.value.database.close() }
                AppLogger.d("project.db.close path=${entry.key}")
                iterator.remove()
            }
        }
        if (holders.isEmpty()) {
            cleanupJob = null
            return false
        }
        true
    }

    private fun ensureCleanupSchedulerLocked() {
        val runningJob = cleanupJob
        if (runningJob?.isActive == true) return
        cleanupJob = scope.launch {
            while (isActive) {
                delay(60_000L)
                if (!closeIdleDatabases()) {
                    return@launch
                }
            }
        }
    }

    internal fun isCleanupSchedulerRunningForTest(): Boolean = cleanupJob?.isActive == true

    internal suspend fun markAllDatabasesIdleForTest(lastAccessEpochMs: Long) = mutex.withLock {
        holders.values.forEach { it.lastAccessEpochMs = lastAccessEpochMs }
    }

    internal suspend fun runIdleCleanupForTest(nowEpochMs: Long): Boolean = closeIdleDatabases(nowEpochMs)

    internal suspend fun isCutoverCompleteForTest(projectId: String): Boolean {
        val project = sharedDatabase.projectDao().get(projectId) ?: return false
        val database = databaseFor(projectId) ?: return false
        return isCutoverComplete(project, database)
    }

    private class DatabaseHolder(
        val database: MapSupervisionDatabase,
        var lastAccessEpochMs: Long = System.currentTimeMillis(),
        var isPrepared: Boolean = false
    )

    private data class CoreProjectPayload(
        val importedFiles: List<ImportedFileEntity>,
        val nodes: List<GisNodeEntity>,
        val routes: List<GisRouteEntity>,
        val nodeProgress: List<NodeProgressEntity>,
        val workVolumeProgress: List<MaterialProgressEntity>,
        val dailyLogs: List<DailyLogEntity>,
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

private val CORE_TABLES = listOf(
    "imported_files",
    "gis_node",
    "gis_route",
    "node_progress",
    "work_volume_progress",
    "daily_log",
    "work_categories",
    "work_plan"
)

private val AUX_TABLES = listOf(
    "note",
    "task",
    "site_photos",
    "report_draft",
    "material_declaration",
    "material_handover"
)

private val CUTOVER_TABLES = listOf("projects") + CORE_TABLES + AUX_TABLES

