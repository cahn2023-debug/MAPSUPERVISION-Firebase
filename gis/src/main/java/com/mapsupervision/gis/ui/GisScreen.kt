package com.mapsupervision.gis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import com.mapsupervision.core.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GisLabelField { CODE, CONTRACTOR, COORDINATE }

enum class MapLayerType { STREET, SATELLITE, SATELLITE_LABELS, DARK }

/**
 * Public GIS rendering contract.
 *
 * `:gis` owns the screen state and passes only rendering inputs/callbacks through this interface.
 * `:gis-maplibre` is responsible for the concrete map implementation and should not introduce
 * business rules outside this contract.
 */
interface GisMapBridge {
    @Composable
    fun Render(
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
    )

    fun zoomIn() {}
    fun zoomOut() {}
    fun fitToObjects() {}
    fun centerOnMyLocation(): Boolean = false
    fun setLayerVisibility(showNodes: Boolean, showRoutes: Boolean) {}
    fun setMeasureEnabled(enabled: Boolean) {}
    fun setBaseMap(type: MapLayerType) {}
    fun centerOnLocation(lat: Double, lng: Double, zoom: Double = 18.0) {}
}

/**
 * Runtime registry used by the app to install the active bridge implementation.
 *
 * The bridge should be installed once at application start and then treated as the current
 * rendering backend for all GIS screens.
 */
object GisMapBridgeRegistry {
    private val _bridge = MutableStateFlow<GisMapBridge?>(null)
    val bridgeState: StateFlow<GisMapBridge?> = _bridge.asStateFlow()

    var bridge: GisMapBridge?
        get() = _bridge.value
        set(value) {
            _bridge.value = value
        }
}

@Composable
fun GisScreen(
    nodes: List<GisNode>? = null,
    routes: List<GisRoute>? = null,
    showNumberLabels: Boolean = false,
    colorByContractor: Boolean = true,
    contractorColors: Map<String, String> = emptyMap(),
    labelField: GisLabelField = GisLabelField.CODE,
    showNodes: Boolean = true,
    showRoutes: Boolean = true,
    measureEnabled: Boolean = false,
    selectedNode: GisNode? = null,
    selectedRoute: GisRoute? = null,
    nodeSizeScale: Float = 1.0f,
    routeWidthScale: Float = 1.0f,
    onNodeClick: (GisNode) -> Unit = {},
    onRouteClick: (GisRoute) -> Unit = {},
    onMeasureDistance: (Double) -> Unit = {},
    viewModel: GisViewModel = hiltViewModel()
) {
    val renderNodes = nodes ?: viewModel.nodes.collectAsState().value
    val renderRoutes = routes ?: viewModel.routes.collectAsState().value
    val styleJson = viewModel.styleJson
    LaunchedEffect(renderNodes, renderRoutes, showNodes, showRoutes, measureEnabled, selectedNode, selectedRoute) {
        AppLogger.d(
            "gis.render nodes=${renderNodes.size} routes=${renderRoutes.size} " +
            "showNodes=$showNodes showRoutes=$showRoutes measure=$measureEnabled " +
                "selectedNode=${selectedNode?.code.orEmpty()} selectedRoute=${selectedRoute?.code.orEmpty()}"
        )
    }

    val bridge = GisMapBridgeRegistry.bridgeState.collectAsState().value

    if (bridge != null) {
        bridge.Render(
            styleJson = styleJson,
            nodes = renderNodes,
            routes = renderRoutes,
            showNumberLabels = showNumberLabels,
            colorByContractor = colorByContractor,
            contractorColors = contractorColors,
            labelField = labelField,
            showNodes = showNodes,
            showRoutes = showRoutes,
            measureEnabled = measureEnabled,
            selectedNode = selectedNode,
            selectedRoute = selectedRoute,
            nodeSizeScale = nodeSizeScale,
            routeWidthScale = routeWidthScale,
            onNodeClick = onNodeClick,
            onRouteClick = onRouteClick,
            onMeasureDistance = onMeasureDistance
        )
    } else {
        FallbackMapPlaceholder(renderNodes, renderRoutes)
    }
}

@Composable
private fun FallbackMapPlaceholder(nodes: List<GisNode>, routes: List<GisRoute>) {
    // Vẽ bản đồ vệ tinh giả lập siêu cao cấp bằng Canvas
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Nền xanh đen huyền ảo
    ) {
        // 1. Vẽ lưới tọa độ mờ ảo kiểu Radar vệ tinh
        val gridSpacing = 60.dp.toPx()
        for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), size.height),
                strokeWidth = 1f
            )
        }
        for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1f
            )
        }

        // 2. Vẽ con sông uốn lượn mềm mại như trong ảnh chụp
        val riverPath = Path().apply {
            moveTo(0f, size.height * 0.55f)
            cubicTo(
                size.width * 0.3f, size.height * 0.52f,
                size.width * 0.6f, size.height * 0.62f,
                size.width, size.height * 0.54f
            )
        }
        drawPath(
            path = riverPath,
            color = Color(0xFF0F3244), // Xanh sẫm màu nước sâu
            style = Stroke(
                width = 32.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // 3. Vẽ mạng lưới đường sá kỹ thuật mờ ảo
        val roadColor = Color(0xFF334155)
        
        // Đường dọc 1
        drawLine(
            color = roadColor,
            start = Offset(size.width * 0.25f, 0f),
            end = Offset(size.width * 0.35f, size.height),
            strokeWidth = 4.dp.toPx()
        )
        // Đường dọc 2
        drawLine(
            color = roadColor,
            start = Offset(size.width * 0.7f, 0f),
            end = Offset(size.width * 0.6f, size.height),
            strokeWidth = 3.dp.toPx()
        )
        // Đường cắt ngang 1
        drawLine(
            color = roadColor,
            start = Offset(0f, size.height * 0.25f),
            end = Offset(size.width, size.height * 0.35f),
            strokeWidth = 3.dp.toPx()
        )
        // Đường chéo mờ kiểu ngõ hẻm đô thị
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(0f, size.height * 0.1f),
            end = Offset(size.width * 0.8f, size.height * 0.9f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(size.width * 0.9f, 0f),
            end = Offset(0f, size.height * 0.8f),
            strokeWidth = 1.55.dp.toPx()
        )
    }
}
