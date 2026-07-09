package com.mapsupervision.data.rag

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.dao.RagDocumentEmbeddingDao
import com.mapsupervision.data.db.entity.RagDocumentEmbeddingEntity
import com.mapsupervision.ai.prompt.CanonicalTextNormalizer
import com.mapsupervision.ai.prompt.DictionaryResolverCore
import com.mapsupervision.ai.prompt.DictionarySnapshot
import com.mapsupervision.ai.core.rag.RagBuildRequest
import com.mapsupervision.ai.core.rag.RagBuildResult
import com.mapsupervision.ai.core.rag.RagContextBlock
import com.mapsupervision.ai.core.rag.RagContextBuilder
import com.mapsupervision.ai.core.rag.RagDocument
import com.mapsupervision.ai.rag.RagDocumentBuilder
import com.mapsupervision.ai.core.rag.RagDocumentType
import com.mapsupervision.ai.core.rag.RagIndexRepository
import com.mapsupervision.ai.core.rag.RagQueryDomain
import com.mapsupervision.ai.core.rag.RagRetrievedDocument
import com.mapsupervision.ai.core.rag.RagRetriever
import com.mapsupervision.ai.core.rag.TextEmbeddingEngine
import com.mapsupervision.domain.model.WorkspaceSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val TAG = "SemanticRag"

@Singleton
class OptionalTfliteTextEmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextEmbeddingEngine {
    private val modelAssetNames = listOf("text_embedding.tflite")
    private val lock = Any()

    @Volatile
    private var textEmbedder: TextEmbedder? = null

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        val normalized = normalizeText(text)
        if (normalized.isBlank()) return@withContext null
        runCatching { embedWithTextEmbedder(normalized) }.getOrNull()
    }

    private fun embedWithTextEmbedder(text: String): FloatArray? {
        val embedder = ensureTextEmbedder() ?: return null
        val embedding = runCatching {
            embedder.embed(text).embeddingResult().embeddings().firstOrNull()
        }.getOrNull() ?: return null
        return extractFloatEmbedding(embedding)
    }

    private fun ensureTextEmbedder(): TextEmbedder? = synchronized(lock) {
        textEmbedder?.let { return it }
        for (assetName in modelAssetNames) {
            val candidate = runCatching {
                TextEmbedder.createFromOptions(
                    context,
                    TextEmbedder.TextEmbedderOptions.builder()
                        .setBaseOptions(
                            BaseOptions.builder()
                                .setModelAssetPath(assetName)
                                .build()
                        )
                        .setL2Normalize(false)
                        .setQuantize(false)
                        .build()
                )
            }.getOrNull() ?: continue
            textEmbedder = candidate
            return candidate
        }
        null
    }

    private fun normalizeText(text: String): String {
        return CanonicalTextNormalizer.normalizeKey(text).ifBlank { text.trim().lowercase(Locale.US) }
    }

    private fun extractFloatEmbedding(embedding: Embedding): FloatArray? {
        val vector = embedding.floatEmbedding()
        if (vector.isEmpty()) return null
        return l2Normalize(vector)
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.fold(0.0) { acc, value -> acc + value * value }.toFloat())
        if (norm <= 0f) return vector
        return FloatArray(vector.size) { idx -> vector[idx] / norm }
    }
}

@Singleton
class RagIndexRepositoryImpl @Inject constructor(
    private val dao: RagDocumentEmbeddingDao,
    private val projectScopedDatabaseProvider: ProjectScopedDatabaseProvider
) : RagIndexRepository {
    override suspend fun listByProject(projectId: String): List<RagDocument> {
        return dao(projectId).byProject(projectId).map { it.toDomain() }
    }

    override suspend fun upsertAll(projectId: String, documents: List<RagDocument>) {
        if (documents.isEmpty()) return
        dao(projectId).upsertAll(documents.map { it.toEntity() })
    }

    override suspend fun deleteMissing(projectId: String, keepIds: Set<String>) {
        val existing = dao(projectId).byProject(projectId)
        val stale = existing.mapNotNull { entity -> entity.id.takeIf { it !in keepIds } }
        if (stale.isNotEmpty()) {
            val now = System.currentTimeMillis()
            dao(projectId).markDeletedByIds(projectId, stale, now, now)
        }
    }

    override suspend fun clearProject(projectId: String) {
        val now = System.currentTimeMillis()
        dao(projectId).markDeletedByProject(projectId, now, now)
    }

    private suspend fun dao(projectId: String): RagDocumentEmbeddingDao {
        return projectScopedDatabaseProvider.databaseFor(projectId)?.ragDocumentEmbeddingDao() ?: dao
    }
}

