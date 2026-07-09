package com.mapsupervision.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mapsupervision.data.db.entity.RagDocumentEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDocumentEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RagDocumentEmbeddingEntity>)

    @Query("SELECT * FROM rag_document_embedding WHERE projectId = :projectId AND isDeleted = 0 ORDER BY updatedAtEpochMs DESC")
    suspend fun byProject(projectId: String): List<RagDocumentEmbeddingEntity>

    @Query("SELECT * FROM rag_document_embedding WHERE projectId = :projectId AND isDeleted = 0 ORDER BY updatedAtEpochMs DESC")
    fun observeByProject(projectId: String): Flow<List<RagDocumentEmbeddingEntity>>

    @Query("UPDATE rag_document_embedding SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND id IN (:ids) AND isDeleted = 0")
    suspend fun markDeletedByIds(projectId: String, ids: List<String>, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("UPDATE rag_document_embedding SET isDeleted = 1, deletedAtEpochMs = :deletedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun markDeletedByProject(projectId: String, updatedAtEpochMs: Long, deletedAtEpochMs: Long)

    @Query("SELECT * FROM rag_document_embedding WHERE projectId = :projectId AND updatedAtEpochMs > :updatedAfterEpochMs ORDER BY updatedAtEpochMs ASC")
    suspend fun changedSince(projectId: String, updatedAfterEpochMs: Long): List<RagDocumentEmbeddingEntity>
}
