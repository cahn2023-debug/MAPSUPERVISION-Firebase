package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.NoteEntity

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteEntity)

    @Query("UPDATE note SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id AND isDeleted = 0")
    suspend fun markDeletedById(id: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query(
        "SELECT * FROM note WHERE projectId = :projectId AND isDeleted = 0 AND (" +
            "objectNodeId IN (SELECT id FROM gis_node WHERE projectId = :projectId AND code = :objectCode) OR " +
            "objectRouteId IN (SELECT id FROM gis_route WHERE projectId = :projectId AND code = :objectCode)" +
        ") ORDER BY createdAtEpochMs DESC"
    )
    suspend fun byObject(projectId: String, objectCode: String): List<NoteEntity>

    @Query("SELECT * FROM note WHERE projectId = :projectId AND isDeleted = 0 ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<NoteEntity>

    @Query("SELECT * FROM note WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProjectIncludingDeleted(projectId: String): List<NoteEntity>

    @Query("SELECT * FROM note WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<NoteEntity>

    @Query("DELETE FROM note WHERE projectId = :projectId AND isDeleted = 1 AND deletedAtEpochMs IS NOT NULL AND deletedAtEpochMs < :deletedBeforeEpochMs")
    suspend fun purgeDeletedBefore(projectId: String, deletedBeforeEpochMs: Long): Int
}
