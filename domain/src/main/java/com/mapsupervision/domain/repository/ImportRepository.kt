package com.mapsupervision.domain.repository

import com.mapsupervision.domain.model.ImportDraft
import com.mapsupervision.domain.model.ConfirmedFieldFlags
import com.mapsupervision.domain.model.ExcelColumnMapping
import com.mapsupervision.domain.model.ExcelPreview
import com.mapsupervision.domain.model.NonExcelFieldPreview
import com.mapsupervision.domain.model.NonExcelImportMapping

interface ImportRepository {
    suspend fun importFile(projectId: String, uri: String): ImportDraft
    suspend fun inspectExcel(uri: String, sheetName: String? = null): ExcelPreview
    suspend fun inspectNonExcelFields(uri: String): NonExcelFieldPreview
    suspend fun importNonExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: NonExcelImportMapping,
        confirmed: ConfirmedFieldFlags
    ): ImportDraft
    suspend fun importExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: ExcelColumnMapping,
        sheetName: String? = null
    ): ImportDraft
}
