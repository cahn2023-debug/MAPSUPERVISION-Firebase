package com.mapsupervision.domain.repository

import com.mapsupervision.core.result.AppResult
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import kotlinx.coroutines.flow.Flow

interface GisRepository {
    suspend fun upsertNode(node: GisNode): AppResult<Unit>
    suspend fun upsertRoute(route: GisRoute): AppResult<Unit>
    suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit>
    suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit>
    suspend fun replaceImportedGeometry(importedFileId: String, nodes: List<GisNode>, routes: List<GisRoute>): AppResult<Unit>
    suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>>
    suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>>
    suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?>
    fun observeNodes(projectId: String, query: String): Flow<List<GisNode>>
    fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>>
}
