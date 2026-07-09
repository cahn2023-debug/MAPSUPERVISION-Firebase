package com.mapsupervision.ai.core.rag

import com.mapsupervision.domain.model.WorkspaceSnapshot
import java.text.Normalizer
import java.util.Locale

enum class RagDocumentType {
    NODE,
    ROUTE,
    WORK_CATEGORY,
    NODE_PROGRESS,
    WORK_VOLUME_PROGRESS,
    DAILY_LOG,
    WORK_PLAN
}

enum class RagQueryDomain {
    PROGRESS,
    PLANNING,
    DAILY_LOG,
    GENERAL;

    companion object {
        fun infer(query: String): RagQueryDomain {
            val normalized = normalize(query)
            val wantsDailyLog = normalized.containsAny(
                "nhat ky",
                "daily log",
                "ghi nhat",
                "bao cao ngay"
            )
            val wantsPlanning = normalized.containsAny(
                "ke hoach",
                "lich thi cong",
                "lich trien khai",
                "du kien",
                "hom nay lam gi",
                "ngay mai lam gi"
            )
            val wantsProgress = normalized.containsAny(
                "tien do",
                "khoi luong",
                "thi cong duoc",
                "hoan thanh",
                "actual",
                "planned",
                "progress",
                "volume"
            )
            return when {
                wantsDailyLog -> DAILY_LOG
                wantsPlanning -> PLANNING
                wantsProgress -> PROGRESS
                else -> GENERAL
            }
        }

        private fun normalize(value: String): String {
            val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
            return decomposed
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .lowercase(Locale.US)
        }

        private fun String.containsAny(vararg needles: String): Boolean {
            return needles.any { contains(it) }
        }
    }
}

data class RagDocument(
    val id: String,
    val projectId: String,
    val docType: RagDocumentType,
    val sourceId: String,
    val sourceCode: String,
    val text: String,
    val contentHash: String,
    val embedding: FloatArray? = null,
    val updatedAtEpochMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RagDocument

        if (id != other.id) return false
        if (projectId != other.projectId) return false
        if (docType != other.docType) return false
        if (sourceId != other.sourceId) return false
        if (sourceCode != other.sourceCode) return false
        if (text != other.text) return false
        if (contentHash != other.contentHash) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (updatedAtEpochMs != other.updatedAtEpochMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + projectId.hashCode()
        result = 31 * result + docType.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + sourceCode.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + updatedAtEpochMs.hashCode()
        return result
    }
}

data class RagRetrievedDocument(
    val document: RagDocument,
    val dictionaryScore: Float,
    val semanticScore: Float,
    val lexicalScore: Float,
    val finalScore: Float,
    val isSelected: Boolean = false
)

data class RagContextBlock(
    val resolvedRefs: String,
    val retrievedContext: String,
    val dbSnapshot: String,
    val recentLogs: String,
    val relatedWorkCategories: String,
    val queryDomain: RagQueryDomain = RagQueryDomain.GENERAL
) {
    fun toPromptBlock(): String = buildString {
        append("query_domain:\n").append(queryDomain.name.lowercase(Locale.US)).append('\n')
        if (resolvedRefs.isNotBlank()) {
            append("resolved_refs:\n").append(resolvedRefs.trim()).append('\n')
        }
        if (retrievedContext.isNotBlank()) {
            append("retrieved_context:\n").append(retrievedContext.trim()).append('\n')
        }
        if (dbSnapshot.isNotBlank()) {
            append("db_snapshot:\n").append(dbSnapshot.trim()).append('\n')
        }
        if (recentLogs.isNotBlank()) {
            append("recent_logs:\n").append(recentLogs.trim()).append('\n')
        }
        if (relatedWorkCategories.isNotBlank()) {
            append("related_work_categories:\n").append(relatedWorkCategories.trim()).append('\n')
        }
    }.trim()
}

data class RagBuildRequest(
    val projectId: String,
    val query: String,
    val workspaceSnapshot: WorkspaceSnapshot? = null,
    val selectedNodeCode: String? = null,
    val selectedRouteCode: String? = null,
    val limit: Int = 8,
    val queryDomain: RagQueryDomain? = null
)

data class RagBuildResult(
    val block: RagContextBlock,
    val retrievedDocuments: List<RagRetrievedDocument>,
    val queryDomain: RagQueryDomain = block.queryDomain
)
