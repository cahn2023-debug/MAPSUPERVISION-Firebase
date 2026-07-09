package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.PhotoTagEntity

@Dao
interface PhotoTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PhotoTagEntity>)

    @Query("DELETE FROM photo_tags WHERE projectId = :projectId AND photoId = :photoId")
    suspend fun deleteForPhoto(projectId: String, photoId: String)

    @Query("SELECT * FROM photo_tags WHERE projectId = :projectId AND photoId IN (:photoIds)")
    suspend fun byPhotoIds(projectId: String, photoIds: List<String>): List<PhotoTagEntity>
}
