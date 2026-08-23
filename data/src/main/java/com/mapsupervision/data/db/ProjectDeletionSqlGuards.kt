package com.mapsupervision.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** Prevents Room/SQLite writes to business rows after a project is locked. */
object ProjectDeletionSqlGuards {
    private val projectTables = listOf(
        "gis_node", "gis_route", "imported_files", "import_session", "import_version",
        "import_conflict", "import_audit", "node_progress", "work_volume_progress",
        "work_categories", "work_plan", "daily_log", "daily_log_line", "daily_log_nodes",
        "daily_log_photos", "task", "note", "site_photos", "photo_tags", "report_draft",
        "material_declaration", "material_handover", "event_outbox",
        "chat_history", "ai_decision_cache", "ai_action_log", "rag_document_embedding"
    )

    fun install(database: SupportSQLiteDatabase) {
        projectTables.forEach { table ->
            val triggerBase = "project_deletion_guard_${table}"
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS ${triggerBase}_insert
                BEFORE INSERT ON `$table`
                WHEN EXISTS (
                    SELECT 1 FROM projects
                    WHERE id = NEW.projectId
                      AND (deletionState <> 'ACTIVE' OR isDeleted = 1)
                )
                BEGIN SELECT RAISE(ABORT, 'Project is locked for deletion'); END
            """.trimIndent())
            database.execSQL("""
                CREATE TRIGGER IF NOT EXISTS ${triggerBase}_update
                BEFORE UPDATE ON `$table`
                WHEN EXISTS (
                    SELECT 1 FROM projects
                    WHERE id = NEW.projectId
                      AND (deletionState <> 'ACTIVE' OR isDeleted = 1)
                )
                BEGIN SELECT RAISE(ABORT, 'Project is locked for deletion'); END
            """.trimIndent())
        }
    }
}
