package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mapsupervision.data.db.entity.ImportVersionEntity

@Dao
interface ImportVersionDao {
    @Upsert
    suspend fun upsert(version: ImportVersionEntity)

    @Query("SELECT * FROM import_version WHERE projectId = :projectId ORDER BY versionNumber DESC")
    suspend fun byProject(projectId: String): List<ImportVersionEntity>

    @Query("SELECT * FROM import_version WHERE projectId = :projectId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun latest(projectId: String): ImportVersionEntity?

    @Query("SELECT * FROM import_version WHERE projectId = :projectId AND versionNumber = :versionNumber LIMIT 1")
    suspend fun findByVersion(projectId: String, versionNumber: Int): ImportVersionEntity?
}