@Singleton
class RagRetrieverImpl @Inject constructor(
    private val repository: RagIndexRepository,
    private val embeddingEngine: TextEmbeddingEngine
) : RagRetriever {
    override suspend fun retrieve(request: RagBuildRequest): RagBuildResult {
        val documents = repository.listByProject(request.projectId)
        if (documents.isEmpty()) {
            return RagBuildResult(block = emptyBlock(request), retrievedDocuments = emptyList())
        }

        val snapshot = request.workspaceSnapshot
        val queryDomain = request.queryDomain ?: RagQueryDomain.infer(request.query)
        val dictionary = snapshot?.let {
            DictionaryResolverCore(
                DictionarySnapshot(
                    projectId = it.projectId,
                    nodes = it.designNodes,
                    routes = it.designRoutes,
                    workCategories = it.workCategories
                )
            )
        }
        val queryVector = embeddingEngine.embed(request.query)
        val selectedNodeCode = request.selectedNodeCode.orEmpty().trim()
        val selectedRouteCode = request.selectedRouteCode.orEmpty().trim()

        val scopedDocuments = documents
            .filter { document -> document.docType in docTypesFor(queryDomain) }
            .ifEmpty { documents }

        val ranked = scopedDocuments.map { document ->
            val dictionaryScore = dictionaryScore(dictionary, request, document, selectedNodeCode, selectedRouteCode)
            val semanticScore = semanticScore(queryVector, document.embedding)
            val lexicalScore = lexicalScore(request.query, document.text)
            var finalScore = dictionaryScore * 0.45f + semanticScore * 0.40f + lexicalScore * 0.15f
            val isSelected = selectedNodeCode.isNotBlank() && (
                document.sourceCode.equals(selectedNodeCode, ignoreCase = true) ||
                    (document.docType == RagDocumentType.NODE_PROGRESS && document.sourceCode.equals(selectedNodeCode, ignoreCase = true)) ||
                    (document.docType == RagDocumentType.WORK_VOLUME_PROGRESS && document.sourceCode.substringBefore(':').equals(selectedNodeCode, ignoreCase = true)) ||
                    (document.docType == RagDocumentType.WORK_PLAN && containsField(document.text, "node_code", selectedNodeCode)) ||
                    (document.docType == RagDocumentType.DAILY_LOG && containsField(document.text, "node_code", selectedNodeCode))
                ) || selectedRouteCode.isNotBlank() && (
                document.sourceCode.equals(selectedRouteCode, ignoreCase = true) ||
                    (document.docType == RagDocumentType.WORK_PLAN && containsField(document.text, "route_code", selectedRouteCode)) ||
                    (document.docType == RagDocumentType.DAILY_LOG && containsField(document.text, "route_code", selectedRouteCode))
                )
            if (isSelected) {
                finalScore += 0.12f
            }
            if (document.docType in primaryDocTypesFor(queryDomain)) {
                finalScore += 0.10f
            }
            RagRetrievedDocument(
                document = document,
                dictionaryScore = dictionaryScore,
                semanticScore = semanticScore,
                lexicalScore = lexicalScore,
                finalScore = finalScore,
                isSelected = isSelected
            )
        }.sortedByDescending { it.finalScore }

        val topHits = ranked.take(request.limit)
        val block = RagContextBlock(
            resolvedRefs = buildResolvedRefs(dictionary, request, selectedNodeCode, selectedRouteCode),
            retrievedContext = buildRetrievedContext(topHits),
            dbSnapshot = buildDbSnapshot(snapshot, selectedNodeCode, selectedRouteCode),
            recentLogs = buildRecentLogs(snapshot, selectedNodeCode, selectedRouteCode),
            relatedWorkCategories = buildRelatedWorkCategories(snapshot, topHits),
            queryDomain = queryDomain
        )
        return RagBuildResult(block = block, retrievedDocuments = topHits, queryDomain = queryDomain)
    }

    private fun docTypesFor(domain: RagQueryDomain): Set<RagDocumentType> {
        return when (domain) {
            RagQueryDomain.PROGRESS -> setOf(
                RagDocumentType.NODE,
                RagDocumentType.ROUTE,
                RagDocumentType.WORK_CATEGORY,
                RagDocumentType.NODE_PROGRESS,
                RagDocumentType.WORK_VOLUME_PROGRESS
            )
            RagQueryDomain.PLANNING -> setOf(
                RagDocumentType.NODE,
                RagDocumentType.ROUTE,
                RagDocumentType.WORK_CATEGORY,
                RagDocumentType.WORK_PLAN
            )
            RagQueryDomain.DAILY_LOG -> setOf(
                RagDocumentType.NODE,
                RagDocumentType.ROUTE,
                RagDocumentType.WORK_CATEGORY,
                RagDocumentType.DAILY_LOG
            )
            RagQueryDomain.GENERAL -> RagDocumentType.entries.toSet()
        }
    }

    private fun primaryDocTypesFor(domain: RagQueryDomain): Set<RagDocumentType> {
        return when (domain) {
            RagQueryDomain.PROGRESS -> setOf(RagDocumentType.NODE_PROGRESS, RagDocumentType.WORK_VOLUME_PROGRESS)
            RagQueryDomain.PLANNING -> setOf(RagDocumentType.WORK_PLAN)
            RagQueryDomain.DAILY_LOG -> setOf(RagDocumentType.DAILY_LOG)
            RagQueryDomain.GENERAL -> emptySet()
        }
    }

    private fun buildResolvedRefs(
        dictionary: DictionaryResolverCore?,
        request: RagBuildRequest,
        selectedNodeCode: String,
        selectedRouteCode: String
    ): String {
        val refs = mutableListOf<String>()
        if (selectedNodeCode.isNotBlank()) refs += "node=$selectedNodeCode"
        if (selectedRouteCode.isNotBlank()) refs += "route=$selectedRouteCode"
        val snapshot = request.workspaceSnapshot
        if (snapshot != null) {
            val core = dictionary ?: DictionaryResolverCore(
                DictionarySnapshot(
                    projectId = snapshot.projectId,
                    nodes = snapshot.designNodes,
                    routes = snapshot.designRoutes,
                    workCategories = snapshot.workCategories
                )
            )
            core.resolveNode(request.query)?.let { refs += "node=${it.value.code}" }
            core.resolveRoute(request.query)?.let { refs += "route=${it.value.code}" }
            core.resolveCategory(request.query)?.let { refs += "category=${it.value.name}:${it.value.unit}" }
        }
        return refs.distinct().joinToString(";")
    }

    private fun buildRetrievedContext(hits: List<RagRetrievedDocument>): String {
        return hits.take(5).joinToString("\n") { hit ->
            buildString {
                append(hit.document.docType.name.lowercase(Locale.US))
                append("[").append(hit.document.sourceCode.ifBlank { hit.document.sourceId }).append("]")
                append(" score=").append(format(hit.finalScore))
                append(" dict=").append(format(hit.dictionaryScore))
                append(" sem=").append(format(hit.semanticScore))
                append(" lex=").append(format(hit.lexicalScore))
                append(" text=").append(hit.document.text.take(220))
            }
        }
    }

    private fun buildDbSnapshot(
        snapshot: WorkspaceSnapshot?,
        selectedNodeCode: String,
        selectedRouteCode: String
    ): String {
        if (snapshot == null) return ""
        val selectedNode = snapshot.designNodes.firstOrNull { it.code.equals(selectedNodeCode, ignoreCase = true) }
            ?: snapshot.designNodes.firstOrNull()
        val selectedRoute = snapshot.designRoutes.firstOrNull { it.code.equals(selectedRouteCode, ignoreCase = true) }
            ?: snapshot.designRoutes.firstOrNull()
        val progress = selectedNode?.let { node -> snapshot.constructionProgress.firstOrNull { it.nodeCode.equals(node.code, ignoreCase = true) } }
        val volumeRows = selectedNode?.let { node -> snapshot.workVolumeRows.filter { it.nodeCode.equals(node.code, ignoreCase = true) } }.orEmpty()
        return buildString {
            append("projectId=").append(snapshot.projectId)
            selectedNode?.let {
                append("\nselected_node=").append(it.code)
                append("\nselected_node_label=").append(it.mapNumberLabel)
                append("\nselected_node_contractor=").append(it.contractor)
                append("\nselected_node_work_volume_summary=").append(it.workVolumeSummary)
            }
            selectedRoute?.let {
                append("\nselected_route=").append(it.code)
                append("\nselected_route_nodes=").append(it.startNodeCode).append(" -> ").append(it.endNodeCode)
                append("\nselected_route_contractor=").append(it.contractor)
            }
            progress?.let {
                append("\nnode_progress_current=").append(it.nodeCode)
                append(" planned=").append(format(it.planned))
                append(" actual=").append(format(it.actual))
                append(" remain=").append(format(it.remain))
                append(" delayed=").append(it.delayed)
                append(" updatedAtEpochMs=").append(it.updatedAtEpochMs)
            }
            if (volumeRows.isNotEmpty()) {
                append("\nwork_volume_progress=").append(
                    volumeRows.take(3).joinToString(" | ") { row ->
                        "${row.workName}:${format(row.actualQty)}/${format(row.plannedQty)}${row.unit}"
                    }
                )
            }
        }
    }

    private fun buildRecentLogs(
        snapshot: WorkspaceSnapshot?,
        selectedNodeCode: String,
        selectedRouteCode: String
    ): String {
        if (snapshot == null) return ""
        val related = snapshot.dailyLogs.filter { log ->
            (selectedNodeCode.isNotBlank() && log.nodeCode.equals(selectedNodeCode, ignoreCase = true)) ||
                (selectedRouteCode.isNotBlank() && log.routeCode.equals(selectedRouteCode, ignoreCase = true))
        }.ifEmpty {
            snapshot.dailyLogs
        }
        return related
            .sortedByDescending { it.createdAtEpochMs }
            .take(5)
            .joinToString(" | ") { log ->
                "${log.nodeCode.orEmpty()}:${log.workItem.take(32)}:${log.note.take(72)}"
            }
    }

    private fun buildRelatedWorkCategories(snapshot: WorkspaceSnapshot?, hits: List<RagRetrievedDocument>): String {
        val categories = snapshot?.workCategories.orEmpty().take(5).joinToString(", ") { "${it.name}:${it.unit}" }
        if (categories.isNotBlank()) return categories
        return hits.filter { it.document.docType == RagDocumentType.WORK_CATEGORY }
            .take(5)
            .joinToString(", ") { "${it.document.sourceCode}:${it.document.text.take(48)}" }
    }

    private fun dictionaryScore(
        dictionary: DictionaryResolverCore?,
        request: RagBuildRequest,
        document: RagDocument,
        selectedNodeCode: String,
        selectedRouteCode: String
    ): Float {
        val snapshot = request.workspaceSnapshot ?: return 0f
        val core = dictionary ?: DictionaryResolverCore(
            DictionarySnapshot(
                projectId = snapshot.projectId,
                nodes = snapshot.designNodes,
                routes = snapshot.designRoutes,
                workCategories = snapshot.workCategories
            )
        )
        val queryNode = core.resolveNode(request.query)?.value?.code.orEmpty()
        val queryRoute = core.resolveRoute(request.query)?.value?.code.orEmpty()
        val queryCategory = core.resolveCategory(request.query)?.value?.name.orEmpty()
        return when (document.docType) {
            RagDocumentType.NODE -> scoreFromMatch(
                match = if (document.sourceCode.equals(queryNode, ignoreCase = true)) {
                    core.resolveNode(request.query)
                } else null,
                selected = document.sourceCode.equals(selectedNodeCode, ignoreCase = true)
            )
            RagDocumentType.ROUTE -> scoreFromMatch(
                match = if (document.sourceCode.equals(queryRoute, ignoreCase = true)) {
                    core.resolveRoute(request.query)
                } else null,
                selected = document.sourceCode.equals(selectedRouteCode, ignoreCase = true)
            )
            RagDocumentType.WORK_CATEGORY -> scoreFromMatch(
                match = if (document.sourceCode.equals(queryCategory, ignoreCase = true)) core.resolveCategory(request.query) else null,
                selected = false
            )
            RagDocumentType.NODE_PROGRESS -> if (document.sourceCode.equals(queryNode, ignoreCase = true) || document.sourceCode.equals(selectedNodeCode, ignoreCase = true)) 1f else 0f
            RagDocumentType.WORK_VOLUME_PROGRESS -> if (document.sourceCode.substringBefore(':').equals(queryNode, ignoreCase = true) || document.sourceCode.substringBefore(':').equals(selectedNodeCode, ignoreCase = true)) 1f else 0f
            RagDocumentType.DAILY_LOG -> if (
                containsField(document.text, "node_code", queryNode) ||
                containsField(document.text, "node_code", selectedNodeCode) ||
                containsField(document.text, "route_code", queryRoute) ||
                containsField(document.text, "route_code", selectedRouteCode)
            ) 0.7f else 0f
            RagDocumentType.WORK_PLAN -> if (
                containsField(document.text, "node_code", queryNode) ||
                containsField(document.text, "node_code", selectedNodeCode) ||
                containsField(document.text, "route_code", queryRoute) ||
                containsField(document.text, "route_code", selectedRouteCode)
            ) 0.7f else 0f
        }
    }

    private fun containsField(text: String, field: String, value: String): Boolean {
        return value.isNotBlank() && text.contains("$field=$value", ignoreCase = true)
    }

    private fun scoreFromMatch(match: com.mapsupervision.ai.prompt.DictionaryMatch<*>?, selected: Boolean): Float {
        val confidence = match?.confidence ?: return if (selected) 1f else 0f
        return (confidence.coerceIn(0, 100) / 100f).let { if (selected) maxOf(1f, it) else it }
    }

    private fun semanticScore(queryVector: FloatArray?, docVector: FloatArray?): Float {
        if (queryVector == null || docVector == null || queryVector.size != docVector.size) return 0f
        var sum = 0f
        for (index in queryVector.indices) {
            sum += queryVector[index] * docVector[index]
        }
        return ((sum + 1f) / 2f).coerceIn(0f, 1f)
    }

    private fun lexicalScore(query: String, text: String): Float {
        val queryTokens = normalizeTokens(query)
        val textTokens = normalizeTokens(text)
        if (queryTokens.isEmpty() || textTokens.isEmpty()) return 0f
        val overlap = queryTokens.count { textTokens.contains(it) }.toFloat()
        return (overlap / queryTokens.size.toFloat()).coerceIn(0f, 1f)
    }

    private fun normalizeTokens(text: String): Set<String> {
        return CanonicalTextNormalizer.normalizeKey(text)
            .split(' ')
            .map { it.trim() }
            .filter { it.length > 1 }
            .toSet()
    }

    private fun emptyBlock(request: RagBuildRequest): RagContextBlock {
        return RagContextBlock(
            resolvedRefs = listOfNotNull(
                request.selectedNodeCode?.takeIf { it.isNotBlank() }?.let { "node=$it" },
                request.selectedRouteCode?.takeIf { it.isNotBlank() }?.let { "route=$it" }
            ).joinToString(";"),
            retrievedContext = "",
            dbSnapshot = request.workspaceSnapshot?.let { "projectId=${it.projectId}" }.orEmpty(),
            recentLogs = "",
            relatedWorkCategories = "",
            queryDomain = request.queryDomain ?: RagQueryDomain.infer(request.query)
        )
    }

    private fun format(value: Float): String = String.format(Locale.US, "%.3f", value)
    private fun format(value: Double): String = String.format(Locale.US, "%.3f", value)
}

