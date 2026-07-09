package com.mapsupervision.app.workspace

enum class DailyLogExportScope {
    ALL,
    DATE_RANGE
}

enum class DailyLogExportFormat {
    PDF,
    DOCX
}

data class ExportDailyLogRequest(
    val projectId: String,
    val scope: DailyLogExportScope,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    val format: DailyLogExportFormat,
    val includePlan: Boolean
)
