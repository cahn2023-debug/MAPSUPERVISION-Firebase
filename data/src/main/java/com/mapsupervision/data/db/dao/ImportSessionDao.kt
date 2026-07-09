package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mapsupervision.data.db.entity.ImportSessionEntity

@Dao
interface ImportSessionDao {
    @Upsert
    suspend fun upsert(session: ImportSessionEntity)

    @Query("SELECT * FROM import_session WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<ImportSessionEntity>

    @Query("SELECT * FROM import_session WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ImportSessionEntity?

    @Query("DELETE FROM import_session WHERE projectId = :projectId AND createdAtEpochMs < :deletedBeforeEpochMs AND status IN ('DELETED', 'ROLLED_BACK')")
    suspend fun purgeCompletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
