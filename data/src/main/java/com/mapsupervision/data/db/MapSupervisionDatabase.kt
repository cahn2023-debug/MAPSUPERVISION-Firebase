package com.mapsupervision.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mapsupervision.data.db.dao.AiDecisionCacheDao
import com.mapsupervision.data.db.dao.ChatHistoryDao
import com.mapsupervision.data.db.dao.DailyLogDao
import com.mapsupervision.data.db.dao.DailyLogLineDao
import com.mapsupervision.data.db.dao.DailyLogNodeDao
import com.mapsupervision.data.db.dao.DailyLogPhotoDao
import com.mapsupervision.data.db.dao.GisNodeDao
import com.mapsupervision.data.db.dao.GisRouteDao
import com.mapsupervision.data.db.dao.ImportedFileDao
import com.mapsupervision.data.db.dao.ImportAuditDao
import com.mapsupervision.data.db.dao.ImportConflictDao
import com.mapsupervision.data.db.dao.ImportSessionDao
import com.mapsupervision.data.db.dao.ImportVersionDao
import com.mapsupervision.data.db.dao.EventOutboxDao
import com.mapsupervision.data.db.dao.MaterialProgressDao
import com.mapsupervision.data.db.dao.NodeProgressDao
import com.mapsupervision.data.db.dao.NoteDao
import com.mapsupervision.data.db.dao.PhotoTagDao
import com.mapsupervision.data.db.dao.TaskDao
import com.mapsupervision.data.db.dao.ProjectDao
import com.mapsupervision.data.db.dao.SitePhotoDao
import com.mapsupervision.data.db.dao.ReportDraftDao
import com.mapsupervision.data.db.dao.RagDocumentEmbeddingDao
import com.mapsupervision.data.db.dao.WorkCategoryDao
import com.mapsupervision.data.db.entity.DailyLogEntity
import com.mapsupervision.data.db.entity.DailyLogLineEntity
import com.mapsupervision.data.db.entity.DailyLogNodeEntity
import com.mapsupervision.data.db.entity.DailyLogPhotoEntity
import com.mapsupervision.data.db.entity.AiDecisionCacheEntity
import com.mapsupervision.data.db.entity.ChatHistoryEntity
import com.mapsupervision.data.db.entity.ReportDraftEntity
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.GisRouteEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.ImportAuditEntity
import com.mapsupervision.data.db.entity.ImportConflictEntity
import com.mapsupervision.data.db.entity.ImportSessionEntity
import com.mapsupervision.data.db.entity.ImportVersionEntity
import com.mapsupervision.data.db.entity.EventOutboxEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.PhotoTagEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.SitePhotoEntity
import com.mapsupervision.data.db.entity.WorkCategoryEntity
import com.mapsupervision.data.db.entity.RagDocumentEmbeddingEntity

import java.util.UUID

import com.mapsupervision.data.db.entity.AiActionLogEntity
import com.mapsupervision.data.db.dao.AiActionLogDao
import com.mapsupervision.data.db.dao.MaterialHandoverDao
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import com.mapsupervision.data.db.entity.WorkPlanEntity
import com.mapsupervision.data.db.dao.WorkPlanDao
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import com.mapsupervision.data.db.dao.MaterialDeclarationDao