@Singleton
class RagContextBuilderImpl @Inject constructor(
    private val retriever: RagRetriever
) : RagContextBuilder {
    override suspend fun build(request: RagBuildRequest): RagBuildResult {
        return retriever.retrieve(request)
    }
}

@Singleton
@OptIn(kotlinx.coroutines.FlowPreview::class)
class RagIndexScheduler @Inject constructor(
    private val indexRepository: RagIndexRepository,
    private val embeddingEngine: TextEmbeddingEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = MutableSharedFlow<WorkspaceSnapshot>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            queue.debounce(250).collectLatest { snapshot ->
                indexSnapshot(snapshot)
            }
        }
    }

    fun schedule(snapshot: WorkspaceSnapshot) {
        queue.tryEmit(snapshot)
    }

    private suspend fun indexSnapshot(snapshot: WorkspaceSnapshot) {
        val existing = indexRepository.listByProject(snapshot.projectId).associateBy { it.id }
        val documents = RagDocumentBuilder.build(snapshot)
        val keepIds = documents.mapTo(mutableSetOf()) { it.id }
        val changed = documents.filter { document ->
            val previous = existing[document.id]
            previous == null || previous.contentHash != document.contentHash
        }

        if (changed.isEmpty() && existing.keys == keepIds) {
            return
        }

        val indexed = changed.map { document ->
            val embedding = embeddingEngine.embed(document.text)
            document.copy(embedding = embedding)
        }
        if (indexed.isNotEmpty()) {
            indexRepository.upsertAll(snapshot.projectId, indexed)
        }
        if (existing.isNotEmpty()) {
            indexRepository.deleteMissing(snapshot.projectId, keepIds)
        }
        Log.d(TAG, "indexed project=${snapshot.projectId} docs=${documents.size} changed=${changed.size} embedded=${indexed.count { it.embedding != null }}")
    }
}

