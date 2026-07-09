package com.mapsupervision.storage.importer

import android.net.Uri
import com.mapsupervision.domain.model.ConfirmedFieldFlags
import com.mapsupervision.domain.model.ExcelColumnMapping
import com.mapsupervision.domain.model.ExcelPreview
import com.mapsupervision.domain.model.ImportAudit
import com.mapsupervision.domain.model.ImportConflict
import com.mapsupervision.domain.model.ImportSession
import com.mapsupervision.domain.model.ImportVersion
import com.mapsupervision.domain.model.ImportDraft
import com.mapsupervision.domain.model.NonExcelFieldPreview
import com.mapsupervision.domain.model.NonExcelImportMapping
import com.mapsupervision.domain.repository.ImportRepository
import javax.inject.Inject

interface ImportInspector {
    suspend fun inspectExcel(uri: String, sheetName: String? = null): ExcelPreview
    suspend fun inspectNonExcelFields(uri: String): NonExcelFieldPreview
}

interface ImportCommitter {
    suspend fun importFile(projectId: String, uri: String): ImportDraft
    suspend fun importExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: ExcelColumnMapping,
        sheetName: String? = null
    ): ImportDraft

    suspend fun importNonExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: NonExcelImportMapping,
        confirmed: ConfirmedFieldFlags
    ): ImportDraft
}

interface ValidationEngine {
    fun validateSession(session: ImportSession): List<String>
    fun validateDraft(session: ImportSession, draft: ImportDraft): List<String>
}

interface DeduplicateEngine {
    fun validateImportedGeometry(draft: ImportDraft): List<String>
}

interface ConflictResolver {
    fun resolve(conflicts: List<ImportConflict>): List<ImportConflict>
}

interface ImportTransactionManager {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

interface ImportEventPublisher {
    suspend fun publish(session: ImportSession, version: ImportVersion?, audit: ImportAudit? = null)
}

interface ImportCoordinator : ImportInspector, ImportCommitter

class DefaultImportCoordinator @Inject constructor(
    private val importRepository: ImportRepository
) : ImportCoordinator {
    override suspend fun inspectExcel(uri: String, sheetName: String?): ExcelPreview =
        importRepository.inspectExcel(uri, sheetName)

    override suspend fun inspectNonExcelFields(uri: String): NonExcelFieldPreview =
        importRepository.inspectNonExcelFields(uri)

    override suspend fun importFile(projectId: String, uri: String): ImportDraft =
        importRepository.importFile(projectId, uri)

    override suspend fun importExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: ExcelColumnMapping,
        sheetName: String?
    ): ImportDraft = importRepository.importExcelWithMapping(projectId, uri, mapping, sheetName)

    override suspend fun importNonExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: NonExcelImportMapping,
        confirmed: ConfirmedFieldFlags
    ): ImportDraft = importRepository.importNonExcelWithMapping(projectId, uri, mapping, confirmed)
}

