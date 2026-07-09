package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mapsupervision.data.db.entity.ImportConflictEntity

@Dao
interface ImportConflictDao {
    @Upsert
    suspend fun upsert(conflict: ImportConflictEntity)

    @Upsert
    suspend fun upsertAll(conflicts: List<ImportConflictEntity>)

    @Query("SELECT * FROM import_conflict WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<ImportConflictEntity>

    @Query("DELETE FROM import_conflict WHERE projectId = :projectId AND createdAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
