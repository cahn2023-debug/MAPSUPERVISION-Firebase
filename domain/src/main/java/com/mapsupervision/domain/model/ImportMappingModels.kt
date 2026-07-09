package com.mapsupervision.domain.model

data class ExcelColumnMapping(
    val positionColumn: String,
    val coordinateColumn: String? = null,
    val latitudeColumn: String? = null,
    val longitudeColumn: String? = null,
    val contractorColumn: String? = null,
    val mapNumberColumn: String? = null,
    val objectTypeColumn: String? = null,
    val ipAddressColumn: String? = null,
    val subnetColumn: String? = null,
    val gatewayColumn: String? = null,
    val signalStatusColumn: String? = null,
    val fiberCoreCountColumn: String? = null,
    val fiberConnectionColumn: String? = null,
    val classificationMode: ExcelClassificationMode = ExcelClassificationMode.AUTO,
    val itemColumns: List<String> = emptyList()
)

data class ExcelPreview(
    val fileName: String,
    val headers: List<String>,
    val sampleRows: List<Map<String, String>>,
    val suggestedMapping: ExcelColumnMapping? = null,
    val suggestedMappingConfidence: Int = 0,
    val sheets: List<String> = emptyList()
)

data class NonExcelPreview(
    val fileName: String,
    val fileType: String,
    val sizeBytes: Long,
    val summary: String,
    val routeLengthMeters: Double = 0.0
)

data class NonExcelFieldCandidateSet(
    val positionOptions: List<String>,
    val coordinateOptions: List<String>,
    val latitudeOptions: List<String>,
    val longitudeOptions: List<String>,
    val contractorOptions: List<String>,
    val mapNumberOptions: List<String>,
    val objectTypeOptions: List<String>,
    val itemOptions: List<String>,
    val routeLengthOptions: List<String>,
    val ipAddressOptions: List<String>,
    val subnetOptions: List<String>,
    val gatewayOptions: List<String>,
    val signalStatusOptions: List<String>,
    val fiberCoreCountOptions: List<String>,
    val fiberConnectionOptions: List<String>
)

data class NonExcelFieldPreview(
    val fileName: String,
    val fileType: String,
    val sizeBytes: Long,
    val summary: String,
    val routeLengthMeters: Double = 0.0,
    val candidates: NonExcelFieldCandidateSet,
    val sampleRows: List<Map<String, String>> = emptyList()
)

data class NonExcelImportMapping(
    val positionField: String,
    val coordinateField: String? = null,
    val latitudeField: String? = null,
    val longitudeField: String? = null,
    val contractorField: String? = null,
    val mapNumberField: String? = null,
    val objectTypeField: String? = null,
    val itemFields: List<String> = emptyList(),
    val routeLengthField: String? = null,
    val ipAddressField: String? = null,
    val subnetField: String? = null,
    val gatewayField: String? = null,
    val signalStatusField: String? = null,
    val fiberCoreCountField: String? = null,
    val fiberConnectionField: String? = null
)

data class ConfirmedFieldFlags(
    val positionField: Boolean = false,
    val coordinateField: Boolean = false,
    val latitudeField: Boolean = false,
    val longitudeField: Boolean = false,
    val contractorField: Boolean = false,
    val mapNumberField: Boolean = false,
    val objectTypeField: Boolean = false,
    val itemFields: Boolean = false,
    val routeLengthField: Boolean = false,
    val ipAddressField: Boolean = false,
    val subnetField: Boolean = false,
    val gatewayField: Boolean = false,
    val signalStatusField: Boolean = false,
    val fiberCoreCountField: Boolean = false,
    val fiberConnectionField: Boolean = false
)

data class ExcelMappingSuggestion(
    val mapping: ExcelColumnMapping,
    val confidence: Int
)

enum class ExcelClassificationMode {
    AUTO,
    BY_OBJECT_TYPE_COLUMN,
    FORCE_NODE,
    FORCE_ROUTE
}

data class DedupMetrics(
    val incomingNodes: Int,
    val strongMatches: Int,
    val weakMatches: Int,
    val coordOnlyRejected: Int,
    val incomingRoutes: Int,
    val skippedSelfRoutes: Int,
    val skippedDuplicateRoutes: Int
)

data class DedupStats(
    val codeMatches: Int = 0,
    val nameMatches: Int = 0,
    val coordMatches: Int = 0,
    val multiSignalMatches: Int = 0,
    val strongMatches: Int = 0,
    val weakMatches: Int = 0,
    val coordOnlyRejected: Int = 0,
    val skippedSelfRoutes: Int = 0,
    val skippedDuplicateRoutes: Int = 0
)

data class DedupQualitySnapshot(
    val score: Int,
    val label: String,
    val risk: String,
    val action: String,
    val actionNote: String,
    val diagnostics: String,
    val hint: String
)

data class MergeResult(
    val nodesToInsert: List<GisNode>,
    val routesToInsert: List<GisRoute>,
    val duplicateNodes: Int,
    val stats: DedupStats = DedupStats()
)