private fun extractSourceCode(docTypeStr: String, text: String, sourceId: String): String {
    val docType = runCatching { RagDocumentType.valueOf(docTypeStr) }.getOrNull() ?: return sourceId
    return try {
        when (docType) {
            RagDocumentType.NODE, RagDocumentType.NODE_PROGRESS -> {
                if (text.contains("node_code=")) {
                    text.substringAfter("node_code=").substringBefore(" ")
                } else {
                    sourceId
                }
            }
            RagDocumentType.ROUTE -> {
                if (text.contains("route_code=")) {
                    text.substringAfter("route_code=").substringBefore(" ")
                } else {
                    sourceId
                }
            }
            RagDocumentType.WORK_CATEGORY -> {
                if (text.contains("category_name=")) {
                    text.substringAfter("category_name=").substringBefore(" unit=")
                } else {
                    sourceId
                }
            }
            RagDocumentType.WORK_VOLUME_PROGRESS -> {
                val nodeCode = text.substringAfter("node_code=").substringBefore(" work_name=")
                val workName = text.substringAfter("work_name=").substringBefore(" planned=")
                "$nodeCode:$workName"
            }
            RagDocumentType.DAILY_LOG -> {
                val nodeCode = text.substringAfter("node_code=").substringBefore(" route_code=")
                if (nodeCode.isNotEmpty()) {
                    nodeCode
                } else {
                    val routeCode = text.substringAfter("route_code=").substringBefore(" work_item=")
                    if (routeCode.isNotEmpty()) {
                        routeCode
                    } else {
                        text.substringAfter("work_item=").substringBefore(" note=")
                    }
                }
            }
            RagDocumentType.WORK_PLAN -> {
                val nodeCode = text.substringAfter("node_code=").substringBefore(" route_code=")
                if (nodeCode.isNotEmpty()) {
                    nodeCode
                } else {
                    val routeCode = text.substringAfter("route_code=").substringBefore(" title=")
                    if (routeCode.isNotEmpty()) {
                        routeCode
                    } else {
                        text.substringAfter("title=").substringBefore(" description=")
                    }
                }
            }
        }
    } catch (e: Exception) {
        sourceId
    }
}

private fun RagDocumentEmbeddingEntity.toDomain(): RagDocument {
    return RagDocument(
        id = id,
        projectId = projectId,
        docType = runCatching { RagDocumentType.valueOf(docType) }.getOrDefault(RagDocumentType.NODE),
        sourceId = sourceId,
        sourceCode = extractSourceCode(docType, text, sourceId),
        text = text,
        contentHash = contentHash,
        embedding = embeddingBlob.toFloatArray(),
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun RagDocument.toEntity(): RagDocumentEmbeddingEntity {
    return RagDocumentEmbeddingEntity(
        id = id,
        projectId = projectId,
        docType = docType.name,
        sourceId = sourceId,
        text = text,
        contentHash = contentHash,
        embeddingBlob = (embedding ?: FloatArray(0)).toByteArray(),
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (value in this) {
        buffer.putFloat(value)
    }
    return buffer.array()
}

private fun ByteArray.toFloatArray(): FloatArray {
    if (isEmpty()) return FloatArray(0)
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val result = FloatArray(size / 4)
    for (index in result.indices) {
        result[index] = buffer.float
    }
    return result
}

private fun FloatArray.orEmpty(): FloatArray = this
