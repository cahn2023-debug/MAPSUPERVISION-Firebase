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

    @Test
    fun catalog_preserves_topological_foreign_key_order() {
        val order = FirebaseSyncTableCatalog.tables.map { it.tableName }
        
        fun indexOf(table: String) = order.indexOf(table)

        assertTrue(indexOf("projects") < indexOf("imported_files"))
        assertTrue(indexOf("imported_files") < indexOf("gis_node"))
        assertTrue(indexOf("imported_files") < indexOf("gis_route"))
        assertTrue(indexOf("imported_files") < indexOf("import_session"))
        assertTrue(indexOf("import_session") < indexOf("import_version"))
        assertTrue(indexOf("gis_node") < indexOf("gis_route"))
        assertTrue(indexOf("gis_node") < indexOf("node_progress"))
        assertTrue(indexOf("work_categories") < indexOf("material_declaration"))
        assertTrue(indexOf("material_declaration") < indexOf("material_handover"))
        assertTrue(indexOf("site_photos") < indexOf("photo_tags"))
        assertTrue(indexOf("site_photos") < indexOf("daily_log_photos"))
        assertTrue(indexOf("daily_log") < indexOf("daily_log_line"))
        assertTrue(indexOf("daily_log") < indexOf("daily_log_nodes"))
        assertTrue(indexOf("daily_log") < indexOf("daily_log_photos"))
    }
}
