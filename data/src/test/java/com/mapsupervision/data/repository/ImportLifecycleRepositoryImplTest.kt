package com.mapsupervision.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.core.result.AppResult
import com.mapsupervision.data.db.MapSupervisionDatabase
import com.mapsupervision.data.db.ProjectScopedDatabaseProvider
import com.mapsupervision.data.db.entity.ImportAuditEntity
import com.mapsupervision.data.db.entity.ImportConflictEntity
import com.mapsupervision.data.db.entity.ImportSessionEntity
import com.mapsupervision.data.db.entity.ImportVersionEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.domain.model.ConfirmedFieldFlags
import com.mapsupervision.domain.model.ExcelColumnMapping
import com.mapsupervision.domain.model.ExcelPreview
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.ImportDraft
import com.mapsupervision.domain.model.NonExcelFieldCandidateSet
import com.mapsupervision.domain.model.NonExcelFieldPreview
import com.mapsupervision.domain.model.NonExcelImportMapping
import com.mapsupervision.domain.model.ProjectStorageMode
import com.mapsupervision.domain.repository.GisRepository
import com.mapsupervision.domain.repository.ImportRepository
import com.mapsupervision.storage.ProjectStorageManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImportLifecycleRepositoryImplTest {

    @Test
    fun rollback_to_version_reimports_geometry_from_saved_source() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val sourceFile = File.createTempFile("rollback-import", ".geojson")
            sourceFile.writeText("""{"type":"FeatureCollection","features":[]}""")
            val fakeImportRepository = FakeImportRepository()
            val fakeGisRepository = FakeGisRepository()
            val repository = ImportLifecycleRepositoryImpl(
                sharedDatabase = database,
                projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, ProjectStorageManager(context)),
                importRepository = fakeImportRepository,
                gisRepository = fakeGisRepository
            )
            database.projectDao().upsert(testProject("project-1"))

            database.importedFileDao().upsert(
                ImportedFileEntity(
                    id = "file-1",
                    projectId = "project-1",
                    fileName = "design.geojson",
                    fileType = "geojson",
                    storedPath = sourceFile.absolutePath,
                    summary = "",
                    importedAtEpochMs = 1L
                )
            )
            database.importSessionDao().upsert(
                ImportSessionEntity(
                    id = "session-1",
                    projectId = "project-1",
                    sourceKind = "direct",
                    sourceFileName = "design.geojson",
                    sourceFileType = "geojson",
                    sourceFilePath = sourceFile.absolutePath,
                    status = "COMPLETED",
                    createdAtEpochMs = 2L,
                    updatedAtEpochMs = 2L,
                    importedFileId = "file-1",
                    featureCount = 1,
                    conflictCount = 0,
                    warningCount = 0,
                    message = ""
                )
            )
            database.importVersionDao().upsert(
                ImportVersionEntity(
                    id = "version-1",
                    projectId = "project-1",
                    importSessionId = "session-1",
                    versionNumber = 1,
                    sourceHash = "hash",
                    createdAtEpochMs = 3L,
                    createdBy = "test",
                    note = ""
                )
            )

            val result = repository.rollbackToVersion("project-1", 1)

            assertTrue(result is AppResult.Success)
            assertEquals("file-1", fakeGisRepository.lastImportedFileId)
            assertEquals(1, fakeGisRepository.lastNodes.size)
            assertEquals("ROLLBACK-NODE", fakeGisRepository.lastNodes.single().code)
        } finally {
            database.close()
        }
    }

    @Test
    fun purge_deleted_artifacts_removes_stale_import_rows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repository = ImportLifecycleRepositoryImpl(
                sharedDatabase = database,
                projectScopedDatabaseProvider = ProjectScopedDatabaseProvider(context, database, ProjectStorageManager(context)),
                importRepository = FakeImportRepository(),
                gisRepository = FakeGisRepository()
            )
            database.projectDao().upsert(testProject("project-2"))

            database.importedFileDao().upsert(
                ImportedFileEntity(
                    id = "file-old",
                    projectId = "project-2",
                    fileName = "old.geojson",
                    fileType = "geojson",
                    storedPath = "/tmp/old.geojson",
                    summary = "",
                    importedAtEpochMs = 1L,
                    isDeleted = true,
                    deletedAtEpochMs = 10L
                )
            )
            database.importSessionDao().upsert(
                ImportSessionEntity(
                    id = "session-old",
                    projectId = "project-2",
                    sourceKind = "direct",
                    sourceFileName = "old.geojson",
                    sourceFileType = "geojson",
                    sourceFilePath = "/tmp/old.geojson",
                    status = "DELETED",
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    importedFileId = "file-old",
                    featureCount = 0,
                    conflictCount = 0,
                    warningCount = 0,
                    message = ""
                )
            )
            database.importAuditDao().upsert(
                ImportAuditEntity(
                    id = "audit-old",
                    projectId = "project-2",
                    importSessionId = "session-old",
                    action = "import_completed",
                    actor = "test",
                    payloadJson = "{}",
                    createdAtEpochMs = 10L
                )
            )
            database.importConflictDao().upsert(
                ImportConflictEntity(
                    id = "conflict-old",
                    projectId = "project-2",
                    importSessionId = "session-old",
                    featureBusinessCode = "N1",
                    conflictType = "DUPLICATE_GEOMETRY",
                    severity = "medium",
                    details = "old",
                    createdAtEpochMs = 10L
                )
            )

            val result = repository.purgeDeletedArtifacts("project-2", deletedBeforeEpochMs = 20L)

            assertTrue(result is AppResult.Success)
            assertTrue(database.importedFileDao().byProject("project-2").isEmpty())
            assertTrue(database.importSessionDao().byProject("project-2").isEmpty())
            assertTrue(database.importAuditDao().byProject("project-2").isEmpty())
            assertTrue(database.importConflictDao().byProject("project-2").isEmpty())
        } finally {
            database.close()
        }
    }
}

