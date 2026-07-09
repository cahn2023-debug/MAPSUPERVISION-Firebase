package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mapsupervision.data.db.entity.ImportAuditEntity

@Dao
interface ImportAuditDao {
    @Upsert
    suspend fun upsert(audit: ImportAuditEntity)

    @Query("SELECT * FROM import_audit WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<ImportAuditEntity>

    @Query("DELETE FROM import_audit WHERE projectId = :projectId AND createdAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
