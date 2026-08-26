"use client";

import { useEffect, useMemo, useState, useCallback, useRef } from "react";
import dynamic from "next/dynamic";
import type { SelectedObject } from "@/components/GisWebMap";
import { imageSourceUrl } from "@/lib/google-drive-image";

type Row = Record<string, unknown>;

type PublicData = {
  project: Row;
  collections: Record<string, Row[]>;
  updatedAtEpochMs: number;
};

type TabKey = "map" | "photos" | "daily_log" | "progress" | "gis";
type ThemeMode = "dark" | "light";
type PhotoViewMode = "nodes" | "tags" | "grid";

const GisWebMap = dynamic(
  () => import("@/components/GisWebMap").then((mod) => mod.GisWebMap),
  {
    ssr: false,
    loading: () => (
      <div className="public-map-loading">
        <div className="public-spinner" />
        <p>Đang khởi tạo bản đồ GIS vệ tinh...</p>
      </div>
    )
  }
);

function display(value: unknown): string {
  if (value == null || value === "") return "—";
  if (typeof value === "object") {
    if ("seconds" in (value as { seconds?: unknown })) {
      const date = new Date(Number((value as { seconds: number }).seconds) * 1000);
      return date.toLocaleString("vi-VN");
    }
    return JSON.stringify(value);
  }
  return String(value);
}

function formatDate(val: unknown): string {
  if (!val) return "—";
  try {
    if (typeof val === "number") {
      const d = new Date(val);
      if (!isNaN(d.getTime())) return d.toLocaleString("vi-VN");
    }
    if (typeof val === "string") {
      const d = new Date(val);
      if (!isNaN(d.getTime())) return d.toLocaleString("vi-VN");
      return val;
    }
    if (typeof val === "object" && "seconds" in (val as { seconds?: unknown })) {
      return new Date(Number((val as { seconds: number }).seconds) * 1000).toLocaleString("vi-VN");
    }
  } catch {
    // fallback
  }
  return String(val);
}

function getNumericCoordinate(val: unknown): number | null {
  if (typeof val === "number" && !isNaN(val)) return val;
  if (typeof val === "string") {
    const parsed = parseFloat(val);
    if (!isNaN(parsed)) return parsed;
  }
  return null;
}

function driveLinkForPhoto(photo: Row): string | undefined {
  const url = String(photo.remoteUrl || photo.url || "").trim();
  return url.startsWith("http://") || url.startsWith("https://") ? url : undefined;
}

function imageUrlForPhoto(photo: Row, width = 800): string | undefined {
  return imageSourceUrl(String(photo.remoteUrl || photo.url || ""), width);
}

function extractPhotoTags(photo: Row): string[] {
  const tagsSet = new Set<string>();
  const csv = String(photo.tagCodesCsv || "").trim();
  if (csv) {
    csv.split(",")
      .map((s) => s.trim())
      .filter(Boolean)
      .forEach((t) => tagsSet.add(t));
  }
  const statusTag = String(photo.statusTag || "").trim();
  if (statusTag) {
    tagsSet.add(statusTag);
  }
  const categoryTag = String(photo.category || photo.tag || "").trim();
  if (categoryTag) {
    tagsSet.add(categoryTag);
  }
  return Array.from(tagsSet);
}

export type PhotoNodeGroup = {
  nodeKey: string;
  nodeName: string;
  nodeCode: string;
  contractor?: string;
  hasTags: boolean;
  tags: string[];
  photosByTag: Record<string, Row[]>;
  untaggedPhotos: Row[];
  allPhotos: Row[];
};

function groupPhotosByNodeAndTag(
  photos: Row[],
  gisNodes: Row[],
  searchKeyword: string,
  selectedTagFilter: string,
  contractorFilter: string
): PhotoNodeGroup[] {
  const nodeMap = new Map<string, Row>();
  for (const node of gisNodes) {
    const id = String(node.id || "").trim();
    const code = String(node.code || node.nodeCode || "").trim();
    const name = String(node.name || "").trim();
    if (id) nodeMap.set(id, node);
    if (code) nodeMap.set(code, node);
    if (name) nodeMap.set(name, node);
  }

  const groupsByKey = new Map<string, {
    nodeKey: string;
    nodeName: string;
    nodeCode: string;
    contractor?: string;
    photos: Row[];
  }>();

  for (const photo of photos) {
    const photoId = String(photo.id || "");
    if (!photoId && !photo.capturedAtEpochMs && !photo.remoteUrl) continue;

    const matchedNodeId = String(photo.matchedNodeId || "").trim();
    const objectCode = String(photo.objectCode || photo.nodeCode || "").trim();

    const matchedNode = (matchedNodeId && nodeMap.get(matchedNodeId)) ||
                        (objectCode && nodeMap.get(objectCode));

    const nodeCode = matchedNode ? String(matchedNode.code || matchedNode.nodeCode || objectCode) : (objectCode || "UNASSIGNED");
    const nodeName = matchedNode ? String(matchedNode.name || nodeCode) : (objectCode ? `Vị trí ${objectCode}` : "Ảnh chưa phân loại Node");
    const contractor = matchedNode ? String(matchedNode.contractor || matchedNode.contractorName || "") : String(photo.contractor || "");
    const nodeKey = nodeCode || matchedNodeId || "UNASSIGNED";

    if (!groupsByKey.has(nodeKey)) {
      groupsByKey.set(nodeKey, {
        nodeKey,
        nodeName,
        nodeCode,
        contractor: contractor || undefined,
        photos: []
      });
    }
    groupsByKey.get(nodeKey)!.photos.push(photo);
  }

  const result: PhotoNodeGroup[] = [];

  for (const group of groupsByKey.values()) {
    if (contractorFilter) {
      const c = (group.contractor || "").toLowerCase();
      if (!c.includes(contractorFilter.toLowerCase())) {
        continue;
      }
    }

    group.photos.sort((a, b) => Number(b.capturedAtEpochMs ?? b.updatedAtEpochMs ?? 0) - Number(a.capturedAtEpochMs ?? a.updatedAtEpochMs ?? 0));

    const tagSet = new Set<string>();
    const photosByTag: Record<string, Row[]> = {};
    const untaggedPhotos: Row[] = [];

    for (const photo of group.photos) {
      const tags = extractPhotoTags(photo);
      if (tags.length === 0) {
        untaggedPhotos.push(photo);
      } else {
        for (const tag of tags) {
          tagSet.add(tag);
          if (!photosByTag[tag]) {
            photosByTag[tag] = [];
          }
          photosByTag[tag].push(photo);
        }
      }
    }

    const tags = Array.from(tagSet).sort((a, b) => a.localeCompare(b, "vi"));
    const hasTags = tags.length > 0;

    let filteredAllPhotos = group.photos;
    if (searchKeyword.trim()) {
      const q = searchKeyword.toLowerCase();
      filteredAllPhotos = filteredAllPhotos.filter((p) => {
        const oCode = String(p.objectCode || "").toLowerCase();
        const eng = String(p.engineer || "").toLowerCase();
        const note = String(p.captureNote || p.caption || "").toLowerCase();
        const nName = group.nodeName.toLowerCase();
        const nCode = group.nodeCode.toLowerCase();
        const pTags = extractPhotoTags(p).join(" ").toLowerCase();
        return oCode.includes(q) || eng.includes(q) || note.includes(q) || nName.includes(q) || nCode.includes(q) || pTags.includes(q);
      });
    }

    if (selectedTagFilter) {
      if (selectedTagFilter === "__UNTAGGED__") {
        filteredAllPhotos = filteredAllPhotos.filter((p) => extractPhotoTags(p).length === 0);
      } else {
        filteredAllPhotos = filteredAllPhotos.filter((p) => extractPhotoTags(p).includes(selectedTagFilter));
      }
    }

    if (filteredAllPhotos.length === 0 && (searchKeyword.trim() || selectedTagFilter)) {
      continue;
    }

    const filteredPhotosByTag: Record<string, Row[]> = {};
    const filteredUntagged: Row[] = [];

    for (const tag of tags) {
      if (selectedTagFilter && selectedTagFilter !== tag) continue;
      const list = (photosByTag[tag] || []).filter((p) => filteredAllPhotos.includes(p));
      if (list.length > 0 || !selectedTagFilter) {
        filteredPhotosByTag[tag] = list;
      }
    }

    const unList = untaggedPhotos.filter((p) => filteredAllPhotos.includes(p));
    if (unList.length > 0) {
      filteredUntagged.push(...unList);
    }

    const finalTags = selectedTagFilter && selectedTagFilter !== "__UNTAGGED__"
      ? (tags.includes(selectedTagFilter) ? [selectedTagFilter] : [])
      : tags;

    result.push({
      nodeKey: group.nodeKey,
      nodeName: group.nodeName,
      nodeCode: group.nodeCode,
      contractor: group.contractor,
      hasTags: hasTags && (!selectedTagFilter || selectedTagFilter !== "__UNTAGGED__"),
      tags: finalTags,
      photosByTag: filteredPhotosByTag,
      untaggedPhotos: filteredUntagged,
      allPhotos: filteredAllPhotos
    });
  }

  return result.sort((a, b) => a.nodeCode.localeCompare(b.nodeCode, "vi"));
}

