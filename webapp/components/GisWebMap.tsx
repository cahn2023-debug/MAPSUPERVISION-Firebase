"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import maplibregl, {
  type GeoJSONSource,
  type LngLatBoundsLike,
  type Map as MapLibreMap,
  type MapGeoJSONFeature,
  type StyleSpecification
} from "maplibre-gl";
import type { GeoJsonProperties, Geometry } from "geojson";

type MapLayerType = "STREET" | "SATELLITE" | "SATELLITE_LABELS" | "DARK";
type LabelField = "CODE" | "CONTRACTOR" | "COORDINATE";

type WebMapPoint = {
  lat: number;
  lon: number;
};

type WebMapNode = {
  id: string;
  code: string;
  contractor: string;
  signalStatus: string;
  mapNumberLabel: string;
  point: WebMapPoint;
  raw: Record<string, unknown>;
};

type WebMapRoute = {
  id: string;
  code: string;
  contractor: string;
  startNodeCode: string;
  endNodeCode: string;
  points: WebMapPoint[];
  raw: Record<string, unknown>;
};

type SelectedObject =
  | { kind: "node"; item: WebMapNode }
  | { kind: "route"; item: WebMapRoute }
  | null;

type FeatureCollection = GeoJSON.FeatureCollection<Geometry, GeoJsonProperties>;

const NODES_SOURCE_ID = "nodes_source";
const ROUTES_SOURCE_ID = "routes_source";
const MEASURE_SOURCE_ID = "measure_source";
const NODES_LAYER_ID = "nodes";
const NODE_LABELS_LAYER_ID = "nodes_labels";
const ROUTES_LAYER_ID = "routes";
const ROUTES_HIT_LAYER_ID = "routes_hit";
const MEASURE_LAYER_ID = "measure_line";

const contractorPalette = ["#f97316", "#22c55e", "#06b6d4", "#a855f7", "#ef4444", "#f59e0b", "#3b82f6"];

const emptyFeatureCollection = (): FeatureCollection => ({
  type: "FeatureCollection",
  features: []
});

function text(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && String(value).trim()) return String(value);
  }
  return "";
}

function isValidCoordinate(lat: number, lon: number): boolean {
  return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && (lat !== 0 || lon !== 0);
}

function normalizeCoordinatePair(lat: number, lon: number): WebMapPoint | null {
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  if (isValidCoordinate(lat, lon)) return { lat, lon };
  if (isValidCoordinate(lon, lat)) return { lat: lon, lon: lat };
  return null;
}

function readCoordinate(row: Record<string, unknown>): WebMapPoint | null {
  const lat = Number(row.latitude ?? row.lat ?? row.first ?? row.latitudeDegrees);
  const lon = Number(row.longitude ?? row.lon ?? row.lng ?? row.second ?? row.longitudeDegrees);
  return normalizeCoordinatePair(lat, lon);
}

function parseRoutePoints(points: unknown): WebMapPoint[] {
  if (Array.isArray(points)) {
    return points
      .map((point) => {
        if (Array.isArray(point) && point.length >= 2) {
          const first = Number(point[0]);
          const second = Number(point[1]);
          return normalizeCoordinatePair(first, second);
        }
        if (!point || typeof point !== "object") return null;
        return readCoordinate(point as Record<string, unknown>);
      })
      .filter((point): point is WebMapPoint => Boolean(point));
  }

  if (typeof points !== "string" || !points.trim()) return [];
  return points
    .split(";")
    .map((chunk) => chunk.trim())
    .filter(Boolean)
    .map((chunk) => {
      const [lat, lon] = chunk.split(",").map(Number);
      return normalizeCoordinatePair(lat, lon);
    })
    .filter((point): point is WebMapPoint => Boolean(point));
}

function hashString(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index);
    hash |= 0;
  }
  return Math.abs(hash);
}

function colorForContractor(contractor: string): string {
  if (!contractor.trim()) return "#f97316";
  return contractorPalette[hashString(contractor) % contractorPalette.length];
}

function compactLabel(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  const firstNumber = trimmed.match(/\d+/)?.[0];
  if (firstNumber) return firstNumber.slice(0, 4);
  return trimmed.slice(0, 3).toUpperCase();
}

