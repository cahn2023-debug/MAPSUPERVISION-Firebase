package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.Note

interface NoteRepository {
    suspend fun add(note: Note): AppResult<Unit>
    suspend fun delete(noteId: String): AppResult<Unit>
    suspend fun byObject(projectId: String, objectCode: String): AppResult<List<Note>>
    suspend fun byProject(projectId: String): AppResult<List<Note>>
}