function PublicPhotoThumbnail({
  photo,
  onClick,
  width = 600
}: {
  photo: Row;
  onClick: () => void;
  width?: number;
}) {
  const directUrl = imageUrlForPhoto(photo, width);
  const proxyUrl = `/api/public/269-2026/media/${encodeURIComponent(String(photo.id))}`;
  const [src, setSrc] = useState<string | undefined>(directUrl || proxyUrl);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setSrc(directUrl || proxyUrl);
    setFailed(false);
  }, [directUrl, proxyUrl]);

  const driveUrl = driveLinkForPhoto(photo);
  const tags = extractPhotoTags(photo);
  const isDone = photo.syncStatus === "DONE" || Boolean(driveUrl);
  const lat = getNumericCoordinate(photo.latitude);
  const lon = getNumericCoordinate(photo.longitude);

  return (
    <div className="photo-card-mini" onClick={onClick}>
      <div className="photo-card-thumb-wrap">
        {!failed && src ? (
          <img
            src={src}
            alt={String(photo.objectCode || "Ảnh thực địa")}
            loading="lazy"
            onError={() => {
              if (src !== proxyUrl) {
                setSrc(proxyUrl);
              } else {
                setFailed(true);
              }
            }}
          />
        ) : (
          <div className="public-photo-placeholder">
            <span>📷 Không thể tải ảnh</span>
          </div>
        )}
        <span className={`photo-card-sync-tag ${isDone ? "done" : "pending"}`}>
          {isDone ? "SYNCED" : "PENDING"}
        </span>
      </div>
      <div className="photo-card-info">
        {tags.length > 0 && (
          <div className="photo-card-tags-row">
            {tags.map((t) => (
              <span key={t} className="photo-badge-tag">
                🏷️ {t}
              </span>
            ))}
          </div>
        )}
        <div className="photo-card-engineer">
          👷 {String(photo.engineer || "Chưa rõ kỹ sư")}
        </div>
        <div className="photo-card-time">
          🕒 {formatDate(photo.capturedAtEpochMs ?? photo.updatedAtEpochMs ?? photo.timestamp ?? photo.createdAt)}
        </div>
        {lat && lon && (
          <div className="public-coord-tag" style={{ fontSize: "10px", color: "var(--muted)", marginTop: "2px" }}>
            📍 {lat.toFixed(5)}, {lon.toFixed(5)}
          </div>
        )}
      </div>
    </div>
  );
}

