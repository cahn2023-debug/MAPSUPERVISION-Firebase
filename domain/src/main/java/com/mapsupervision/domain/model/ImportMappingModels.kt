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
    val itemColumns: List<String> = emptyList(),
    val allowPartialImport: Boolean = false,
    val extendedMetadataColumns: Set<String> = emptySet(),
    val duplicatePolicy: DuplicateImportPolicy = DuplicateImportPolicy.SKIP,
    val deduplicationKey: DuplicateBusinessKey = DuplicateBusinessKey.CODE
)

enum class DuplicateImportPolicy { SKIP, UPDATE }

enum class DuplicateBusinessKey { CODE, COORDINATES, COMPOSITE_CODE_COORD }

data class ExcelPreview(
    val fileName: String,
    val headers: List<String>,
    val sampleRows: List<Map<String, String>>,
    val allRows: List<Map<String, String>> = emptyList(),
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

data class ExcelMappingSuggestion(val mapping: ExcelColumnMapping, val confidence: Int)

enum class ExcelClassificationMode { AUTO, BY_OBJECT_TYPE_COLUMN, FORCE_NODE, FORCE_ROUTE }

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

data class InvalidRowDetail(val rowIndex: Int, val rawIdentifier: String, val reason: String)

data class TabularValidationResult(
    val isValid: Boolean,
    val canConfirm: Boolean,
    val requiredErrors: List<String>,
    val validRowCount: Int,
    val invalidRowCount: Int,
    val invalidRows: List<InvalidRowDetail>,
    val duplicateRowCount: Int = 0,
    val suggestedBusinessKey: DuplicateBusinessKey = DuplicateBusinessKey.CODE,
    val guidance: String
)

object TabularMappingValidator {
    fun validate(
        headers: List<String>,
        rows: List<Map<String, String>>,
        positionColumn: String? = null,
        coordinateColumn: String? = null,
        latitudeColumn: String? = null,
        longitudeColumn: String? = null,
        useTwoColumnCoordinates: Boolean = false,
        allowPartialImport: Boolean = false,
        deduplicationKey: DuplicateBusinessKey = DuplicateBusinessKey.CODE,
        existingNodeCodes: Set<String> = emptySet(),
        existingNodeCoords: Set<Long> = emptySet()
    ): TabularValidationResult {
        val errors = mutableListOf<String>()
        val pos = positionColumn?.trim().orEmpty()
        if (pos.isBlank()) errors += "Chưa chọn cột Vị trí (Mã vị trí, bắt buộc)."
        else if (headers.isNotEmpty() && pos !in headers) errors += "Cột Vị trí (Mã vị trí) '$pos' không tồn tại."

        fun requireHeader(value: String?, label: String) {
            val name = value?.trim().orEmpty()
            val displayLabel = label.replaceFirstChar { it.uppercase() }
            if (name.isBlank()) errors += "Chưa chọn cột $displayLabel."
            else if (headers.isNotEmpty() && name !in headers) errors += "Cột $displayLabel '$name' không tồn tại."
        }
        if (useTwoColumnCoordinates) {
            requireHeader(latitudeColumn, "vĩ độ (Latitude)")
            requireHeader(longitudeColumn, "kinh độ (Longitude)")
        } else {
            requireHeader(coordinateColumn, "tọa độ GPS")
        }
        if (errors.isNotEmpty() || rows.isEmpty()) {
            val guidance = if (errors.isNotEmpty()) "Vui lòng cấu hình: ${errors.joinToString("; ")}" else "File không có dòng dữ liệu."
            return TabularValidationResult(false, false, errors, 0, rows.size, emptyList(), guidance = guidance)
        }

        var valid = 0
        var duplicates = 0
        val invalid = mutableListOf<InvalidRowDetail>()
        val codes = mutableSetOf<String>()
        val coords = mutableSetOf<Long>()
        val composites = mutableSetOf<String>()
        for ((index, row) in rows.withIndex()) {
            val rowNumber = index + 1
            val code = row[pos]?.trim().orEmpty()
            if (code.isBlank()) {
                invalid += InvalidRowDetail(rowNumber, "Dòng $rowNumber", "Mã vị trí trống")
                continue
            }
            val pair = if (useTwoColumnCoordinates) {
                val lat = row[latitudeColumn?.trim().orEmpty()]?.trim()?.toDoubleOrNull()
                val lon = row[longitudeColumn?.trim().orEmpty()]?.trim()?.toDoubleOrNull()
                if (lat != null && lon != null) lat to lon else null
            } else {
                val parts = row[coordinateColumn?.trim().orEmpty()].orEmpty().trim().split(Regex("[,;\\s]+"))
                if (parts.size >= 2) parts[0].toDoubleOrNull()?.let { lat -> parts[1].toDoubleOrNull()?.let { lon -> lat to lon } } else null
            }
            val lat = pair?.first
            val lon = pair?.second
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                invalid += InvalidRowDetail(rowNumber, code, "Tọa độ không hợp lệ hoặc thiếu")
                continue
            }
            valid++
            val codeKey = code.uppercase()
            val coordKey = (lat * 1e6).toLong() xor ((lon * 1e6).toLong() * 31L)
            val isCodeDup = !codes.add(codeKey)
            val isCoordDup = !coords.add(coordKey)
            val isCompositeDup = !composites.add("$codeKey|$coordKey")

            val isDuplicate = when (deduplicationKey) {
                DuplicateBusinessKey.CODE -> isCodeDup || codeKey in existingNodeCodes
                DuplicateBusinessKey.COORDINATES -> isCoordDup || coordKey in existingNodeCoords
                DuplicateBusinessKey.COMPOSITE_CODE_COORD -> isCompositeDup || (codeKey in existingNodeCodes && coordKey in existingNodeCoords)
            }
            if (isDuplicate) duplicates++
        }
        val canConfirm = valid > 0 && (invalid.isEmpty() || allowPartialImport)
        val guidance = when {
            canConfirm && invalid.isEmpty() -> "Tất cả $valid dòng hợp lệ và sẵn sàng nhập."
            canConfirm -> "Có ${invalid.size} dòng lỗi; sẽ nhập $valid dòng hợp lệ."
            invalid.isNotEmpty() -> "Có ${invalid.size} dòng lỗi. Hãy sửa file hoặc bật nhập phần hợp lệ."
            else -> "Không có dòng hợp lệ để nhập."
        }
        return TabularValidationResult(
            isValid = errors.isEmpty() && invalid.isEmpty() && valid > 0,
            canConfirm = errors.isEmpty() && canConfirm,
            requiredErrors = errors,
            validRowCount = valid,
            invalidRowCount = invalid.size,
            invalidRows = invalid,
            duplicateRowCount = duplicates,
            suggestedBusinessKey = when {
                codes.size == valid -> DuplicateBusinessKey.CODE
                coords.size == valid -> DuplicateBusinessKey.COORDINATES
                else -> DuplicateBusinessKey.COMPOSITE_CODE_COORD
            },
            guidance = guidance
        )
    }
}
