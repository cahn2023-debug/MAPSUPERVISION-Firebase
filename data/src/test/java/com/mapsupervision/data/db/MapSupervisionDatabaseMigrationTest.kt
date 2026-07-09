package com.mapsupervision.data.db

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.domain.model.PhotoLocationStatus
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MapSupervisionDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("mapsupervision-db-migration-test").toFile()
        context = TestDatabaseContext(ApplicationProvider.getApplicationContext(), tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `migration 19 to 20 rebuilds site photos table without legacy defaults`() {
        val dbName = "legacy19.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion19Database(dbFile)

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                assertEquals(0, database.sitePhotoDao().byProject("project-1").size)
            }
            assertProjectsTableHasNormalizedDefaults(database)
            assertSitePhotosTableHasNoLegacyDefaults(database)
            assertImportedFilesTableHasNoLegacyDeletedColumn(database)
            assertImportedFilesIndexExists(database)
            assertTaskTableHasNoLegacyColumns(database)
            assertTaskIndexesExist(database)
            assertDailyLogTableHasNormalizedColumns(database)
            assertNoteTableHasNormalizedColumns(database)
            assertWorkCategoriesTableHasNormalizedColumns(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 19 to 20 preserves existing user data`() {
        val dbName = "legacy19_data.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion19Database(dbFile)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                """
                INSERT INTO `daily_log` (
                    `id`, `projectId`, `workItem`, `manpower`, `note`, `createdAtEpochMs`, `weather`, `temperature`, `nodeCode`, `dateEpochDay`, `volume`, `unit`, `categoryName`
                ) VALUES (
                    'log-custom-1', 'project-1', 'Excavation', 5, 'Sunny day work', 150, 'Sunny', 32.5, 'N-1', 2000, 15.0, 'm3', 'Category A'
                )
                """.trimIndent()
            )
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            println("DEBUG: Room DB absolute path = ${database.openHelper.writableDatabase.path}")
            runBlocking {
                val logs = database.dailyLogDao().byProject("project-1")
                assertEquals(1, logs.size)
                val log = logs.single()
                assertEquals("log-custom-1", log.id)
                assertEquals("Excavation", log.workItem)
                assertEquals("Sunny", log.weather)
                assertEquals(32.5, log.temperature, 0.001)
                assertEquals("", log.batchGroupId)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 8 to 46 compiles and validates successfully`() {
        val dbName = "legacy8.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyVersion8Database(dbFile)

        val database = migratingDatabase(dbName).build()

        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            assertProjectsTableHasNormalizedDefaults(database)
            assertLatestSchema(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `every legacy schema from 9 to 31 migrates to version 46`() {
        for (version in 9..31) {
            val dbName = "legacy-version-$version.sqlite"
            val dbFile = File(tempDir, dbName)
            createLegacyDatabaseFromSchema(dbFile, version)

            val database = migratingDatabase(dbName).build()
            try {
                assertEquals("Legacy version $version did not migrate to 46", 46, database.openHelper.writableDatabase.version)
                assertLatestSchema(database)
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun confirmRequiredSchemasExist() {
        for (version in 9..40) {
            if (version in 36..39) continue
            val file = File("data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            val relativeFile = File("../data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            val alternateFile = File("schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            
            val exists = file.exists() || relativeFile.exists() || alternateFile.exists()
            assertEquals("Room schema artifact for version $version is missing on disk", true, exists)
        }
    }

    private fun assertAllTablesExist(database: MapSupervisionDatabase) {
        val expectedTables = listOf(
            "projects", "node_progress", "site_photos", "daily_log", "gis_node", "gis_route",
            "imported_files", "work_volume_progress", "note", "task", "work_categories",
            "ai_decision_cache", "chat_history", "report_draft"
        )
        database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
            for (table in expectedTables) {
                assertEquals("Table $table does not exist in version 23", true, tables.contains(table))
            }
        }
    }

    private fun assertGisRouteTableHasPointsAndDesignLength(database: MapSupervisionDatabase) {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`gis_route`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            var pointsFound = false
            var designLengthFound = false
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(nameIndex)
                if (columnName == "points") {
                    pointsFound = true
                    assertEquals("TEXT", cursor.getString(typeIndex))
                }
                if (columnName == "designLength") {
                    designLengthFound = true
                    assertEquals("TEXT", cursor.getString(typeIndex))
                }
            }
            assertEquals("points column missing on gis_route", true, pointsFound)
            assertEquals("designLength column missing on gis_route", true, designLengthFound)
        }
    }

    private fun verifyVersion23ConstraintsAndForeignKeys(database: MapSupervisionDatabase) {
        val db = database.openHelper.writableDatabase

        // Ensure a parent project exists for FK validation
        db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) " +
                "VALUES ('proj-fk-validation', 'Project FK Validation', 'proj-fk-validation', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")

        // 1. Check CHECK constraints
        try {
            db.execSQL(
                "INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `storageMode`, `projectDbPath`) " +
                "VALUES ('proj-invalid-chk', 'Invalid', 'invalid', 0, 1000, 'INVALID_MODE', '')"
            )
            org.junit.Assert.fail("Expected CHECK constraint violation for projects.storageMode")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected
        }

        try {
            db.execSQL(
                "INSERT INTO `task` (`id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`) " +
                "VALUES ('task-invalid-chk', 'proj-fk-validation', 'N1', 'Invalid Task', '', 'INVALID_STATUS', 1200)"
            )
            org.junit.Assert.fail("Expected CHECK constraint violation for task.status")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected
        }

        try {
            db.execSQL(
                "INSERT INTO `site_photos` (`id`, `projectId`, `objectCode`, `tagCodesCsv`, `filePath`, `thumbnailPath`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`) " +
                "VALUES ('photo-invalid-chk', 'proj-fk-validation', 'N1', '', '', '', 0, 'INVALID_STATUS', '', 0, 0, 0)"
            )
            org.junit.Assert.fail("Expected CHECK constraint violation for site_photos.locationStatus")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected
        }

        // 2. Check UNIQUE constraints
        // For gis_node (projectId, code)
        try {
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`) VALUES ('node-1', 'proj-fk-validation', 'N1', 'CON1', 10.0, 20.0, '', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`) VALUES ('node-2', 'proj-fk-validation', 'N1', 'CON1', 11.0, 21.0, '', '')")
            org.junit.Assert.fail("Expected UNIQUE constraint violation for gis_node (projectId, code)")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected
        }

        // For gis_route (projectId, code)
        try {
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `designLength`) VALUES ('route-1', 'proj-fk-validation', 'R1', 'CON1', 'N1', 'N2', '[]', NULL)")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `designLength`) VALUES ('route-2', 'proj-fk-validation', 'R1', 'CON1', 'N1', 'N2', '[]', NULL)")
            org.junit.Assert.fail("Expected UNIQUE constraint violation for gis_route (projectId, code)")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // Expected
        }

        // 3. Clean up projects test data
        db.execSQL("DELETE FROM `projects` WHERE `id` = 'proj-fk-validation'")
    }

    private fun createLegacyVersion8Database(dbFile: File) {
        val skippedIndices = setOf(
            "index_gis_node_projectId_code",
            "index_gis_route_projectId_code",
            "index_imported_files_projectId_importedAtEpochMs",
            "index_task_projectId_createdAtEpochMs",
            "index_task_projectId_objectCode_createdAtEpochMs",
            "index_site_photos_projectId_objectCode_capturedAtEpochMs",
            "index_note_projectId_createdAtEpochMs",
            "index_note_projectId_objectCode_createdAtEpochMs"
        )
        createLegacyDatabaseFromSchema(dbFile, schemaVersion = 9, dbVersion = 8, skippedIndices = skippedIndices)
    }

    private fun createLegacyVersion19Database(dbFile: File) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(19)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val createSql = when (entity.getString("tableName")) {
                    "projects" -> """
                        CREATE TABLE IF NOT EXISTS `projects` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `slug` TEXT NOT NULL,
                            `isArchived` INTEGER NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `metadataVersion` INTEGER NOT NULL,
                            `updatedAtEpochMs` INTEGER NOT NULL,
                            `storageMode` TEXT NOT NULL,
                            `projectDbPath` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "site_photos" -> """
                        CREATE TABLE IF NOT EXISTS `site_photos` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `objectCode` TEXT NOT NULL,
                            `tagCodesCsv` TEXT NOT NULL DEFAULT '',
                            `matchedNodeCode` TEXT,
                            `matchedRouteCode` TEXT,
                            `filePath` TEXT NOT NULL,
                            `thumbnailPath` TEXT NOT NULL,
                            `latitude` REAL,
                            `longitude` REAL,
                            `locationAccuracyM` REAL,
                            `isGpsMocked` INTEGER NOT NULL DEFAULT 0,
                            `locationStatus` TEXT NOT NULL DEFAULT 'MISSING',
                            `engineer` TEXT NOT NULL,
                            `capturedAtEpochMs` INTEGER NOT NULL,
                            `matchedAtEpochMs` INTEGER NOT NULL DEFAULT 0,
                            `matchingTimeOffsetMs` INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "imported_files" -> """
                        CREATE TABLE IF NOT EXISTS `imported_files` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `fileType` TEXT NOT NULL,
                            `storedPath` TEXT NOT NULL,
                            `summary` TEXT NOT NULL,
                            `importedAtEpochMs` INTEGER NOT NULL,
                            `deletedAtEpochMs` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "task" -> """
                        CREATE TABLE IF NOT EXISTS `task` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `objectCode` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `completedAtEpochMs` INTEGER,
                            `assignee` TEXT,
                            `dueDateEpochMs` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    "daily_log" -> """
                        CREATE TABLE IF NOT EXISTS `daily_log` (
                            `id` TEXT NOT NULL,
                            `projectId` TEXT NOT NULL,
                            `workItem` TEXT NOT NULL,
                            `manpower` INTEGER NOT NULL,
                            `note` TEXT NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            `weather` TEXT NOT NULL,
                            `temperature` REAL NOT NULL,
                            `nodeCode` TEXT,
                            `dateEpochDay` INTEGER NOT NULL,
                            `volume` REAL NOT NULL,
                            `unit` TEXT NOT NULL,
                            `categoryName` TEXT NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent()
                    else -> entity.getString("createSql")
                }
                val resolvedCreateSql = createSql.replace("\${TABLE_NAME}", entity.getString("tableName"))
                db.execSQL(resolvedCreateSql)
            }
            db.execSQL(
                """
                INSERT INTO `projects` (
                    `id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`,
                    `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`
                ) VALUES (
                    'project-1', 'Project 1', 'slug-1', 0, 1000, 1, 0, 'LEGACY_SHARED', '/legacy/path'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `imported_files` (
                    `id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`, `deletedAtEpochMs`
                ) VALUES (
                    'imported-1', 'project-1', 'nodes.kml', 'KML', '/tmp/nodes.kml', 'seed', 100, 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `task` (
                    `id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`, `completedAtEpochMs`, `assignee`, `dueDateEpochMs`
                ) VALUES (
                    'task-1', 'project-1', 'OBJ-1', 'legacy title', 'legacy desc', 'OPEN', 200, NULL, 'user-a', 300
                )
                """.trimIndent()
            )
            db.setVersion(19)
        }
    }

    private fun assertSitePhotosTableHasNoLegacyDefaults(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "site_photos")
        val legacyDefaultFields = listOf(
            "tagCodesCsv", "matchedNodeCode", "matchedRouteCode", "isGpsMocked",
            "locationStatus", "matchedAtEpochMs", "matchingTimeOffsetMs"
        )
        for (field in legacyDefaultFields) {
            assertNull(columns[field]?.defaultValue)
        }
    }

    private fun assertImportedFilesTableHasNoLegacyDeletedColumn(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "imported_files")
        assertEquals(false, columns.containsKey("deleted"))
    }

    private fun assertImportedFilesIndexExists(database: MapSupervisionDatabase) {
        val indexes = tableIndexes(database, "imported_files")
        assertEquals(true, indexes.contains("index_imported_files_projectId_importedAtEpochMs"))
    }

    private fun assertProjectsTableHasNormalizedDefaults(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "projects")
        assertEquals("3", columns["metadataVersion"]?.defaultValue)
        assertEquals("0", columns["updatedAtEpochMs"]?.defaultValue)
        assertEquals("'LEGACY_SHARED'", columns["storageMode"]?.defaultValue)
        assertEquals("''", columns["projectDbPath"]?.defaultValue)
        assertNull(columns["projectCode"]?.defaultValue)
    }

    private fun assertTaskTableHasNoLegacyColumns(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "task")
        assertEquals(false, columns.containsKey("assignee"))
        assertEquals(false, columns.containsKey("dueDateEpochMs"))
    }

    private fun assertTaskIndexesExist(database: MapSupervisionDatabase) {
        val indexes = tableIndexes(database, "task")
        assertEquals(true, indexes.contains("index_task_projectId_createdAtEpochMs"))
        assertEquals(true, indexes.contains("index_task_objectNodeId"))
        assertEquals(true, indexes.contains("index_task_objectRouteId"))
    }

    private fun assertDailyLogTableHasNormalizedColumns(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "daily_log")
        val expectedCols = listOf(
            "id", "projectId", "workItem", "manpower", "note", "createdAtEpochMs",
            "weather", "temperature", "dateEpochDay", "volume", "unit",
            "categoryName", "batchGroupId", "linkedWorkPlanId", "plannedWorkName",
            "plannedQuantity", "plannedUnit", "photoMatchOffsetMinutes",
            "nodeId", "routeId", "plannedNodeId", "plannedRouteId",
            "updatedAtEpochMs", "isDeleted", "deletedAtEpochMs"
        )
        for (col in expectedCols) {
            assertEquals("daily_log table missing column $col", true, columns.containsKey(col))
        }
        val indexes = tableIndexes(database, "daily_log")
        assertEquals(true, indexes.contains("index_daily_log_projectId_createdAtEpochMs"))
        assertEquals(true, indexes.contains("index_daily_log_projectId_dateEpochDay"))
        assertEquals(true, indexes.contains("index_daily_log_projectId_batchGroupId"))
        val lineColumns = tableColumns(database, "daily_log_line")
        val expectedLineCols = listOf(
            "id", "projectId", "dailyLogId", "lineType", "workName", "categoryName",
            "quantity", "unit", "linkedWorkPlanId", "nodeId", "routeId",
            "createdAtEpochMs", "updatedAtEpochMs"
        )
        for (col in expectedLineCols) {
            assertEquals("daily_log_line table missing column $col", true, lineColumns.containsKey(col))
        }
        val lineIndexes = tableIndexes(database, "daily_log_line")
        assertEquals(true, lineIndexes.contains("index_daily_log_line_projectId_dailyLogId"))
    }

    private fun assertNoteTableHasNormalizedColumns(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "note")
        val expectedCols = listOf("id", "projectId", "content", "createdAtEpochMs", "objectNodeId", "objectRouteId", "updatedAtEpochMs", "isDeleted", "deletedAtEpochMs")
        for (col in expectedCols) {
            assertEquals(true, columns.containsKey(col))
        }
        val indexes = tableIndexes(database, "note")
        assertEquals(true, indexes.contains("index_note_projectId_createdAtEpochMs"))
        assertEquals(true, indexes.contains("index_note_objectNodeId"))
        assertEquals(true, indexes.contains("index_note_objectRouteId"))
    }

    private fun assertWorkCategoriesTableHasNormalizedColumns(database: MapSupervisionDatabase) {
        val columns = tableColumns(database, "work_categories")
        val expectedCols = listOf("id", "projectId", "name", "unit", "createdAtEpochMs")
        for (col in expectedCols) {
            assertEquals(true, columns.containsKey(col))
        }
        val indexes = tableIndexes(database, "work_categories")
        assertEquals(true, indexes.contains("index_work_categories_projectId"))
        assertEquals(true, indexes.contains("index_work_categories_projectId_createdAtEpochMs"))
    }

    private fun migratingDatabase(dbName: String) =
        Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()

    private fun createLegacyDatabaseFromSchema(
        dbFile: File,
        schemaVersion: Int,
        dbVersion: Int = schemaVersion,
        skippedIndices: Set<String> = emptySet()
    ) {
        dbFile.parentFile?.mkdirs()
        val schema = loadSchema(schemaVersion)
        SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, null).use { db ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                db.execSQL(createSql)

                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        val indexName = index.getString("name")
                        if (indexName !in skippedIndices) {
                            val indexSql = index.getString("createSql").replace("\${TABLE_NAME}", tableName)
                            db.execSQL(indexSql)
                        }
                    }
                }
            }
            db.setVersion(dbVersion)
        }
    }

    private fun assertLatestSchema(database: MapSupervisionDatabase) {
        assertAllTablesExist(
            database,
            listOf(
                "projects",
                "node_progress",
                "site_photos",
                "daily_log",
                "gis_node",
                "gis_route",
                "imported_files",
                "work_volume_progress",
                "note",
                "task",
                "work_categories",
                "ai_decision_cache",
                "chat_history",
                "report_draft",
                "ai_action_log",
                "work_plan",
                "daily_log_line",
                "material_handover",
                "material_declaration",
                "rag_document_embedding"
            )
        )
        assertColumnExists(database, "gis_node", "workVolumeSummary")
        assertColumnExists(database, "work_volume_progress", "workName")
        assertColumnExists(database, "work_plan", "quantity")
        assertColumnExists(database, "work_plan", "unit")
        assertColumnExists(database, "work_plan", "batchGroupId")
        assertColumnExists(database, "daily_log", "linkedWorkPlanId")
        assertColumnExists(database, "daily_log", "plannedWorkName")
        assertColumnExists(database, "daily_log", "plannedQuantity")
        assertColumnExists(database, "daily_log", "plannedUnit")
        assertColumnExists(database, "daily_log", "plannedNodeId")
        assertColumnExists(database, "daily_log", "plannedRouteId")
        assertColumnExists(database, "daily_log_line", "lineType")
        assertColumnExists(database, "daily_log_line", "workName")
        assertColumnExists(database, "daily_log_line", "linkedWorkPlanId")
    }

    private data class ColumnInfo(
        val name: String,
        val type: String,
        val defaultValue: String?
    )

    private fun tableColumns(database: MapSupervisionDatabase, tableName: String): Map<String, ColumnInfo> {
        val columns = mutableMapOf<String, ColumnInfo>()
        database.openHelper.readableDatabase.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val defaultIndex = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                val type = cursor.getString(typeIndex)
                val defaultValue = if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex)
                columns[name] = ColumnInfo(name, type, defaultValue)
            }
        }
        return columns
    }

    private fun tableIndexes(database: MapSupervisionDatabase, tableName: String): Set<String> {
        val indexes = mutableSetOf<String>()
        database.openHelper.readableDatabase.query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexes.add(cursor.getString(nameIndex))
            }
        }
        return indexes
    }

    private fun assertAllTablesExist(database: MapSupervisionDatabase, expectedTables: List<String>) {
        database.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
            for (table in expectedTables) {
                assertEquals("Table $table does not exist in migrated schema", true, tables.contains(table))
            }
        }
    }

    private fun assertColumnExists(database: MapSupervisionDatabase, tableName: String, columnName: String) {
        val columns = tableColumns(database, tableName)
        assertEquals("Column $columnName missing from $tableName", true, columns.containsKey(columnName))
    }

    private fun tableExists(database: MapSupervisionDatabase, tableName: String): Boolean {
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private val schemaCache = mutableMapOf<Int, JSONObject>()

    private fun loadSchema(version: Int): JSONObject {
        return schemaCache.getOrPut(version) {
            val candidates = listOf(
                File("data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json"),
                File("../data/schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json"),
                File("schemas/com.mapsupervision.data.db.MapSupervisionDatabase/$version.json")
            )
            val schemaFile = candidates.firstOrNull { it.exists() }
                ?: error("Could not locate Room schema $version.json")
            JSONObject(schemaFile.readText(Charsets.UTF_8))
        }
    }

    private class TestDatabaseContext(base: Context, private val baseDir: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String): File {
            return if (name.startsWith("legacy")) File(baseDir, name) else super.getDatabasePath(name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?
        ): SQLiteDatabase {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: android.database.DatabaseErrorHandler?
        ): SQLiteDatabase {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory)
        }
    }

    @Test
    fun `migration 20 to 21 adds points column to gis route`() {
        val dbName = "legacy20.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 20)

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.readableDatabase.query("PRAGMA table_info(`gis_route`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                var pointsColumnFound = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "points") {
                        pointsColumnFound = true
                        assertEquals("TEXT", cursor.getString(typeIndex))
                    }
                }
                assertEquals(true, pointsColumnFound)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 25 to 26 adds work plan table`() {
        val dbName = "legacy25.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 25)

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.readableDatabase.query("PRAGMA table_info(`work_plan`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                val columns = mutableMapOf<String, String>()
                while (cursor.moveToNext()) {
                    columns[cursor.getString(nameIndex)] = cursor.getString(typeIndex)
                }
                assertEquals("TEXT", columns["id"])
                assertEquals("TEXT", columns["projectId"])
                assertEquals("TEXT", columns["title"])
                assertEquals("TEXT", columns["description"])
                assertEquals("INTEGER", columns["plannedDateEpochDay"])
                assertEquals("TEXT", columns["nodeCode"])
                assertEquals("TEXT", columns["routeCode"])
                assertEquals("TEXT", columns["taskId"])
                assertEquals("TEXT", columns["sourceRawInput"])
                assertEquals("INTEGER", columns["createdAtEpochMs"])
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 31 to 32 adds work plan quantity unit and batch group defaults`() {
        val dbName = "legacy31.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 31)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `work_plan` (`id`, `projectId`, `title`, `description`, `plannedDateEpochDay`, `nodeCode`, `routeCode`, `taskId`, `sourceRawInput`, `createdAtEpochMs`) VALUES ('wp1', 'proj1', 'Dao ranh', 'Ke hoach cu', 2000, 'N-1', NULL, NULL, '', 1234)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            database.openHelper.readableDatabase.query("PRAGMA table_info(`work_plan`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val typeIndex = cursor.getColumnIndex("type")
                val defaultIndex = cursor.getColumnIndex("dflt_value")
                val columns = mutableMapOf<String, Triple<String, String?, Boolean>>()
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(nameIndex)
                    columns[columnName] = Triple(
                        cursor.getString(typeIndex),
                        if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex),
                        true
                    )
                }
                assertEquals("REAL", columns["quantity"]?.first)
                assertEquals("TEXT", columns["unit"]?.first)
                assertEquals("TEXT", columns["batchGroupId"]?.first)
            }

            val plans = runBlocking { database.workPlanDao().byProject("proj1") }
            assertEquals(1, plans.size)
            val plan = plans.first()
            assertEquals(0.0, plan.quantity, 0.001)
            assertEquals("", plan.unit)
            assertEquals("", plan.batchGroupId)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 20 to 21 merges routes and aggregates progress`() {
        val dbName = "legacy20_merge.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 20)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Insert parent project to satisfy FK constraint in MIGRATION_22_23
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")

            // Insert legacy nodes
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n1', 'proj1', 'ROUTE_1_P1', 'CON1', 10.0, 20.0, '', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n2', 'proj1', 'ROUTE_1_P2', 'CON1', 10.1, 20.1, '', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n3', 'proj1', 'ROUTE_1_P3', 'CON1', 10.2, 20.2, '', '')")

            // Insert segmented routes
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`) VALUES ('r1', 'proj1', 'ROUTE_1_S1', 'CON1', 'ROUTE_1_P1', 'ROUTE_1_P2')")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`) VALUES ('r2', 'proj1', 'ROUTE_1_S2', 'CON1', 'ROUTE_1_P2', 'ROUTE_1_P3')")

            // Insert node progress
            db.execSQL("INSERT INTO `node_progress` (`id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`) VALUES ('np1', 'proj1', 'ROUTE_1_S1', 100.0, 40.0, 60.0, 0, 1000)")
            db.execSQL("INSERT INTO `node_progress` (`id`, `projectId`, `nodeCode`, `planned`, `actual`, `remain`, `delayed`, `updatedAtEpochMs`) VALUES ('np2', 'proj1', 'ROUTE_1_S2', 200.0, 60.0, 140.0, 1, 2000)")

            // Insert material progress
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) VALUES ('mp1', 'proj1', 'ROUTE_1_S1', 'Concrete', 50.0, 20.0, 1000)")
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) VALUES ('mp2', 'proj1', 'ROUTE_1_S2', 'Concrete', 80.0, 30.0, 2000)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            // Verify route is merged
            val routes = runBlocking { database.gisRouteDao().byProject("proj1") }
            assertEquals(1, routes.size)
            val mergedRoute = routes.first()
            assertEquals("ROUTE_1", mergedRoute.code)
            assertEquals("ROUTE_1_P1", mergedRoute.startNodeCode)
            assertEquals("ROUTE_1_P3", mergedRoute.endNodeCode)
            assertEquals(listOf(10.0 to 20.0, 10.1 to 20.1, 10.2 to 20.2), mergedRoute.points)

            // Verify progress is aggregated
            val progressList = runBlocking { database.nodeProgressDao().byProject("proj1") }
            assertEquals(1, progressList.size)
            val mergedProgress = progressList.first()
            assertEquals(null, mergedProgress.nodeId)
            assertEquals(300.0f, mergedProgress.planned)
            assertEquals(100.0f, mergedProgress.actual)
            assertEquals(200.0f, mergedProgress.remain)
            assertEquals(true, mergedProgress.delayed)

            // Verify materials aggregated
            val materials = runBlocking { database.materialProgressDao().byProject("proj1") }
            assertEquals(1, materials.size)
            val mergedMat = materials.first()
            assertEquals("ROUTE_1", mergedMat.nodeCode)
            assertEquals("Concrete", mergedMat.materialName)
            assertEquals(130.0f, mergedMat.plannedQty)
            assertEquals(50.0f, mergedMat.actualQty)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 21 to 22 adds designLength to route and backfills from node summary`() {
        val dbName = "legacy21.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 21)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Insert parent project to satisfy FK constraint in MIGRATION_22_23
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")

            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n1', 'proj1', 'ROUTE_1_P1', 'CON1', 10.0, 20.0, '', 'Vật tư:\n  Pipe: 2\nrouteLength: 125.4 m')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`) VALUES ('n2', 'proj1', 'ROUTE_1_P2', 'CON1', 10.1, 20.1, '', 'routeLength: 125.4 m')")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`) VALUES ('r1', 'proj1', 'ROUTE_1', 'CON1', 'ROUTE_1_P1', 'ROUTE_1_P2', '[]')")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val routes = runBlocking { database.gisRouteDao().byProject("proj1") }
            assertEquals(1, routes.size)
            val route = routes.first()
            assertEquals("125.4 m", route.designLength)

            val nodes = runBlocking { database.gisNodeDao().byProject("proj1") }
            val n1 = nodes.first { it.id == "n1" }
            val n2 = nodes.first { it.id == "n2" }

            assertEquals("Vật tư:\n  Pipe: 2", n1.workVolumeSummary)
            assertEquals("", n2.workVolumeSummary)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 22 to 23 adds foreign keys and checks cascade delete`() {
        val dbName = "legacy22.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 22)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj-fk-1', 'Project FK', 'proj-fk', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `imported_files` (`id`, `projectId`, `fileName`, `fileType`, `storedPath`, `summary`, `importedAtEpochMs`) VALUES ('file-fk-1', 'proj-fk-1', 'test.kml', 'KML', '/path', '', 1100)")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`) VALUES ('node-fk-1', 'proj-fk-1', 'N1', 'CON1', 10.0, 20.0, '', '', 'file-fk-1')")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `importedFileId`, `designLength`) VALUES ('route-fk-1', 'proj-fk-1', 'R1', 'CON1', 'N1', 'N2', '[]', 'file-fk-1', NULL)")
            db.execSQL("INSERT INTO `task` (`id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`) VALUES ('task-fk-1', 'proj-fk-1', 'N1', 'Task 1', '', 'TODO', 1200)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                val project = database.projectDao().get("proj-fk-1")
                org.junit.Assert.assertNotNull(project)
                
                val nodes = database.gisNodeDao().byProject("proj-fk-1")
                assertEquals(1, nodes.size)
                assertEquals("node-fk-1", nodes.first().id)
                
                val tasks = database.taskDao().byProject("proj-fk-1")
                assertEquals(1, tasks.size)
                assertEquals("task-fk-1", tasks.first().id)
            }

            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `storageMode`, `projectDbPath`) " +
                    "VALUES ('proj-invalid', 'Invalid', 'invalid', 0, 1000, 'INVALID_MODE', '')"
                )
                org.junit.Assert.fail("Expected CHECK constraint violation for projects.storageMode")
            } catch (e: android.database.sqlite.SQLiteException) {
                // Expected
            }

            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO `task` (`id`, `projectId`, `objectCode`, `title`, `description`, `status`, `createdAtEpochMs`) " +
                    "VALUES ('task-invalid', 'proj-fk-1', 'N1', 'Invalid Task', '', 'INVALID_STATUS', 1200)"
                )
                org.junit.Assert.fail("Expected CHECK constraint violation for task.status")
            } catch (e: android.database.sqlite.SQLiteException) {
                // Expected
            }

            try {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`) " +
                    "VALUES ('node-dup', 'proj-fk-1', 'N1', 'CON1', 11.0, 21.0, '', '')"
                )
                org.junit.Assert.fail("Expected UNIQUE constraint violation for gis_node (projectId, code)")
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // Expected
            }

            database.openHelper.writableDatabase.execSQL("DELETE FROM `imported_files` WHERE `id` = 'file-fk-1'")
            runBlocking {
                val nodes = database.gisNodeDao().byProject("proj-fk-1")
                assertEquals(1, nodes.size)
                assertNull(nodes.first().importedFileId)
            }

            database.openHelper.writableDatabase.execSQL("DELETE FROM `task` WHERE `projectId` = 'proj-fk-1'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM `gis_node` WHERE `projectId` = 'proj-fk-1'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM `gis_route` WHERE `projectId` = 'proj-fk-1'")
            database.openHelper.writableDatabase.execSQL("DELETE FROM `projects` WHERE `id` = 'proj-fk-1'")
            runBlocking {
                assertNull(database.projectDao().get("proj-fk-1"))
                assertEquals(0, database.gisNodeDao().byProject("proj-fk-1").size)
                assertEquals(0, database.gisRouteDao().byProject("proj-fk-1").size)
                assertEquals(0, database.taskDao().byProject("proj-fk-1").size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 23 to 24 adds unit column to material progress`() {
        val dbName = "legacy23.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 23)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`) VALUES ('mp1', 'proj1', 'N1', 'Concrete', 50.0, 20.0, 1000)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val materials = runBlocking { database.materialProgressDao().byProject("proj1") }
            assertEquals(1, materials.size)
            val mat = materials.first()
            assertEquals("mp1", mat.id)
            assertEquals("", mat.unit)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 24 to 25 adds ai action log table`() {
        val dbName = "legacy24_ai_action.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 24)

        val database = migratingDatabase(dbName).build()
        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            assertTrue(tableExists(database, "ai_action_log"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 27 to 28 renames material progress to work volume progress`() {
        val dbName = "legacy27.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 27)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `materialSummary`, `importedFileId`) VALUES ('node1', 'proj1', 'N1', 'CON1', 10.0, 20.0, '', 'Legacy summary', NULL)")
            db.execSQL("INSERT INTO `material_progress` (`id`, `projectId`, `nodeCode`, `materialName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`, `unit`) VALUES ('mp1', 'proj1', 'N1', 'Concrete', 50.0, 20.0, 1000, 'm3')")
        }

        val database = migratingDatabase(dbName).build()
        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            assertTrue(tableExists(database, "work_volume_progress"))
            val nodes = runBlocking { database.gisNodeDao().byProject("proj1") }
            assertEquals("Legacy summary", nodes.single().workVolumeSummary)
            val progress = runBlocking { database.materialProgressDao().byProject("proj1") }
            assertEquals(1, progress.size)
            assertEquals("Concrete", progress.single().materialName)
            assertEquals("m3", progress.single().unit)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 28 to 29 adds material handover table`() {
        val dbName = "legacy28_material_handover.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 28)

        val database = migratingDatabase(dbName).build()
        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            assertTrue(tableExists(database, "material_handover"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 29 to 30 adds material declaration table`() {
        val dbName = "legacy29_material_declaration.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 29)

        val database = migratingDatabase(dbName).build()
        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            assertTrue(tableExists(database, "material_declaration"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 30 to 31 adds rag document embedding table`() {
        val dbName = "legacy30_rag.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 30)

        val database = migratingDatabase(dbName).build()
        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            assertTrue(tableExists(database, "rag_document_embedding"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 26 to 27 adds mediaType, mimeType, durationMs columns to site photos`() {
        val dbName = "legacy26.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 26)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '')")
            db.execSQL("INSERT INTO `site_photos` (`id`, `projectId`, `objectCode`, `tagCodesCsv`, `filePath`, `thumbnailPath`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`) " +
                    "VALUES ('photo1', 'proj1', 'N1', '', '/path/to/file.jpg', '/path/to/thumb.jpg', 0, 'OK', 'Engineer', 1000, 0, 0)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val photos = runBlocking { database.sitePhotoDao().byProject("proj1") }
            assertEquals(1, photos.size)
            val photo = photos.first()
            assertEquals("photo1", photo.id)
            assertEquals(com.mapsupervision.domain.model.MediaType.IMAGE, photo.mediaType)
            assertEquals("image/jpeg", photo.mimeType)
            assertEquals(0L, photo.durationMs)
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 43 to 45 backfills material handover and work volume position links`() {
        val dbName = "legacy43.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 43)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `isDeleted`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '', 0)")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`, `importedFileId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`) VALUES ('node1', 'proj1', 'N1', 'CON1', 10.0, 20.0, '', '', NULL, 1000, 0, NULL)")
            db.execSQL("INSERT INTO `work_volume_progress` (`id`, `projectId`, `nodeCode`, `workName`, `plannedQty`, `actualQty`, `updatedAtEpochMs`, `unit`, `nodeId`, `isDeleted`, `deletedAtEpochMs`) VALUES ('wp1', 'proj1', 'N1', 'Work A', 10.0, 1.0, 1000, 'm', NULL, 0, NULL)")
            db.execSQL("INSERT INTO `material_handover` (`id`, `projectId`, `nodeCode`, `workName`, `materialName`, `contractor`, `quantity`, `unit`, `handoverDateEpochDay`, `note`, `createdAtEpochMs`, `nodeId`, `materialDeclarationId`, `workCategoryId`, `receiver`) VALUES ('ho1', 'proj1', 'N1', 'Work A:Steel', '', 'CON1', 5.0, 'kg', 10, '', 1000, NULL, NULL, NULL, '')")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            database.openHelper.readableDatabase.query("SELECT `workName`, `materialName`, `nodeId` FROM `material_handover` WHERE `id` = 'ho1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Work A", cursor.getString(0))
                assertEquals("Steel", cursor.getString(1))
                assertEquals("node1", cursor.getString(2))
            }
            database.openHelper.readableDatabase.query("SELECT `nodeId` FROM `work_volume_progress` WHERE `id` = 'wp1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("node1", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 42 to 43 restores site photo objectCode and backfills from matched node`() {
        val dbName = "legacy42.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 42)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `isDeleted`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '', 0)")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`, `importedFileId`, `updatedAtEpochMs`, `isDeleted`, `deletedAtEpochMs`) VALUES ('node1', 'proj1', 'N1', 'CON1', 10.0, 20.0, '', '', NULL, 1000, 0, NULL)")
            db.execSQL(
                "INSERT INTO `site_photos` (`id`, `projectId`, `tagCodesCsv`, `filePath`, `thumbnailPath`, `latitude`, `longitude`, `locationAccuracyM`, `isGpsMocked`, `locationStatus`, `engineer`, `capturedAtEpochMs`, `matchedAtEpochMs`, `matchingTimeOffsetMs`, `mediaType`, `mimeType`, `durationMs`, `address`, `captureNote`, `matchedNodeId`, `matchedRouteId`, `updatedAtEpochMs`, `syncStatus`, `remoteUrl`, `lastSyncAttemptEpochMs`, `isDeleted`, `deletedAtEpochMs`) " +
                    "VALUES ('photo1', 'proj1', '', '/path/to/file.jpg', '/path/to/thumb.jpg', 10.0, 20.0, NULL, 0, 'OK', 'Engineer', 1000, 0, 0, 'IMAGE', 'image/jpeg', 0, NULL, NULL, 'node1', NULL, 1000, 'PENDING', NULL, NULL, 0, NULL)"
            )
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(46, database.openHelper.writableDatabase.version)
            database.openHelper.readableDatabase.query("SELECT `objectCode` FROM `site_photos` WHERE `id` = 'photo1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("N1", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 45 to 46 adds network and fiber fields`() {
        val dbName = "legacy45.sqlite"
        val dbFile = File(tempDir, dbName)
        createLegacyDatabaseFromSchema(dbFile, 45)

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO `projects` (`id`, `name`, `slug`, `isArchived`, `createdAtEpochMs`, `metadataVersion`, `updatedAtEpochMs`, `storageMode`, `projectDbPath`, `isDeleted`) VALUES ('proj1', 'Project 1', 'proj1', 0, 1000, 3, 1000, 'LEGACY_SHARED', '', 0)")
            db.execSQL("INSERT INTO `gis_node` (`id`, `projectId`, `code`, `contractor`, `latitude`, `longitude`, `mapNumberLabel`, `workVolumeSummary`, `updatedAtEpochMs`, `isDeleted`) VALUES ('node1', 'proj1', 'N1', 'CON1', 10.0, 20.0, '', '', 1000, 0)")
            db.execSQL("INSERT INTO `gis_route` (`id`, `projectId`, `code`, `contractor`, `startNodeCode`, `endNodeCode`, `points`, `updatedAtEpochMs`, `isDeleted`) VALUES ('route1', 'proj1', 'R1', 'CON1', 'N1', 'N2', '[]', 1000, 0)")
        }

        val database = Room.databaseBuilder(context, MapSupervisionDatabase::class.java, dbName)
            .addMigrations(*MapSupervisionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(46, database.openHelper.writableDatabase.version)

            // Verify new columns exist on gis_node with default values
            database.openHelper.readableDatabase.query("SELECT `ipAddress`, `subnet`, `gateway`, `signalStatus` FROM `gis_node` WHERE `id` = 'node1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("UNKNOWN", cursor.getString(3))
            }

            // Verify new columns exist on gis_route with default values
            database.openHelper.readableDatabase.query("SELECT `fiberCoreCount`, `fiberConnection` FROM `gis_route` WHERE `id` = 'route1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals("", cursor.getString(1))
            }
        } finally {
            database.close()
        }
    }
}
