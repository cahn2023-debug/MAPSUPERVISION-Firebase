package com.mapsupervision.data.sync

import android.database.Cursor
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class WorkspaceSnapshotExporter @Inject constructor(
    private val sharedDatabase: MapSupervisionDatabase,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) {
    companion object {
        val PUBLIC_COLLECTIONS = listOf(
            "gis_node",
            "gis_route",
            "task",
            "note",
            "work_plan",
            "daily_log",
            "site_photos",
            "work_volume_progress",
            "material_declaration",
            "material_handover",
            "report_draft"
        )
    }

    suspend fun exportProjectSnapshotJson(projectId: String): String? {
        val projectEntity = sharedDatabase.projectDao().get(projectId) ?: return null
        if (projectEntity.isDeleted) return null

        val projectJson = JSONObject().apply {
            put("id", projectEntity.id)
            put("name", projectEntity.name)
            put("slug", projectEntity.slug)
            put("storageMode", projectEntity.storageMode.name)
            put("createdAtEpochMs", projectEntity.createdAtEpochMs)
            put("updatedAtEpochMs", projectEntity.updatedAtEpochMs)
            put("isArchived", projectEntity.isArchived)
            put("mediaStorageProvider", projectEntity.mediaStorageProvider)
            put("mediaStorageFolderId", projectEntity.mediaStorageFolderId)
        }

        val collectionsJson = JSONObject()
        val scopedDb = projectScopedDatabaseProvider.databaseFor(projectId)

        PUBLIC_COLLECTIONS.forEach { tableName ->
            val rows = loadActiveRows(tableName, projectId, scopedDb)
            val jsonArray = JSONArray()
            rows.forEach { rowMap ->
                jsonArray.put(JSONObject(rowMap))
            }
            collectionsJson.put(tableName, jsonArray)
        }

        val snapshotPayload = JSONObject().apply {
            put("project", projectJson)
            put("collections", collectionsJson)
            put("updatedAtEpochMs", System.currentTimeMillis())
        }

        return snapshotPayload.toString()
    }

    private fun loadActiveRows(
        tableName: String,
        projectId: String,
        scopedDb: MapSupervisionDatabase?
    ): List<Map<String, Any?>> {
        val dbToUse = when {
            scopedDb != null && hasTable(scopedDb, tableName) -> scopedDb
            hasTable(sharedDatabase, tableName) -> sharedDatabase
            else -> return emptyList()
        }

        val sqliteDb = dbToUse.openHelper.readableDatabase
        val hasIsDeleted = hasColumn(sqliteDb, tableName, "isDeleted")
        val hasProjectId = hasColumn(sqliteDb, tableName, "projectId")

        val query = when {
            hasProjectId && hasIsDeleted -> "SELECT * FROM `$tableName` WHERE `projectId` = ? AND (`isDeleted` = 0 OR `isDeleted` IS NULL)"
            hasProjectId -> "SELECT * FROM `$tableName` WHERE `projectId` = ?"
            hasIsDeleted -> "SELECT * FROM `$tableName` WHERE (`isDeleted` = 0 OR `isDeleted` IS NULL)"
            else -> "SELECT * FROM `$tableName`"
        }

        val args = if (hasProjectId) arrayOf<Any>(projectId) else emptyArray()
        val cursor = sqliteDb.query(SimpleSQLiteQuery(query, args))
        return cursor.use { it.toRowMaps() }
    }

    private fun hasTable(db: MapSupervisionDatabase, tableName: String): Boolean {
        return try {
            val cursor = db.openHelper.readableDatabase.query(
                SimpleSQLiteQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
            )
            cursor.use { it.moveToFirst() }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        return try {
            val cursor = db.query(SimpleSQLiteQuery("PRAGMA table_info(`$tableName`)"))
            cursor.use {
                while (it.moveToNext()) {
                    val nameIndex = it.getColumnIndex("name")
                    if (nameIndex >= 0 && it.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun Cursor.toRowMaps(): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        while (moveToNext()) {
            val row = linkedMapOf<String, Any?>()
            for (index in 0 until columnCount) {
                row[getColumnName(index)] = when (getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
                    Cursor.FIELD_TYPE_STRING -> getString(index)
                    Cursor.FIELD_TYPE_BLOB -> null
                    else -> getString(index)
                }
            }
            rows += row
        }
        return rows
    }
}