export default function PublicProjectPage() {
  const [data, setData] = useState<PublicData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState<TabKey>("map");
  const [theme, setTheme] = useState<ThemeMode>("dark");

  // Filters & Selection
  const [searchQuery, setSearchQuery] = useState("");
  const [filterContractor, setFilterContractor] = useState("");
  const [filterWork, setFilterWork] = useState("");
  const [selected, setSelected] = useState<SelectedObject>(null);

  // Photo tab specific state
  const [photoSearchKeyword, setPhotoSearchKeyword] = useState("");
  const [selectedTagFilter, setSelectedTagFilter] = useState("");
  const [photoViewMode, setPhotoViewMode] = useState<PhotoViewMode>("nodes");
  const [collapsedNodes, setCollapsedNodes] = useState<Record<string, boolean>>({});

  // Lightbox State
  const [activeLightboxPhoto, setActiveLightboxPhoto] = useState<Row | null>(null);
  const [activeLightboxPlaylist, setActiveLightboxPlaylist] = useState<Row[]>([]);
  const [lightboxZoom, setLightboxZoom] = useState(1);
  const lightboxRef = useRef<HTMLDivElement | null>(null);

  // Initialize theme
  useEffect(() => {
    if (typeof window !== "undefined") {
      const savedTheme = localStorage.getItem("mapsupervision_public_theme") as ThemeMode | null;
      const initial = savedTheme || "dark";
      setTheme(initial);
      document.documentElement.dataset.theme = initial;
    }
  }, []);

  const toggleTheme = () => {
    const nextTheme: ThemeMode = theme === "dark" ? "light" : "dark";
    setTheme(nextTheme);
    if (typeof window !== "undefined") {
      document.documentElement.dataset.theme = nextTheme;
      localStorage.setItem("mapsupervision_public_theme", nextTheme);
    }
  };

  // Data fetching
  const loadData = useCallback(async (isManual = false) => {
    if (isManual) setRefreshing(true);
    try {
      const response = await fetch("/api/public/269-2026", { cache: "no-store" });
      const body = await response.json();
      if (!response.ok) throw new Error(body.error || "Không thể tải dữ liệu dự án.");
      setData(body);
      setError("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu dự án.");
    } finally {
      setLoading(false);
      if (isManual) setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData(false);
    const timer = window.setInterval(() => {
      void loadData(false);
    }, 30_000);
    return () => window.clearInterval(timer);
  }, [loadData]);

  // Extract collections safely
  const nodes = useMemo(() => data?.collections.gis_node ?? [], [data]);
  const routes = useMemo(() => data?.collections.gis_route ?? [], [data]);
  const photos = useMemo(() => data?.collections.site_photos ?? [], [data]);
  const dailyLogs = useMemo(() => data?.collections.daily_log ?? [], [data]);
  const tasks = useMemo(() => data?.collections.task ?? [], [data]);
  const volumes = useMemo(() => data?.collections.work_volume_progress ?? [], [data]);
  const materialDecs = useMemo(() => data?.collections.material_declaration ?? [], [data]);
  const materialHandovers = useMemo(() => data?.collections.material_handover ?? [], [data]);

  // Options for filters
  const contractorOptions = useMemo(() => {
    const set = new Set<string>();
    routes.forEach((r) => {
      const c = String(r.contractor ?? "").trim();
      if (c) set.add(c);
    });
    nodes.forEach((n) => {
      const c = String(n.contractor ?? "").trim();
      if (c) set.add(c);
    });
    dailyLogs.forEach((l) => {
      const c = String(l.contractor ?? "").trim();
      if (c) set.add(c);
    });
    return Array.from(set).sort();
  }, [routes, nodes, dailyLogs]);

  // Photo grouping and tags
  const allAvailableTags = useMemo(() => {
    const set = new Set<string>();
    for (const p of photos) {
      extractPhotoTags(p).forEach((t) => set.add(t));
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b, "vi"));
  }, [photos]);

  const photoNodeGroups = useMemo(() => {
    return groupPhotosByNodeAndTag(
      photos,
      nodes,
      photoSearchKeyword,
      selectedTagFilter,
      filterContractor
    );
  }, [photos, nodes, photoSearchKeyword, selectedTagFilter, filterContractor]);

  const allFilteredPhotosFlat = useMemo(() => {
    const list: Row[] = [];
    photoNodeGroups.forEach((g) => {
      list.push(...g.allPhotos);
    });
    return list;
  }, [photoNodeGroups]);

  // Tag groups (when grouped by tag)
  const photosGroupedByTag = useMemo(() => {
    const map = new Map<string, Row[]>();
    const untagged: Row[] = [];

    for (const p of allFilteredPhotosFlat) {
      const tags = extractPhotoTags(p);
      if (tags.length === 0) {
        untagged.push(p);
      } else {
        tags.forEach((t) => {
          if (!map.has(t)) map.set(t, []);
          map.get(t)!.push(p);
        });
      }
    }

    return {
      tagged: Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b, "vi")),
      untagged
    };
  }, [allFilteredPhotosFlat]);

  const totalPhotosCount = photos.length;
  const syncedPhotosCount = useMemo(() => {
    return photos.filter((p) => String(p.syncStatus || "") === "DONE" || Boolean(p.remoteUrl)).length;
  }, [photos]);

  // Filtered daily logs
  const filteredDailyLogs = useMemo(() => {
    return dailyLogs.filter((log) => {
      if (filterContractor) {
        const c = String(log.contractor ?? "").toLowerCase();
        if (!c.includes(filterContractor.toLowerCase())) return false;
      }
      return true;
    });
  }, [dailyLogs, filterContractor]);

  // Selected GIS item details
  const selectedObjectDetail = useMemo(() => {
    if (!selected) return null;
    if (selected.kind === "node") {
      const node = nodes.find((n) => String(n.code ?? n.name ?? n.id) === selected.code);
      const nodePhotos = photos.filter((p) => {
        const code = String(p.objectCode ?? p.nodeId ?? p.matchedNodeId ?? "");
        return code === selected.code || (node && (code === String(node.id) || code === String(node.code)));
      });
      return { kind: "node" as const, item: node, photos: nodePhotos };
    } else {
      const route = routes.find((r) => String(r.code ?? r.name ?? r.id) === selected.code);
      const routePhotos = photos.filter((p) => {
        const code = String(p.objectCode ?? p.routeId ?? "");
        return code === selected.code || (route && (code === String(route.id) || code === String(route.code)));
      });
      return { kind: "route" as const, item: route, photos: routePhotos };
    }
  }, [selected, nodes, routes, photos]);

  // Lightbox handlers
  const openLightbox = (photo: Row, playlist: Row[]) => {
    setActiveLightboxPhoto(photo);
    setActiveLightboxPlaylist(playlist.length > 0 ? playlist : [photo]);
    setLightboxZoom(1);
  };

  const closeLightbox = () => {
    setActiveLightboxPhoto(null);
    setActiveLightboxPlaylist([]);
    setLightboxZoom(1);
  };

  const currentLightboxIndex = useMemo(() => {
    if (!activeLightboxPhoto) return -1;
    return activeLightboxPlaylist.findIndex((p) => String(p.id) === String(activeLightboxPhoto.id));
  }, [activeLightboxPhoto, activeLightboxPlaylist]);

  const hasPrevPhoto = currentLightboxIndex > 0;
  const hasNextPhoto = currentLightboxIndex >= 0 && currentLightboxIndex < activeLightboxPlaylist.length - 1;

  const prevPhoto = () => {
    if (hasPrevPhoto) {
      setActiveLightboxPhoto(activeLightboxPlaylist[currentLightboxIndex - 1]);
      setLightboxZoom(1);
    }
  };

  const nextPhoto = () => {
    if (hasNextPhoto) {
      setActiveLightboxPhoto(activeLightboxPlaylist[currentLightboxIndex + 1]);
      setLightboxZoom(1);
    }
  };

  // Keyboard navigation for Lightbox
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!activeLightboxPhoto) return;
      if (e.key === "Escape") closeLightbox();
      if (e.key === "ArrowLeft" && hasPrevPhoto) prevPhoto();
      if (e.key === "ArrowRight" && hasNextPhoto) nextPhoto();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [activeLightboxPhoto, hasPrevPhoto, hasNextPhoto]);

  // Calculations for summary cards
  const totalRouteLengthKm = useMemo(() => {
    const meters = routes.reduce((acc, r) => {
      const len = parseFloat(String(r.length ?? r.totalLengthMeters ?? 0));
      return acc + (isNaN(len) ? 0 : len);
    }, 0);
    return (meters / 1000).toFixed(2);
  }, [routes]);

  if (loading) {
    return (
      <div className="public-portal-loading-screen">
        <div className="public-portal-loading-card">
          <div className="public-spinner large" />
          <h2>Đang tải dữ liệu dự án 269 - 2026...</h2>
          <p className="muted">Hệ thống đang kết nối dữ liệu giám sát thời gian thực từ Cloud Firestore.</p>
        </div>
      </div>
    );
  }

  if (error && !data) {
    return (
      <div className="public-portal-loading-screen">
        <div className="public-portal-error-card">
          <div className="error-icon" style={{ fontSize: "40px" }}>⚠️</div>
          <h2>Không thể tải thông tin dự án</h2>
          <p className="error">{error}</p>
          <button className="public-btn primary" onClick={() => void loadData(true)}>
            Thử tải lại
          </button>
        </div>
      </div>
    );
  }

  const projectName = display(data?.project?.name ?? "Dự án 269 - 2026");
  const projectCode = display(data?.project?.code ?? "269-2026");
  const lastUpdated = formatDate(data?.updatedAtEpochMs);

  return (
    <div className="public-portal-root">
      {/* HEADER SECTION */}
      <header className="public-portal-header">
        <div className="public-header-top">
          <div className="public-brand-area">
            <div className="public-badge-group">
              <span className="public-badge live">TRỰC TUYẾN</span>
              <span className="public-badge code">{projectCode}</span>
            </div>
            <h1 className="public-project-title">{projectName}</h1>
            <p className="public-subtitle">
              Cổng thông tin Giám sát Hiện trường & Bản đồ GIS Công trình
            </p>
          </div>

          <div className="public-header-actions">
            <span className="public-update-badge" title="Thời gian dữ liệu mới nhất được cập nhật">
              🕒 Cập nhật: <strong>{lastUpdated}</strong>
            </span>

            <button
              className={`public-btn ${refreshing ? "spinning" : ""}`}
              onClick={() => void loadData(true)}
              disabled={refreshing}
              title="Làm mới dữ liệu từ Firestore"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
              </svg>
              <span>{refreshing ? "Đang tải..." : "Làm mới"}</span>
            </button>

            <button
              className="public-btn theme-btn"
              onClick={toggleTheme}
              title={theme === "dark" ? "Chuyển sang Giao diện Sáng" : "Chuyển sang Giao diện Tối"}
            >
              {theme === "dark" ? "☀️ Sáng" : "🌙 Tối"}
            </button>
          </div>
        </div>

        {/* KPI METRICS OVERVIEW CARDS */}
        <div className="public-kpi-grid">
          <div className="public-kpi-card">
            <span className="public-kpi-label">Điểm đo GIS (Nodes)</span>
            <strong className="public-kpi-value accent">{nodes.length}</strong>
            <span className="public-kpi-sub">Vị trí trạm / cọc mốc</span>
          </div>

          <div className="public-kpi-card">
            <span className="public-kpi-label">Tuyến cáp GIS</span>
            <strong className="public-kpi-value">{routes.length}</strong>
            <span className="public-kpi-sub">Tổng: {totalRouteLengthKm} km</span>
          </div>

          <div className="public-kpi-card">
            <span className="public-kpi-label">Ảnh thực địa</span>
            <strong className="public-kpi-value">{photos.length}</strong>
            <span className="public-kpi-sub">{syncedPhotosCount} ảnh đã sync Drive</span>
          </div>

          <div className="public-kpi-card">
            <span className="public-kpi-label">Nhật ký giám sát</span>
            <strong className="public-kpi-value">{dailyLogs.length}</strong>
            <span className="public-kpi-sub">Bản ghi thi công</span>
          </div>

          <div className="public-kpi-card">
            <span className="public-kpi-label">Công việc giám sát</span>
            <strong className="public-kpi-value">{tasks.length}</strong>
            <span className="public-kpi-sub">Hạng mục nhiệm vụ</span>
          </div>

          <div className="public-kpi-card">
            <span className="public-kpi-label">Hạng mục khối lượng</span>
            <strong className="public-kpi-value">{volumes.length}</strong>
            <span className="public-kpi-sub">Tiến độ thi công</span>
          </div>
        </div>

        {/* NAVIGATION TABS & GLOBAL FILTER */}
        <div className="public-nav-bar">
          <nav className="public-tabs-nav" aria-label="Tabs dự án">
            <button
              className={`public-tab-btn ${activeTab === "map" ? "active" : ""}`}
              onClick={() => setActiveTab("map")}
            >
              🗺️ Bản đồ GIS & Tổng quan <span className="public-tab-count">{nodes.length + routes.length}</span>
            </button>
            <button
              className={`public-tab-btn ${activeTab === "photos" ? "active" : ""}`}
              onClick={() => setActiveTab("photos")}
            >
              📷 Thư viện ảnh thực địa <span className="public-tab-count">{photos.length}</span>
            </button>
            <button
              className={`public-tab-btn ${activeTab === "daily_log" ? "active" : ""}`}
              onClick={() => setActiveTab("daily_log")}
            >
              📝 Nhật ký & Công việc <span className="public-tab-count">{dailyLogs.length}</span>
            </button>
            <button
              className={`public-tab-btn ${activeTab === "progress" ? "active" : ""}`}
              onClick={() => setActiveTab("progress")}
            >
              📊 Tiến độ & Vật tư <span className="public-tab-count">{volumes.length}</span>
            </button>
            <button
              className={`public-tab-btn ${activeTab === "gis" ? "active" : ""}`}
              onClick={() => setActiveTab("gis")}
            >
              🗂️ Tra cứu dữ liệu GIS
            </button>
          </nav>

          <div className="public-filter-bar">
            <div className="public-search-wrapper">
              <span className="search-icon">🔍</span>
              <input
                type="text"
                placeholder="Tìm kiếm theo mã Node, Tuyến, Kỹ sư, Hạng mục..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="public-search-input"
              />
              {searchQuery && (
                <button className="clear-search-btn" onClick={() => setSearchQuery("")}>✕</button>
              )}
            </div>

            {contractorOptions.length > 0 && (
              <select
                className="public-select"
                value={filterContractor}
                onChange={(e) => setFilterContractor(e.target.value)}
              >
                <option value="">Tất cả Nhà thầu ({contractorOptions.length})</option>
                {contractorOptions.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            )}

            {(filterContractor || searchQuery) && (
              <button className="public-btn subtle" onClick={() => { setFilterContractor(""); setSearchQuery(""); }}>Xóa bộ lọc</button>
            )}
          </div>
        </div>
      </header>

      {/* MAIN CONTENT AREA */}
      <main className="public-portal-body">
        {/* TAB 1: GIS MAP & OVERVIEW */}
        {activeTab === "map" && (
          <section className="public-map-layout">
            <div className="public-map-canvas-container">
              <GisWebMap
                nodes={nodes}
                routes={routes}
                selected={selected}
                onSelect={setSelected}
                searchQuery={searchQuery}
                onSearchQueryChange={setSearchQuery}
                filterContractor={filterContractor}
                onFilterContractorChange={setFilterContractor}
                filterWork={filterWork}
                onFilterWorkChange={setFilterWork}
                contractorOptions={contractorOptions}
                workNameOptions={[]}
              />
            </div>

            {/* SELECTION DETAIL OVERLAY */}
            {selectedObjectDetail && (
              <aside className="public-selection-card">
                <div className="public-selection-header">
                  <div>
                    <span className="public-tag">{selectedObjectDetail.kind === "node" ? "GIS NODE" : "GIS ROUTE"}</span>
                    <h3>{display(selectedObjectDetail.item?.code ?? selectedObjectDetail.item?.name ?? selected?.code)}</h3>
                  </div>
                  <button className="public-btn icon-only" onClick={() => setSelected(null)}>✕</button>
                </div>

                <div className="public-selection-body">
                  <div className="public-info-row">
                    <span className="muted">Nhà thầu:</span>
                    <strong>{display(selectedObjectDetail.item?.contractor)}</strong>
                  </div>
                  {selectedObjectDetail.kind === "node" && (
                    <>
                      <div className="public-info-row">
                        <span className="muted">Tọa độ:</span>
                        <span>{display(selectedObjectDetail.item?.latitude)}, {display(selectedObjectDetail.item?.longitude)}</span>
                      </div>
                    </>
                  )}
                  {selectedObjectDetail.kind === "route" && (
                    <div className="public-info-row">
                      <span className="muted">Chiều dài:</span>
                      <strong>{display(selectedObjectDetail.item?.length ?? selectedObjectDetail.item?.totalLengthMeters)} m</strong>
                    </div>
                  )}
                  
                  <div className="public-selection-photos">
                    <h4>Hình ảnh liên kết ({selectedObjectDetail.photos.length})</h4>
                    {selectedObjectDetail.photos.length === 0 ? (
                      <p className="muted small">Chưa có hình ảnh chụp tại vị trí này.</p>
                    ) : (
                      <div className="public-photo-mini-grid">
                        {selectedObjectDetail.photos.map((p) => {
                          return (
                            <button
                              key={String(p.id)}
                              className="public-photo-mini-card"
                              onClick={() => openLightbox(p, selectedObjectDetail.photos)}
                            >
                              <img
                                src={imageUrlForPhoto(p, 400) || `/api/public/269-2026/media/${encodeURIComponent(String(p.id))}`}
                                alt={display(p.objectCode)}
                                loading="lazy"
                              />
                              <span className="public-photo-mini-label">{display(p.engineer || p.objectCode)}</span>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              </aside>
            )}
          </section>
        )}

        {/* TAB 2: SITE PHOTOS GALLERY (WITH GOOGLE DRIVE MECHANISM & ADVANCED GROUPING) */}
        {activeTab === "photos" && (
          <section className="media-gallery-section">
            {/* Toolbar */}
            <div className="media-toolbar">
              <div className="media-toolbar-top">
                <div className="media-search-box">
                  <span className="media-search-icon">🔍</span>
                  <input
                    type="text"
                    className="media-search-input"
                    placeholder="Tìm kiếm theo Tên Node, Mã Node, Kỹ sư, Thẻ tag, Ghi chú..."
                    value={photoSearchKeyword}
                    onChange={(e) => setPhotoSearchKeyword(e.target.value)}
                  />
                </div>

                <div className="media-toolbar-actions">
                  <div className="public-view-switcher">
                    <button
                      type="button"
                      className={`public-view-btn ${photoViewMode === "nodes" ? "active" : ""}`}
                      onClick={() => setPhotoViewMode("nodes")}
                      title="Phân chia theo Thư mục Node & Cột Tag"
                    >
                      📂 Theo Node & Tag
                    </button>
                    <button
                      type="button"
                      className={`public-view-btn ${photoViewMode === "tags" ? "active" : ""}`}
                      onClick={() => setPhotoViewMode("tags")}
                      title="Phân chia theo Nhóm Tag"
                    >
                      🏷️ Nhóm theo Tag
                    </button>
                    <button
                      type="button"
                      className={`public-view-btn ${photoViewMode === "grid" ? "active" : ""}`}
                      onClick={() => setPhotoViewMode("grid")}
                      title="Hiển thị lưới toàn bộ ảnh"
                    >
                      🖼️ Tất cả ({allFilteredPhotosFlat.length})
                    </button>
                  </div>

                  <div className="media-stat-pill">
                    <span>Thư mục:</span>
                    <strong>{photoNodeGroups.length}</strong>
                  </div>
                  <div className="media-stat-pill">
                    <span>Tổng ảnh:</span>
                    <strong>{allFilteredPhotosFlat.length}</strong>
                  </div>
                  <div className="media-stat-pill">
                    <span>Sync Drive:</span>
                    <strong style={{ color: "var(--success)" }}>{syncedPhotosCount}</strong>
                  </div>

                  {photoViewMode === "nodes" && photoNodeGroups.length > 0 && (
                    <button
                      type="button"
                      className="public-btn subtle small"
                      onClick={() => {
                        const allKeys: Record<string, boolean> = {};
                        const isAnyExpanded = photoNodeGroups.some((g) => !collapsedNodes[g.nodeKey]);
                        photoNodeGroups.forEach((g) => {
                          allKeys[g.nodeKey] = isAnyExpanded;
                        });
                        setCollapsedNodes(allKeys);
                      }}
                    >
                      {photoNodeGroups.some((g) => !collapsedNodes[g.nodeKey]) ? "Thu gọn tất cả" : "Mở rộng tất cả"}
                    </button>
                  )}
                </div>
              </div>

              {/* Tag Filter Pills */}
              {allAvailableTags.length > 0 && (
                <div className="media-tag-pills-wrapper">
                  <span className="media-tag-pills-label">Lọc thẻ Tag:</span>
                  <button
                    type="button"
                    className={`media-tag-pill ${!selectedTagFilter ? "active" : ""}`}
                    onClick={() => setSelectedTagFilter("")}
                  >
                    Tất cả tag
                    <span className="pill-count">{totalPhotosCount}</span>
                  </button>
                  {allAvailableTags.map((tag) => {
                    const count = photos.filter((p) => extractPhotoTags(p).includes(tag)).length;
                    return (
                      <button
                        key={tag}
                        type="button"
                        className={`media-tag-pill ${selectedTagFilter === tag ? "active" : ""}`}
                        onClick={() => setSelectedTagFilter(selectedTagFilter === tag ? "" : tag)}
                      >
                        🏷️ {tag}
                        <span className="pill-count">{count}</span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* CONTENT BY VIEW MODE */}
            {allFilteredPhotosFlat.length === 0 ? (
              <div className="public-empty-state">
                <p>Không tìm thấy hình ảnh nào phù hợp với bộ lọc hiện tại.</p>
              </div>
            ) : photoViewMode === "nodes" ? (
              /* VIEW MODE 1: GROUP BY NODE FOLDER & TAG COLUMNS (KANBAN) */
              <div className="node-folder-list">
                {photoNodeGroups.map((group) => {
                  const isCollapsed = Boolean(collapsedNodes[group.nodeKey]);
                  return (
                    <div key={group.nodeKey} className="node-folder-card">
                      <div
                        className="node-folder-header"
                        onClick={() => {
                          setCollapsedNodes((prev) => ({
                            ...prev,
                            [group.nodeKey]: !prev[group.nodeKey]
                          }));
                        }}
                      >
                        <div className="node-folder-title-left">
                          <span className="node-folder-icon">📂</span>
                          <div className="node-folder-meta">
                            <div className="node-folder-title">
                              <span>{group.nodeName}</span>
                              {group.nodeCode && group.nodeCode !== group.nodeName && (
                                <span className="node-folder-code-badge">{group.nodeCode}</span>
                              )}
                            </div>
                            {group.contractor && (
                              <span className="node-folder-sub">Nhà thầu: {group.contractor}</span>
                            )}
                          </div>
                        </div>
                        <div className="node-folder-header-right">
                          <span className="node-folder-count-badge">
                            {group.allPhotos.length} ảnh {group.hasTags ? `· ${group.tags.length} cột tag` : ""}
                          </span>
                          <span className={`node-folder-chevron ${isCollapsed ? "collapsed" : ""}`}>
                            ▼
                          </span>
                        </div>
                      </div>

                      {!isCollapsed && (
                        <div className="node-folder-body">
                          {group.hasTags ? (
                            <div className="tag-columns-container">
                              {group.tags.map((tag) => {
                                const tagPhotos = group.photosByTag[tag] || [];
                                if (tagPhotos.length === 0) return null;
                                return (
                                  <div key={tag} className="tag-column">
                                    <div className="tag-column-header">
                                      <span className="tag-column-title" title={tag}>
                                        🏷️ {tag}
                                      </span>
                                      <span className="tag-column-count">{tagPhotos.length}</span>
                                    </div>
                                    <div className="tag-column-body">
                                      {tagPhotos.map((photo) => (
                                        <PublicPhotoThumbnail
                                          key={String(photo.id)}
                                          photo={photo}
                                          onClick={() => openLightbox(photo, group.allPhotos)}
                                        />
                                      ))}
                                    </div>
                                  </div>
                                );
                              })}

                              {group.untaggedPhotos.length > 0 && (
                                <div className="tag-column untagged">
                                  <div className="tag-column-header">
                                    <span className="tag-column-title">
                                      📁 Chưa gắn tag
                                    </span>
                                    <span className="tag-column-count">{group.untaggedPhotos.length}</span>
                                  </div>
                                  <div className="tag-column-body">
                                    {group.untaggedPhotos.map((photo) => (
                                      <PublicPhotoThumbnail
                                        key={String(photo.id)}
                                        photo={photo}
                                        onClick={() => openLightbox(photo, group.allPhotos)}
                                      />
                                    ))}
                                  </div>
                                </div>
                              )}
                            </div>
                          ) : (
                            <div className="public-photo-gallery-grid">
                              {group.allPhotos.map((photo) => (
                                <PublicPhotoThumbnail
                                  key={String(photo.id)}
                                  photo={photo}
                                  onClick={() => openLightbox(photo, group.allPhotos)}
                                />
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : photoViewMode === "tags" ? (
              /* VIEW MODE 2: GROUPED BY TAG SECTIONS */
              <div className="node-folder-list">
                {photosGroupedByTag.tagged.map(([tag, tagPhotos]) => (
                  <div key={tag} className="tag-group-section">
                    <div className="tag-group-header">
                      <div className="tag-group-title">
                        <span>🏷️ Thẻ Tag: {tag}</span>
                      </div>
                      <span className="tag-group-count">{tagPhotos.length} ảnh</span>
                    </div>
                    <div className="public-photo-gallery-grid">
                      {tagPhotos.map((photo) => (
                        <PublicPhotoThumbnail
                          key={String(photo.id)}
                          photo={photo}
                          onClick={() => openLightbox(photo, tagPhotos)}
                        />
                      ))}
                    </div>
                  </div>
                ))}

                {photosGroupedByTag.untagged.length > 0 && (
                  <div className="tag-group-section" style={{ borderStyle: "dashed" }}>
                    <div className="tag-group-header">
                      <div className="tag-group-title">
                        <span>📁 Chưa gắn thẻ tag</span>
                      </div>
                      <span className="tag-group-count">{photosGroupedByTag.untagged.length} ảnh</span>
                    </div>
                    <div className="public-photo-gallery-grid">
                      {photosGroupedByTag.untagged.map((photo) => (
                        <PublicPhotoThumbnail
                          key={String(photo.id)}
                          photo={photo}
                          onClick={() => openLightbox(photo, photosGroupedByTag.untagged)}
                        />
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* VIEW MODE 3: FLAT GRID */
              <div className="public-photo-gallery-grid">
                {allFilteredPhotosFlat.map((photo) => (
                  <PublicPhotoThumbnail
                    key={String(photo.id)}
                    photo={photo}
                    onClick={() => openLightbox(photo, allFilteredPhotosFlat)}
                  />
                ))}
              </div>
            )}
          </section>
        )}

        {/* TAB 3: DAILY SUPERVISION LOGS & TASKS */}
        {activeTab === "daily_log" && (
          <section className="public-section-container">
            <div className="public-dual-columns">
              <div className="public-column">
                <div className="public-section-header">
                  <h2>Nhật ký thi công ({filteredDailyLogs.length})</h2>
                  <span className="public-tag">Dòng thời gian</span>
                </div>

                {filteredDailyLogs.length === 0 ? (
                  <div className="public-empty-state">
                    <p>Chưa có bản ghi nhật ký thi công nào.</p>
                  </div>
                ) : (
                  <div className="public-timeline">
                    {filteredDailyLogs.map((log) => (
                      <article key={String(log.id)} className="public-timeline-card">
                        <div className="public-timeline-header">
                          <span className="public-timeline-date">📅 {formatDate(log.date || log.createdAt)}</span>
                          <span className="public-tag category">{display(log.categoryName || "Giám sát")}</span>
                        </div>
                        <h4 className="public-timeline-work">{display(log.workItem || log.workContent || "Hạng mục công việc")}</h4>
                        <div className="public-timeline-metrics">
                          <div className="metric">
                            <span className="muted">👷 Nhân lực:</span>
                            <strong>{display(log.manpowerCount ?? log.workerCount ?? "—")}</strong>
                          </div>
                          <div className="metric">
                            <span className="muted">🚜 Máy móc:</span>
                            <strong>{display(log.machineryCount ?? log.equipment ?? "—")}</strong>
                          </div>
                          <div className="metric">
                            <span className="muted">🌤️ Thời tiết:</span>
                            <strong>{display(log.weather ?? "Bình thường")}</strong>
                          </div>
                        </div>
                        {Boolean(log.note) && <p className="public-timeline-note">📝 {display(log.note)}</p>}
                        <div className="public-timeline-footer">
                          <span className="muted">Nhà thầu: <strong>{display(log.contractor)}</strong></span>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
              </div>

              <div className="public-column">
                <div className="public-section-header">
                  <h2>Công việc & Nhiệm vụ ({tasks.length})</h2>
                  <span className="public-tag">Phân công</span>
                </div>

                {tasks.length === 0 ? (
                  <div className="public-empty-state">
                    <p>Chưa có danh sách công việc được khởi tạo.</p>
                  </div>
                ) : (
                  <div className="public-tasks-list">
                    {tasks.map((task) => {
                      const status = String(task.status || "TODO");
                      return (
                        <div key={String(task.id)} className={`public-task-card ${status === "DONE" ? "done" : ""}`}>
                          <div className="public-task-status-line">
                            <span className={`public-task-badge ${status.toLowerCase()}`}>
                              {status === "DONE" ? "✓ HOÀN THÀNH" : status === "IN_PROGRESS" ? "⏳ ĐANG LÀM" : "📋 CHỜ XỬ LÝ"}
                            </span>
                            <span className="public-priority-tag">Ưu tiên: {display(task.priority || "Vừa")}</span>
                          </div>
                          <h4>{display(task.title || task.name)}</h4>
                          {Boolean(task.description) && <p className="public-task-desc">{display(task.description)}</p>}
                          <div className="public-task-meta">
                            <span>👤 Phụ trách: <strong>{display(task.assignedTo || task.assignee || "—")}</strong></span>
                            <span>📅 Hạn: <strong>{formatDate(task.dueDate || task.deadline)}</strong></span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          </section>
        )}

        {/* TAB 4: WORK VOLUME PROGRESS & MATERIALS */}
        {activeTab === "progress" && (
          <section className="public-section-container">
            <div className="public-dual-columns">
              <div className="public-card-box">
                <div className="public-section-header">
                  <h3>Tiến độ hoàn thành khối lượng ({volumes.length})</h3>
                </div>
                {volumes.length === 0 ? (
                  <div className="public-empty-state">Chưa có dữ liệu khối lượng.</div>
                ) : (
                  <div className="public-table-container">
                    <table className="public-table">
                      <thead>
                        <tr>
                          <th>Hạng mục / Vị trí</th>
                          <th>Kế hoạch</th>
                          <th>Thực tế</th>
                          <th>Tiến độ</th>
                        </tr>
                      </thead>
                      <tbody>
                        {volumes.map((vol) => {
                          const planned = parseFloat(String(vol.plannedVolume ?? vol.totalVolume ?? 0));
                          const actual = parseFloat(String(vol.completedVolume ?? vol.actualVolume ?? 0));
                          const percent = planned > 0 ? Math.min(100, Math.round((actual / planned) * 100)) : 0;
                          return (
                            <tr key={String(vol.id)}>
                              <td>
                                <strong>{display(vol.itemName || vol.workName || vol.nodeCode)}</strong>
                                <br />
                                <small className="muted">{display(vol.unit || "Đơn vị")}</small>
                              </td>
                              <td>{planned.toLocaleString("vi-VN")}</td>
                              <td><strong>{actual.toLocaleString("vi-VN")}</strong></td>
                              <td>
                                <div className="public-progress-cell">
                                  <div className="public-progress-bar">
                                    <div className="public-progress-fill" style={{ width: `${percent}%` }} />
                                  </div>
                                  <span>{percent}%</span>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="public-card-box">
                <div className="public-section-header">
                  <h3>Bàn giao & Khai báo vật tư ({materialHandovers.length + materialDecs.length})</h3>
                </div>
                {materialHandovers.length === 0 && materialDecs.length === 0 ? (
                  <div className="public-empty-state">Chưa có thông tin vật tư.</div>
                ) : (
                  <div className="public-table-container">
                    <table className="public-table">
                      <thead>
                        <tr>
                          <th>Loại vật tư</th>
                          <th>Số lượng</th>
                          <th>Nhà thầu / Người nhận</th>
                          <th>Ngày</th>
                        </tr>
                      </thead>
                      <tbody>
                        {materialHandovers.map((m) => (
                          <tr key={String(m.id)}>
                            <td>
                              <span className="public-tag">Bàn giao</span> <strong>{display(m.materialName || m.name)}</strong>
                            </td>
                            <td>{display(m.quantity)} {display(m.unit)}</td>
                            <td>{display(m.recipient || m.contractor)}</td>
                            <td>{formatDate(m.handoverDate || m.createdAt)}</td>
                          </tr>
                        ))}
                        {materialDecs.map((d) => (
                          <tr key={String(d.id)}>
                            <td>
                              <span className="public-tag category">Khai báo</span> <strong>{display(d.materialName || d.name)}</strong>
                            </td>
                            <td>{display(d.quantity)} {display(d.unit)}</td>
                            <td>{display(d.contractor || d.supplier)}</td>
                            <td>{formatDate(d.declarationDate || d.createdAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          </section>
        )}

        {/* TAB 5: GIS INVENTORY TABLE */}
        {activeTab === "gis" && (
          <section className="public-section-container">
            <div className="public-card-box">
              <div className="public-section-header">
                <h3>Danh bạ Điểm đo GIS (Nodes - {nodes.length})</h3>
              </div>
              <div className="public-table-container">
                <table className="public-table">
                  <thead>
                    <tr>
                      <th>Mã Node</th>
                      <th>Tên điểm</th>
                      <th>Nhà thầu</th>
                      <th>Tọa độ (Lat, Lon)</th>
                      <th>Tín hiệu</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {nodes.slice(0, 100).map((n) => (
                      <tr key={String(n.id)}>
                        <td><span className="public-code-highlight">{display(n.code || n.nodeCode)}</span></td>
                        <td>{display(n.name)}</td>
                        <td>{display(n.contractor)}</td>
                        <td>
                          {n.latitude && n.longitude ? (
                            <small className="muted font-mono">{Number(n.latitude).toFixed(5)}, {Number(n.longitude).toFixed(5)}</small>
                          ) : "—"}
                        </td>
                        <td><span className="public-tag signal">{display(n.signalStatus || "OK")}</span></td>
                        <td>
                          <button
                            className="public-btn small"
                            onClick={() => {
                              setSelected({ kind: "node", code: String(n.code || n.name || n.id) });
                              setActiveTab("map");
                            }}
                          >
                            Xem bản đồ
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="public-card-box">
              <div className="public-section-header">
                <h3>Danh bạ Tuyến cáp GIS (Routes - {routes.length})</h3>
              </div>
              <div className="public-table-container">
                <table className="public-table">
                  <thead>
                    <tr>
                      <th>Mã Tuyến</th>
                      <th>Điểm đầu ➔ Điểm cuối</th>
                      <th>Nhà thầu</th>
                      <th>Chiều dài (m)</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {routes.slice(0, 100).map((r) => (
                      <tr key={String(r.id)}>
                        <td><span className="public-code-highlight">{display(r.code || r.routeCode)}</span></td>
                        <td>{display(r.startNodeCode)} ➔ {display(r.endNodeCode)}</td>
                        <td>{display(r.contractor)}</td>
                        <td><strong>{display(r.length ?? r.totalLengthMeters)} m</strong></td>
                        <td>
                          <button
                            className="public-btn small"
                            onClick={() => {
                              setSelected({ kind: "route", code: String(r.code || r.name || r.id) });
                              setActiveTab("map");
                            }}
                          >
                            Xem bản đồ
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        )}
      </main>

      {/* LIGHTBOX MODAL WITH ZOOM, GOOGLE DRIVE LINK & GPS */}
      {activeLightboxPhoto && (
        <div className="public-lightbox-backdrop" onClick={closeLightbox}>
          <div
            className="public-lightbox-modal"
            ref={lightboxRef}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="public-lightbox-header">
              <div className="public-lightbox-title-box">
                <span className="public-tag code">{display(activeLightboxPhoto.objectCode || "ẢNH THỰC ĐỊA")}</span>
                <h3>{display(activeLightboxPhoto.objectCode || activeLightboxPhoto.id)}</h3>
                {currentLightboxIndex >= 0 && (
                  <span className="muted small">({currentLightboxIndex + 1} / {activeLightboxPlaylist.length})</span>
                )}
              </div>

              <div className="public-lightbox-ctrls">
                <button
                  className="public-btn icon-only small"
                  onClick={() => setLightboxZoom((z) => Math.max(0.5, z - 0.25))}
                  title="Thu nhỏ (-)"
                >
                  🔍-
                </button>
                <span className="public-zoom-level">{Math.round(lightboxZoom * 100)}%</span>
                <button
                  className="public-btn icon-only small"
                  onClick={() => setLightboxZoom((z) => Math.min(3, z + 0.25))}
                  title="Phóng to (+)"
                >
                  🔍+
                </button>
                <button
                  className="public-btn icon-only small"
                  onClick={() => setLightboxZoom(1)}
                  title="Tỷ lệ gốc 1:1"
                >
                  1:1
                </button>
                <button className="public-btn icon-only danger" onClick={closeLightbox} title="Đóng (Esc)">
                  ✕
                </button>
              </div>
            </div>

            <div className="public-lightbox-stage">
              {hasPrevPhoto && (
                <button className="public-lightbox-nav prev" onClick={prevPhoto} title="Ảnh trước (←)">
                  ‹
                </button>
              )}

              <div className="public-lightbox-image-container">
                <img
                  src={
                    imageUrlForPhoto(activeLightboxPhoto, 1600) ||
                    `/api/public/269-2026/media/${encodeURIComponent(String(activeLightboxPhoto.id))}`
                  }
                  alt={display(activeLightboxPhoto.objectCode)}
                  className="public-lightbox-img"
                  style={{ transform: `scale(${lightboxZoom})` }}
                />
              </div>

              {hasNextPhoto && (
                <button className="public-lightbox-nav next" onClick={nextPhoto} title="Ảnh kế tiếp (→)">
                  ›
                </button>
              )}
            </div>

            <div className="public-lightbox-footer">
              <div className="public-lightbox-meta-item">
                <span className="muted">👤 Kỹ sư giám sát:</span>
                <strong>{display(activeLightboxPhoto.engineer)}</strong>
              </div>

              <div className="public-lightbox-meta-item">
                <span className="muted">🕒 Thời gian:</span>
                <span>{formatDate(activeLightboxPhoto.capturedAtEpochMs ?? activeLightboxPhoto.updatedAtEpochMs ?? activeLightboxPhoto.timestamp)}</span>
              </div>

              {Boolean(activeLightboxPhoto.latitude && activeLightboxPhoto.longitude) && (
                <div className="public-lightbox-meta-item">
                  <span className="muted">📍 Tọa độ GPS:</span>
                  <a
                    href={`https://www.google.com/maps?q=${activeLightboxPhoto.latitude},${activeLightboxPhoto.longitude}`}
                    target="_blank"
                    rel="noreferrer"
                    className="public-link"
                    title="Mở trên Google Maps"
                  >
                    {display(activeLightboxPhoto.latitude)}, {display(activeLightboxPhoto.longitude)} ↗
                  </a>
                </div>
              )}

              {extractPhotoTags(activeLightboxPhoto).length > 0 && (
                <div className="public-lightbox-meta-item">
                  <span className="muted">🏷️ Thẻ tag:</span>
                  <div style={{ display: "flex", gap: "4px" }}>
                    {extractPhotoTags(activeLightboxPhoto).map((t) => (
                      <span key={t} className="photo-badge-tag">
                        {t}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {Boolean(activeLightboxPhoto.captureNote || activeLightboxPhoto.caption) && (
                <div className="public-lightbox-meta-item caption">
                  <span className="muted">📝 Ghi chú:</span>
                  <span>{display(activeLightboxPhoto.captureNote || activeLightboxPhoto.caption)}</span>
                </div>
              )}

              <div className="public-lightbox-meta-item" style={{ marginLeft: "auto", display: "flex", gap: "10px" }}>
                {Boolean(driveLinkForPhoto(activeLightboxPhoto)) && (
                  <a
                    href={driveLinkForPhoto(activeLightboxPhoto)}
                    target="_blank"
                    rel="noreferrer"
                    className="public-btn small"
                    style={{ textDecoration: "none" }}
                  >
                    📁 Mở trên Google Drive
                  </a>
                )}
                <a
                  href={
                    imageUrlForPhoto(activeLightboxPhoto, 2000) ||
                    `/api/public/269-2026/media/${encodeURIComponent(String(activeLightboxPhoto.id))}`
                  }
                  target="_blank"
                  rel="noreferrer"
                  download={`photo_${activeLightboxPhoto.id}.jpg`}
                  className="public-btn small"
                  style={{ textDecoration: "none" }}
                >
                  ⬇️ Tải ảnh
                </a>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* FOOTER */}
      <footer className="public-portal-footer">
        <div className="public-portal-footer-content">
          <p>© 2026 Hệ Thống Giám Sát & Quản Lý Thi Công MAPSUPERVISION · Dự Án 269 - 2026</p>
          <p className="muted">Cổng thông tin trực tuyến dành cho Giám sát, Ban Quản lý, Nhà thầu và Đối tác</p>
        </div>
      </footer>
    </div>
  );
}
