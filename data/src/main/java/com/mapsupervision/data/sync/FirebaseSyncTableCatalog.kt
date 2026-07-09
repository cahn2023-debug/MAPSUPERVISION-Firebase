package com.mapsupervision.data.sync

internal enum class SyncScope {
    SHARED_ONLY,
    PROJECT_MIRROR
}

internal data class FirebaseSyncTable(
    val tableName: String,
    val collectionName: String = tableName,
    val projectIdColumn: String = "projectId",
    val idColumn: String = "id",
    val syncCursorColumn: String,
    val scope: SyncScope = SyncScope.PROJECT_MIRROR
)

internal object FirebaseSyncTableCatalog {
    val tables = listOf(
        FirebaseSyncTable("projects", collectionName = "__project_root__", projectIdColumn = "id", syncCursorColumn = "updatedAtEpochMs", scope = SyncScope.SHARED_ONLY),
        FirebaseSyncTable("gis_node", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("gis_route", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("imported_files", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("import_session", syncCursorColumn = "updatedAtEpochMs", scope = SyncScope.SHARED_ONLY),
        FirebaseSyncTable("import_version", syncCursorColumn = "createdAtEpochMs", scope = SyncScope.SHARED_ONLY),
        FirebaseSyncTable("import_conflict", syncCursorColumn = "createdAtEpochMs", scope = SyncScope.SHARED_ONLY),
        FirebaseSyncTable("import_audit", syncCursorColumn = "createdAtEpochMs", scope = SyncScope.SHARED_ONLY),
        FirebaseSyncTable("node_progress", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("work_volume_progress", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("work_categories", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("work_plan", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("daily_log", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("daily_log_line", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("daily_log_nodes", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("daily_log_photos", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("task", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("note", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("site_photos", syncCursorColumn = "updatedAtEpochMs"),
        FirebaseSyncTable("photo_tags", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("report_draft", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("material_declaration", syncCursorColumn = "createdAtEpochMs"),
        FirebaseSyncTable("material_handover", syncCursorColumn = "createdAtEpochMs")
    )

    fun byTableName(tableName: String): FirebaseSyncTable =
        tables.first { it.tableName == tableName }
}