function formatNodeLabel(node: WebMapNode, labelField: LabelField): string {
  if (labelField === "CONTRACTOR") return compactLabel(node.contractor);
  if (labelField === "COORDINATE") return compactLabel(`${node.point.lat.toFixed(5)},${node.point.lon.toFixed(5)}`);
  return compactLabel(node.mapNumberLabel || node.code);
}

function formatDistance(meters: number): string {
  if (meters >= 1000) return `${(meters / 1000).toFixed(2)} km`;
  return `${Math.round(meters)} m`;
}

function haversineMeters(a: WebMapPoint, b: WebMapPoint): number {
  const earthRadius = 6_371_000;
  const lat1 = (a.lat * Math.PI) / 180;
  const lat2 = (b.lat * Math.PI) / 180;
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLon = ((b.lon - a.lon) * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * earthRadius * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function styleForLayer(layer: MapLayerType): StyleSpecification {
  const tileUrl =
    layer === "STREET"
      ? "https://mt1.google.com/vt/lyrs=m&hl=vi&x={x}&y={y}&z={z}"
      : layer === "SATELLITE"
        ? "https://mt1.google.com/vt/lyrs=s&hl=vi&x={x}&y={y}&z={z}"
        : layer === "SATELLITE_LABELS"
          ? "https://mt1.google.com/vt/lyrs=y&hl=vi&x={x}&y={y}&z={z}"
          : "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png";

  return {
    version: 8,
    name: layer,
    glyphs: "https://orangemug.github.io/font-glyphs/glyphs/{fontstack}/{range}.pbf",
    sources: {
      base_raster: {
        type: "raster",
        tiles: [tileUrl],
        tileSize: 256,
        minzoom: 0,
        maxzoom: 20
      },
      [NODES_SOURCE_ID]: { type: "geojson", data: emptyFeatureCollection() },
      [ROUTES_SOURCE_ID]: { type: "geojson", data: emptyFeatureCollection() },
      [MEASURE_SOURCE_ID]: { type: "geojson", data: emptyFeatureCollection() }
    },
    layers: [
      { id: "base", type: "raster", source: "base_raster" },
      {
        id: ROUTES_LAYER_ID,
        type: "line",
        source: ROUTES_SOURCE_ID,
        paint: {
          "line-color": ["case", ["boolean", ["get", "selected"], false], "#ef4444", "#1a73e8"],
          "line-width": ["case", ["boolean", ["get", "selected"], false], 6, 4],
          "line-opacity": 0.88
        }
      },
      {
        id: ROUTES_HIT_LAYER_ID,
        type: "line",
        source: ROUTES_SOURCE_ID,
        paint: {
          "line-color": "#000000",
          "line-width": 18,
          "line-opacity": 0
        }
      },
      {
        id: MEASURE_LAYER_ID,
        type: "line",
        source: MEASURE_SOURCE_ID,
        paint: {
          "line-color": "#ef4444",
          "line-width": 3,
          "line-dasharray": [2, 2]
        }
      },
      {
        id: NODES_LAYER_ID,
        type: "circle",
        source: NODES_SOURCE_ID,
        paint: {
          "circle-color": ["coalesce", ["get", "color"], "#f97316"],
          "circle-radius": ["case", ["boolean", ["get", "selected"], false], 13, ["coalesce", ["get", "signalRadius"], 10]],
          "circle-stroke-color": ["case", ["boolean", ["get", "selected"], false], "#facc15", ["coalesce", ["get", "signalStrokeColor"], "#ffffff"]],
          "circle-stroke-width": ["case", ["boolean", ["get", "selected"], false], 4, ["coalesce", ["get", "signalStrokeWidth"], 2.5]]
        }
      },
      {
        id: NODE_LABELS_LAYER_ID,
        type: "symbol",
        source: NODES_SOURCE_ID,
        layout: {
          "text-field": ["get", "label"],
          "text-font": ["Roboto Regular"],
          "text-size": 11,
          "text-offset": [0, 0],
          "text-anchor": "center",
          "text-allow-overlap": true,
          "text-ignore-placement": true
        },
        paint: {
          "text-color": "#ffffff",
          "text-halo-color": "#111827",
          "text-halo-width": 1
        }
      }
    ]
  };
}

function mapBounds(nodes: WebMapNode[], routes: WebMapRoute[]): LngLatBoundsLike | null {
  const points = [
    ...nodes.map((node) => node.point),
    ...routes.flatMap((route) => route.points)
  ];
  if (!points.length) return null;

  const bounds = new maplibregl.LngLatBounds();
  points.forEach((point) => bounds.extend([point.lon, point.lat]));
  return bounds;
}

function getFeatureCode(feature: MapGeoJSONFeature | undefined): string {
  const value = feature?.properties?.code;
  return typeof value === "string" ? value : "";
}

function hasLayer(map: MapLibreMap, layerId: string): boolean {
  return Boolean(map.getLayer(layerId));
}

function hasSource(map: MapLibreMap, sourceId: string): boolean {
  return Boolean(map.getSource(sourceId));
}

export function GisWebMap({
  nodes,
  routes
}: {
  nodes: Record<string, unknown>[];
  routes: Record<string, unknown>[];
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const nodesByCodeRef = useRef(new Map<string, WebMapNode>());
  const routesByCodeRef = useRef(new Map<string, WebMapRoute>());
  const measureEnabledRef = useRef(false);
  const lastAutoFitSignatureRef = useRef("");
  const [baseMap, setBaseMap] = useState<MapLayerType>("STREET");
  const [showNodes, setShowNodes] = useState(true);
  const [showRoutes, setShowRoutes] = useState(true);
  const [showLabels, setShowLabels] = useState(true);
  const [labelField, setLabelField] = useState<LabelField>("CODE");
  const [colorByContractor, setColorByContractor] = useState(true);
  const [measureEnabled, setMeasureEnabled] = useState(false);
  const [measurePoints, setMeasurePoints] = useState<WebMapPoint[]>([]);
  const [selected, setSelected] = useState<SelectedObject>(null);
  const [styleLoaded, setStyleLoaded] = useState(0);

  const prepared = useMemo(() => {
    const preparedNodes = nodes
      .map((node) => {
        const point = readCoordinate(node);
        const code = text(node, "code", "nodeCode", "name");
        const id = String(node.id ?? code);
        if (!point || !id || !code) return null;
        return {
          id,
          code,
          contractor: text(node, "contractor", "contractorName"),
          signalStatus: text(node, "signalStatus"),
          mapNumberLabel: text(node, "mapNumberLabel", "mapNumber", "code", "nodeCode", "name"),
          point,
          raw: node
        } satisfies WebMapNode;
      })
      .filter((node): node is WebMapNode => Boolean(node));

    const nodeByCode = new Map(preparedNodes.map((node) => [node.code.trim().toUpperCase(), node]));
    const preparedRoutes = routes
      .map((route) => {
        const code = text(route, "code", "routeCode", "name");
        const explicitPoints = parseRoutePoints(route.points ?? route.pathPoints ?? route.coordinates);
        const startNodeCode = text(route, "startNodeCode", "fromNodeCode", "startCode");
        const endNodeCode = text(route, "endNodeCode", "toNodeCode", "endCode");
        const fallbackPoints =
          explicitPoints.length > 1
            ? explicitPoints
            : [
                nodeByCode.get(startNodeCode.trim().toUpperCase())?.point,
                nodeByCode.get(endNodeCode.trim().toUpperCase())?.point
              ].filter((point): point is WebMapPoint => Boolean(point));
        const id = String(route.id ?? code);
        if (!id || !code || fallbackPoints.length < 2) return null;
        return {
          id,
          code,
          contractor: text(route, "contractor", "contractorName"),
          startNodeCode,
          endNodeCode,
          points: fallbackPoints,
          raw: route
        } satisfies WebMapRoute;
      })
      .filter((route): route is WebMapRoute => Boolean(route));

    return { nodes: preparedNodes, routes: preparedRoutes };
  }, [nodes, routes]);

  const nodeGeoJson = useMemo<FeatureCollection>(() => ({
    type: "FeatureCollection",
    features: prepared.nodes.map((node) => ({
      type: "Feature",
      geometry: {
        type: "Point",
        coordinates: [node.point.lon, node.point.lat]
      },
      properties: {
        code: node.code,
        contractor: node.contractor,
        label: showLabels ? formatNodeLabel(node, labelField) : "",
        color: colorByContractor ? colorForContractor(node.contractor) : "#f97316",
        signalStatus: node.signalStatus,
        signalStrokeColor: node.signalStatus === "HAS_SIGNAL" ? "#22c55e" : node.signalStatus === "NO_SIGNAL" ? "#ef4444" : "#ffffff",
        signalStrokeWidth: node.signalStatus === "HAS_SIGNAL" || node.signalStatus === "NO_SIGNAL" ? 4 : 2,
        signalRadius: node.signalStatus === "HAS_SIGNAL" ? 11.5 : node.signalStatus === "NO_SIGNAL" ? 11 : 10,
        selected: selected?.kind === "node" && selected.item.id === node.id
      }
    }))
  }), [colorByContractor, labelField, prepared.nodes, selected, showLabels]);

  const routeGeoJson = useMemo<FeatureCollection>(() => ({
    type: "FeatureCollection",
    features: prepared.routes.map((route) => ({
      type: "Feature",
      geometry: {
        type: "LineString",
        coordinates: route.points.map((point) => [point.lon, point.lat])
      },
      properties: {
        code: route.code,
        contractor: route.contractor,
        selected: selected?.kind === "route" && selected.item.id === route.id
      }
    }))
  }), [prepared.routes, selected]);

  const measureGeoJson = useMemo<FeatureCollection>(() => {
    if (measurePoints.length !== 2) return emptyFeatureCollection();
    return {
      type: "FeatureCollection",
      features: [
        {
          type: "Feature",
          geometry: {
            type: "LineString",
            coordinates: measurePoints.map((point) => [point.lon, point.lat])
          },
          properties: {
            distance_m: haversineMeters(measurePoints[0], measurePoints[1])
          }
        }
      ]
    };
  }, [measurePoints]);
  const nodeGeoJsonRef = useRef(nodeGeoJson);
  const routeGeoJsonRef = useRef(routeGeoJson);
  const measureGeoJsonRef = useRef(measureGeoJson);
  const visibilityRef = useRef({
    measureEnabled,
    showLabels,
    showNodes,
    showRoutes
  });

  nodeGeoJsonRef.current = nodeGeoJson;
  routeGeoJsonRef.current = routeGeoJson;
  measureGeoJsonRef.current = measureGeoJson;
  visibilityRef.current = {
    measureEnabled,
    showLabels,
    showNodes,
    showRoutes
  };

  const hasRenderableData = prepared.nodes.length > 0 || prepared.routes.length > 0;
  const distanceText = measurePoints.length === 2 ? formatDistance(haversineMeters(measurePoints[0], measurePoints[1])) : "";

  useEffect(() => {
    nodesByCodeRef.current = new Map(prepared.nodes.map((node) => [node.code, node]));
    routesByCodeRef.current = new Map(prepared.routes.map((route) => [route.code, route]));
  }, [prepared.nodes, prepared.routes]);

  useEffect(() => {
    measureEnabledRef.current = measureEnabled;
    if (!measureEnabled) setMeasurePoints([]);
  }, [measureEnabled]);

  function syncMapPresentation() {
    const map = mapRef.current;
    if (!map || !map.isStyleLoaded()) return;

    if (hasSource(map, NODES_SOURCE_ID)) {
      (map.getSource(NODES_SOURCE_ID) as GeoJSONSource).setData(nodeGeoJsonRef.current);
    }
    if (hasSource(map, ROUTES_SOURCE_ID)) {
      (map.getSource(ROUTES_SOURCE_ID) as GeoJSONSource).setData(routeGeoJsonRef.current);
    }
    if (hasSource(map, MEASURE_SOURCE_ID)) {
      (map.getSource(MEASURE_SOURCE_ID) as GeoJSONSource).setData(measureGeoJsonRef.current);
    }

    const { showNodes, showLabels, showRoutes, measureEnabled } = visibilityRef.current;
    if (hasLayer(map, NODES_LAYER_ID)) {
      map.setLayoutProperty(NODES_LAYER_ID, "visibility", showNodes ? "visible" : "none");
    }
    if (hasLayer(map, NODE_LABELS_LAYER_ID)) {
      map.setLayoutProperty(NODE_LABELS_LAYER_ID, "visibility", showNodes && showLabels ? "visible" : "none");
    }
    if (hasLayer(map, ROUTES_LAYER_ID)) {
      map.setLayoutProperty(ROUTES_LAYER_ID, "visibility", showRoutes ? "visible" : "none");
    }
    if (hasLayer(map, ROUTES_HIT_LAYER_ID)) {
      map.setLayoutProperty(ROUTES_HIT_LAYER_ID, "visibility", showRoutes && !measureEnabled ? "visible" : "none");
    }
    if (hasLayer(map, MEASURE_LAYER_ID)) {
      map.setLayoutProperty(MEASURE_LAYER_ID, "visibility", measureEnabled ? "visible" : "none");
    }
  }

  useEffect(() => {
    if (!hasRenderableData) {
      mapRef.current?.remove();
      mapRef.current = null;
      return;
    }
    if (!containerRef.current || mapRef.current) return;

    const map = new maplibregl.Map({
      container: containerRef.current,
      style: styleForLayer(baseMap),
      center: [105.8342, 21.0278],
      zoom: 12,
      attributionControl: false
    });
    mapRef.current = map;
    map.addControl(new maplibregl.AttributionControl({ compact: true }), "bottom-left");

    const handleStyleReady = () => {
      if (!map.isStyleLoaded()) return;
      syncMapPresentation();
      setStyleLoaded((value) => value + 1);
    };

    map.on("load", handleStyleReady);
    map.on("styledata", handleStyleReady);
    map.on("click", (event) => {
      if (!map.isStyleLoaded()) return;

      if (measureEnabledRef.current) {
        const nextPoint = { lat: event.lngLat.lat, lon: event.lngLat.lng };
        setMeasurePoints((current) => (current.length >= 2 ? [nextPoint] : [...current, nextPoint]));
        setSelected(null);
        return;
      }

      const point = event.point;
      const nodeFeatures = hasLayer(map, NODES_LAYER_ID)
        ? map.queryRenderedFeatures(
            [
              [point.x - 24, point.y - 24],
              [point.x + 24, point.y + 24]
            ],
            { layers: [NODES_LAYER_ID] }
          )
        : [];
      const nodeCode = getFeatureCode(nodeFeatures[0]);
      const node = nodesByCodeRef.current.get(nodeCode);
      if (node) {
        setSelected({ kind: "node", item: node });
        map.easeTo({ center: [node.point.lon, node.point.lat], zoom: Math.max(map.getZoom(), 18) });
        return;
      }

      const routeFeatures = hasLayer(map, ROUTES_HIT_LAYER_ID)
        ? map.queryRenderedFeatures(
            [
              [point.x - 18, point.y - 18],
              [point.x + 18, point.y + 18]
            ],
            { layers: [ROUTES_HIT_LAYER_ID] }
          )
        : [];
      const routeCode = getFeatureCode(routeFeatures[0]);
      const route = routesByCodeRef.current.get(routeCode);
      if (route) {
        setSelected({ kind: "route", item: route });
        fitRoute(map, route);
      }
    });

    return () => {
      map.off("load", handleStyleReady);
      map.off("styledata", handleStyleReady);
      map.remove();
      mapRef.current = null;
    };
  }, [hasRenderableData]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !hasRenderableData) return;
    if (map.getStyle()?.name === baseMap) return;
    map.setStyle(styleForLayer(baseMap));
  }, [baseMap, hasRenderableData]);

  useEffect(() => {
    syncMapPresentation();
  }, [measureGeoJson, nodeGeoJson, routeGeoJson, measureEnabled, showLabels, showNodes, showRoutes]);

  useEffect(() => {
    const map = mapRef.current;
    const bounds = mapBounds(prepared.nodes, prepared.routes);
    if (!map || !bounds || !map.isStyleLoaded()) return;

    const signature = `${prepared.nodes.length}:${prepared.routes.length}:${prepared.nodes[0]?.id ?? ""}:${prepared.routes[0]?.id ?? ""}`;
    if (lastAutoFitSignatureRef.current === signature) return;
    lastAutoFitSignatureRef.current = signature;
    map.fitBounds(bounds, { padding: 72, maxZoom: 18, duration: 0 });
  }, [prepared.nodes, prepared.routes, styleLoaded]);

  function fitAll() {
    const map = mapRef.current;
    const bounds = mapBounds(prepared.nodes, prepared.routes);
    if (!map || !bounds) return;
    map.fitBounds(bounds, { padding: 72, maxZoom: 18, duration: 650 });
  }

  function zoomIn() {
    mapRef.current?.zoomIn();
  }

  function zoomOut() {
    mapRef.current?.zoomOut();
  }

  if (!hasRenderableData) {
    return (
      <div className="web-map-empty empty-state">
        Thiết bị hiện địa chưa đồng bộ dữ liệu bản đồ có tọa độ GPS hợp lệ.
      </div>
    );
  }

  return (
    <div className="web-map-shell">
      <div className="web-map-toolbar" aria-label="Công cụ bản đồ">
        <button type="button" className="map-icon-button" onClick={zoomIn} aria-label="Phóng to">
          +
        </button>
        <button type="button" className="map-icon-button" onClick={zoomOut} aria-label="Thu nhỏ">
          -
        </button>
        <button type="button" className="tiny-button" onClick={fitAll}>
          Fit
        </button>
        <select value={baseMap} onChange={(event) => setBaseMap(event.target.value as MapLayerType)} aria-label="Nền bản đồ">
          <option value="STREET">Đường phố</option>
          <option value="SATELLITE">Vệ tinh</option>
          <option value="SATELLITE_LABELS">Vệ tinh + tên đường</option>
          <option value="DARK">Nền tối</option>
        </select>
        <label className="map-toggle">
          <input type="checkbox" checked={showNodes} onChange={(event) => setShowNodes(event.target.checked)} />
          <span>Node</span>
        </label>
        <label className="map-toggle">
          <input type="checkbox" checked={showRoutes} onChange={(event) => setShowRoutes(event.target.checked)} />
          <span>Route</span>
        </label>
        <label className="map-toggle">
          <input type="checkbox" checked={showLabels} onChange={(event) => setShowLabels(event.target.checked)} />
          <span>Nhãn</span>
        </label>
        <select value={labelField} disabled={!showLabels} onChange={(event) => setLabelField(event.target.value as LabelField)} aria-label="Kiểu nhãn">
          <option value="CODE">Mã</option>
          <option value="CONTRACTOR">Nhà thầu</option>
          <option value="COORDINATE">Tọa độ</option>
        </select>
        <label className="map-toggle">
          <input type="checkbox" checked={colorByContractor} onChange={(event) => setColorByContractor(event.target.checked)} />
          <span>Màu NT</span>
        </label>
        <button
          type="button"
          className={measureEnabled ? "tiny-button active" : "tiny-button"}
          onClick={() => setMeasureEnabled((current) => !current)}
        >
          Đo
        </button>
      </div>

      <div ref={containerRef} className={measureEnabled ? "web-map-canvas measuring" : "web-map-canvas"} />

      <div className="web-map-status">
        <strong>{prepared.nodes.length}</strong> node · <strong>{prepared.routes.length}</strong> tuyến
      </div>

      {measureEnabled ? (
        <div className="web-map-measure">
          {distanceText || "Chọn 2 điểm để đo khoảng cách"}
        </div>
      ) : null}

      {selected ? (
        <SelectedCard selected={selected} onClose={() => setSelected(null)} />
      ) : null}
    </div>
  );
}

function fitRoute(map: MapLibreMap, route: WebMapRoute) {
  const bounds = new maplibregl.LngLatBounds();
  route.points.forEach((point) => bounds.extend([point.lon, point.lat]));
  map.fitBounds(bounds, { padding: 84, maxZoom: 18, duration: 650 });
}

function SelectedCard({ selected, onClose }: { selected: SelectedObject; onClose: () => void }) {
  if (!selected) return null;
  const isNode = selected.kind === "node";
  const item = selected.item;
  return (
    <article className="web-map-selected">
      <div className="web-map-selected-heading">
        <div>
          <span>{isNode ? "Thông tin điểm nút" : "Thông tin tuyến"}</span>
          <strong>{item.code}</strong>
        </div>
        <button type="button" className="map-icon-button" onClick={onClose} aria-label="Đóng">
          x
        </button>
      </div>
      <dl>
        <div>
          <dt>Nhà thầu</dt>
          <dd>{item.contractor || "Chưa xác định"}</dd>
        </div>
        {isNode ? (
          <>
            <div>
              <dt>Tọa độ</dt>
              <dd>{`${selected.item.point.lat.toFixed(6)}, ${selected.item.point.lon.toFixed(6)}`}</dd>
            </div>
            <div>
              <dt>Trạng thái tín hiệu</dt>
              <dd>{selected.item.signalStatus || "UNKNOWN"}</dd>
            </div>
          </>
        ) : (
          <>
            <div>
              <dt>Đầu tuyến</dt>
              <dd>{selected.item.startNodeCode || "Không rõ"}</dd>
            </div>
            <div>
              <dt>Cuối tuyến</dt>
              <dd>{selected.item.endNodeCode || "Không rõ"}</dd>
            </div>
          </>
        )}
      </dl>
    </article>
  );
}