private class FakeImportRepository : ImportRepository {
    override suspend fun importFile(projectId: String, uri: String): ImportDraft = ImportDraft(
        fileName = "rollback.geojson",
        fileType = "geojson",
        storedPath = uri,
        summary = "rollback",
        suggestedNodes = listOf(
            GisNode(
                id = "node-1",
                projectId = projectId,
                code = "ROLLBACK-NODE",
                contractor = "UPLOAD",
                latitude = 10.0,
                longitude = 106.0,
                mapNumberLabel = "RB-1",
                workVolumeSummary = ""
            )
        ),
        suggestedRoutes = emptyList()
    )

    override suspend fun inspectExcel(uri: String, sheetName: String?): ExcelPreview =
        throw UnsupportedOperationException()

    override suspend fun inspectNonExcelFields(uri: String): NonExcelFieldPreview =
        NonExcelFieldPreview(
            fileName = "x",
            fileType = "geojson",
            sizeBytes = 0L,
            summary = "",
            routeLengthMeters = 0.0,
            candidates = NonExcelFieldCandidateSet(
                positionOptions = emptyList(),
                coordinateOptions = emptyList(),
                latitudeOptions = emptyList(),
                longitudeOptions = emptyList(),
                contractorOptions = emptyList(),
                mapNumberOptions = emptyList(),
                objectTypeOptions = emptyList(),
                itemOptions = emptyList(),
                routeLengthOptions = emptyList(),
                ipAddressOptions = emptyList(),
                subnetOptions = emptyList(),
                gatewayOptions = emptyList(),
                signalStatusOptions = emptyList(),
                fiberCoreCountOptions = emptyList(),
                fiberConnectionOptions = emptyList()
            ),
            sampleRows = emptyList()
        )

    override suspend fun importNonExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: NonExcelImportMapping,
        confirmed: ConfirmedFieldFlags
    ): ImportDraft = throw UnsupportedOperationException()

    override suspend fun importExcelWithMapping(
        projectId: String,
        uri: String,
        mapping: ExcelColumnMapping,
        sheetName: String?
    ): ImportDraft = throw UnsupportedOperationException()
}

private fun testProject(projectId: String) = ProjectEntity(
    id = projectId,
    name = projectId,
    slug = projectId,
    isArchived = false,
    createdAtEpochMs = 1L,
    storageMode = ProjectStorageMode.LEGACY_SHARED
)

private class FakeGisRepository : GisRepository {
    var lastImportedFileId: String? = null
    var lastNodes: List<GisNode> = emptyList()
    var lastRoutes: List<GisRoute> = emptyList()

    override suspend fun upsertNode(node: GisNode): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoute(route: GisRoute): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertNodes(nodes: List<GisNode>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun upsertRoutes(routes: List<GisRoute>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun replaceImportedGeometry(
        importedFileId: String,
        nodes: List<GisNode>,
        routes: List<GisRoute>
    ): AppResult<Unit> {
        lastImportedFileId = importedFileId
        lastNodes = nodes
        lastRoutes = routes
        return AppResult.Success(Unit)
    }

    override suspend fun searchNodes(projectId: String, query: String): AppResult<List<GisNode>> = AppResult.Success(emptyList())
    override suspend fun searchRoutes(projectId: String, query: String): AppResult<List<GisRoute>> = AppResult.Success(emptyList())
    override suspend fun findNodeByCode(projectId: String, code: String): AppResult<GisNode?> = AppResult.Success(null)
    override fun observeNodes(projectId: String, query: String): Flow<List<GisNode>> = emptyFlow()
    override fun observeRoutes(projectId: String, query: String): Flow<List<GisRoute>> = emptyFlow()
}
