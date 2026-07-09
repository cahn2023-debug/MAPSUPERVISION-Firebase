package com.mapsupervision.app.workspace

enum class ConflictAction {
    AUTO_MERGE,
    ASK_USER,
    MANUAL_REVIEW
}

data class ImportConflictDecision(
    val action: ConflictAction,
    val severity: String,
    val requiresAttention: Boolean,
    val statusMessage: String
)

internal object ImportConflictPolicy {
    fun decide(risk: String, duplicateCount: Int, coordRejectCount: Int): ConflictAction {
        if (duplicateCount <= 0 && coordRejectCount <= 0) return ConflictAction.AUTO_MERGE
        return when {
            risk == "high" -> ConflictAction.MANUAL_REVIEW
            risk == "medium" -> ConflictAction.ASK_USER
            duplicateCount > 3 || coordRejectCount > 3 -> ConflictAction.ASK_USER
            else -> ConflictAction.AUTO_MERGE
        }
    }

    fun severityFor(risk: String): String = when (risk) {
        "high" -> "high"
        "medium" -> "medium"
        else -> "low"
    }

    fun evaluate(
        risk: String,
        duplicateCount: Int,
        coordRejectCount: Int,
        batchAction: String = ""
    ): ImportConflictDecision {
        val baseAction = decide(risk, duplicateCount, coordRejectCount)
        val routedAction = when (batchAction) {
            "review_required" -> ConflictAction.MANUAL_REVIEW
            "review_recommended" -> if (baseAction == ConflictAction.AUTO_MERGE) ConflictAction.ASK_USER else baseAction
            else -> baseAction
        }
        val severity = when (routedAction) {
            ConflictAction.MANUAL_REVIEW -> "high"
            ConflictAction.ASK_USER -> "medium"
            ConflictAction.AUTO_MERGE -> severityFor(risk)
        }
        val message = when (routedAction) {
            ConflictAction.MANUAL_REVIEW -> "manual_review"
            ConflictAction.ASK_USER -> "ask_user"
            ConflictAction.AUTO_MERGE -> "auto_merge"
        }
        return ImportConflictDecision(
            action = routedAction,
            severity = severity,
            requiresAttention = routedAction != ConflictAction.AUTO_MERGE,
            statusMessage = message
        )
    }
}