@Database(
    entities = [
        ProjectEntity::class,
        NodeProgressEntity::class,
        SitePhotoEntity::class,
        DailyLogEntity::class,
        DailyLogLineEntity::class,
        DailyLogNodeEntity::class,
        DailyLogPhotoEntity::class,
        GisNodeEntity::class,
        GisRouteEntity::class,
        ImportedFileEntity::class,
        ImportSessionEntity::class,
        ImportVersionEntity::class,
        ImportConflictEntity::class,
        ImportAuditEntity::class,
        EventOutboxEntity::class,
        MaterialProgressEntity::class,
        NoteEntity::class,
        PhotoTagEntity::class,
        TaskEntity::class,
        WorkCategoryEntity::class,
        AiDecisionCacheEntity::class,
        ChatHistoryEntity::class,
        ReportDraftEntity::class,
        AiActionLogEntity::class,
        WorkPlanEntity::class,
        MaterialHandoverEntity::class,
        MaterialDeclarationEntity::class,
        RagDocumentEmbeddingEntity::class
    ],
    version = 46,
    exportSchema = true
)
@TypeConverters(DbTypeConverters::class)
abstract class MapSupervisionDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun nodeProgressDao(): NodeProgressDao
    abstract fun sitePhotoDao(): SitePhotoDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun dailyLogLineDao(): DailyLogLineDao
    abstract fun dailyLogNodeDao(): DailyLogNodeDao
    abstract fun dailyLogPhotoDao(): DailyLogPhotoDao
    abstract fun gisNodeDao(): GisNodeDao
    abstract fun gisRouteDao(): GisRouteDao
    abstract fun importedFileDao(): ImportedFileDao
    abstract fun importSessionDao(): ImportSessionDao
    abstract fun importVersionDao(): ImportVersionDao
    abstract fun importConflictDao(): ImportConflictDao
    abstract fun importAuditDao(): ImportAuditDao
    abstract fun eventOutboxDao(): EventOutboxDao
    abstract fun workVolumeProgressDao(): MaterialProgressDao
    fun materialProgressDao(): MaterialProgressDao = workVolumeProgressDao()
    abstract fun noteDao(): NoteDao
    abstract fun photoTagDao(): PhotoTagDao
    abstract fun taskDao(): TaskDao
    abstract fun workCategoryDao(): WorkCategoryDao
    abstract fun aiDecisionCacheDao(): AiDecisionCacheDao
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun reportDraftDao(): ReportDraftDao
    abstract fun aiActionLogDao(): AiActionLogDao
    abstract fun workPlanDao(): WorkPlanDao
    abstract fun materialHandoverDao(): MaterialHandoverDao
    abstract fun materialDeclarationDao(): MaterialDeclarationDao
    abstract fun ragDocumentEmbeddingDao(): RagDocumentEmbeddingDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_node_projectId_code` ON `gis_node` (`projectId`, `code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_projectId_code` ON `gis_route` (`projectId`, `code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `weather` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `temperature` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `nodeCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `dateEpochDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `volume` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `categoryName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_categories` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId` ON `work_categories` (`projectId`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `metadataVersion` INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `projects` SET `updatedAtEpochMs` = `createdAtEpochMs` WHERE `updatedAtEpochMs` = 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `storageMode` TEXT NOT NULL DEFAULT 'LEGACY_SHARED'")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `projectDbPath` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `locationAccuracyM` REAL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `isGpsMocked` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `locationStatus` TEXT NOT NULL DEFAULT 'MISSING'")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_decision_cache` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `payloadHash` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId_capability_payloadHash` ON `ai_decision_cache` (`projectId`, `capability`, `payloadHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId` ON `ai_decision_cache` (`projectId`)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "index_daily_log_projectId",
                    "index_daily_log_createdAtEpochMs",
                    "index_gis_node_projectId",
                    "index_gis_node_code",
                    "index_gis_node_contractor",
                    "index_gis_route_projectId",
                    "index_gis_route_code",
                    "index_gis_route_contractor",
                    "index_imported_files_projectId",
                    "index_imported_files_fileType",
                    "index_material_progress_projectId",
                    "index_material_progress_nodeCode",
                    "index_material_progress_materialName",
                    "index_node_progress_projectId",
                    "index_node_progress_nodeCode",
                    "index_note_projectId",
                    "index_note_objectCode",
                    "index_note_createdAtEpochMs",
                    "index_note_projectId_objectCode",
                    "index_task_projectId",
                    "index_task_objectCode",
                    "index_task_status",
                    "index_task_createdAtEpochMs",
                    "index_task_projectId_objectCode",
                    "index_site_photos_projectId",
                    "index_site_photos_objectCode",
                    "index_site_photos_capturedAtEpochMs",
                    "index_site_photos_projectId_objectCode"
                ).forEach { indexName ->
                    db.execSQL("DROP INDEX IF EXISTS `$indexName`")
                }
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_progress_nodeCode` ON `material_progress` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_nodeCode` ON `node_progress` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId_createdAtEpochMs` ON `work_categories` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `routeCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `batchGroupId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `appliedNodeCodesCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `linkedPhotoIdsCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `photoMatchOffsetMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `tagCodesCsv` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedNodeCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedRouteCode` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedAtEpochMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchingTimeOffsetMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_history` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId` ON `chat_history` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId_createdAtEpochMs` ON `chat_history` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `report_draft` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `executiveSummary` TEXT NOT NULL,
                        `riskSection` TEXT NOT NULL,
                        `recommendedActionsCsv` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_draft_projectId_createdAtEpochMs` ON `report_draft` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val projectCols = getTableColumns(db, "projects")
                rebuildProjectsTable(db, projectCols.contains("projectCode"))
                rebuildImportedFilesTable(db)
                rebuildSitePhotosTable(db)
                rebuildTaskTable(db)
                rebuildDailyLogTable(db)
                rebuildNoteTable(db)
                rebuildWorkCategoriesTable(db)
            }

            private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    if (nameIdx != -1) {
                        while (cursor.moveToNext()) {
                            columns.add(cursor.getString(nameIdx))
                        }
                    }
                }
                return columns
            }

            private fun rebuildProjectsTable(db: SupportSQLiteDatabase, hasProjectCode: Boolean) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `projects_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `slug` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `metadataVersion` INTEGER NOT NULL DEFAULT 3,
                        `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                        `storageMode` TEXT NOT NULL DEFAULT 'LEGACY_SHARED',
                        `projectDbPath` TEXT NOT NULL DEFAULT '',
                        `projectCode` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `projects_new` (
                        `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`,
                        `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `projectCode`
                    )
                    SELECT
                        `id`,
                        `name`,
                        `slug`,
                        `isArchived`,
                        `createdAtEpochMs`,
                        COALESCE(`metadataVersion`, 3),
                        CASE
                            WHEN `updatedAtEpochMs` IS NULL OR `updatedAtEpochMs` = 0 THEN `createdAtEpochMs`
                            ELSE `updatedAtEpochMs`
                        END,
                        COALESCE(`storageMode`, 'LEGACY_SHARED'),
                        COALESCE(`projectDbPath`, ''),
                        ${if (hasProjectCode) "`projectCode`" else "NULL"}
                    FROM `projects`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `projects`")
                db.execSQL("ALTER TABLE `projects_new` RENAME TO `projects`")
            }

            private fun rebuildImportedFilesTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `imported_files_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `storedPath` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `importedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `imported_files_new` (
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    FROM `imported_files`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `imported_files`")
                db.execSQL("ALTER TABLE `imported_files_new` RENAME TO `imported_files`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")
            }

            private fun rebuildSitePhotosTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_photos_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `tagCodesCsv` TEXT NOT NULL,
                        `matchedNodeCode` TEXT,
                        `matchedRouteCode` TEXT,
                        `filePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationAccuracyM` REAL,
                        `isGpsMocked` INTEGER NOT NULL,
                        `locationStatus` TEXT NOT NULL,
                        `engineer` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `matchedAtEpochMs` INTEGER NOT NULL,
                        `matchingTimeOffsetMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `site_photos_new` (
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`,
                        `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`,
                        `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`,
                        `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`,
                        `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    FROM `site_photos`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")
            }

            private fun rebuildTaskTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `task_new` (
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`
                    FROM `task`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")
            }

            private fun rebuildDailyLogTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_log_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `workItem` TEXT NOT NULL,
                        `manpower` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `weather` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `nodeCode` TEXT,
                        `routeCode` TEXT,
                        `dateEpochDay` INTEGER NOT NULL,
                        `volume` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `batchGroupId` TEXT NOT NULL,
                        `appliedNodeCodesCsv` TEXT NOT NULL,
                        `linkedPhotoIdsCsv` TEXT NOT NULL,
                        `photoMatchOffsetMinutes` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val cols = getTableColumns(db, "daily_log")
                val selectWeather = if (cols.contains("weather")) "`weather`" else "''"
                val selectTemperature = if (cols.contains("temperature")) "`temperature`" else "0.0"
                val selectNodeCode = if (cols.contains("nodeCode")) "`nodeCode`" else "NULL"
                val selectRouteCode = if (cols.contains("routeCode")) "`routeCode`" else "NULL"
                val selectDateEpochDay = if (cols.contains("dateEpochDay")) "`dateEpochDay`" else "0"
                val selectVolume = if (cols.contains("volume")) "`volume`" else "0.0"
                val selectUnit = if (cols.contains("unit")) "`unit`" else "''"
                val selectCategoryName = if (cols.contains("categoryName")) "`categoryName`" else "''"
                val selectBatchGroupId = if (cols.contains("batchGroupId")) "`batchGroupId`" else "''"
                val selectAppliedNodeCodesCsv = if (cols.contains("appliedNodeCodesCsv")) "`appliedNodeCodesCsv`" else "''"
                val selectLinkedPhotoIdsCsv = if (cols.contains("linkedPhotoIdsCsv")) "`linkedPhotoIdsCsv`" else "''"
                val selectPhotoMatchOffsetMinutes = if (cols.contains("photoMatchOffsetMinutes")) "`photoMatchOffsetMinutes`" else "0"

                db.execSQL(
                    """
                    INSERT INTO `daily_log_new` (
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`,
                        `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`,
                        `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`,
                        `linkedPhotoIdsCsv`, `photoMatchOffsetMinutes`
                    )
                    SELECT
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`,
                        COALESCE($selectWeather, ''),
                        COALESCE($selectTemperature, 0.0),
                        $selectNodeCode,
                        $selectRouteCode,
                        COALESCE($selectDateEpochDay, 0),
                        COALESCE($selectVolume, 0.0),
                        COALESCE($selectUnit, ''),
                        COALESCE($selectCategoryName, ''),
                        COALESCE($selectBatchGroupId, ''),
                        COALESCE($selectAppliedNodeCodesCsv, ''),
                        COALESCE($selectLinkedPhotoIdsCsv, ''),
                        COALESCE($selectPhotoMatchOffsetMinutes, 0)
                    FROM `daily_log`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
            }

            private fun rebuildNoteTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `note_new` (
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    FROM `note`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")
            }

            private fun rebuildWorkCategoriesTable(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_categories_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `work_categories_new` (
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    FROM `work_categories`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `work_categories`")
                db.execSQL("ALTER TABLE `work_categories_new` RENAME TO `work_categories`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId` ON `work_categories` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId_createdAtEpochMs` ON `work_categories` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add points column
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `points` TEXT NOT NULL DEFAULT ''")

                // 2. Read all nodes to locate coordinates
                val nodesMap = mutableMapOf<String, Pair<Double, Double>>()
                db.query("SELECT `code`, `latitude`, `longitude` FROM `gis_node`").use { cursor ->
                    val codeIdx = cursor.getColumnIndex("code")
                    val latIdx = cursor.getColumnIndex("latitude")
                    val lonIdx = cursor.getColumnIndex("longitude")
                    if (codeIdx != -1 && latIdx != -1 && lonIdx != -1) {
                        while (cursor.moveToNext()) {
                            val code = cursor.getString(codeIdx)
                            val lat = cursor.getDouble(latIdx)
                            val lon = cursor.getDouble(lonIdx)
                            nodesMap[code.trim().uppercase()] = lat to lon
                        }
                    }
                }

                // 3. Read all routes
                data class TempRoute(
                    val id: String,
                    val projectId: String,
                    val code: String,
                    val contractor: String,
                    val startNodeCode: String,
                    val endNodeCode: String
                )
                val routesList = mutableListOf<TempRoute>()
                db.query("SELECT `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode` FROM `gis_route`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val projIdx = cursor.getColumnIndex("projectId")
                    val codeIdx = cursor.getColumnIndex("code")
                    val contrIdx = cursor.getColumnIndex("contractor")
                    val startIdx = cursor.getColumnIndex("startNodeCode")
                    val endIdx = cursor.getColumnIndex("endNodeCode")
                    if (idIdx != -1 && projIdx != -1 && codeIdx != -1 && contrIdx != -1 && startIdx != -1 && endIdx != -1) {
                        while (cursor.moveToNext()) {
                            routesList.add(
                                TempRoute(
                                    id = cursor.getString(idIdx),
                                    projectId = cursor.getString(projIdx),
                                    code = cursor.getString(codeIdx),
                                    contractor = cursor.getString(contrIdx),
                                    startNodeCode = cursor.getString(startIdx),
                                    endNodeCode = cursor.getString(endIdx)
                                )
                            )
                        }
                    }
                }

                // Helper to compute distance between coordinates
                fun distanceMeters(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
                    val earthRadius = 6_371_000.0
                    val lat1 = Math.toRadians(p1.first)
                    val lat2 = Math.toRadians(p2.first)
                    val dLat = lat2 - lat1
                    val dLon = Math.toRadians(p2.second - p1.second)
                    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                            Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
                    return 2 * earthRadius * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
                }

                val segmentRegex = Regex("^(.*)_S(\\d+)$", RegexOption.IGNORE_CASE)
                val groupedRoutes = routesList.groupBy { route ->
                    val match = segmentRegex.find(route.code)
                    if (match != null) match.groupValues[1].trim().uppercase() else route.code.trim().uppercase()
                }

                groupedRoutes.forEach { (baseRouteCode, segments) ->
                    val sortedSegments = segments.sortedBy { route ->
                        val match = segmentRegex.find(route.code)
                        if (match != null) match.groupValues[2].toIntOrNull() ?: 1 else 1
                    }

                    val firstSeg = sortedSegments.first()
                    val lastSeg = sortedSegments.last()

                    // Collect all segment endpoints coordinates in order
                    val mergedPoints = mutableListOf<Pair<Double, Double>>()
                    for (seg in sortedSegments) {
                        val startCoord = nodesMap[seg.startNodeCode.trim().uppercase()]
                        val endCoord = nodesMap[seg.endNodeCode.trim().uppercase()]
                        if (startCoord != null) {
                            if (mergedPoints.isEmpty()) {
                                mergedPoints.add(startCoord)
                            } else {
                                val last = mergedPoints.last()
                                if (distanceMeters(last, startCoord) > 1.0) {
                                    mergedPoints.add(startCoord)
                                }
                            }
                        }
                        if (endCoord != null) {
                            if (mergedPoints.isEmpty()) {
                                mergedPoints.add(endCoord)
                            } else {
                                val last = mergedPoints.last()
                                if (distanceMeters(last, endCoord) > 1.0) {
                                    mergedPoints.add(endCoord)
                                }
                            }
                        }
                    }

                    if (mergedPoints.size > 1) {
                        val startPoint = mergedPoints.first()
                        val endPoint = mergedPoints.last()

                        val startCode = "${baseRouteCode}_P1".uppercase()
                        val endCode = "${baseRouteCode}_P${mergedPoints.size}".uppercase()

                        // Insert start/end nodes if they don't exist
                        db.execSQL(
                            "INSERT OR IGNORE INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(UUID.randomUUID().toString(), firstSeg.projectId, startCode, firstSeg.contractor, startPoint.first, startPoint.second, "", "")
                        )
                        db.execSQL(
                            "INSERT OR IGNORE INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(UUID.randomUUID().toString(), lastSeg.projectId, endCode, lastSeg.contractor, endPoint.first, endPoint.second, "", "")
                        )

                        // Serialize points list: latitude,longitude;latitude,longitude
                        val pointsStr = mergedPoints.joinToString(";") { "${it.first},${it.second}" }

                        // Insert the unified route
                        db.execSQL(
                            "INSERT OR REPLACE INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(UUID.randomUUID().toString(), firstSeg.projectId, baseRouteCode, firstSeg.contractor, startCode, endCode, pointsStr)
                        )

                        // Delete legacy segments
                        segments.forEach { seg ->
                            db.execSQL("DELETE FROM `gis_route` WHERE `id` = ?", arrayOf(seg.id))
                            // Delete intermediate node vertices
                            db.execSQL("DELETE FROM `gis_node` WHERE `code` = ?", arrayOf(seg.startNodeCode))
                            db.execSQL("DELETE FROM `gis_node` WHERE `code` = ?", arrayOf(seg.endNodeCode))
                        }
                    }
                }

                // 4. Group and aggregate Node Progress
                data class TempNodeProgress(
                    val id: String,
                    val projectId: String,
                    val nodeCode: String,
                    val planned: Float,
                    val actual: Float,
                    val remain: Float,
                    val delayed: Int,
                    val updatedAtEpochMs: Long
                )
                val progressList = mutableListOf<TempNodeProgress>()
                db.query("SELECT `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs` FROM `node_progress`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val projIdx = cursor.getColumnIndex("projectId")
                    val nodeIdx = cursor.getColumnIndex("nodeCode")
                    val planIdx = cursor.getColumnIndex("planned")
                    val actIdx = cursor.getColumnIndex("actual")
                    val remIdx = cursor.getColumnIndex("remain")
                    val delIdx = cursor.getColumnIndex("delayed")
                    val upIdx = cursor.getColumnIndex("updatedAtEpochMs")
                    if (idIdx != -1 && projIdx != -1 && nodeIdx != -1 && planIdx != -1 && actIdx != -1 && remIdx != -1 && delIdx != -1 && upIdx != -1) {
                        while (cursor.moveToNext()) {
                            progressList.add(
                                TempNodeProgress(
                                    id = cursor.getString(idIdx),
                                    projectId = cursor.getString(projIdx),
                                    nodeCode = cursor.getString(nodeIdx),
                                    planned = cursor.getFloat(planIdx),
                                    actual = cursor.getFloat(actIdx),
                                    remain = cursor.getFloat(remIdx),
                                    delayed = cursor.getInt(delIdx),
                                    updatedAtEpochMs = cursor.getLong(upIdx)
                                )
                            )
                        }
                    }
                }

                val progressGroups = progressList.groupBy { progress ->
                    val match = segmentRegex.find(progress.nodeCode)
                    if (match != null) match.groupValues[1].trim().uppercase() else progress.nodeCode.trim().uppercase()
                }

                progressGroups.forEach { (baseRouteCode, items) ->
                    // Find if it was segmented (has items with _S pattern)
                    val segmentedItems = items.filter { segmentRegex.containsMatchIn(it.nodeCode) }
                    if (segmentedItems.isNotEmpty()) {
                        val sumPlanned = items.sumOf { it.planned.toDouble() }.toFloat()
                        val sumActual = items.sumOf { it.actual.toDouble() }.toFloat()
                        val sumRemain = items.sumOf { it.remain.toDouble() }.toFloat()
                        val isDelayed = items.any { it.delayed != 0 }
                        val maxUpdated = items.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
                        val firstItem = items.first()

                        // Insert aggregated progress
                        db.execSQL(
                            "INSERT OR REPLACE INTO `node_progress` (`id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(UUID.randomUUID().toString(), firstItem.projectId, baseRouteCode, sumPlanned, sumActual, sumRemain, if (isDelayed) 1 else 0, maxUpdated)
                        )

                        // Delete old progress records
                        segmentedItems.forEach { item ->
                            db.execSQL("DELETE FROM `node_progress` WHERE `id` = ?", arrayOf(item.id))
                        }
                    }
                }

                // 5. Group and aggregate Material Progress
                data class TempMaterialProgress(
                    val id: String,
                    val projectId: String,
                    val nodeCode: String,
                    val materialName: String,
                    val plannedQty: Float,
                    val actualQty: Float,
                    val updatedAtEpochMs: Long
                )
                val matProgressList = mutableListOf<TempMaterialProgress>()
                db.query("SELECT `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs` FROM `material_progress`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val projIdx = cursor.getColumnIndex("projectId")
                    val nodeIdx = cursor.getColumnIndex("nodeCode")
                    val nameIdx = cursor.getColumnIndex("materialName")
                    val planIdx = cursor.getColumnIndex("plannedQty")
                    val actIdx = cursor.getColumnIndex("actualQty")
                    val upIdx = cursor.getColumnIndex("updatedAtEpochMs")
                    if (idIdx != -1 && projIdx != -1 && nodeIdx != -1 && nameIdx != -1 && planIdx != -1 && actIdx != -1 && upIdx != -1) {
                        while (cursor.moveToNext()) {
                            matProgressList.add(
                                TempMaterialProgress(
                                    id = cursor.getString(idIdx),
                                    projectId = cursor.getString(projIdx),
                                    nodeCode = cursor.getString(nodeIdx),
                                    materialName = cursor.getString(nameIdx),
                                    plannedQty = cursor.getFloat(planIdx),
                                    actualQty = cursor.getFloat(actIdx),
                                    updatedAtEpochMs = cursor.getLong(upIdx)
                                )
                            )
                        }
                    }
                }

                val matGroups = matProgressList.groupBy { mat ->
                    val match = segmentRegex.find(mat.nodeCode)
                    val baseCode = if (match != null) match.groupValues[1].trim().uppercase() else mat.nodeCode.trim().uppercase()
                    baseCode to mat.materialName.trim()
                }

                matGroups.forEach { (key, items) ->
                    val (baseRouteCode, materialName) = key
                    val segmentedItems = items.filter { segmentRegex.containsMatchIn(it.nodeCode) }
                    if (segmentedItems.isNotEmpty()) {
                        val sumPlanned = items.sumOf { it.plannedQty.toDouble() }.toFloat()
                        val sumActual = items.sumOf { it.actualQty.toDouble() }.toFloat()
                        val maxUpdated = items.maxOfOrNull { it.updatedAtEpochMs } ?: 0L
                        val firstItem = items.first()

                        db.execSQL(
                            "INSERT OR REPLACE INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(UUID.randomUUID().toString(), firstItem.projectId, baseRouteCode, materialName, sumPlanned, sumActual, maxUpdated)
                        )

                        segmentedItems.forEach { item ->
                            db.execSQL("DELETE FROM `material_progress` WHERE `id` = ?", arrayOf(item.id))
                        }
                    }
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add designLength column to gis_route
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `designLength` TEXT DEFAULT NULL")

                // 2. Read all routes
                data class TempRoute(
                    val id: String,
                    val startNodeCode: String,
                    val endNodeCode: String
                )
                val routesList = mutableListOf<TempRoute>()
                db.query("SELECT `id`, `startNodeCode`, `endNodeCode` FROM `gis_route`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val startIdx = cursor.getColumnIndex("startNodeCode")
                    val endIdx = cursor.getColumnIndex("endNodeCode")
                    if (idIdx != -1 && startIdx != -1 && endIdx != -1) {
                        while (cursor.moveToNext()) {
                            routesList.add(
                                TempRoute(
                                    id = cursor.getString(idIdx),
                                    startNodeCode = cursor.getString(startIdx),
                                    endNodeCode = cursor.getString(endIdx)
                                )
                            )
                        }
                    }
                }

                // 3. Read all nodes to locate materialSummary
                val nodesMap = mutableMapOf<String, Pair<String, String>>()
                db.query("SELECT `id`, `code`, `materialSummary` FROM `gis_node`").use { cursor ->
                    val idIdx = cursor.getColumnIndex("id")
                    val codeIdx = cursor.getColumnIndex("code")
                    val summaryIdx = cursor.getColumnIndex("materialSummary")
                    if (idIdx != -1 && codeIdx != -1 && summaryIdx != -1) {
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idIdx)
                            val code = cursor.getString(codeIdx).trim().uppercase()
                            val summary = cursor.getString(summaryIdx).orEmpty()
                            nodesMap[code] = id to summary
                        }
                    }
                }

                // Helper to extract routeLength
                fun extractLength(summary: String): String? {
                    return summary.split('\n')
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("routeLength:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                }

                // Helper to remove routeLength line from summary
                fun cleanSummary(summary: String): String {
                    return summary.split('\n')
                        .filter { !it.trim().startsWith("routeLength:", ignoreCase = true) }
                        .joinToString("\n")
                        .trim()
                }

                // 4. Update routes with designLength and clean up node summaries
                routesList.forEach { route ->
                    val startNodeData = nodesMap[route.startNodeCode.trim().uppercase()]
                    val endNodeData = nodesMap[route.endNodeCode.trim().uppercase()]

                    var lengthVal: String? = null
                    if (startNodeData != null) {
                        lengthVal = extractLength(startNodeData.second)
                    }
                    if (lengthVal == null && endNodeData != null) {
                        lengthVal = extractLength(endNodeData.second)
                    }

                    if (lengthVal != null) {
                        // Update route designLength
                        db.execSQL(
                            "UPDATE `gis_route` SET `designLength` = ? WHERE `id` = ?",
                            arrayOf(lengthVal, route.id)
                        )

                        // Clean up node summaries
                        if (startNodeData != null && startNodeData.second.contains("routeLength:", ignoreCase = true)) {
                            val cleaned = cleanSummary(startNodeData.second)
                            db.execSQL(
                                "UPDATE `gis_node` SET `materialSummary` = ? WHERE `id` = ?",
                                arrayOf(cleaned, startNodeData.first)
                            )
                            nodesMap[route.startNodeCode.trim().uppercase()] = startNodeData.first to cleaned
                        }
                        if (endNodeData != null && endNodeData.second.contains("routeLength:", ignoreCase = true)) {
                            val cleaned = cleanSummary(endNodeData.second)
                            db.execSQL(
                                "UPDATE `gis_node` SET `materialSummary` = ? WHERE `id` = ?",
                                arrayOf(cleaned, endNodeData.first)
                            )
                            nodesMap[route.endNodeCode.trim().uppercase()] = endNodeData.first to cleaned
                        }
                    }
                }
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. projects
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `projects_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `slug` TEXT NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `metadataVersion` INTEGER NOT NULL DEFAULT 3,
                        `updatedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                        `storageMode` TEXT NOT NULL DEFAULT 'LEGACY_SHARED',
                        `projectDbPath` TEXT NOT NULL DEFAULT '',
                        `projectCode` TEXT,
                        PRIMARY KEY(`id`),
                        CHECK (`storageMode` IN ('LEGACY_SHARED', 'PROJECT_DB'))
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `projects_new` (
                        `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `projectCode`
                    )
                    SELECT
                        `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `projectCode`
                    FROM `projects`
                """.trimIndent())
                db.execSQL("DROP TABLE `projects`")
                db.execSQL("ALTER TABLE `projects_new` RENAME TO `projects`")

                // 2. imported_files
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `imported_files_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `storedPath` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `importedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `imported_files_new` (
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`
                    FROM `imported_files`
                """.trimIndent())
                db.execSQL("DROP TABLE `imported_files`")
                db.execSQL("ALTER TABLE `imported_files_new` RENAME TO `imported_files`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")

                // 3. gis_node
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gis_node_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `mapNumberLabel` TEXT NOT NULL,
                        `materialSummary` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `gis_node_new` (
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`
                    FROM `gis_node`
                """.trimIndent())
                db.execSQL("DROP TABLE `gis_node`")
                db.execSQL("ALTER TABLE `gis_node_new` RENAME TO `gis_node`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_node_importedFileId` ON `gis_node` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_node_projectId_code` ON `gis_node` (`projectId`, `code`)")

                // 4. gis_route
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gis_route_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `startNodeCode` TEXT NOT NULL,
                        `endNodeCode` TEXT NOT NULL,
                        `points` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        `designLength` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `gis_route_new` (
                        `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`, `designLength`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`, `designLength`
                    FROM `gis_route`
                """.trimIndent())
                db.execSQL("DROP TABLE `gis_route`")
                db.execSQL("ALTER TABLE `gis_route_new` RENAME TO `gis_route`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_importedFileId` ON `gis_route` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_route_projectId_code` ON `gis_route` (`projectId`, `code`)")

                // 5. node_progress
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `node_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `planned` REAL NOT NULL,
                        `actual` REAL NOT NULL,
                        `remain` REAL NOT NULL,
                        `delayed` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `node_progress_new` (
                        `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`
                    FROM `node_progress`
                """.trimIndent())
                db.execSQL("DROP TABLE `node_progress`")
                db.execSQL("ALTER TABLE `node_progress_new` RENAME TO `node_progress`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_projectId_nodeCode` ON `node_progress` (`projectId`, `nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_nodeCode` ON `node_progress` (`nodeCode`)")

                // 6. material_progress
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `material_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `plannedQty` REAL NOT NULL,
                        `actualQty` REAL NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `material_progress_new` (
                        `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`
                    FROM `material_progress`
                """.trimIndent())
                db.execSQL("DROP TABLE `material_progress`")
                db.execSQL("ALTER TABLE `material_progress_new` RENAME TO `material_progress`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_material_progress_projectId_nodeCode_materialName` ON `material_progress` (`projectId`, `nodeCode`, `materialName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_progress_nodeCode` ON `material_progress` (`nodeCode`)")

                // 7. daily_log
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_log_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `workItem` TEXT NOT NULL,
                        `manpower` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `weather` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `nodeCode` TEXT,
                        `routeCode` TEXT,
                        `dateEpochDay` INTEGER NOT NULL,
                        `volume` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `batchGroupId` TEXT NOT NULL,
                        `appliedNodeCodesCsv` TEXT NOT NULL,
                        `linkedPhotoIdsCsv` TEXT NOT NULL,
                        `photoMatchOffsetMinutes` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `daily_log_new` (
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`, `linkedPhotoIdsCsv`, `photoMatchOffsetMinutes`
                    )
                    SELECT
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`, `linkedPhotoIdsCsv`, `photoMatchOffsetMinutes`
                    FROM `daily_log`
                """.trimIndent())
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")

                // 8. site_photos
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `site_photos_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `tagCodesCsv` TEXT NOT NULL,
                        `matchedNodeCode` TEXT,
                        `matchedRouteCode` TEXT,
                        `filePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationAccuracyM` REAL,
                        `isGpsMocked` INTEGER NOT NULL,
                        `locationStatus` TEXT NOT NULL,
                        `engineer` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `matchedAtEpochMs` INTEGER NOT NULL,
                        `matchingTimeOffsetMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        CHECK (`locationStatus` IN ('OK', 'MISSING', 'INACCURATE'))
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `site_photos_new` (
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`
                    FROM `site_photos`
                """.trimIndent())
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")

                // 9. task
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        CHECK (`status` IN ('TODO', 'IN_PROGRESS', 'COMPLETED'))
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `task_new` (
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `title`, `description`,
                        CASE
                            WHEN `status` = 'OPEN' THEN 'TODO'
                            WHEN `status` = 'DONE' THEN 'COMPLETED'
                            ELSE `status`
                        END,
                        `createdAtEpochMs`, `completedAtEpochMs`
                    FROM `task`
                """.trimIndent())
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")

                // 10. note
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `note_new` (
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`
                    FROM `note`
                """.trimIndent())
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")

                // 11. work_categories
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `work_categories_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `work_categories_new` (
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `name`, `unit`, `createdAtEpochMs`
                    FROM `work_categories`
                """.trimIndent())
                db.execSQL("DROP TABLE `work_categories`")
                db.execSQL("ALTER TABLE `work_categories_new` RENAME TO `work_categories`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId` ON `work_categories` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_categories_projectId_createdAtEpochMs` ON `work_categories` (`projectId`, `createdAtEpochMs`)")

                // 12. ai_decision_cache
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_decision_cache_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `capability` TEXT NOT NULL,
                        `payloadHash` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `ai_decision_cache_new` (
                        `id`, `projectId`, `capability`, `payloadHash`, `resultJson`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `capability`, `payloadHash`, `resultJson`, `createdAtEpochMs`
                    FROM `ai_decision_cache`
                """.trimIndent())
                db.execSQL("DROP TABLE `ai_decision_cache`")
                db.execSQL("ALTER TABLE `ai_decision_cache_new` RENAME TO `ai_decision_cache`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId_capability_payloadHash` ON `ai_decision_cache` (`projectId`, `capability`, `payloadHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_decision_cache_projectId` ON `ai_decision_cache` (`projectId`)")

                // 13. chat_history
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_history_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `chat_history_new` (
                        `id`, `projectId`, `role`, `text`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `role`, `text`, `createdAtEpochMs`
                    FROM `chat_history`
                """.trimIndent())
                db.execSQL("DROP TABLE `chat_history`")
                db.execSQL("ALTER TABLE `chat_history_new` RENAME TO `chat_history`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId_createdAtEpochMs` ON `chat_history` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_history_projectId` ON `chat_history` (`projectId`)")

                // 14. report_draft
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `report_draft_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `executiveSummary` TEXT NOT NULL,
                        `riskSection` TEXT NOT NULL,
                        `recommendedActionsCsv` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `report_draft_new` (
                        `id`, `projectId`, `title`, `executiveSummary`, `riskSection`, `recommendedActionsCsv`, `createdAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `title`, `executiveSummary`, `riskSection`, `recommendedActionsCsv`, `createdAtEpochMs`
                    FROM `report_draft`
                """.trimIndent())
                db.execSQL("DROP TABLE `report_draft`")
                db.execSQL("ALTER TABLE `report_draft_new` RENAME TO `report_draft`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_report_draft_projectId_createdAtEpochMs` ON `report_draft` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `material_progress` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ai_action_log` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `rawInput` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `draftJson` TEXT NOT NULL,
                        `confidence` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_action_log_projectId` ON `ai_action_log` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_action_log_projectId_timestamp` ON `ai_action_log` (`projectId`, `timestamp`)")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `work_plan` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `plannedDateEpochDay` INTEGER NOT NULL,
                        `nodeCode` TEXT,
                        `routeCode` TEXT,
                        `taskId` TEXT,
                        `sourceRawInput` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_plan_projectId_plannedDateEpochDay` ON `work_plan` (`projectId`, `plannedDateEpochDay`)")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT 'IMAGE'")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT 'image/jpeg'")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gis_node_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `mapNumberLabel` TEXT NOT NULL,
                        `workVolumeSummary` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `gis_node_new` (
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`, `importedFileId`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`
                    FROM `gis_node`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `gis_node`")
                db.execSQL("ALTER TABLE `gis_node_new` RENAME TO `gis_node`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_node_importedFileId` ON `gis_node` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_node_projectId_code` ON `gis_node` (`projectId`, `code`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_volume_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `plannedQty` REAL NOT NULL,
                        `actualQty` REAL NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `unit` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `work_volume_progress_new` (
                        `id`, `projectId`, `nodeCode`, `workName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`, `unit`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`, `unit`
                    FROM `material_progress`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `material_progress`")
                db.execSQL("ALTER TABLE `work_volume_progress_new` RENAME TO `work_volume_progress`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_volume_progress_projectId_nodeCode_workName` ON `work_volume_progress` (`projectId`, `nodeCode`, `workName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_nodeCode` ON `work_volume_progress` (`nodeCode`)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `material_handover` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `handoverDateEpochDay` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_projectId` ON `material_handover` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_nodeCode` ON `material_handover` (`nodeCode`)")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `material_declaration` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `projectId` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `ratio` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_declaration_projectId` ON `material_declaration` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_declaration_projectId_workName` ON `material_declaration` (`projectId`, `workName`)")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rag_document_embedding` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `docType` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `sourceCode` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `embeddingBlob` BLOB NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_docType` ON `rag_document_embedding` (`projectId`, `docType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_sourceCode` ON `rag_document_embedding` (`projectId`, `sourceCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_contentHash` ON `rag_document_embedding` (`projectId`, `contentHash`)")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `work_plan` ADD COLUMN `quantity` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `work_plan` ADD COLUMN `unit` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `work_plan` ADD COLUMN `batchGroupId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `address` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `captureNote` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `nodeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `routeId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodeId` ON `daily_log` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_routeId` ON `daily_log` (`routeId`)")

                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedNodeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `site_photos` ADD COLUMN `matchedRouteId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedNodeId` ON `site_photos` (`matchedNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedRouteId` ON `site_photos` (`matchedRouteId`)")

                db.execSQL("ALTER TABLE `work_volume_progress` ADD COLUMN `nodeId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_nodeId` ON `work_volume_progress` (`nodeId`)")

                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `startNodeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `endNodeId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_startNodeId` ON `gis_route` (`startNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_endNodeId` ON `gis_route` (`endNodeId`)")

                db.execSQL("ALTER TABLE `material_handover` ADD COLUMN `nodeId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_nodeId` ON `material_handover` (`nodeId`)")

                db.execSQL("ALTER TABLE `material_declaration` ADD COLUMN `batchId` TEXT DEFAULT NULL")

                db.execSQL("ALTER TABLE `work_plan` ADD COLUMN `nodeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `work_plan` ADD COLUMN `routeId` TEXT DEFAULT NULL")

                db.execSQL("ALTER TABLE `note` ADD COLUMN `objectNodeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `note` ADD COLUMN `objectRouteId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectNodeId` ON `note` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectRouteId` ON `note` (`objectRouteId`)")

                db.execSQL("ALTER TABLE `task` ADD COLUMN `objectNodeId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `task` ADD COLUMN `objectRouteId` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectNodeId` ON `task` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectRouteId` ON `task` (`objectRouteId`)")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL("ALTER TABLE `projects` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `projects` ADD COLUMN `deletedAtEpochMs` INTEGER DEFAULT NULL")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `node_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `planned` REAL NOT NULL,
                        `actual` REAL NOT NULL,
                        `remain` REAL NOT NULL,
                        `delayed` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `node_progress_new` (
                        `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`, 0, NULL
                    FROM `node_progress`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `node_progress`")
                db.execSQL("ALTER TABLE `node_progress_new` RENAME TO `node_progress`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_projectId_nodeCode` ON `node_progress` (`projectId`, `nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_nodeCode` ON `node_progress` (`nodeCode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_photos_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `tagCodesCsv` TEXT NOT NULL,
                        `matchedNodeCode` TEXT,
                        `matchedRouteCode` TEXT,
                        `filePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationAccuracyM` REAL,
                        `isGpsMocked` INTEGER NOT NULL,
                        `locationStatus` TEXT NOT NULL,
                        `engineer` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `matchedAtEpochMs` INTEGER NOT NULL,
                        `matchingTimeOffsetMs` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `address` TEXT,
                        `captureNote` TEXT,
                        `matchedNodeId` TEXT,
                        `matchedRouteId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `remoteUrl` TEXT,
                        `lastSyncAttemptEpochMs` INTEGER,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `site_photos_new` (
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`, `filePath`, `thumbnailPath`,
                        `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`,
                        `matchedAtEpochMs`, `matchingTimeOffsetMs`, `mediaType`, `mimeType`, `durationMs`, `address`, `captureNote`,
                        `matchedNodeId`, `matchedRouteId`, `updatedAtEpochMs`, `syncStatus`, `remoteUrl`, `lastSyncAttemptEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `tagCodesCsv`, `matchedNodeCode`, `matchedRouteCode`, `filePath`, `thumbnailPath`,
                        `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`,
                        `matchedAtEpochMs`, `matchingTimeOffsetMs`, `mediaType`, `mimeType`, `durationMs`, `address`, `captureNote`,
                        `matchedNodeId`, `matchedRouteId`, `capturedAtEpochMs`, 'PENDING', NULL, NULL, 0, NULL
                    FROM `site_photos`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedNodeId` ON `site_photos` (`matchedNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedRouteId` ON `site_photos` (`matchedRouteId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_log_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `workItem` TEXT NOT NULL,
                        `manpower` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `weather` TEXT NOT NULL,
                        `temperature` REAL NOT NULL,
                        `nodeCode` TEXT,
                        `routeCode` TEXT,
                        `dateEpochDay` INTEGER NOT NULL,
                        `volume` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `batchGroupId` TEXT NOT NULL,
                        `appliedNodeCodesCsv` TEXT NOT NULL,
                        `linkedPhotoIdsCsv` TEXT NOT NULL,
                        `photoMatchOffsetMinutes` INTEGER NOT NULL,
                        `nodeId` TEXT,
                        `routeId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `daily_log_new` (
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`,
                        `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`, `linkedPhotoIdsCsv`,
                        `photoMatchOffsetMinutes`, `nodeId`, `routeId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`,
                        `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `appliedNodeCodesCsv`, `linkedPhotoIdsCsv`,
                        `photoMatchOffsetMinutes`, `nodeId`, `routeId`, `createdAtEpochMs`, 0, NULL
                    FROM `daily_log`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodeId` ON `daily_log` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_routeId` ON `daily_log` (`routeId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gis_node_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `mapNumberLabel` TEXT NOT NULL,
                        `workVolumeSummary` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `gis_node_new` (
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`,
                        `importedFileId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`,
                        `importedFileId`, COALESCE((SELECT `updatedAtEpochMs` FROM `projects` p WHERE p.`id` = `gis_node`.`projectId`), 0), 0, NULL
                    FROM `gis_node`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `gis_node`")
                db.execSQL("ALTER TABLE `gis_node_new` RENAME TO `gis_node`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_node_importedFileId` ON `gis_node` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_node_projectId_code` ON `gis_node` (`projectId`, `code`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gis_route_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `startNodeCode` TEXT NOT NULL,
                        `endNodeCode` TEXT NOT NULL,
                        `points` TEXT NOT NULL,
                        `importedFileId` TEXT,
                        `designLength` TEXT,
                        `startNodeId` TEXT,
                        `endNodeId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `gis_route_new` (
                        `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`,
                        `designLength`, `startNodeId`, `endNodeId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`,
                        `designLength`, `startNodeId`, `endNodeId`, COALESCE((SELECT `updatedAtEpochMs` FROM `projects` p WHERE p.`id` = `gis_route`.`projectId`), 0), 0, NULL
                    FROM `gis_route`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `gis_route`")
                db.execSQL("ALTER TABLE `gis_route_new` RENAME TO `gis_route`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_importedFileId` ON `gis_route` (`importedFileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_route_projectId_code` ON `gis_route` (`projectId`, `code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_startNodeId` ON `gis_route` (`startNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_endNodeId` ON `gis_route` (`endNodeId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `imported_files_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileType` TEXT NOT NULL,
                        `storedPath` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `importedAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `imported_files_new` (
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`,
                        `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`,
                        `importedAtEpochMs`, 0, NULL
                    FROM `imported_files`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `imported_files`")
                db.execSQL("ALTER TABLE `imported_files_new` RENAME TO `imported_files`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_imported_files_projectId_importedAtEpochMs` ON `imported_files` (`projectId`, `importedAtEpochMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_volume_progress_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `plannedQty` REAL NOT NULL,
                        `actualQty` REAL NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `unit` TEXT NOT NULL,
                        `nodeId` TEXT,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `work_volume_progress_new` (
                        `id`, `projectId`, `nodeCode`, `workName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`, `unit`, `nodeId`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `nodeCode`, `workName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`, `unit`, `nodeId`, 0, NULL
                    FROM `work_volume_progress`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `work_volume_progress`")
                db.execSQL("ALTER TABLE `work_volume_progress_new` RENAME TO `work_volume_progress`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_volume_progress_projectId_nodeCode_workName` ON `work_volume_progress` (`projectId`, `nodeCode`, `workName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_nodeCode` ON `work_volume_progress` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_nodeId` ON `work_volume_progress` (`nodeId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `objectNodeId` TEXT,
                        `objectRouteId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `note_new` (
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`, `objectNodeId`, `objectRouteId`,
                        `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `content`, `createdAtEpochMs`, `objectNodeId`, `objectRouteId`,
                        `createdAtEpochMs`, 0, NULL
                    FROM `note`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectNodeId` ON `note` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectRouteId` ON `note` (`objectRouteId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER,
                        `objectNodeId` TEXT,
                        `objectRouteId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `task_new` (
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`,
                        `objectNodeId`, `objectRouteId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`,
                        `objectNodeId`, `objectRouteId`, `createdAtEpochMs`, 0, NULL
                    FROM `task`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectNodeId` ON `task` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectRouteId` ON `task` (`objectRouteId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rag_document_embedding_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `docType` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `sourceCode` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `contentHash` TEXT NOT NULL,
                        `embeddingBlob` BLOB NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `rag_document_embedding_new` (
                        `id`, `projectId`, `docType`, `sourceId`, `sourceCode`, `text`, `contentHash`, `embeddingBlob`,
                        `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`
                    )
                    SELECT
                        `id`, `projectId`, `docType`, `sourceId`, `sourceCode`, `text`, `contentHash`, `embeddingBlob`,
                        `updatedAtEpochMs`, 0, NULL
                    FROM `rag_document_embedding`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `rag_document_embedding`")
                db.execSQL("ALTER TABLE `rag_document_embedding_new` RENAME TO `rag_document_embedding`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_docType` ON `rag_document_embedding` (`projectId`, `docType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_sourceCode` ON `rag_document_embedding` (`projectId`, `sourceCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_contentHash` ON `rag_document_embedding` (`projectId`, `contentHash`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_log_nodes` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `dailyLogId` TEXT NOT NULL,
                        `nodeId` TEXT,
                        `nodeCodeSnapshot` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`dailyLogId`) REFERENCES `daily_log`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodes_projectId_dailyLogId` ON `daily_log_nodes` (`projectId`, `dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodes_dailyLogId` ON `daily_log_nodes` (`dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodes_nodeId` ON `daily_log_nodes` (`nodeId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_log_photos` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `dailyLogId` TEXT NOT NULL,
                        `photoId` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`dailyLogId`) REFERENCES `daily_log`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_photos_projectId_dailyLogId` ON `daily_log_photos` (`projectId`, `dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_photos_dailyLogId` ON `daily_log_photos` (`dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_photos_photoId` ON `daily_log_photos` (`photoId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photo_tags` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `photoId` TEXT NOT NULL,
                        `tagCode` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`photoId`) REFERENCES `site_photos`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_tags_projectId_photoId` ON `photo_tags` (`projectId`, `photoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_tags_photoId` ON `photo_tags` (`photoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_tags_projectId_tagCode` ON `photo_tags` (`projectId`, `tagCode`)")

                db.execSQL(
                    """
                    WITH RECURSIVE split(photoId, projectId, rest, token, createdAtEpochMs) AS (
                        SELECT id, projectId, tagCodesCsv || ',', '', updatedAtEpochMs
                        FROM site_photos
                        WHERE tagCodesCsv <> ''
                        UNION ALL
                        SELECT photoId, projectId, substr(rest, instr(rest, ',') + 1), trim(substr(rest, 1, instr(rest, ',') - 1)), createdAtEpochMs
                        FROM split
                        WHERE rest <> ''
                    )
                    INSERT OR IGNORE INTO `photo_tags` (`id`, `projectId`, `photoId`, `tagCode`, `createdAtEpochMs`)
                    SELECT photoId || ':tag:' || replace(replace(token, ':', '_'), ' ', '_'), projectId, photoId, token, createdAtEpochMs
                    FROM split
                    WHERE token <> ''
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    WITH RECURSIVE split(dailyLogId, projectId, rest, token, createdAtEpochMs) AS (
                        SELECT id, projectId, linkedPhotoIdsCsv || ',', '', updatedAtEpochMs
                        FROM daily_log
                        WHERE linkedPhotoIdsCsv <> ''
                        UNION ALL
                        SELECT dailyLogId, projectId, substr(rest, instr(rest, ',') + 1), trim(substr(rest, 1, instr(rest, ',') - 1)), createdAtEpochMs
                        FROM split
                        WHERE rest <> ''
                    )
                    INSERT OR IGNORE INTO `daily_log_photos` (`id`, `projectId`, `dailyLogId`, `photoId`, `createdAtEpochMs`)
                    SELECT dailyLogId || ':photo:' || replace(replace(token, ':', '_'), ' ', '_'), projectId, dailyLogId, token, createdAtEpochMs
                    FROM split
                    WHERE token <> ''
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    WITH RECURSIVE split(dailyLogId, projectId, rest, token, createdAtEpochMs) AS (
                        SELECT id, projectId, appliedNodeCodesCsv || ',', '', updatedAtEpochMs
                        FROM daily_log
                        WHERE appliedNodeCodesCsv <> ''
                        UNION ALL
                        SELECT dailyLogId, projectId, substr(rest, instr(rest, ',') + 1), trim(substr(rest, 1, instr(rest, ',') - 1)), createdAtEpochMs
                        FROM split
                        WHERE rest <> ''
                    )
                    INSERT OR IGNORE INTO `daily_log_nodes` (`id`, `projectId`, `dailyLogId`, `nodeId`, `nodeCodeSnapshot`, `createdAtEpochMs`)
                    SELECT
                        dailyLogId || ':node:' || replace(replace(token, ':', '_'), ' ', '_'),
                        projectId,
                        dailyLogId,
                        (SELECT id FROM gis_node WHERE gis_node.projectId = split.projectId AND gis_node.code = token LIMIT 1),
                        token,
                        createdAtEpochMs
                    FROM split
                    WHERE token <> ''
                    """.trimIndent()
                )

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `material_declaration_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `materialName` TEXT NOT NULL,
                        `ratio` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `batchId` TEXT,
                        `workCategoryId` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`workCategoryId`) REFERENCES `work_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `material_declaration_new` (
                        `id`, `projectId`, `workName`, `materialName`, `ratio`, `unit`, `createdAtEpochMs`, `batchId`, `workCategoryId`
                    )
                    SELECT
                        md.`id`,
                        md.`projectId`,
                        md.`workName`,
                        md.`materialName`,
                        md.`ratio`,
                        md.`unit`,
                        md.`createdAtEpochMs`,
                        md.`batchId`,
                        (
                            SELECT wc.`id`
                            FROM `work_categories` wc
                            WHERE wc.`projectId` = md.`projectId`
                              AND LOWER(TRIM(wc.`name`)) = LOWER(TRIM(md.`workName`))
                            LIMIT 1
                        )
                    FROM `material_declaration` md
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `material_declaration`")
                db.execSQL("ALTER TABLE `material_declaration_new` RENAME TO `material_declaration`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_declaration_projectId` ON `material_declaration` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_declaration_projectId_workName` ON `material_declaration` (`projectId`, `workName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_declaration_workCategoryId` ON `material_declaration` (`workCategoryId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `material_handover_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `nodeCode` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `contractor` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `handoverDateEpochDay` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `nodeId` TEXT,
                        `materialDeclarationId` TEXT,
                        `workCategoryId` TEXT,
                        `receiver` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`materialDeclarationId`) REFERENCES `material_declaration`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`workCategoryId`) REFERENCES `work_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `material_handover_new` (
                        `id`, `projectId`, `nodeCode`, `workName`, `contractor`, `quantity`, `unit`, `handoverDateEpochDay`,
                        `note`, `createdAtEpochMs`, `nodeId`, `materialDeclarationId`, `workCategoryId`, `receiver`
                    )
                    SELECT
                        mh.`id`,
                        mh.`projectId`,
                        mh.`nodeCode`,
                        mh.`workName`,
                        mh.`contractor`,
                        mh.`quantity`,
                        mh.`unit`,
                        mh.`handoverDateEpochDay`,
                        mh.`note`,
                        mh.`createdAtEpochMs`,
                        mh.`nodeId`,
                        (
                            SELECT md.`id`
                            FROM `material_declaration` md
                            WHERE md.`projectId` = mh.`projectId`
                              AND LOWER(TRIM(md.`workName`)) = LOWER(TRIM(
                                  CASE
                                      WHEN instr(mh.`workName`, ':') > 0 THEN substr(mh.`workName`, 1, instr(mh.`workName`, ':') - 1)
                                      ELSE mh.`workName`
                                  END
                              ))
                              AND LOWER(TRIM(md.`materialName`)) = LOWER(TRIM(
                                  CASE
                                      WHEN instr(mh.`workName`, ':') > 0 THEN substr(mh.`workName`, instr(mh.`workName`, ':') + 1)
                                      ELSE ''
                                  END
                              ))
                            LIMIT 1
                        ),
                        (
                            SELECT wc.`id`
                            FROM `work_categories` wc
                            WHERE wc.`projectId` = mh.`projectId`
                              AND LOWER(TRIM(wc.`name`)) = LOWER(TRIM(
                                  CASE
                                      WHEN instr(mh.`workName`, ':') > 0 THEN substr(mh.`workName`, 1, instr(mh.`workName`, ':') - 1)
                                      ELSE mh.`workName`
                                  END
                              ))
                            LIMIT 1
                        ),
                        ''
                    FROM `material_handover` mh
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `material_handover`")
                db.execSQL("ALTER TABLE `material_handover_new` RENAME TO `material_handover`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_projectId` ON `material_handover` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_nodeCode` ON `material_handover` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_nodeId` ON `material_handover` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_materialDeclarationId` ON `material_handover` (`materialDeclarationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_workCategoryId` ON `material_handover` (`workCategoryId`)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                // 1. Backfill all ID columns from matching code columns where null
                db.execSQL("UPDATE `gis_route` SET `startNodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `gis_route`.`projectId` AND `gis_node`.`code` = `gis_route`.`startNodeCode` LIMIT 1) WHERE `startNodeId` IS NULL")
                db.execSQL("UPDATE `gis_route` SET `endNodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `gis_route`.`projectId` AND `gis_node`.`code` = `gis_route`.`endNodeCode` LIMIT 1) WHERE `endNodeId` IS NULL")
                db.execSQL("UPDATE `site_photos` SET `matchedNodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `site_photos`.`projectId` AND `gis_node`.`code` = `site_photos`.`matchedNodeCode` LIMIT 1) WHERE `matchedNodeId` IS NULL")
                db.execSQL("UPDATE `site_photos` SET `matchedRouteId` = (SELECT `id` FROM `gis_route` WHERE `gis_route`.`projectId` = `site_photos`.`projectId` AND `gis_route`.`code` = `site_photos`.`matchedRouteCode` LIMIT 1) WHERE `matchedRouteId` IS NULL")
                db.execSQL("UPDATE `daily_log` SET `nodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `daily_log`.`projectId` AND `gis_node`.`code` = `daily_log`.`nodeCode` LIMIT 1) WHERE `nodeId` IS NULL")
                db.execSQL("UPDATE `daily_log` SET `routeId` = (SELECT `id` FROM `gis_route` WHERE `gis_route`.`projectId` = `daily_log`.`projectId` AND `gis_route`.`code` = `daily_log`.`routeCode` LIMIT 1) WHERE `routeId` IS NULL")
                db.execSQL("UPDATE `daily_log_nodes` SET `nodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `daily_log_nodes`.`projectId` AND `gis_node`.`code` = `daily_log_nodes`.`nodeCodeSnapshot` LIMIT 1) WHERE `nodeId` IS NULL")
                db.execSQL("UPDATE `work_volume_progress` SET `nodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `work_volume_progress`.`projectId` AND `gis_node`.`code` = `work_volume_progress`.`nodeCode` LIMIT 1) WHERE `nodeId` IS NULL")
                db.execSQL("UPDATE `material_handover` SET `nodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `material_handover`.`projectId` AND `gis_node`.`code` = `material_handover`.`nodeCode` LIMIT 1) WHERE `nodeId` IS NULL")
                db.execSQL("UPDATE `work_plan` SET `nodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `work_plan`.`projectId` AND `gis_node`.`code` = `work_plan`.`nodeCode` LIMIT 1) WHERE `nodeId` IS NULL")
                db.execSQL("UPDATE `work_plan` SET `routeId` = (SELECT `id` FROM `gis_route` WHERE `gis_route`.`projectId` = `work_plan`.`projectId` AND `gis_route`.`code` = `work_plan`.`routeCode` LIMIT 1) WHERE `routeId` IS NULL")
                db.execSQL("UPDATE `task` SET `objectNodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `task`.`projectId` AND `gis_node`.`code` = `task`.`objectCode` LIMIT 1) WHERE `objectNodeId` IS NULL")
                db.execSQL("UPDATE `task` SET `objectRouteId` = (SELECT `id` FROM `gis_route` WHERE `gis_route`.`projectId` = `task`.`projectId` AND `gis_route`.`code` = `task`.`objectCode` LIMIT 1) WHERE `objectRouteId` IS NULL")
                db.execSQL("UPDATE `note` SET `objectNodeId` = (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `note`.`projectId` AND `gis_node`.`code` = `note`.`objectCode` LIMIT 1) WHERE `objectNodeId` IS NULL")
                db.execSQL("UPDATE `note` SET `objectRouteId` = (SELECT `id` FROM `gis_route` WHERE `gis_route`.`projectId` = `note`.`projectId` AND `gis_route`.`code` = `note`.`objectCode` LIMIT 1) WHERE `objectRouteId` IS NULL")

                // 2. Rebuild gis_route
                db.execSQL("CREATE TABLE IF NOT EXISTS `gis_route_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `code` TEXT NOT NULL, `contractor` TEXT NOT NULL, `startNodeCode` TEXT NOT NULL, `endNodeCode` TEXT NOT NULL, `points` TEXT NOT NULL, `importedFileId` TEXT, `designLength` TEXT, `startNodeId` TEXT, `endNodeId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`startNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`endNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `gis_route_new` SELECT * FROM `gis_route`")
                db.execSQL("DROP TABLE `gis_route`")
                db.execSQL("ALTER TABLE `gis_route_new` RENAME TO `gis_route`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_gis_route_projectId_code` ON `gis_route` (`projectId`, `code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_importedFileId` ON `gis_route` (`importedFileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_startNodeId` ON `gis_route` (`startNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gis_route_endNodeId` ON `gis_route` (`endNodeId`)")

                // 3. Rebuild site_photos
                db.execSQL("CREATE TABLE IF NOT EXISTS `site_photos_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `objectCode` TEXT NOT NULL, `tagCodesCsv` TEXT NOT NULL, `matchedNodeCode` TEXT, `matchedRouteCode` TEXT, `filePath` TEXT NOT NULL, `thumbnailPath` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `locationAccuracyM` REAL, `isGpsMocked` INTEGER NOT NULL, `locationStatus` TEXT NOT NULL, `engineer` TEXT NOT NULL, `capturedAtEpochMs` INTEGER NOT NULL, `matchedAtEpochMs` INTEGER NOT NULL, `matchingTimeOffsetMs` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `address` TEXT, `captureNote` TEXT, `matchedNodeId` TEXT, `matchedRouteId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `remoteUrl` TEXT, `lastSyncAttemptEpochMs` INTEGER, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`matchedNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`matchedRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `site_photos_new` SELECT * FROM `site_photos`")
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_objectCode_capturedAtEpochMs` ON `site_photos` (`projectId`, `objectCode`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_objectCode` ON `site_photos` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedNodeCode` ON `site_photos` (`projectId`, `matchedNodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_matchedRouteCode` ON `site_photos` (`projectId`, `matchedRouteCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedNodeId` ON `site_photos` (`matchedNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedRouteId` ON `site_photos` (`matchedRouteId`)")

                // 4. Rebuild daily_log
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_log_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `workItem` TEXT NOT NULL, `manpower` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `weather` TEXT NOT NULL, `temperature` REAL NOT NULL, `nodeCode` TEXT, `routeCode` TEXT, `dateEpochDay` INTEGER NOT NULL, `volume` REAL NOT NULL, `unit` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `batchGroupId` TEXT NOT NULL, `appliedNodeCodesCsv` TEXT NOT NULL, `linkedPhotoIdsCsv` TEXT NOT NULL, `photoMatchOffsetMinutes` INTEGER NOT NULL, `nodeId` TEXT, `routeId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`routeId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `daily_log_new` SELECT * FROM `daily_log`")
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodeId` ON `daily_log` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_routeId` ON `daily_log` (`routeId`)")

                // 5. Rebuild daily_log_nodes
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_log_nodes_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `dailyLogId` TEXT NOT NULL, `nodeId` TEXT, `nodeCodeSnapshot` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`dailyLogId`) REFERENCES `daily_log`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `daily_log_nodes_new` SELECT * FROM `daily_log_nodes`")
                db.execSQL("DROP TABLE `daily_log_nodes`")
                db.execSQL("ALTER TABLE `daily_log_nodes_new` RENAME TO `daily_log_nodes`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodes_projectId_dailyLogId` ON `daily_log_nodes` (`projectId`, `dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodes_dailyLogId` ON `daily_log_nodes` (`dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodes_nodeId` ON `daily_log_nodes` (`nodeId`)")

                // 6. Rebuild daily_log_photos
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_log_photos_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `dailyLogId` TEXT NOT NULL, `photoId` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`dailyLogId`) REFERENCES `daily_log`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`photoId`) REFERENCES `site_photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO `daily_log_photos_new` SELECT * FROM `daily_log_photos`")
                db.execSQL("DROP TABLE `daily_log_photos`")
                db.execSQL("ALTER TABLE `daily_log_photos_new` RENAME TO `daily_log_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_photos_projectId_dailyLogId` ON `daily_log_photos` (`projectId`, `dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_photos_dailyLogId` ON `daily_log_photos` (`dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_photos_photoId` ON `daily_log_photos` (`photoId`)")

                // 7. Rebuild work_volume_progress
                db.execSQL("CREATE TABLE IF NOT EXISTS `work_volume_progress_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `nodeCode` TEXT NOT NULL, `workName` TEXT NOT NULL, `plannedQty` REAL NOT NULL, `actualQty` REAL NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `unit` TEXT NOT NULL, `nodeId` TEXT, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `work_volume_progress_new` SELECT * FROM `work_volume_progress`")
                db.execSQL("DROP TABLE `work_volume_progress`")
                db.execSQL("ALTER TABLE `work_volume_progress_new` RENAME TO `work_volume_progress`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_work_volume_progress_projectId_nodeCode_workName` ON `work_volume_progress` (`projectId`, `nodeCode`, `workName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_nodeCode` ON `work_volume_progress` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_nodeId` ON `work_volume_progress` (`nodeId`)")

                // 8. Rebuild material_handover
                db.execSQL("CREATE TABLE IF NOT EXISTS `material_handover_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `nodeCode` TEXT NOT NULL, `workName` TEXT NOT NULL, `contractor` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `handoverDateEpochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `nodeId` TEXT, `materialDeclarationId` TEXT, `workCategoryId` TEXT, `receiver` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`materialDeclarationId`) REFERENCES `material_declaration`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`workCategoryId`) REFERENCES `work_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `material_handover_new` SELECT * FROM `material_handover`")
                db.execSQL("DROP TABLE `material_handover`")
                db.execSQL("ALTER TABLE `material_handover_new` RENAME TO `material_handover`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_projectId` ON `material_handover` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_nodeCode` ON `material_handover` (`nodeCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_nodeId` ON `material_handover` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_materialDeclarationId` ON `material_handover` (`materialDeclarationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_workCategoryId` ON `material_handover` (`workCategoryId`)")

                // 9. Rebuild work_plan
                db.execSQL("CREATE TABLE IF NOT EXISTS `work_plan_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `plannedDateEpochDay` INTEGER NOT NULL, `nodeCode` TEXT, `routeCode` TEXT, `taskId` TEXT, `sourceRawInput` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `batchGroupId` TEXT NOT NULL, `nodeId` TEXT, `routeId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`routeId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `work_plan_new` SELECT * FROM `work_plan`")
                db.execSQL("DROP TABLE `work_plan`")
                db.execSQL("ALTER TABLE `work_plan_new` RENAME TO `work_plan`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_plan_projectId_plannedDateEpochDay` ON `work_plan` (`projectId`, `plannedDateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_plan_nodeId` ON `work_plan` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_plan_routeId` ON `work_plan` (`routeId`)")

                // 10. Rebuild task
                db.execSQL("CREATE TABLE IF NOT EXISTS `task_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `objectCode` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `completedAtEpochMs` INTEGER, `objectNodeId` TEXT, `objectRouteId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`objectNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`objectRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `task_new` SELECT * FROM `task`")
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectCode_createdAtEpochMs` ON `task` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectCode` ON `task` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectNodeId` ON `task` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectRouteId` ON `task` (`objectRouteId`)")

                // 11. Rebuild note
                db.execSQL("CREATE TABLE IF NOT EXISTS `note_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `objectCode` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `objectNodeId` TEXT, `objectRouteId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`objectNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`objectRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `note_new` SELECT * FROM `note`")
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_objectCode_createdAtEpochMs` ON `note` (`projectId`, `objectCode`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectCode` ON `note` (`objectCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectNodeId` ON `note` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectRouteId` ON `note` (`objectRouteId`)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_log_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `workItem` TEXT NOT NULL, `manpower` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `weather` TEXT NOT NULL, `temperature` REAL NOT NULL, `nodeCode` TEXT, `routeCode` TEXT, `dateEpochDay` INTEGER NOT NULL, `volume` REAL NOT NULL, `unit` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `batchGroupId` TEXT NOT NULL, `photoMatchOffsetMinutes` INTEGER NOT NULL, `nodeId` TEXT, `routeId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`routeId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `daily_log_new` (`id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `photoMatchOffsetMinutes`, `nodeId`, `routeId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`) SELECT `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `routeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `photoMatchOffsetMinutes`, `nodeId`, `routeId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs` FROM `daily_log`")
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodeId` ON `daily_log` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_routeId` ON `daily_log` (`routeId`)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                // Rebuild site_photos dropping objectCode, matchedNodeCode, matchedRouteCode
                db.execSQL("CREATE TABLE IF NOT EXISTS `site_photos_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `tagCodesCsv` TEXT NOT NULL, `filePath` TEXT NOT NULL, `thumbnailPath` TEXT NOT NULL, `latitude` REAL, `longitude` REAL, `locationAccuracyM` REAL, `isGpsMocked` INTEGER NOT NULL, `locationStatus` TEXT NOT NULL, `engineer` TEXT NOT NULL, `capturedAtEpochMs` INTEGER NOT NULL, `matchedAtEpochMs` INTEGER NOT NULL, `matchingTimeOffsetMs` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `address` TEXT, `captureNote` TEXT, `matchedNodeId` TEXT, `matchedRouteId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `remoteUrl` TEXT, `lastSyncAttemptEpochMs` INTEGER, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`matchedNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`matchedRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `site_photos_new` SELECT `id`, `projectId`, `tagCodesCsv`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`, `mediaType`, `mimeType`, `durationMs`, `address`, `captureNote`, `matchedNodeId`, `matchedRouteId`, `updatedAtEpochMs`, `syncStatus`, `remoteUrl`, `lastSyncAttemptEpochMs`, `isDeleted`, `deletedAtEpochMs` FROM `site_photos`")
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedNodeId` ON `site_photos` (`matchedNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedRouteId` ON `site_photos` (`matchedRouteId`)")

                // Rebuild daily_log dropping nodeCode, routeCode
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_log_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `workItem` TEXT NOT NULL, `manpower` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `weather` TEXT NOT NULL, `temperature` REAL NOT NULL, `dateEpochDay` INTEGER NOT NULL, `volume` REAL NOT NULL, `unit` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `batchGroupId` TEXT NOT NULL, `photoMatchOffsetMinutes` INTEGER NOT NULL, `nodeId` TEXT, `routeId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`routeId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `daily_log_new` SELECT `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `dateEpochDay`, `volume`, `unit`, `categoryName`, `batchGroupId`, `photoMatchOffsetMinutes`, `nodeId`, `routeId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs` FROM `daily_log`")
                db.execSQL("DROP TABLE `daily_log`")
                db.execSQL("ALTER TABLE `daily_log_new` RENAME TO `daily_log`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_createdAtEpochMs` ON `daily_log` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_dateEpochDay` ON `daily_log` (`projectId`, `dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_projectId_batchGroupId` ON `daily_log` (`projectId`, `batchGroupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_nodeId` ON `daily_log` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_routeId` ON `daily_log` (`routeId`)")

                // Rebuild task dropping objectCode, adding CHECK constraint
                db.execSQL("CREATE TABLE IF NOT EXISTS `task_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `completedAtEpochMs` INTEGER, `objectNodeId` TEXT, `objectRouteId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`objectNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`objectRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, CHECK (`objectNodeId` IS NOT NULL OR `objectRouteId` IS NOT NULL))")
                db.execSQL("UPDATE `task` SET `objectNodeId` = 'unknown' WHERE `objectNodeId` IS NULL AND `objectRouteId` IS NULL")
                db.execSQL("INSERT INTO `task_new` SELECT `id`, `projectId`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`, `objectNodeId`, `objectRouteId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs` FROM `task`")
                db.execSQL("DROP TABLE `task`")
                db.execSQL("ALTER TABLE `task_new` RENAME TO `task`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_createdAtEpochMs` ON `task` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectNodeId` ON `task` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_objectRouteId` ON `task` (`objectRouteId`)")

                // Rebuild note dropping objectCode, adding CHECK constraint
                db.execSQL("CREATE TABLE IF NOT EXISTS `note_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `objectNodeId` TEXT, `objectRouteId` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`objectNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`objectRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, CHECK (`objectNodeId` IS NOT NULL OR `objectRouteId` IS NOT NULL))")
                db.execSQL("UPDATE `note` SET `objectNodeId` = 'unknown' WHERE `objectNodeId` IS NULL AND `objectRouteId` IS NULL")
                db.execSQL("INSERT INTO `note_new` SELECT `id`, `projectId`, `content`, `createdAtEpochMs`, `objectNodeId`, `objectRouteId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs` FROM `note`")
                db.execSQL("DROP TABLE `note`")
                db.execSQL("ALTER TABLE `note_new` RENAME TO `note`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_projectId_createdAtEpochMs` ON `note` (`projectId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectNodeId` ON `note` (`objectNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_objectRouteId` ON `note` (`objectRouteId`)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("CREATE TABLE IF NOT EXISTS `node_progress_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `planned` REAL NOT NULL, `actual` REAL NOT NULL, `remain` REAL NOT NULL, `delayed` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `nodeId` TEXT, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO `node_progress_new` (`id`, `projectId`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`, `nodeId`, `isDeleted`, `deletedAtEpochMs`) SELECT `id`, `projectId`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`, (SELECT `id` FROM `gis_node` WHERE `gis_node`.`projectId` = `node_progress`.`projectId` AND `gis_node`.`code` = `node_progress`.`nodeCode` LIMIT 1), `isDeleted`, `deletedAtEpochMs` FROM `node_progress`")
                db.execSQL("DROP TABLE `node_progress`")
                db.execSQL("ALTER TABLE `node_progress_new` RENAME TO `node_progress`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_node_progress_projectId_nodeId` ON `node_progress` (`projectId`, `nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_node_progress_nodeId` ON `node_progress` (`nodeId`)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("CREATE TABLE IF NOT EXISTS `rag_document_embedding_new` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `docType` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `text` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `embeddingBlob` BLOB NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `deletedAtEpochMs` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("INSERT INTO `rag_document_embedding_new` (`id`, `projectId`, `docType`, `sourceId`, `text`, `contentHash`, `embeddingBlob`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`) SELECT `id`, `projectId`, `docType`, `sourceId`, `text`, `contentHash`, `embeddingBlob`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs` FROM `rag_document_embedding`")
                db.execSQL("DROP TABLE `rag_document_embedding`")
                db.execSQL("ALTER TABLE `rag_document_embedding_new` RENAME TO `rag_document_embedding`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_docType` ON `rag_document_embedding` (`projectId`, `docType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_sourceId` ON `rag_document_embedding` (`projectId`, `sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rag_document_embedding_projectId_contentHash` ON `rag_document_embedding` (`projectId`, `contentHash`)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `material_handover` ADD COLUMN `materialName` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `material_handover`
                    SET `materialName` = SUBSTR(`workName`, INSTR(`workName`, ':') + 1),
                        `workName` = SUBSTR(`workName`, 1, INSTR(`workName`, ':') - 1)
                    WHERE INSTR(`workName`, ':') > 0
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_session` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `sourceKind` TEXT NOT NULL,
                        `sourceFileName` TEXT NOT NULL,
                        `sourceFileType` TEXT NOT NULL,
                        `sourceFilePath` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `importedFileId` TEXT,
                        `featureCount` INTEGER NOT NULL,
                        `conflictCount` INTEGER NOT NULL,
                        `warningCount` INTEGER NOT NULL,
                        `message` TEXT NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`importedFileId`) REFERENCES `imported_files`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_session_projectId` ON `import_session` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_session_importedFileId` ON `import_session` (`importedFileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_session_projectId_createdAtEpochMs` ON `import_session` (`projectId`, `createdAtEpochMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_version` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `importSessionId` TEXT NOT NULL,
                        `versionNumber` INTEGER NOT NULL,
                        `sourceHash` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `createdBy` TEXT NOT NULL DEFAULT '',
                        `note` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`importSessionId`) REFERENCES `import_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_import_version_projectId_versionNumber` ON `import_version` (`projectId`, `versionNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_version_projectId` ON `import_version` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_version_importSessionId` ON `import_version` (`importSessionId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_conflict` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `importSessionId` TEXT NOT NULL,
                        `featureBusinessCode` TEXT NOT NULL,
                        `conflictType` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `resolvedBy` TEXT,
                        `resolvedAtEpochMs` INTEGER,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`importSessionId`) REFERENCES `import_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_conflict_projectId` ON `import_conflict` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_conflict_importSessionId` ON `import_conflict` (`importSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_conflict_projectId_severity_createdAtEpochMs` ON `import_conflict` (`projectId`, `severity`, `createdAtEpochMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_audit` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `importSessionId` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `actor` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`importSessionId`) REFERENCES `import_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_audit_projectId` ON `import_audit` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_audit_importSessionId` ON `import_audit` (`importSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_audit_projectId_createdAtEpochMs` ON `import_audit` (`projectId`, `createdAtEpochMs`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `event_outbox` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT,
                        `eventType` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `availableAtEpochMs` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `dispatchedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_outbox_status_availableAtEpochMs` ON `event_outbox` (`status`, `availableAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_outbox_projectId_createdAtEpochMs` ON `event_outbox` (`projectId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_photos_new` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `objectCode` TEXT NOT NULL,
                        `tagCodesCsv` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `thumbnailPath` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `locationAccuracyM` REAL,
                        `isGpsMocked` INTEGER NOT NULL,
                        `locationStatus` TEXT NOT NULL,
                        `engineer` TEXT NOT NULL,
                        `capturedAtEpochMs` INTEGER NOT NULL,
                        `matchedAtEpochMs` INTEGER NOT NULL,
                        `matchingTimeOffsetMs` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `address` TEXT,
                        `captureNote` TEXT,
                        `matchedNodeId` TEXT,
                        `matchedRouteId` TEXT,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `remoteUrl` TEXT,
                        `lastSyncAttemptEpochMs` INTEGER,
                        `isDeleted` INTEGER NOT NULL,
                        `deletedAtEpochMs` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`matchedNodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`matchedRouteId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `site_photos_new` (
                        `id`,
                        `projectId`,
                        `objectCode`,
                        `tagCodesCsv`,
                        `filePath`,
                        `thumbnailPath`,
                        `latitude`,
                        `longitude`,
                        `locationAccuracyM`,
                        `isGpsMocked`,
                        `locationStatus`,
                        `engineer`,
                        `capturedAtEpochMs`,
                        `matchedAtEpochMs`,
                        `matchingTimeOffsetMs`,
                        `mediaType`,
                        `mimeType`,
                        `durationMs`,
                        `address`,
                        `captureNote`,
                        `matchedNodeId`,
                        `matchedRouteId`,
                        `updatedAtEpochMs`,
                        `syncStatus`,
                        `remoteUrl`,
                        `lastSyncAttemptEpochMs`,
                        `isDeleted`,
                        `deletedAtEpochMs`
                    )
                    SELECT
                        sp.`id`,
                        sp.`projectId`,
                        COALESCE(
                            (SELECT `code` FROM `gis_node` WHERE `gis_node`.`id` = sp.`matchedNodeId` LIMIT 1),
                            (SELECT `code` FROM `gis_route` WHERE `gis_route`.`id` = sp.`matchedRouteId` LIMIT 1),
                            ''
                        ) AS `objectCode`,
                        sp.`tagCodesCsv`,
                        sp.`filePath`,
                        sp.`thumbnailPath`,
                        sp.`latitude`,
                        sp.`longitude`,
                        sp.`locationAccuracyM`,
                        sp.`isGpsMocked`,
                        sp.`locationStatus`,
                        sp.`engineer`,
                        sp.`capturedAtEpochMs`,
                        sp.`matchedAtEpochMs`,
                        sp.`matchingTimeOffsetMs`,
                        sp.`mediaType`,
                        sp.`mimeType`,
                        sp.`durationMs`,
                        sp.`address`,
                        sp.`captureNote`,
                        sp.`matchedNodeId`,
                        sp.`matchedRouteId`,
                        sp.`updatedAtEpochMs`,
                        sp.`syncStatus`,
                        sp.`remoteUrl`,
                        sp.`lastSyncAttemptEpochMs`,
                        sp.`isDeleted`,
                        sp.`deletedAtEpochMs`
                    FROM `site_photos` sp
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `site_photos`")
                db.execSQL("ALTER TABLE `site_photos_new` RENAME TO `site_photos`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_projectId_capturedAtEpochMs` ON `site_photos` (`projectId`, `capturedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedNodeId` ON `site_photos` (`matchedNodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_photos_matchedRouteId` ON `site_photos` (`matchedRouteId`)")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE `material_handover`
                    SET `materialName` = TRIM(SUBSTR(`workName`, INSTR(`workName`, ':') + 1)),
                        `workName` = TRIM(SUBSTR(`workName`, 1, INSTR(`workName`, ':') - 1))
                    WHERE TRIM(COALESCE(`materialName`, '')) = ''
                      AND INSTR(`workName`, ':') > 0
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `material_handover`
                    SET `nodeId` = (
                        SELECT `id`
                        FROM `gis_node`
                        WHERE `gis_node`.`projectId` = `material_handover`.`projectId`
                          AND UPPER(TRIM(`gis_node`.`code`)) = UPPER(TRIM(`material_handover`.`nodeCode`))
                        LIMIT 1
                    )
                    WHERE `nodeId` IS NULL
                      AND TRIM(COALESCE(`nodeCode`, '')) <> ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `work_volume_progress`
                    SET `nodeId` = (
                        SELECT `id`
                        FROM `gis_node`
                        WHERE `gis_node`.`projectId` = `work_volume_progress`.`projectId`
                          AND UPPER(TRIM(`gis_node`.`code`)) = UPPER(TRIM(`work_volume_progress`.`nodeCode`))
                        LIMIT 1
                    )
                    WHERE `nodeId` IS NULL
                      AND TRIM(COALESCE(`nodeCode`, '')) <> ''
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_volume_progress_projectId_nodeId_workName` ON `work_volume_progress` (`projectId`, `nodeId`, `workName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_handover_projectId_nodeId_handoverDateEpochDay` ON `material_handover` (`projectId`, `nodeId`, `handoverDateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_declaration_projectId_materialName` ON `material_declaration` (`projectId`, `materialName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectNodeId_createdAtEpochMs` ON `task` (`projectId`, `objectNodeId`, `createdAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_projectId_objectRouteId_createdAtEpochMs` ON `task` (`projectId`, `objectRouteId`, `createdAtEpochMs`)")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `linkedWorkPlanId` TEXT")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `plannedWorkName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `plannedQuantity` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `plannedUnit` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `plannedNodeId` TEXT")
                db.execSQL("ALTER TABLE `daily_log` ADD COLUMN `plannedRouteId` TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_log_line` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `dailyLogId` TEXT NOT NULL,
                        `lineType` TEXT NOT NULL,
                        `workName` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `linkedWorkPlanId` TEXT,
                        `nodeId` TEXT,
                        `routeId` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`dailyLogId`) REFERENCES `daily_log`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`nodeId`) REFERENCES `gis_node`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`routeId`) REFERENCES `gis_route`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_line_projectId_dailyLogId` ON `daily_log_line` (`projectId`, `dailyLogId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_line_nodeId` ON `daily_log_line` (`nodeId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_log_line_routeId` ON `daily_log_line` (`routeId`)")
            }
        }

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `gis_node` ADD COLUMN `ipAddress` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `gis_node` ADD COLUMN `subnet` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `gis_node` ADD COLUMN `gateway` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `gis_node` ADD COLUMN `signalStatus` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `fiberCoreCount` INTEGER")
                db.execSQL("ALTER TABLE `gis_route` ADD COLUMN `fiberConnection` TEXT NOT NULL DEFAULT ''")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
            MIGRATION_36_37,
            MIGRATION_37_38,
            MIGRATION_38_39,
            MIGRATION_39_40,
            MIGRATION_40_41,
            MIGRATION_41_42,
            MIGRATION_42_43,
            MIGRATION_43_44,
            MIGRATION_44_45,
            MIGRATION_45_46
        )
    }
}
