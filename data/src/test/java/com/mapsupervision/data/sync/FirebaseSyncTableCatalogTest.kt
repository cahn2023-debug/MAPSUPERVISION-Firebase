package com.mapsupervision.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseSyncTableCatalogTest {

    @Test
    fun catalog_contains_core_and_media_tables() {
        val names = FirebaseSyncTableCatalog.tables.map { it.tableName }.toSet()

        assertTrue("projects" in names)
        assertTrue("gis_node" in names)
        assertTrue("gis_route" in names)
        assertTrue("daily_log" in names)
        assertTrue("site_photos" in names)
        assertTrue("task" in names)
        assertTrue("material_handover" in names)
    }

    @Test
    fun project_root_uses_root_collection_marker() {
        val table = FirebaseSyncTableCatalog.byTableName("projects")

        assertEquals("__project_root__", table.collectionName)
        assertEquals(SyncScope.SHARED_ONLY, table.scope)
    }
}
