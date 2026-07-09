package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.domain.model.WorkVolumeProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceMapFilteringTest {
    @Test
    fun build_map_design_nodes_returns_all_nodes_when_no_map_filters_are_active() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            designRoutes = listOf(
                GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
            )
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(listOf("P-001", "P-002"), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_filters_only_nodes_and_does_not_retain_route_endpoints() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1)
        val nodeC = GisNode("n3", "p1", "P-003", "C", 10.2, 106.2)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            designRoutes = listOf(
                GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
            ),
            mapUi = MapUiState(searchQuery = "R-001")
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(emptyList<String>(), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_filters_to_selected_contractor() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "A", 10.1, 106.1)
        val nodeC = GisNode("n3", "p1", "P-003", "B", 10.2, 106.2)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            designRoutes = listOf(
                GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002"),
                GisRoute("r2", "p1", "R-002", "B", "P-002", "P-003")
            ),
            mapUi = MapUiState(filterContractor = "A")
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(listOf("P-001", "P-002"), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_excludes_hidden_contractors() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            mapUi = MapUiState(hiddenContractors = setOf("A"))
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(listOf("P-002"), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_returns_empty_when_selected_contractor_is_hidden() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0)
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1)
        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            mapUi = MapUiState(
                filterContractor = "A",
                hiddenContractors = setOf("A")
            )
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = buildMapDesignNodes(state, indexes)

        assertEquals(emptyList<String>(), result.map { it.code })
    }

    @Test
    fun build_map_design_nodes_filters_by_code_and_map_number_label() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, mapNumberLabel = "So 12")
        val nodeB = GisNode("n2", "p1", "CAB-002", "B", 10.1, 106.1, mapNumberLabel = "So 34")
        val stateByCode = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            mapUi = MapUiState(searchQuery = "cab002")
        )

        val indexesByCode = buildWorkspaceIndexes(stateByCode)
        val resultByCode = buildMapDesignNodes(stateByCode, indexesByCode)
        assertEquals(listOf("CAB-002"), resultByCode.map { it.code })

        val stateByMapNumber = stateByCode.copy(mapUi = MapUiState(searchQuery = "số 12"))
        val indexesByMapNumber = buildWorkspaceIndexes(stateByMapNumber)
        val resultByMapNumber = buildMapDesignNodes(stateByMapNumber, indexesByMapNumber)
        assertEquals(listOf("P-001"), resultByMapNumber.map { it.code })
    }

    @Test
    fun build_map_design_nodes_filters_by_material_type() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, workVolumeSummary = "Cable: 10\nPipe: 5")
        val nodeB = GisNode("n2", "p1", "P-002", "A", 10.1, 106.1, workVolumeSummary = "Cable: 20")
        val nodeC = GisNode("n3", "p1", "P-003", "B", 10.2, 106.2, workVolumeSummary = "")
        
        val progressRow = WorkVolumeProgress("m1", "p1", "P-003", "Camera", 0f, 1f, 0L, "")

        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            workVolumeRows = listOf(progressRow),
            mapUi = MapUiState(filterMaterialType = "Cable")
        )
        val indexes = buildWorkspaceIndexes(state)
        val result = buildMapDesignNodes(state, indexes)
        assertEquals(listOf("P-001", "P-002"), result.map { it.code })

        val stateCamera = state.copy(mapUi = MapUiState(filterMaterialType = "camera"))
        val resultCamera = buildMapDesignNodes(stateCamera, indexes)
        assertEquals(listOf("P-003"), resultCamera.map { it.code })
    }

    @Test
    fun material_type_options_combines_material_summary_and_material_rows_correctly() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, workVolumeSummary = "Cable: 10\n  pipe: 5 ")
        val nodeB = GisNode("n2", "p1", "P-002", "A", 10.1, 106.1, workVolumeSummary = "CABLE: 20")
        val progressRow = WorkVolumeProgress("m1", "p1", "P-003", "camera", 0f, 1f, 0L, "")
        val progressRowDup = WorkVolumeProgress("m2", "p1", "P-001", "Pipe", 0f, 1f, 0L, "")

        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            workVolumeRows = listOf(progressRow, progressRowDup)
        )
        val indexes = buildWorkspaceIndexes(state)
        
        assertEquals(listOf("Cable", "camera", "pipe"), indexes.materialTypeOptions)
    }

    @Test
    fun routes_filtering_only_retains_routes_connected_to_live_nodes() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, workVolumeSummary = "Cable: 10")
        val nodeB = GisNode("n2", "p1", "P-002", "A", 10.1, 106.1, workVolumeSummary = "Cable: 20")
        val nodeC = GisNode("n3", "p1", "P-003", "B", 10.2, 106.2, workVolumeSummary = "")
        
        val route1 = GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
        val route2 = GisRoute("r2", "p1", "R-002", "B", "P-002", "P-003")

        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            designRoutes = listOf(route1, route2),
            mapUi = MapUiState(filterMaterialType = "Cable")
        )
        val indexes = buildWorkspaceIndexes(state)
        val liveNodes = buildMapDesignNodes(state, indexes)
        
        val result = filterRoutes(state.designRoutes, state.mapUi, indexes, liveNodes)
        assertEquals(listOf("R-001", "R-002"), result.map { it.code })

        val nodeAUnique = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, workVolumeSummary = "Unique: 1")
        val stateUnique = state.copy(
            designNodes = listOf(nodeAUnique, nodeB, nodeC),
            mapUi = MapUiState(filterMaterialType = "Unique")
        )
        val indexesUnique = buildWorkspaceIndexes(stateUnique)
        val liveNodesUnique = buildMapDesignNodes(stateUnique, indexesUnique)

        val resultUnique = filterRoutes(stateUnique.designRoutes, stateUnique.mapUi, indexesUnique, liveNodesUnique)
        assertEquals(listOf("R-001"), resultUnique.map { it.code })
    }

    @Test
    fun routes_filtering_matches_code_start_end_node_and_keeps_node_filter_separate() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, mapNumberLabel = "01")
        val nodeB = GisNode("n2", "p1", "P-002", "A", 10.1, 106.1, mapNumberLabel = "02")
        val nodeC = GisNode("n3", "p1", "P-003", "B", 10.2, 106.2, mapNumberLabel = "03")
        val route1 = GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
        val route2 = GisRoute("r2", "p1", "CAP-003", "B", "P-003", "P-002")

        val byRouteCodeState = WorkspaceState(
            designNodes = listOf(nodeA, nodeB, nodeC),
            designRoutes = listOf(route1, route2),
            mapUi = MapUiState(searchQuery = "cap003")
        )
        val byRouteCodeIndexes = buildWorkspaceIndexes(byRouteCodeState)
        val byRouteCodeNodes = buildMapDesignNodes(byRouteCodeState, byRouteCodeIndexes)
        val byRouteCodeRoutes = filterRoutes(
            byRouteCodeState.designRoutes,
            byRouteCodeState.mapUi,
            byRouteCodeIndexes,
            byRouteCodeNodes
        )
        assertEquals(emptyList<String>(), byRouteCodeNodes.map { it.code })
        assertEquals(listOf("CAP-003"), byRouteCodeRoutes.map { it.code })

        val byEndNodeState = byRouteCodeState.copy(mapUi = MapUiState(searchQuery = "p002"))
        val byEndNodeIndexes = buildWorkspaceIndexes(byEndNodeState)
        val byEndNodeNodes = buildMapDesignNodes(byEndNodeState, byEndNodeIndexes)
        val byEndNodeRoutes = filterRoutes(
            byEndNodeState.designRoutes,
            byEndNodeState.mapUi,
            byEndNodeIndexes,
            byEndNodeNodes
        )
        assertEquals(listOf("P-002"), byEndNodeNodes.map { it.code })
        assertEquals(listOf("R-001", "CAP-003"), byEndNodeRoutes.map { it.code })
    }

    @Test
    fun routes_filtering_excludes_hidden_contractors_and_selected_contractor() {
        val nodeA = GisNode("n1", "p1", "P-001", "A", 10.0, 106.0, workVolumeSummary = "Cable: 10")
        val nodeB = GisNode("n2", "p1", "P-002", "B", 10.1, 106.1, workVolumeSummary = "Cable: 20")
        val route1 = GisRoute("r1", "p1", "R-001", "A", "P-001", "P-002")
        val route2 = GisRoute("r2", "p1", "R-002", "B", "P-002", "P-001")

        val state = WorkspaceState(
            designNodes = listOf(nodeA, nodeB),
            designRoutes = listOf(route1, route2),
            mapUi = MapUiState(hiddenContractors = setOf("A"))
        )
        val indexes = buildWorkspaceIndexes(state)
        val liveNodes = buildMapDesignNodes(state, indexes)

        val result = filterRoutes(state.designRoutes, state.mapUi, indexes, liveNodes)
        assertEquals(listOf("R-002"), result.map { it.code })

        val selectedHiddenState = state.copy(
            mapUi = MapUiState(
                filterContractor = "A",
                hiddenContractors = setOf("A")
            )
        )
        val selectedIndexes = buildWorkspaceIndexes(selectedHiddenState)
        val selectedLiveNodes = buildMapDesignNodes(selectedHiddenState, selectedIndexes)
        val selectedResult = filterRoutes(selectedHiddenState.designRoutes, selectedHiddenState.mapUi, selectedIndexes, selectedLiveNodes)
        assertEquals(emptyList<String>(), selectedResult.map { it.code })
    }

    @Test
    fun routes_filtering_keeps_route_with_renderable_polyline_even_without_live_end_nodes() {
        val routeOnly = GisRoute(
            id = "r1",
            projectId = "p1",
            code = "R-POINTS",
            contractor = "A",
            startNodeCode = "",
            endNodeCode = "",
            points = listOf(10.0 to 106.0, 10.1 to 106.1, 10.2 to 106.2)
        )
        val state = WorkspaceState(
            designRoutes = listOf(routeOnly),
            mapUi = MapUiState()
        )
        val indexes = buildWorkspaceIndexes(state)

        val result = filterRoutes(state.designRoutes, state.mapUi, indexes, liveNodes = emptyList())

        assertEquals(listOf("R-POINTS"), result.map { it.code })
    }
}

