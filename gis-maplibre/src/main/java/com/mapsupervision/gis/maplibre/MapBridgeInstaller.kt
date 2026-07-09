package com.mapsupervision.gis.maplibre

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.location.Location
import android.location.LocationManager
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.model.GisRoute
import com.mapsupervision.gis.ui.GisMapBridge
import com.mapsupervision.gis.ui.GisMapBridgeRegistry
import com.mapsupervision.gis.ui.GisLabelField
import com.mapsupervision.gis.ui.MapLayerType
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class MapRenderKey(
    val nodeSignature: Long,
    val routeSignature: Long,
    val nodeCount: Int,
    val routeCount: Int,
    val labelField: GisLabelField,
    val showNumberLabels: Boolean,
    val colorByContractor: Boolean,
    val contractorColorsHash: Int,
    val nodeSizeScale: Float,
    val routeWidthScale: Float
)

internal fun buildMapRenderKey(
    nodes: List<GisNode>,
    routes: List<GisRoute>,
    labelField: GisLabelField,
    showNumberLabels: Boolean,
    colorByContractor: Boolean,
    contractorColors: Map<String, String>,
    nodeSizeScale: Float,
    routeWidthScale: Float
): MapRenderKey {
    return MapRenderKey(
        nodeSignature = nodes.stableNodeSignature(),
        routeSignature = routes.stableRouteSignature(),
        nodeCount = nodes.size,
        routeCount = routes.size,
        labelField = labelField,
        showNumberLabels = showNumberLabels,
        colorByContractor = colorByContractor,
        contractorColorsHash = contractorColors.hashCode(),
        nodeSizeScale = nodeSizeScale,
        routeWidthScale = routeWidthScale
    )
}

private fun List<GisNode>.stableNodeSignature(): Long {
    var signature = 1L
    for (node in this) {
        signature = signature.mix(node.id)
            .mix(node.code)
            .mix(node.contractor)
            .mix(node.latitude)
            .mix(node.longitude)
            .mix(node.mapNumberLabel)
            .mix(node.signalStatus)
            .mix(node.importedFileId)
    }
    return signature
}

private fun List<GisRoute>.stableRouteSignature(): Long {
    var signature = 1L
    for (route in this) {
        signature = signature.mix(route.id)
            .mix(route.code)
            .mix(route.contractor)
            .mix(route.startNodeCode)
            .mix(route.endNodeCode)
            .mix(route.importedFileId)
        for (point in route.points) {
            signature = signature.mix(point.first).mix(point.second)
        }
    }
    return signature
}

private fun Long.mix(value: Any?): Long = 31L * this + (value?.hashCode() ?: 0).toLong()

internal enum class MapRenderTier { LIGHTWEIGHT, FULL }

internal data class MapRenderPolicyDecision(
    val renderTier: MapRenderTier,
    val scheduleDetailUpgrade: Boolean
)

internal fun resolveMapRenderPolicy(
    preferLightweightRender: Boolean,
    requestedShowNumberLabels: Boolean,
    isLowRamDevice: Boolean
): MapRenderPolicyDecision {
    if (!preferLightweightRender || !requestedShowNumberLabels) {
        return MapRenderPolicyDecision(
            renderTier = MapRenderTier.FULL,
            scheduleDetailUpgrade = false
        )
    }
    if (isLowRamDevice) {
        return MapRenderPolicyDecision(
            renderTier = MapRenderTier.LIGHTWEIGHT,
            scheduleDetailUpgrade = false
        )
    }
    return MapRenderPolicyDecision(
        renderTier = MapRenderTier.LIGHTWEIGHT,
        scheduleDetailUpgrade = true
    )
}

object MapBridgeInstaller {
    @JvmStatic
    fun install(context: android.content.Context) {
        runCatching {
            MapLibre.getInstance(context, "", WellKnownTileServer.MapLibre)
        }
        GisMapBridgeRegistry.bridge = MapLibreGisMapBridge()
    }
}

