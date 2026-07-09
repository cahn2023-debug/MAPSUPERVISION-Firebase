package com.mapsupervision.data.db

import android.database.Cursor
import com.mapsupervision.core.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugQueryPlanInspector @Inject constructor(
    private val sharedDatabase: MapSupervisionDatabase,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) {
    suspend fun logPlan(projectId: String, sql: String) {
        val db = projectScopedDatabaseProvider.databaseFor(projectId) ?: sharedDatabase
        val cursor: Cursor = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql")
        cursor.use {
            val rows = mutableListOf<String>()
            while (it.moveToNext()) {
                rows += it.getString(it.columnCount - 1)
            }
            AppLogger.d("db.query.plan project=$projectId sql=${sql.take(80)} plan=${rows.joinToString(" | ")}")
        }
    }
}
