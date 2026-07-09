package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.MaterialHandoverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialHandoverDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MaterialHandoverEntity)

    @Delete
    suspend fun delete(entity: MaterialHandoverEntity)

    @Query("SELECT * FROM material_handover WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<MaterialHandoverEntity>

    @Query("SELECT * FROM material_handover WHERE projectId = :projectId ORDER BY createdAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<MaterialHandoverEntity>>
}