private class MapLibreGisMapBridge : GisMapBridge {
    companion object {
        private const val TAG = "MapLibreGisMapBridge"
        private const val NODES_SOURCE_ID = "nodes_source"
        private const val ROUTES_SOURCE_ID = "routes_source"
        private const val MEASURE_SOURCE_ID = "measure_source"
        private const val NODES_LAYER_ID = "nodes"
        private const val NODE_LABELS_LAYER_ID = "nodes_labels"
        private const val ROUTES_LAYER_ID = "routes"
        private const val MEASURE_LAYER_ID = "measure_line"
        private const val DETAIL_UPGRADE_DELAY_MS = 350L
    }

    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var map: MapLibreMap? = null
    private var mapViewRef: MapView? = null
    private var appContext: Context? = null
    private var nodesSnapshot: List<GisNode> = emptyList()
    private var routesSnapshot: List<GisRoute> = emptyList()
    private var latestLabelField: GisLabelField = GisLabelField.CODE
    private var latestShowNumberLabels: Boolean = false
    private var latestColorByContractor: Boolean = true
    private var latestContractorColors: Map<String, String> = emptyMap()
    private var latestShowNodes: Boolean = true
    private var latestShowRoutes: Boolean = true
    private var latestNodeSizeScale: Float = 1.0f
    private var latestRouteWidthScale: Float = 1.0f
    private var measureEnabled: Boolean = false
    private var selectedNodeSnapshot: GisNode? = null
    private var selectedRouteSnapshot: GisRoute? = null
    private var measurePoints: MutableList<LatLng> = mutableListOf()
    private var onNodeClickCallback: ((GisNode) -> Unit)? = null
    private var onRouteClickCallback: ((GisRoute) -> Unit)? = null
    private var onMeasureDistanceCallback: ((Double) -> Unit)? = null
    private var clickListenerAttached = false
    private var didFitBoundsOnce = false
    private var lastLayerSignature: Int = 0
    private var lastMeasureSignature: Int = 0
    private var lastFocusedSelectionKey: String? = null
    private var lastRenderKey: MapRenderKey? = null
    private var mapUpdateJob: Job? = null
    private var detailUpgradeJob: Job? = null
    private var styleEpoch: Int = 0
    private val missingSourceWarnings = mutableSetOf<String>()
    private var preferLightweightRender: Boolean = true
    private var isCurrentDeviceLowRam: Boolean = false

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
    ) {
        val context = LocalContext.current
        appContext = context.applicationContext
        isCurrentDeviceLowRam = isLowRamDevice(context.applicationContext)
        nodesSnapshot = nodes
        routesSnapshot = routes
        latestShowNumberLabels = showNumberLabels
        latestColorByContractor = colorByContractor
        latestContractorColors = contractorColors
        latestLabelField = labelField
        latestShowNodes = showNodes
        latestShowRoutes = showRoutes
        latestNodeSizeScale = nodeSizeScale
        latestRouteWidthScale = routeWidthScale
        this.measureEnabled = measureEnabled
        selectedNodeSnapshot = selectedNode
        selectedRouteSnapshot = selectedRoute
        onNodeClickCallback = onNodeClick
        onRouteClickCallback = onRouteClick
        onMeasureDistanceCallback = onMeasureDistance
        // logSnapshot removed to avoid heavy synchronous main-thread computations in Compose Render loop.

        val mapView = remember { 
            MapView(context).also { mv ->
                mv.onCreate(null)
                resetRuntimeState(keepSnapshots = true)
                mv.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    val width = right - left
                    val height = bottom - top
                    if (width > 0 && height > 0) {
                        map?.let { loadedMap ->
                            if (loadedMap.style != null) {
                                focusSelectionIfNeeded()
                            }
                        }
                    }
                }
            }
        }
        mapViewRef = mapView

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, mapView) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        mapView.onResume()
                        mapView.getMapAsync { loadedMap ->
                            loadedMap.getStyle {
                                map = loadedMap
                                val wasFitted = didFitBoundsOnce
                                resetRuntimeState(keepSnapshots = true)
                                didFitBoundsOnce = wasFitted
                                ensureClickListener(loadedMap)
                                updateMapData()
                                applyLayerVisibility()
                                updateMeasureGeoJson()
                            }
                        }
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                        // Prevent camera/data operations on stale map references.
                        map = null
                        clickListenerAttached = false
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDestroy()
                map = null
                mapViewRef = null
                resetRuntimeState(keepSnapshots = true)
            }
        }

        DisposableEffect(mapView) {
            mapView.getMapAsync { loadedMap ->
                if (map == null || loadedMap != map) {
                    bindMap(loadedMap, styleJson)
                }
            }
            onDispose {}
        }

        AndroidView(
            factory = { mapView },
            update = {
                // Explicitly reference all state-dependent variables inside the update block 
                // so Jetpack Compose knows it must re-run this block when the inputs change.
                val _style = styleJson
                val _nodes = nodes
                val _routes = routes
                val _showLabels = showNumberLabels
                val _colorBy = colorByContractor
                val _contractorColors = contractorColors
                val _label = labelField
                val _showN = showNodes
                val _showR = showRoutes
                val _measure = measureEnabled
                val _selNode = selectedNode
                val _selRoute = selectedRoute
                val _nodeScale = nodeSizeScale
                val _routeScale = routeWidthScale

                val currentMap = map
                if (currentMap != null && currentMap.style != null) {
                    ensureClickListener(currentMap)
                    updateMapData()
                    applyLayerVisibility()
                    updateMeasureGeoJson()
                    if (!focusSelectionIfNeeded()) {
                        fitIfNeeded()
                    }
                } else {
                    it.getMapAsync { loadedMap ->
                        bindMap(loadedMap, styleJson)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    override fun zoomIn() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        mapRef.animateCamera(CameraUpdateFactory.zoomIn())
    }

    override fun zoomOut() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        mapRef.animateCamera(CameraUpdateFactory.zoomOut())
    }

    override fun fitToObjects() {
        tryFitToObjects()
    }

    private fun tryFitToObjects(): Boolean {
        val mapRef = map ?: return false
        if (mapRef.style == null) return false
        val points = renderCoordinatesForMapObjects(nodesSnapshot, routesSnapshot)
        if (points.isEmpty()) return false
        if (points.size == 1) {
            val point = points.first()
            mapRef.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(point.latitude, point.longitude),
                    17.0
                )
            )
            Log.d(
                TAG,
                "fitToObjects single-point nodes=${nodesSnapshot.size} routes=${routesSnapshot.size} lat=${point.latitude} lon=${point.longitude}"
            )
            return true
        }
        val bounds = LatLngBounds.Builder().apply {
            points.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()
        mapRef.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        Log.d(TAG, "fitToObjects success nodes=${nodesSnapshot.size} routes=${routesSnapshot.size} points=${points.size} latRange=%.5f..%.5f lonRange=%.5f..%.5f".format(minLat, maxLat, minLon, maxLon))
        return true
    }

    override fun centerOnMyLocation(): Boolean {
        val context = appContext ?: return false
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val best: Location? = lastKnownLocations(lm, providers)
            .maxByOrNull { it.time }

        val point = best ?: return false
        val mapRef = map ?: return false
        if (mapRef.style == null) return false
        mapRef.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 15.5)
        )
        return true
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocations(
        locationManager: LocationManager,
        providers: List<String>
    ): List<Location> {
        return providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
    }

    override fun setLayerVisibility(showNodes: Boolean, showRoutes: Boolean) {
        latestShowNodes = showNodes
        latestShowRoutes = showRoutes
        applyLayerVisibility()
    }

    override fun setMeasureEnabled(enabled: Boolean) {
        measureEnabled = enabled
        if (!enabled) {
            measurePoints.clear()
            updateMeasureGeoJson()
        }
    }

    override fun setBaseMap(type: MapLayerType) {
        val mapRef = map ?: return
        val styleUri = when (type) {
            MapLayerType.STREET -> "asset://style_street.json"
            MapLayerType.SATELLITE -> "asset://style_satellite.json"
            MapLayerType.SATELLITE_LABELS -> "asset://style_satellite_labels.json"
            MapLayerType.DARK -> "asset://style_dark.json"
        }
        styleEpoch++
        mapRef.setStyle(Style.Builder().fromUri(styleUri)) {
            resetRuntimeState(keepSnapshots = true)
            Log.d(TAG, "setStyle callback baseMap=$type epoch=$styleEpoch")
            ensureClickListener(mapRef)
            updateMapData()
            applyLayerVisibility()
            updateMeasureGeoJson()
            if (!focusSelectionIfNeeded()) {
                fitIfNeeded()
            }
        }
    }

    override fun centerOnLocation(lat: Double, lng: Double, zoom: Double) {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        val target = normalizeCoordinatePair(lat, lng) ?: return
        mapRef.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), zoom)
        )
    }

    private fun ensureClickListener(map: MapLibreMap) {
        if (clickListenerAttached) return
        map.addOnMapClickListener { point ->
            if (measureEnabled) {
                onMeasureTap(point)
                return@addOnMapClickListener true
            }
            val screenPoint = map.projection.toScreenLocation(point)

            // Larger hitbox for nodes (easier to tap on mobile)
            val nodeHitBox = RectF(
                screenPoint.x - 44f,
                screenPoint.y - 44f,
                screenPoint.x + 44f,
                screenPoint.y + 44f
            )
            // Wider hitbox for routes (line is thin, needs generous tolerance)
            val routeHitBox = RectF(
                screenPoint.x - 28f,
                screenPoint.y - 28f,
                screenPoint.x + 28f,
                screenPoint.y + 28f
            )

            // Check nodes FIRST — nodes take priority over routes
            val clickedNodes = map.queryRenderedFeatures(nodeHitBox, "nodes")
            val nodeCode = clickedNodes.firstOrNull()?.getProperty("code")?.asString
            if (!nodeCode.isNullOrBlank()) {
                nodesSnapshot.firstOrNull { it.code == nodeCode }?.let {
                    onNodeClickCallback?.invoke(it)
                    return@addOnMapClickListener true
                }
            }

            // Then check routes
            val clickedRoutes = map.queryRenderedFeatures(routeHitBox, "routes")
            val routeCode = clickedRoutes.firstOrNull()?.getProperty("code")?.asString
            if (!routeCode.isNullOrBlank()) {
                routesSnapshot.firstOrNull { it.code == routeCode }?.let {
                    onRouteClickCallback?.invoke(it)
                    return@addOnMapClickListener true
                }
            }

            false
        }
        clickListenerAttached = true
    }

    private fun onMeasureTap(point: LatLng) {
        if (measurePoints.size >= 2) {
            measurePoints.clear()
        }
        measurePoints.add(point)
        updateMeasureGeoJson()
        if (measurePoints.size == 2) {
            val distM = haversineMeters(measurePoints[0], measurePoints[1])
            onMeasureDistanceCallback?.invoke(distM)
        }
    }

    private fun updateMapData() {
        val mapRef = map ?: return
        val style = mapRef.style ?: return

        val localNodes = nodesSnapshot.toList()
        val localRoutes = routesSnapshot.toList()
        val localLabelField = latestLabelField
        val localShowNumberLabels = latestShowNumberLabels
        val localColorByContractor = latestColorByContractor
        val localContractorColors = latestContractorColors
        val localNodeSizeScale = latestNodeSizeScale
        val localRouteWidthScale = latestRouteWidthScale
        val localStyleEpoch = styleEpoch
        val localPreferLightweightRender = preferLightweightRender
        val localIsLowRamDevice = isCurrentDeviceLowRam
        val previousRenderKey = lastRenderKey

        mapUpdateJob?.cancel()
        mapUpdateJob = renderScope.launch {
            val renderPayload = withContext(Dispatchers.Default) {
                val requestedRenderKey = buildMapRenderKey(
                    nodes = localNodes,
                    routes = localRoutes,
                    labelField = localLabelField,
                    showNumberLabels = localShowNumberLabels,
                    colorByContractor = localColorByContractor,
                    contractorColors = localContractorColors,
                    nodeSizeScale = localNodeSizeScale,
                    routeWidthScale = localRouteWidthScale
                )
                val renderPolicy = resolveMapRenderPolicy(
                    preferLightweightRender = localPreferLightweightRender,
                    requestedShowNumberLabels = localShowNumberLabels,
                    isLowRamDevice = localIsLowRamDevice
                )
                val effectiveShowNumberLabels =
                    localShowNumberLabels && renderPolicy.renderTier == MapRenderTier.FULL
                val effectiveRenderKey = requestedRenderKey.copy(
                    showNumberLabels = effectiveShowNumberLabels
                )

                if (effectiveRenderKey == previousRenderKey && localStyleEpoch == styleEpoch) {
                    return@withContext null
                }

                val localRenderableNodes = localNodes.mapNotNull { node ->
                    renderCoordinateForNode(node)?.let { point -> node to point }
                }

                val nodeByCode = localRenderableNodes.associate { (node, point) ->
                    node.code.trim().uppercase() to point
                }
                val routeNodeCodes = HashSet<String>(localRoutes.size * 2)
                localRoutes.forEach { route ->
                    routeNodeCodes += route.startNodeCode.trim().uppercase()
                    routeNodeCodes += route.endNodeCode.trim().uppercase()
                }

                val displayedNodes = localRenderableNodes.filter { (node, _) ->
                    val upperCode = node.code.trim().uppercase()
                    !(routeNodeCodes.contains(upperCode) && isStructuralRouteNodeCode(upperCode))
                }

                val nodeFeatures = displayedNodes.map { (node, point) ->
                    val baseColor = if (localColorByContractor) colorForContractor(node.contractor, localContractorColors) else "#f97316"
                    val signalColor = baseColor
                    Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                        addStringProperty("code", node.code)
                        addStringProperty("contractor", node.contractor)
                        addStringProperty("signalStatus", node.signalStatus.name)
                        addStringProperty(
                            "label",
                            if (!effectiveShowNumberLabels) "" else formatNodeLabel(node, localLabelField)
                        )
                        addStringProperty("color", signalColor)
                        addStringProperty(
                            "signalStrokeColor",
                            when (node.signalStatus.name) {
                                "HAS_SIGNAL" -> "#22c55e"
                                "NO_SIGNAL" -> "#ef4444"
                                else -> "#ffffff"
                            }
                        )
                        addNumberProperty(
                            "signalStrokeWidth",
                            (when (node.signalStatus.name) {
                                "HAS_SIGNAL", "NO_SIGNAL" -> 4.0
                                else -> 2.0
                            }) * localNodeSizeScale
                        )
                        addNumberProperty(
                            "signalRadius",
                            (when (node.signalStatus.name) {
                                "HAS_SIGNAL" -> 11.5
                                "NO_SIGNAL" -> 11.0
                                else -> 10.0
                            }) * localNodeSizeScale
                        )
                    }
                }

                var skippedRoutes = 0
                var skippedInvalidPointRoutes = 0
                val routeFeatures = localRoutes.mapNotNull { r ->
                    if (r.points.isNotEmpty()) {
                        val renderPoints = renderCoordinatesForRoutePoints(r)
                        if (renderPoints.size < 2) {
                            skippedInvalidPointRoutes++
                            return@mapNotNull null
                        }
                        Feature.fromGeometry(
                            LineString.fromLngLats(
                                renderPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                            )
                        ).apply {
                            addStringProperty("code", r.code)
                        }
                    } else {
                        val start = nodeByCode[r.startNodeCode.trim().uppercase()]
                        val end = nodeByCode[r.endNodeCode.trim().uppercase()]
                        if (start != null && end != null) {
                            Feature.fromGeometry(
                                LineString.fromLngLats(
                                    listOf(
                                        Point.fromLngLat(start.longitude, start.latitude),
                                        Point.fromLngLat(end.longitude, end.latitude)
                                    )
                                )
                            ).apply {
                                addStringProperty("code", r.code)
                            }
                        } else {
                            skippedRoutes++
                            null
                        }
                    }
                }

                val nodeGeoJson = FeatureCollection.fromFeatures(nodeFeatures).toJson()
                val routeGeoJson = FeatureCollection.fromFeatures(routeFeatures).toJson()

                RenderPayload(
                    nodeGeoJson = nodeGeoJson,
                    routeGeoJson = routeGeoJson,
                    nodeFeaturesBuilt = nodeFeatures.size,
                    routeFeaturesBuilt = routeFeatures.size,
                    skippedRoutes = skippedRoutes,
                    skippedInvalidPointRoutes = skippedInvalidPointRoutes,
                    newRenderKey = effectiveRenderKey,
                    scheduleDetailUpgrade = renderPolicy.scheduleDetailUpgrade,
                    requestedRenderKey = requestedRenderKey,
                    committedTier = renderPolicy.renderTier
                )
            }

            if (renderPayload == null || localStyleEpoch != styleEpoch) {
                return@launch
            }

            val currentStyle = map?.style ?: return@launch
            currentStyle.getSourceAs<GeoJsonSource>(NODES_SOURCE_ID)?.let { source ->
                source.setGeoJson(renderPayload.nodeGeoJson)
                Log.d(
                    TAG,
                    "nodes render path committed source=$NODES_SOURCE_ID features=${renderPayload.nodeFeaturesBuilt}"
                )
            } ?: warnMissingSourceOnce(NODES_SOURCE_ID)

            currentStyle.getSourceAs<GeoJsonSource>(ROUTES_SOURCE_ID)?.let { source ->
                source.setGeoJson(renderPayload.routeGeoJson)
            } ?: warnMissingSourceOnce(ROUTES_SOURCE_ID)

            lastRenderKey = renderPayload.newRenderKey
            when (renderPayload.committedTier) {
                MapRenderTier.FULL -> preferLightweightRender = false
                MapRenderTier.LIGHTWEIGHT -> preferLightweightRender = true
            }
            if (renderPayload.scheduleDetailUpgrade) {
                scheduleDetailUpgrade(renderPayload.requestedRenderKey, localStyleEpoch)
            } else {
                clearPendingDetailUpgrade()
            }

            Log.d(
                TAG,
                "updateMapData committed nodesIn=${localNodes.size} " +
                    "nodeFeatures=${renderPayload.nodeFeaturesBuilt} routesIn=${localRoutes.size} " +
                    "routeFeatures=${renderPayload.routeFeaturesBuilt} skippedRoutes=${renderPayload.skippedRoutes} " +
                    "skippedInvalidPointRoutes=${renderPayload.skippedInvalidPointRoutes} " +
                    "tier=${renderPayload.committedTier}"
            )
        }
    }

    private fun buildRenderKey(
        nodes: List<GisNode>,
        routes: List<GisRoute>,
        labelField: GisLabelField,
        showNumberLabels: Boolean,
        colorByContractor: Boolean,
        contractorColors: Map<String, String>,
        nodeSizeScale: Float,
        routeWidthScale: Float
    ): MapRenderKey = buildMapRenderKey(nodes, routes, labelField, showNumberLabels, colorByContractor, contractorColors, nodeSizeScale, routeWidthScale)

    private fun clearPendingMapUpdate() {
        mapUpdateJob?.cancel()
        mapUpdateJob = null
    }

    private fun clearPendingDetailUpgrade() {
        detailUpgradeJob?.cancel()
        detailUpgradeJob = null
    }

    private fun scheduleDetailUpgrade(requestedRenderKey: MapRenderKey, expectedStyleEpoch: Int) {
        clearPendingDetailUpgrade()
        detailUpgradeJob = renderScope.launch {
            delay(DETAIL_UPGRADE_DELAY_MS)
            if (styleEpoch != expectedStyleEpoch) return@launch
            if (!preferLightweightRender) return@launch
            val latestRequestedRenderKey = buildMapRenderKey(
                nodes = nodesSnapshot,
                routes = routesSnapshot,
                labelField = latestLabelField,
                showNumberLabels = latestShowNumberLabels,
                colorByContractor = latestColorByContractor,
                contractorColors = latestContractorColors,
                nodeSizeScale = latestNodeSizeScale,
                routeWidthScale = latestRouteWidthScale
            )
            if (latestRequestedRenderKey != requestedRenderKey) return@launch
            preferLightweightRender = false
            updateMapData()
        }
    }

    private fun updateMeasureGeoJson() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        val currentMeasureSignature = measurePoints.fold(1) { acc, p ->
            31 * acc + p.latitude.hashCode() + p.longitude.hashCode()
        } * 31 + if (measureEnabled) 1 else 0
        if (currentMeasureSignature == lastMeasureSignature) return
        lastMeasureSignature = currentMeasureSignature
        mapRef.getStyle()?.let { style ->
            val source = style.getSourceAs<GeoJsonSource>(MEASURE_SOURCE_ID)
            if (source == null) {
                Log.w(TAG, "Missing source: $MEASURE_SOURCE_ID")
                return@let
            }
            val features = mutableListOf<Feature>()
            if (measurePoints.size == 2) {
                val line = LineString.fromLngLats(
                    measurePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                )
                features += Feature.fromGeometry(line).apply {
                    addNumberProperty("distance_m", haversineMeters(measurePoints[0], measurePoints[1]))
                }
            }
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun applyLayerVisibility() {
        val mapRef = map ?: return
        if (mapRef.style == null) return
        val signature = (if (latestShowNodes) 1 else 0) * 1000 +
            (if (latestShowRoutes) 1 else 0) * 100 +
            (if (measureEnabled) 1 else 0) * 10 +
            (latestRouteWidthScale * 100).toInt()
        if (signature == lastLayerSignature) return
        lastLayerSignature = signature
        Log.d(TAG, "applyLayerVisibility showNodes=$latestShowNodes showRoutes=$latestShowRoutes measureEnabled=$measureEnabled routeWidthScale=$latestRouteWidthScale")
        mapRef.getStyle()?.let { style ->
            val nodeVisibility = if (latestShowNodes) Property.VISIBLE else Property.NONE
            val routeVisibility = if (latestShowRoutes) Property.VISIBLE else Property.NONE
            style.getLayer(NODES_LAYER_ID)?.setProperties(PropertyFactory.visibility(nodeVisibility))
            style.getLayer(NODE_LABELS_LAYER_ID)?.setProperties(PropertyFactory.visibility(nodeVisibility))
            style.getLayer(ROUTES_LAYER_ID)?.setProperties(
                PropertyFactory.visibility(routeVisibility),
                PropertyFactory.lineWidth(4.0f * latestRouteWidthScale)
            )
            style.getLayer(MEASURE_LAYER_ID)?.setProperties(
                PropertyFactory.visibility(if (measureEnabled) Property.VISIBLE else Property.NONE)
            )
        }
    }

    private fun formatNodeLabel(node: GisNode, labelField: GisLabelField): String = when (labelField) {
        GisLabelField.CODE -> compactLabel(node.mapNumberLabel.ifBlank { node.code })
        GisLabelField.CONTRACTOR -> compactLabel(node.contractor)
        GisLabelField.COORDINATE -> compactLabel("${"%.5f".format(node.latitude)},${"%.5f".format(node.longitude)}")
    }

    private fun compactLabel(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val firstNumber = Regex("""\d+""").find(trimmed)?.value
        if (!firstNumber.isNullOrBlank()) {
            return firstNumber.take(4)
        }
        return trimmed.take(3).uppercase()
    }

    private fun haversineMeters(a: LatLng, b: LatLng): Double {
        val earthRadius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun colorForContractor(contractor: String, customColors: Map<String, String> = emptyMap()): String {
        customColors[contractor]?.let { return it }
        val palette = listOf("#f97316", "#22c55e", "#06b6d4", "#a855f7", "#ef4444", "#f59e0b", "#3b82f6")
        return palette[abs(contractor.hashCode()) % palette.size]
    }

    private fun isStructuralRouteNodeCode(code: String): Boolean {
        val upper = code.trim().uppercase()
        return (upper.contains("#PM") && upper.contains("_P")) ||
                upper.contains("_P") ||
                upper.endsWith("_S") ||
                upper.endsWith("_E")
    }

    private fun fitIfNeeded() {
        if (!didFitBoundsOnce && renderCoordinatesForMapObjects(nodesSnapshot, routesSnapshot).isNotEmpty()) {
            didFitBoundsOnce = tryFitToObjects()
        }
    }

    private fun focusSelectionIfNeeded(): Boolean {
        val mapRef = map ?: return false
        if (mapRef.style == null) return false

        val mv = mapViewRef ?: return false
        if (mv.width <= 0 || mv.height <= 0) {
            return false
        }

        selectedNodeSnapshot?.let { node ->
            val point = renderCoordinateForNode(node) ?: return@let
            val key = "node:${node.id}:${point.latitude}:${point.longitude}"
            if (lastFocusedSelectionKey != key) {
                mapRef.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 18.0)
                )
                lastFocusedSelectionKey = key
                didFitBoundsOnce = true
            }
            return true
        }

        selectedRouteSnapshot?.let { route ->
            val routePoints = renderCoordinatesForRoutePoints(route)
            if (routePoints.isNotEmpty()) {
                val key = "route:${route.id}:${route.code}:${routePoints.hashCode()}"
                if (lastFocusedSelectionKey != key) {
                    if (routePoints.size == 1) {
                        val point = routePoints.single()
                        mapRef.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 18.0)
                        )
                    } else {
                        val bounds = LatLngBounds.Builder().apply {
                            routePoints.forEach { include(LatLng(it.latitude, it.longitude)) }
                        }.build()
                        mapRef.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                    }
                    lastFocusedSelectionKey = key
                    didFitBoundsOnce = true
                }
                return true
            }

            val nodesByCode = nodesSnapshot.associateBy { it.code.trim().uppercase() }
            val startNode = nodesByCode[route.startNodeCode.trim().uppercase()]
            val endNode = nodesByCode[route.endNodeCode.trim().uppercase()]
            val startPoint = startNode?.let(::renderCoordinateForNode)
            val endPoint = endNode?.let(::renderCoordinateForNode)
            if (startNode != null && endNode != null && startPoint != null && endPoint != null) {
                val midLat = (startPoint.latitude + endPoint.latitude) / 2.0
                val midLng = (startPoint.longitude + endPoint.longitude) / 2.0
                val key = "route:${route.id}:${route.code}:${startNode.id}:${endNode.id}"
                if (lastFocusedSelectionKey != key) {
                    mapRef.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(midLat, midLng), 18.0)
                    )
                    lastFocusedSelectionKey = key
                    didFitBoundsOnce = true
                }
                return true
            }
        }

        lastFocusedSelectionKey = null
        return false
    }

    private fun bindMap(loadedMap: MapLibreMap, styleJson: String) {
        val styleBuilder = if (styleJson.startsWith("asset://")) {
            Style.Builder().fromUri(styleJson)
        } else {
            Style.Builder().fromJson(styleJson)
        }

        val currentStyle = loadedMap.style
        if (currentStyle != null && map == loadedMap) {
            // Already initialized with a style, just update data
            updateMapData()
            applyLayerVisibility()
            updateMeasureGeoJson()
            return
        }

        map = loadedMap
        styleEpoch++

        // Hide MapLibre logo and attribution
        loadedMap.uiSettings.isLogoEnabled = false
        loadedMap.uiSettings.isAttributionEnabled = false
        
        // Only set default camera if we haven't moved yet
        if (!didFitBoundsOnce) {
            loadedMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(13.8, 109.8))
                .zoom(12.0)
                .build()
        }

        loadedMap.setStyle(styleBuilder) {
            resetRuntimeState(keepSnapshots = true)
            ensureClickListener(loadedMap)
            updateMapData()
            applyLayerVisibility()
            updateMeasureGeoJson()
            if (!focusSelectionIfNeeded()) {
                fitIfNeeded()
            }
        }
        ensureClickListener(loadedMap)
    }

    private fun resetRuntimeState(keepSnapshots: Boolean) {
        clearPendingMapUpdate()
        clearPendingDetailUpgrade()
        lastLayerSignature = 0
        lastMeasureSignature = 0
        lastRenderKey = null
        lastFocusedSelectionKey = null
        didFitBoundsOnce = false
        clickListenerAttached = false
        preferLightweightRender = true
        missingSourceWarnings.clear()
        if (!keepSnapshots) {
            nodesSnapshot = emptyList()
            routesSnapshot = emptyList()
        }
    }

    private fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        return activityManager.isLowRamDevice || activityManager.memoryClass <= 192
    }

    private fun warnMissingSourceOnce(sourceId: String) {
        if (missingSourceWarnings.add(sourceId)) {
            Log.w(TAG, "Missing source: $sourceId")
        }
    }

    private fun logSnapshot(stage: String, nodes: List<GisNode>, routes: List<GisRoute>) {
        val summary = summarizeCoordinates(nodes)
        Log.d(
            TAG,
            "$stage nodes=${nodes.size} routes=${routes.size} nodesValid=${summary.validCount} invalidNodes=${summary.invalidCount} " +
                "latRange=${summary.latRangeText} lonRange=${summary.lonRangeText}"
        )
    }

    private data class RenderPayload(
        val nodeGeoJson: String,
        val routeGeoJson: String,
        val nodeFeaturesBuilt: Int,
        val routeFeaturesBuilt: Int,
        val skippedRoutes: Int,
        val skippedInvalidPointRoutes: Int,
        val newRenderKey: MapRenderKey,
        val scheduleDetailUpgrade: Boolean,
        val requestedRenderKey: MapRenderKey,
        val committedTier: MapRenderTier
    )
}

