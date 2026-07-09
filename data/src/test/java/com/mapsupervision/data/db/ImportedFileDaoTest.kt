package com.mapsupervision.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mapsupervision.data.db.entity.GisNodeEntity
import com.mapsupervision.data.db.entity.ImportedFileEntity
import com.mapsupervision.data.db.entity.MaterialProgressEntity
import com.mapsupervision.data.db.entity.NodeProgressEntity
import com.mapsupervision.data.db.entity.NoteEntity
import com.mapsupervision.data.db.entity.ProjectEntity
import com.mapsupervision.data.db.entity.TaskEntity
import com.mapsupervision.domain.model.ProjectStorageMode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImportedFileDaoTest {

    @Test
    fun deleteById_removes_children_using_node_code_keys() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MapSupervisionDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val projectId = "project-1"
            val fileId = "file-1"
            val nodeId = "node-1"
            val nodeCode = "N-001"

            database.projectDao().upsert(
                ProjectEntity(
                    id = projectId,
                    name = "Project 1",
                    slug = "project-1",
                    isArchived = false,
                    createdAtEpochMs = 1L,
                    storageMode = ProjectStorageMode.LEGACY_SHARED
                )
            )
            database.importedFileDao().upsert(
                ImportedFileEntity(
                    id = fileId,
                    projectId = projectId,
                    fileName = "design.xlsx",
                    fileType = "xlsx",
                    storedPath = "/tmp/design.xlsx",
                    summary = "",
                    importedAtEpochMs = 2L
                )
            )
            database.gisNodeDao().upsert(
                GisNodeEntity(
                    id = nodeId,
                    projectId = projectId,
                    code = nodeCode,
                    contractor = "Contractor A",
                    latitude = 10.0,
                    longitude = 106.0,
                    mapNumberLabel = "Map 1",
                    workVolumeSummary = "",
                    importedFileId = fileId
                )
            )
            database.nodeProgressDao().upsert(
                NodeProgressEntity(
                    id = "progress-1",
                    projectId = projectId,
                    nodeId = nodeId,
                    planned = 100f,
                    actual = 20f,
                    remain = 80f,
                    delayed = false,
                    updatedAtEpochMs = 3L
                )
            )
            database.workVolumeProgressDao().upsert(
                MaterialProgressEntity(
                    id = "material-1",
                    projectId = projectId,
                    nodeCode = nodeCode,
                    nodeId = nodeId,
                    materialName = "Cap",
                    plannedQty = 10f,
                    actualQty = 4f,
                    updatedAtEpochMs = 3L
                )
            )
            database.noteDao().insert(
                NoteEntity(
                    id = "note-1",
                    projectId = projectId,
                    objectNodeId = nodeId,
                    content = "note",
                    createdAtEpochMs = 4L
                )
            )
            database.taskDao().upsert(
                TaskEntity(
                    id = "task-1",
                    projectId = projectId,
                    objectNodeId = nodeId,
                    title = "task",
                    description = "",
                    status = "TODO",
                    createdAtEpochMs = 5L,
                    completedAtEpochMs = null
                )
            )

            database.importedFileDao().deleteById(fileId, 10L, 10L)

            assertTrue(database.gisNodeDao().byProject(projectId).isEmpty())
            assertTrue(database.nodeProgressDao().byProject(projectId).isEmpty())
            assertTrue(database.workVolumeProgressDao().byProject(projectId).isEmpty())
            assertTrue(database.noteDao().byProject(projectId).isEmpty())
            assertTrue(database.taskDao().byProject(projectId).isEmpty())
            assertTrue(database.importedFileDao().byProject(projectId).isEmpty())
        } finally {
            database.close()
        }
    }
}
