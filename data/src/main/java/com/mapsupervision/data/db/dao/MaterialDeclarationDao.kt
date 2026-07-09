package com.mapsupervision.data.db.dao

import androidx.room.*
import com.mapsupervision.data.db.entity.MaterialDeclarationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDeclarationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(declaration: MaterialDeclarationEntity)

    @Delete
    suspend fun delete(declaration: MaterialDeclarationEntity)

    @Query("SELECT * FROM material_declaration WHERE projectId = :projectId ORDER BY createdAtEpochMs ASC")
    suspend fun getByProject(projectId: String): List<MaterialDeclarationEntity>

    @Query("SELECT * FROM material_declaration WHERE projectId = :projectId ORDER BY createdAtEpochMs ASC")
    fun observeByProject(projectId: String): Flow<List<MaterialDeclarationEntity>>

    @Query("DELETE FROM material_declaration WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)
}