internal fun isRenderableNode(node: GisNode): Boolean = renderCoordinateForNode(node) != null

internal fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
    return latitude in -90.0..90.0 && longitude in -180.0..180.0
}

internal data class RenderCoordinate(
    val latitude: Double,
    val longitude: Double,
    val swapped: Boolean
)

internal fun normalizeCoordinatePair(latitude: Double, longitude: Double): RenderCoordinate? {
    if (isValidCoordinate(latitude, longitude)) {
        return RenderCoordinate(latitude, longitude, swapped = false)
    }
    if (isValidCoordinate(longitude, latitude)) {
        return RenderCoordinate(longitude, latitude, swapped = true)
    }
    return null
}

internal fun renderCoordinateForNode(node: GisNode): RenderCoordinate? =
    normalizeCoordinatePair(node.latitude, node.longitude)

internal fun renderCoordinatesForRoutePoints(route: GisRoute): List<RenderCoordinate> =
    route.points.mapNotNull { point -> normalizeCoordinatePair(point.first, point.second) }

internal fun renderCoordinatesForMapObjects(
    nodes: List<GisNode>,
    routes: List<GisRoute>
): List<RenderCoordinate> {
    if (nodes.isEmpty() && routes.isEmpty()) return emptyList()
    return buildList {
        nodes.mapNotNullTo(this, ::renderCoordinateForNode)
        routes.forEach { route -> addAll(renderCoordinatesForRoutePoints(route)) }
    }
}

internal data class CoordinateSummary(
    val validCount: Int,
    val invalidCount: Int,
    val latRangeText: String,
    val lonRangeText: String
)

internal fun summarizeCoordinates(nodes: List<GisNode>): CoordinateSummary {
    val renderableNodes = nodes.mapNotNull(::renderCoordinateForNode)
    val invalidCount = nodes.size - renderableNodes.size
    if (renderableNodes.isEmpty()) {
        return CoordinateSummary(
            validCount = 0,
            invalidCount = invalidCount,
            latRangeText = "n/a",
            lonRangeText = "n/a"
        )
    }
    val minLat = renderableNodes.minOf { it.latitude }
    val maxLat = renderableNodes.maxOf { it.latitude }
    val minLon = renderableNodes.minOf { it.longitude }
    val maxLon = renderableNodes.maxOf { it.longitude }
    return CoordinateSummary(
        validCount = renderableNodes.size,
        invalidCount = invalidCount,
        latRangeText = "%.5f..%.5f".format(minLat, maxLat),
        lonRangeText = "%.5f..%.5f".format(minLon, maxLon)
    )
}
