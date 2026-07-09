package com.mapsupervision.gis.ui

import androidx.compose.runtime.Composable
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import org.junit.Assert.assertSame
import org.junit.Test

class GisMapBridgeRegistryTest {
    private val fakeBridge = object : GisMapBridge {
        @Composable
        override fun Render(
            styleJson: String,
            nodes: List<GisNode>,
            routes: List<GisRoute>,
            showNumberLabels: Boolean,
            colorByContractor: Boolean,
            contractorColors: Map<String, String>,
            labelField: GisLabelField,
            showNodes: Boolean,
            showRoutes: Boolean,
            measureEnabled: Boolean,
            selectedNode: GisNode?,
            selectedRoute: GisRoute?,
            nodeSizeScale: Float,
            routeWidthScale: Float,
            onNodeClick: (GisNode) -> Unit,
            onRouteClick: (GisRoute) -> Unit,
            onMeasureDistance: (Double) -> Unit
        ) = Unit
    }

    @Test
    fun bridge_state_updates_when_registry_is_replaced() {
        val original = GisMapBridgeRegistry.bridge
        try {
            GisMapBridgeRegistry.bridge = fakeBridge
            assertSame(fakeBridge, GisMapBridgeRegistry.bridgeState.value)
        } finally {
            GisMapBridgeRegistry.bridge = original
        }
    }
}
