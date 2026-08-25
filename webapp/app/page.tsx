"use client";

import { useEffect, useMemo, useState } from "react";
import type { FirebaseError } from "firebase/app";
import { EmailAuthProvider, reauthenticateWithCredential } from "firebase/auth";
import dynamic from "next/dynamic";
import type { SelectedObject } from "@/components/GisWebMap";
import { auth, db, firebaseReady, getFirebaseUserAdminClaim, observeAuth, registerWithEmail, sendVerificationEmail, signInWithEmail, signOutCurrentUser, type FirebaseUser } from "@/lib/firebase";
import { imageSourceUrl } from "@/lib/google-drive-image";
import {
  createDailyLogDocument,
  deleteProjectMemberRecord,
  createProjectDocument,
  createTaskDocument,
  emptyProjectCollections,
  requestDeleteProjectApi,
  saveProjectMember,
  setActiveProjectForUser,
  subscribeCurrentProjectMember,
  subscribeProjectDocument,
  subscribeProjectMembers,
  subscribeProjectAccessRequests,
  transitionProjectAccessRequest,
  subscribeProjects,
  subscribeLatestCatalogMigration,
  subscribeProjectTable,
  subscribeUsersDirectory,
  syncTables,
  upsertUserProfile,
  updateTaskStatusDocument,
  type ContractorScope,
  type ProjectCollections,
  type ProjectDoc,
  type ProjectDraft,
  type ProjectMemberRow,
  type SitePhotoRow,
  type TaskRow,
  type UserProfileRow,
  type AccessAdminAction,
  type AccessRequestStatus,
  type ProjectAccessRequestRow
} from "@/lib/sync";

type TaskDraft = {
  title: string;
  description: string;
};

type DailyLogDraft = {
  workItem: string;
  note: string;
  manpower: string;
  volume: string;
  unit: string;
  categoryName: string;
  weather: string;
};

type AuthMode = "signIn" | "register";

const GisWebMap = dynamic(
  () => import("@/components/GisWebMap").then((module) => module.GisWebMap),
  {
    ssr: false,
    loading: () => <div className="web-map-empty empty-state">Đang tải bản đồ...</div>
  }
);

const emptyTask: TaskDraft = { title: "", description: "" };
const emptyDailyLog: DailyLogDraft = {
  workItem: "",
  note: "",
  manpower: "0",
  volume: "0",
  unit: "m",
  categoryName: "",
  weather: ""
};

const emptyProjectDraft: ProjectDraft = { name: "", projectCode: "" };

function errorMessage(error: unknown): string {
  const firebaseError = error as FirebaseError;
  if (firebaseError?.code === "permission-denied") {
    return "Tài khoản chưa được cấp quyền cho dự án này.";
  }
  if (firebaseError?.code === "auth/invalid-credential") {
    return "Email hoặc mật khẩu không đúng.";
  }
  if (firebaseError?.code === "auth/configuration-not-found") {
    return "Firebase Authentication chưa được khởi tạo cho project này. Vào Firebase Console > Authentication > Get started, rồi bật Email/Password trong Sign-in method.";
  }
  if (firebaseError?.code === "auth/operation-not-allowed") {
    return "Firebase chưa bật phương thức đăng nhập Email/Password. Vào Authentication > Sign-in method để bật Email/Password.";
  }
  if (firebaseError?.code === "auth/email-already-in-use") {
    return "Email này đã được đăng ký. Vui lòng đăng nhập hoặc sử dụng email khác.";
  }
  if (firebaseError?.code === "auth/weak-password") {
    return "Mật khẩu cần có ít nhất 6 ký tự.";
  }
  if (firebaseError?.code === "auth/invalid-email") {
    return "Email không hợp lệ.";
  }
  if (firebaseError?.code === "auth/too-many-requests") {
    return "Bạn thao tác quá nhiều lần. Vui lòng chờ một lúc rồi thử lại.";
  }
  return firebaseError?.message ?? "Có lỗi xảy ra trong quá trình kết nối.";
}

function driveLinkForPhoto(photo: Record<string, unknown>): string | undefined {
  const url = String(photo.remoteUrl || photo.url || "").trim();
  return url.startsWith("http://") || url.startsWith("https://") ? url : undefined;
}

function imageUrlForPhoto(photo: Record<string, unknown>, width: number): string | undefined {
  return imageSourceUrl(String(photo.remoteUrl || photo.url || ""), width);
}

function syncLabelForPhoto(photo: SitePhotoRow, driveUrl?: string): string {
  const status = text(photo, "syncStatus") || (driveUrl ? "DONE" : "PENDING");
  const error = text(photo, "syncErrorMessage");
  return status === "FAILED" && error ? `Sync: FAILED - ${error}` : `Sync: ${status}`;
}

function SitePhotoPreview({
  src,
  alt,
  className
}: {
  src?: string;
  alt?: string;
  className?: string;
}) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (!src || failedSrc === src) {
    return <div className="photo-placeholder animate-pulse" />;
  }

  return <img src={src} alt={alt} className={className} onError={() => setFailedSrc(src)} />;
}

function formatDateTime(value: unknown): string {
  const millis = Number(value ?? 0);
  if (!Number.isFinite(millis) || millis <= 0) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(millis));
}

function formatNumber(value: unknown, suffix = ""): string {
  const number = Number(value ?? 0);
  if (!Number.isFinite(number)) return suffix ? `0 ${suffix}` : "0";
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(number)}${suffix ? ` ${suffix}` : ""}`;
}

function text(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && String(value).trim()) return String(value);
  }
  return "";
}

function normalizeContractor(value: string): string {
  return value.trim().toLowerCase();
}

function collectContractorOptions(collections: ProjectCollections): string[] {
  return [...collections.gis_node, ...collections.gis_route, ...collections.material_handover]
    .map((row) => text(row, "contractor", "contractorName"))
    .filter(Boolean)
    .filter((value, index, array) => array.indexOf(value) === index)
    .sort((left, right) => left.localeCompare(right, "vi"));
}

function filterCollectionsForMember(
  collections: ProjectCollections,
  member: ProjectMemberRow | null,
  isAdmin: boolean
): ProjectCollections {
  if (isAdmin || !member || member.contractorScope !== "SCOPED" || !member.allowedContractors.length) {
    return collections;
  }

  const allowedContractors = new Set(member.allowedContractors.map(normalizeContractor));
  const allowedNodes = collections.gis_node.filter((row) => allowedContractors.has(normalizeContractor(text(row, "contractor", "contractorName"))));
  const allowedRoutes = collections.gis_route.filter((row) => allowedContractors.has(normalizeContractor(text(row, "contractor", "contractorName"))));
  const allowedNodeCodes = new Set(allowedNodes.map((row) => text(row, "code", "nodeCode", "name")));
  const allowedRouteCodes = new Set(allowedRoutes.map((row) => text(row, "code", "routeCode", "name")));

  return {
    ...collections,
    gis_node: allowedNodes,
    gis_route: allowedRoutes,
    work_volume_progress: collections.work_volume_progress.filter((row) => allowedNodeCodes.has(text(row, "nodeCode", "code"))),
    work_plan: collections.work_plan.filter((row) => {
      const nodeCode = text(row, "nodeCode");
      const routeCode = text(row, "routeCode");
      return (!nodeCode || allowedNodeCodes.has(nodeCode)) && (!routeCode || allowedRouteCodes.has(routeCode));
    }),
    material_handover: collections.material_handover.filter((row) => allowedContractors.has(normalizeContractor(text(row, "contractor", "contractorName"))))
  };
}

export type PhotoNodeGroup = {
  nodeKey: string;
  nodeName: string;
  nodeCode: string;
  contractor?: string;
  hasTags: boolean;
  tags: string[];
  photosByTag: Record<string, SitePhotoRow[]>;
  untaggedPhotos: SitePhotoRow[];
  allPhotos: SitePhotoRow[];
};

function extractPhotoTags(photo: Record<string, unknown> | SitePhotoRow): string[] {
  const tagsSet = new Set<string>();
  const csv = text(photo, "tagCodesCsv");
  if (csv) {
    csv.split(",")
      .map((s) => s.trim())
      .filter(Boolean)
      .forEach((t) => tagsSet.add(t));
  }
  const statusTag = text(photo, "statusTag");
  if (statusTag) {
    tagsSet.add(statusTag);
  }
  return Array.from(tagsSet);
}

function groupPhotosByNodeAndTag(
  photos: Array<Record<string, unknown>> | SitePhotoRow[],
  gisNodes: Array<Record<string, unknown>>,
  searchKeyword: string,
  selectedTagFilter: string
): PhotoNodeGroup[] {
  const nodeMap = new Map<string, Record<string, unknown>>();
  for (const node of gisNodes) {
    const id = text(node, "id");
    const code = text(node, "code", "nodeCode");
    const name = text(node, "name");
    if (id) nodeMap.set(id, node);
    if (code) nodeMap.set(code, node);
    if (name) nodeMap.set(name, node);
  }

  const groupsByKey = new Map<string, {
    nodeKey: string;
    nodeName: string;
    nodeCode: string;
    contractor?: string;
    photos: SitePhotoRow[];
  }>();

  for (const rawPhoto of photos) {
    const photo = rawPhoto as SitePhotoRow;
    const photoId = text(photo, "id");
    if (!photoId && !photo.capturedAtEpochMs) continue;

    const matchedNodeId = text(photo, "matchedNodeId");
    const objectCode = text(photo, "objectCode", "nodeCode");

    const matchedNode = (matchedNodeId && nodeMap.get(matchedNodeId)) ||
                        (objectCode && nodeMap.get(objectCode));

    const nodeCode = matchedNode ? text(matchedNode, "code", "nodeCode") || objectCode : (objectCode || "UNASSIGNED");
    const nodeName = matchedNode ? text(matchedNode, "name") || nodeCode : (objectCode ? `Mục ${objectCode}` : "Ảnh chưa phân loại Node");
    const contractor = matchedNode ? text(matchedNode, "contractor", "contractorName") : undefined;
    const nodeKey = nodeCode || matchedNodeId || "UNASSIGNED";

    if (!groupsByKey.has(nodeKey)) {
      groupsByKey.set(nodeKey, {
        nodeKey,
        nodeName,
        nodeCode,
        contractor,
        photos: []
      });
    }
    groupsByKey.get(nodeKey)!.photos.push(photo);
  }

  const result: PhotoNodeGroup[] = [];

  for (const group of groupsByKey.values()) {
    group.photos.sort((a, b) => Number(b.capturedAtEpochMs ?? b.updatedAtEpochMs ?? 0) - Number(a.capturedAtEpochMs ?? a.updatedAtEpochMs ?? 0));

    const tagSet = new Set<string>();
    const photosByTag: Record<string, SitePhotoRow[]> = {};
    const untaggedPhotos: SitePhotoRow[] = [];

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
        const oCode = text(p, "objectCode").toLowerCase();
        const eng = text(p, "engineer").toLowerCase();
        const note = text(p, "captureNote").toLowerCase();
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

    const filteredPhotosByTag: Record<string, SitePhotoRow[]> = {};
    const filteredUntagged: SitePhotoRow[] = [];

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

function PhotoCardItem({
  photo,
  onClick
}: {
  photo: SitePhotoRow;
  onClick: () => void;
}) {
  const driveUrl = driveLinkForPhoto(photo);
  const imageUrl = imageUrlForPhoto(photo, 600);
  const tags = extractPhotoTags(photo);
  const isDone = photo.syncStatus === "DONE" || Boolean(driveUrl);

  return (
    <div className="photo-card-mini" onClick={onClick}>
      <div className="photo-card-thumb-wrap">
        {imageUrl ? (
          <SitePhotoPreview
            src={imageUrl}
            alt={text(photo, "objectCode") || "Ảnh thực địa"}
          />
        ) : (
          <div className="photo-placeholder" />
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
                {t}
              </span>
            ))}
          </div>
        )}
        <div className="photo-card-engineer">
          👷 {text(photo, "engineer") || "Chưa rõ kỹ sư"}
        </div>
        <div className="photo-card-time">
          🕒 {formatDateTime(photo.capturedAtEpochMs ?? photo.updatedAtEpochMs)}
        </div>
      </div>
    </div>
  );
}

function PhotoLightboxModal({
  photo,
  allPhotos,
  onClose,
  onSelectPhoto
}: {
  photo: SitePhotoRow | null;
  allPhotos: SitePhotoRow[];
  onClose: () => void;
  onSelectPhoto: (nextPhoto: SitePhotoRow) => void;
}) {
  if (!photo) return null;

  const currentIndex = allPhotos.findIndex((p) => p.id === photo.id);
  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex >= 0 && currentIndex < allPhotos.length - 1;

  const handlePrev = () => {
    if (hasPrev) onSelectPhoto(allPhotos[currentIndex - 1]);
  };

  const handleNext = () => {
    if (hasNext) onSelectPhoto(allPhotos[currentIndex + 1]);
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowLeft" && hasPrev) handlePrev();
      if (e.key === "ArrowRight" && hasNext) handleNext();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [currentIndex, hasPrev, hasNext, onClose]);

  const driveUrl = driveLinkForPhoto(photo);
  const imageUrl = imageUrlForPhoto(photo, 1000);
  const tags = extractPhotoTags(photo);

  return (
    <div className="photo-lightbox-backdrop" onClick={onClose}>
      <div className="photo-lightbox-modal" onClick={(e) => e.stopPropagation()}>
        <div className="photo-lightbox-header">
          <div className="photo-lightbox-title-area">
            <span className="photo-lightbox-badge">ẢNH THỰC ĐỊA</span>
            <h3>{text(photo, "objectCode", "nodeCode") || "Ảnh không gắn mã"}</h3>
            {currentIndex >= 0 && (
              <span className="photo-lightbox-counter">
                {currentIndex + 1} / {allPhotos.length}
              </span>
            )}
          </div>
          <button type="button" className="photo-lightbox-close" onClick={onClose} title="Đóng (Esc)">
            ✕
          </button>
        </div>

        <div className="photo-lightbox-body">
          <div className="photo-lightbox-stage">
            {hasPrev && (
              <button
                type="button"
                className="photo-lightbox-nav prev"
                onClick={handlePrev}
                title="Ảnh trước (←)"
              >
                ‹
              </button>
            )}
            <div className="photo-lightbox-img-wrapper">
              {imageUrl ? (
                <SitePhotoPreview
                  src={imageUrl}
                  alt={text(photo, "objectCode") || "Ảnh thực địa"}
                  className="photo-lightbox-img"
                />
              ) : (
                <div className="photo-placeholder" style={{ height: "100%", minHeight: "360px" }} />
              )}
            </div>
            {hasNext && (
              <button
                type="button"
                className="photo-lightbox-nav next"
                onClick={handleNext}
                title="Ảnh tiếp theo (→)"
              >
                ›
              </button>
            )}
          </div>

          <aside className="photo-lightbox-details">
            <div className="photo-detail-section">
              <h4>Thông tin chụp</h4>
              <div className="photo-detail-grid">
                <div className="detail-item">
                  <span className="detail-label">Kỹ sư chụp</span>
                  <strong className="detail-value">{text(photo, "engineer") || "Không rõ"}</strong>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Thời gian</span>
                  <strong className="detail-value">{formatDateTime(photo.capturedAtEpochMs ?? photo.updatedAtEpochMs)}</strong>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Mã đối tượng (Node)</span>
                  <strong className="detail-value text-accent">{text(photo, "objectCode") || "—"}</strong>
                </div>
                <div className="detail-item">
                  <span className="detail-label">Trạng thái đồng bộ</span>
                  <span className={`sync-status-badge ${photo.syncStatus === "DONE" || driveUrl ? "success" : "pending"}`}>
                    {syncLabelForPhoto(photo, driveUrl)}
                  </span>
                </div>
              </div>
            </div>

            <div className="photo-detail-section">
              <h4>Thẻ phân loại (Tags)</h4>
              {tags.length > 0 ? (
                <div className="photo-tags-container">
                  {tags.map((tag) => (
                    <span key={tag} className="photo-tag-pill">
                      🏷️ {tag}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="detail-empty">Chưa gắn thẻ tag</p>
              )}
            </div>

            <div className="photo-detail-section">
              <h4>Vị trí GPS & Địa chỉ</h4>
              <div className="photo-detail-grid">
                <div className="detail-item">
                  <span className="detail-label">Tọa độ GPS</span>
                  <strong className="detail-value font-mono">
                    {photo.latitude && photo.longitude
                      ? `${photo.latitude.toFixed(6)}, ${photo.longitude.toFixed(6)}`
                      : "Không có dữ liệu GPS"}
                  </strong>
                </div>
                {photo.locationAccuracyM !== undefined && photo.locationAccuracyM !== null && (
                  <div className="detail-item">
                    <span className="detail-label">Độ chính xác</span>
                    <strong className="detail-value">±{photo.locationAccuracyM}m</strong>
                  </div>
                )}
                {photo.address && (
                  <div className="detail-item full-width">
                    <span className="detail-label">Địa chỉ</span>
                    <strong className="detail-value">{photo.address}</strong>
                  </div>
                )}
              </div>
            </div>

            {photo.captureNote && (
              <div className="photo-detail-section">
                <h4>Ghi chú hiện trường</h4>
                <div className="photo-note-box">{photo.captureNote}</div>
              </div>
            )}

            <div className="photo-lightbox-actions">
              {driveUrl && (
                <a
                  href={driveUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="primary-button"
                  style={{ textDecoration: "none", textAlign: "center" }}
                >
                  📁 Mở trên Google Drive
                </a>
              )}
              <button
                type="button"
                className="secondary-button"
                onClick={onClose}
              >
                Đóng
              </button>
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}

export default function HomePage() {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>("signIn");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authNotice, setAuthNotice] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [projects, setProjects] = useState<ProjectDoc[]>([]);
  const [catalogMigrationReport, setCatalogMigrationReport] = useState<import("@/lib/sync").CatalogMigrationReport | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [manualProjectId, setManualProjectId] = useState("");
  const [projectDraft, setProjectDraft] = useState<ProjectDraft>(emptyProjectDraft);
  const [projectCreateState, setProjectCreateState] = useState("");
  const [projectCreateBusy, setProjectCreateBusy] = useState(false);
  const [project, setProject] = useState<ProjectDoc | null>(null);
  const [collections, setCollections] = useState<ProjectCollections>(() => emptyProjectCollections());
  const [currentMember, setCurrentMember] = useState<ProjectMemberRow | null>(null);
  const [usersDirectory, setUsersDirectory] = useState<UserProfileRow[]>([]);
  const [projectMembers, setProjectMembers] = useState<ProjectMemberRow[]>([]);
  const [accessRequests, setAccessRequests] = useState<ProjectAccessRequestRow[]>([]);
  const [accessRequestWriteState, setAccessRequestWriteState] = useState("");
  const [accessRequestBusyId, setAccessRequestBusyId] = useState<string | null>(null);
  const [accessRequestGroups, setAccessRequestGroups] = useState("gis_node");
  const [accessRequestScope, setAccessRequestScope] = useState<ContractorScope>("ALL");
  const [accessRequestContractors, setAccessRequestContractors] = useState("");
  const [selectedManagedUid, setSelectedManagedUid] = useState("");
  const [memberDraft, setMemberDraft] = useState<ProjectMemberRow | null>(null);
  const [memberSearch, setMemberSearch] = useState("");
  const [memberWriteState, setMemberWriteState] = useState("");
  const [memberWriteBusy, setMemberWriteBusy] = useState(false);
  const [accessError, setAccessError] = useState("");
  const [taskDraft, setTaskDraft] = useState(emptyTask);
  const [dailyLogDraft, setDailyLogDraft] = useState(emptyDailyLog);
  const [writeState, setWriteState] = useState("");
  const [writeBusy, setWriteBusy] = useState(false);
  const [activeTab, setActiveTab] = useState<"overview" | "tasks" | "media" | "admin">("overview");
  const [selectedMapObject, setSelectedMapObject] = useState<SelectedObject>(null);
  const [mapSearchQuery, setMapSearchQuery] = useState("");
  const [mapFilterContractor, setMapFilterContractor] = useState("");
  const [mapFilterWork, setMapFilterWork] = useState("");
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteSuccessMsg, setDeleteSuccessMsg] = useState<string | null>(null);
  const [photoSearchKeyword, setPhotoSearchKeyword] = useState("");
  const [selectedTagFilter, setSelectedTagFilter] = useState("");
  const [collapsedNodes, setCollapsedNodes] = useState<Record<string, boolean>>({});
  const [activeLightboxPhoto, setActiveLightboxPhoto] = useState<SitePhotoRow | null>(null);
  const [activeLightboxPlaylist, setActiveLightboxPlaylist] = useState<SitePhotoRow[]>([]);
  const [appVersion, setAppVersion] = useState("v0.1.0");

  useEffect(() => {
    fetch("/version.json")
      .then((res) => res.json())
      .then((data) => {
        if (data?.version) setAppVersion(`v${data.version}`);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    return observeAuth((nextUser) => {
      if (nextUser && !nextUser.emailVerified) {
        setUser(null);
        setAuthError("Tài khoản chưa kích hoạt. Vui lòng kiểm tra email xác thực trước khi đăng nhập.");
        void signOutCurrentUser();
        setAuthReady(true);
        return;
      }
      setUser(nextUser);
      setAuthReady(true);
      if (!nextUser) {
        setIsAdmin(false);
        setProjects([]);
        setSelectedProjectId("");
        setProject(null);
        setCollections(emptyProjectCollections());
        setCurrentMember(null);
        setUsersDirectory([]);
        setProjectMembers([]);
        setSelectedManagedUid("");
        setMemberDraft(null);
      }
    });
  }, []);

  useEffect(() => {
    let isCancelled = false;
    if (!user) {
      setIsAdmin(false);
      return;
    }
    getFirebaseUserAdminClaim(user, true)
      .then((admin) => {
        if (!isCancelled) setIsAdmin(admin);
      })
      .catch((error) => {
        console.warn("Failed to read Firebase token claims:", error);
        if (!isCancelled) setIsAdmin(false);
      });
    return () => {
      isCancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (!db || !user) return;
    void upsertUserProfile(db, {
      uid: user.uid,
      email: user.email,
      displayName: user.displayName,
      emailVerified: user.emailVerified
    }).catch((error) => {
      console.warn("Failed to upsert user profile:", error);
    });
  }, [user]);

  useEffect(() => {
    if (!db || !user) return;
    const firestore = db;
    setAccessError("");
    const unsubscribe = subscribeProjects(
      firestore,
      (rows) => {
        setProjects(rows);
        setAccessError("");
        setSelectedProjectId((current) => {
          const next = current || rows[0]?.id || "";
          if (!current && next) void setActiveProjectForUser(firestore, user.uid, next);
          return next;
        });
      },
      (error) => {
        setProjects([]);
        setAccessError(`Không thể tải danh sách dự án Cloud: ${error.message}`);
        console.warn("Failed to subscribe projects list:", error);
      }
    );
    return unsubscribe;
  }, [user, isAdmin]);

  useEffect(() => {
    if (!db || !user || !isAdmin) {
      setCatalogMigrationReport(null);
      return;
    }
    return subscribeLatestCatalogMigration(db, setCatalogMigrationReport, (error) => {
      console.warn("Failed to subscribe catalog migration report:", error);
    });
  }, [user, isAdmin]);

  useEffect(() => {
    if (!db || !user || !isAdmin) {
      setUsersDirectory([]);
      return;
    }
    return subscribeUsersDirectory(
      db,
      (rows) => setUsersDirectory(rows),
      (error) => console.warn("Failed to subscribe users directory:", error)
    );
  }, [user, isAdmin]);

  useEffect(() => {
    if (!db || !user || !isAdmin) {
      setAccessRequests([]);
      return;
    }
    return subscribeProjectAccessRequests(
      db,
      (rows) => setAccessRequests(rows),
      (error) => setAccessRequestWriteState(errorMessage(error))
    );
  }, [user, isAdmin]);

  useEffect(() => {
    if (!db || !user || !selectedProjectId) return;
    const firestore = db;
    setProject(null);
    setAccessError("");
    const unsubscribe = subscribeProjectDocument(
      firestore,
      selectedProjectId,
      (nextProject) => {
        setProject(nextProject);
        if (!nextProject) {
          setAccessError("Không tìm thấy dự án hoặc dự án đã bị xóa.");
        } else {
          setAccessError("");
        }
      },
      (error) => setAccessError(errorMessage(error))
    );
    return unsubscribe;
  }, [selectedProjectId, user]);

  useEffect(() => {
    if (!db || !user || !selectedProjectId) {
      setCurrentMember(null);
      return;
    }
    return subscribeCurrentProjectMember(
      db,
      selectedProjectId,
      user.uid,
      (row) => setCurrentMember(row),
      (error) => console.warn("Failed to subscribe current member:", error)
    );
  }, [selectedProjectId, user]);

  useEffect(() => {
    if (!db || !selectedProjectId || !isAdmin) {
      setProjectMembers([]);
      return;
    }
    return subscribeProjectMembers(
      db,
      selectedProjectId,
      (rows) => setProjectMembers(rows),
      (error) => console.warn("Failed to subscribe project members:", error)
    );
  }, [selectedProjectId, isAdmin]);

  useEffect(() => {
    if (!selectedManagedUid) {
      setMemberDraft(null);
      return;
    }
    const selectedUser = usersDirectory.find((item) => item.uid === selectedManagedUid);
    const existingMember = projectMembers.find((item) => item.uid === selectedManagedUid);
    if (!selectedUser && !existingMember) {
      setMemberDraft(null);
      return;
    }
    setMemberDraft({
      uid: selectedManagedUid,
      email: existingMember?.email ?? selectedUser?.email ?? "",
      displayName: existingMember?.displayName ?? selectedUser?.displayName ?? null,
      role: "MEMBER",
      isActive: existingMember?.isActive ?? true,
      contractorScope: existingMember?.contractorScope ?? "ALL",
      allowedContractors: existingMember?.allowedContractors ?? [],
      grantedByUid: existingMember?.grantedByUid ?? user?.uid ?? "",
      grantedAtEpochMs: existingMember?.grantedAtEpochMs ?? Date.now(),
      updatedAtEpochMs: existingMember?.updatedAtEpochMs ?? Date.now()
    });
  }, [selectedManagedUid, usersDirectory, projectMembers, user]);

  useEffect(() => {
    if (!db || !user || !selectedProjectId || accessError) {
      setCollections(emptyProjectCollections());
      return;
    }
    const firestore = db;
    setCollections(emptyProjectCollections());
    const unsubscribes = syncTables.map((tableName) =>
      subscribeProjectTable(
        firestore,
        selectedProjectId,
        tableName,
        (updatedTable, rows) => {
          setCollections((current) => ({ ...current, [updatedTable]: rows }));
        },
        () => undefined
      )
    );
    return () => unsubscribes.forEach((unsubscribe) => unsubscribe());
  }, [selectedProjectId, user, accessError]);

  const visibleCollections = useMemo(
    () => filterCollectionsForMember(collections, currentMember, isAdmin),
    [collections, currentMember, isAdmin]
  );

  const contractorOptions = useMemo(
    () => collectContractorOptions(collections),
    [collections]
  );

  const workNameOptions = useMemo(
    () =>
      visibleCollections.work_volume_progress
        .map((row) => text(row, "workName", "categoryName", "name"))
        .filter(Boolean)
        .filter((value, index, array) => array.indexOf(value) === index)
        .sort((left, right) => left.localeCompare(right, "vi")),
    [visibleCollections.work_volume_progress]
  );

  const filteredUsers = useMemo(() => {
    const query = memberSearch.trim().toLowerCase();
    if (!query) return usersDirectory;
    return usersDirectory.filter((row) =>
      row.email.toLowerCase().includes(query) ||
      (row.displayName ?? "").toLowerCase().includes(query)
    );
  }, [usersDirectory, memberSearch]);

  const stats = useMemo(() => {
    const latest = syncTables
      .flatMap((tableName) => visibleCollections[tableName].map((row) => Number(row.updatedAtEpochMs ?? row.createdAtEpochMs ?? 0)))
      .filter((value) => Number.isFinite(value) && value > 0)
      .sort((left, right) => right - left)[0];
    const plannedQty = visibleCollections.work_volume_progress.reduce((sum, row) => sum + Number(row.plannedQty ?? row.quantity ?? 0), 0);
    const actualQty = visibleCollections.work_volume_progress.reduce((sum, row) => sum + Number(row.actualQty ?? row.completedQty ?? 0), 0);
    const openTasks = visibleCollections.task.filter((task) => String(task.status ?? "TODO") !== "DONE").length;
    return {
      latest,
      plannedQty,
      actualQty,
      openTasks,
      materialPercent: plannedQty > 0 ? Math.min(100, (actualQty / plannedQty) * 100) : 0
    };
  }, [visibleCollections]);

  const photoNodeGroups = useMemo(() => {
    return groupPhotosByNodeAndTag(
      visibleCollections.site_photos,
      visibleCollections.gis_node,
      photoSearchKeyword,
      selectedTagFilter
    );
  }, [visibleCollections.site_photos, visibleCollections.gis_node, photoSearchKeyword, selectedTagFilter]);

  const allAvailableTags = useMemo(() => {
    const set = new Set<string>();
    for (const p of visibleCollections.site_photos) {
      extractPhotoTags(p).forEach((t) => set.add(t));
    }
    return Array.from(set).sort((a, b) => a.localeCompare(b, "vi"));
  }, [visibleCollections.site_photos]);

  const totalPhotosCount = visibleCollections.site_photos.length;
  const syncedPhotosCount = useMemo(() => {
    return visibleCollections.site_photos.filter((p) => text(p, "syncStatus") === "DONE" || Boolean(p.remoteUrl)).length;
  }, [visibleCollections.site_photos]);

  async function handleAuthSubmit() {
    if (authMode === "register" && password !== confirmPassword) {
      setAuthError("Mật khẩu xác nhận không khớp.");
      return;
    }
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");
    try {
      if (authMode === "register") {
        const newUser = await registerWithEmail(email, password);
        await sendVerificationEmail(newUser);
        await signOutCurrentUser();
        setAuthNotice("Đã gửi email kích hoạt tài khoản. Vui lòng mở email và bấm link xác thực trước khi đăng nhập.");
        setAuthMode("signIn");
      } else {
        const signedInUser = await signInWithEmail(email, password);
        if (!signedInUser.emailVerified) {
          await sendVerificationEmail(signedInUser);
          await signOutCurrentUser();
          setAuthError("Tài khoản chưa kích hoạt. Hệ thống đã gửi lại email xác thực, vui lòng kiểm tra hộp thư.");
          return;
        }
      }
      setPassword("");
      setConfirmPassword("");
    } catch (error) {
      setAuthError(errorMessage(error));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleSignOut() {
    await signOutCurrentUser();
  }

  function openManualProject() {
    const value = manualProjectId.trim();
    if (value) {
      setSelectedProjectId(value);
      if (db && user) void setActiveProjectForUser(db, user.uid, value);
      setAccessError("");
    }
  }

  function selectProject(projectId: string) {
    setSelectedProjectId(projectId);
    setAccessError("");
    if (db && user) void setActiveProjectForUser(db, user.uid, projectId || null);
  }

  async function handleCreateProject() {
    if (!db || !user || !isAdmin || !projectDraft.name.trim()) return;
    const firestore = db;
    setProjectCreateBusy(true);
    setProjectCreateState("Đang tạo dự án...");
    try {
      const createdProject = await createProjectDocument(
        firestore,
        {
          uid: user.uid,
          email: user.email,
          displayName: user.displayName
        },
        projectDraft
      );
      setProjectDraft(emptyProjectDraft);
      selectProject(createdProject.id);
      setManualProjectId("");
      setAccessError("");
      setProjectCreateState(`Đã tạo dự án ${createdProject.name}.`);
    } catch (error) {
      setProjectCreateState(errorMessage(error));
    } finally {
      setProjectCreateBusy(false);
    }
  }

  async function handleSaveMember() {
    if (!db || !user || !isAdmin || !selectedProjectId || !memberDraft) return;
    const firestore = db;
    setMemberWriteBusy(true);
    setMemberWriteState("Dang luu phan quyen...");
    try {
      await saveProjectMember(firestore, selectedProjectId, user.uid, memberDraft);
      setMemberWriteState(`Da cap quyen cho ${memberDraft.email}.`);
    } catch (error) {
      setMemberWriteState(errorMessage(error));
    } finally {
      setMemberWriteBusy(false);
    }
  }

  async function handleDeleteMember(uid: string) {
    if (!db || !isAdmin || !selectedProjectId) return;
    setMemberWriteBusy(true);
    setMemberWriteState("Dang thu hoi quyen...");
    try {
      await deleteProjectMemberRecord(db, selectedProjectId, uid);
      if (selectedManagedUid === uid) {
        setSelectedManagedUid("");
      }
      setMemberWriteState("Da thu hoi quyen project.");
    } catch (error) {
      setMemberWriteState(errorMessage(error));
    } finally {
      setMemberWriteBusy(false);
    }
  }

  async function handleDeleteProject(password: string, typedIdentity: string) {
    if (!auth?.currentUser || !selectedProjectId) return;
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      if (auth.currentUser.email && password) {
        const credential = EmailAuthProvider.credential(auth.currentUser.email, password);
        await reauthenticateWithCredential(auth.currentUser, credential);
      }
      const token = await auth.currentUser.getIdToken(true);
      await requestDeleteProjectApi(selectedProjectId, token, typedIdentity, true);
      setIsDeleteModalOpen(false);
      setSelectedProjectId("");
      setProject(null);
      setDeleteSuccessMsg("Đã xóa vĩnh viễn dự án thành công khỏi hệ thống Firebase Cloud.");
      setTimeout(() => setDeleteSuccessMsg(null), 6000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Xóa dự án thất bại.";
      setDeleteError(msg);
    } finally {
      setDeleteBusy(false);
    }
  }

  async function handleAccessRequestTransition(
    request: ProjectAccessRequestRow,
    action: AccessAdminAction,
    overrideScope?: {
      groups?: string[];
      contractorScope?: ContractorScope;
      contractors?: string[];
    }
  ) {
    if (!db || !user || !isAdmin) return;
    setAccessRequestBusyId(request.requestId);
    setAccessRequestWriteState("Đang cập nhật yêu cầu...");
    try {
      const groups = overrideScope?.groups ?? accessRequestGroups.split(",").map((value) => value.trim()).filter(Boolean);
      const contractorScope = overrideScope?.contractorScope ?? accessRequestScope;
      const contractors = overrideScope?.contractors ?? accessRequestContractors.split(",").map((value) => value.trim()).filter(Boolean);
      const next = await transitionProjectAccessRequest(
        db,
        user.uid,
        request,
        action,
        groups,
        contractorScope,
        contractors
      );
      setAccessRequests((current) => current.map((item) => item.requestId === next.requestId ? next : item));
      setAccessRequestWriteState(`Đã ${action === "APPROVE" ? "phê duyệt" : action === "REJECT" ? "từ chối" : "thu hồi"} và ghi audit.`);
    } catch (error) {
      setAccessRequestWriteState(errorMessage(error));
    } finally {
      setAccessRequestBusyId(null);
    }
  }

  async function handleCreateTask() {
    if (!db || !selectedProjectId || !taskDraft.title.trim()) return;
    const firestore = db;
    setWriteBusy(true);
    setWriteState("Đang tạo công việc...");
    try {
      await createTaskDocument(firestore, selectedProjectId, taskDraft.title, taskDraft.description);
      setTaskDraft(emptyTask);
      setWriteState("Đã ghi công việc lên Firestore thành công.");
    } catch (error) {
      setWriteState(errorMessage(error));
    } finally {
      setWriteBusy(false);
    }
  }

  async function handleCreateDailyLog() {
    if (!db || !selectedProjectId || !dailyLogDraft.workItem.trim()) return;
    const firestore = db;
    setWriteBusy(true);
    setWriteState("Đang tạo nhật ký thi công...");
    try {
      await createDailyLogDocument(firestore, selectedProjectId, dailyLogDraft);
      setDailyLogDraft(emptyDailyLog);
      setWriteState("Đã ghi nhật ký lên Firestore thành công.");
    } catch (error) {
      setWriteState(errorMessage(error));
    } finally {
      setWriteBusy(false);
    }
  }

  async function handleTaskStatus(task: Record<string, unknown>, status: TaskRow["status"]) {
    if (!db || !selectedProjectId) return;
    const firestore = db;
    setWriteState("Đang cập nhật trạng thái công việc...");
    try {
      await updateTaskStatusDocument(firestore, selectedProjectId, task, status);
      setWriteState("Đã cập nhật trạng thái công việc thành công.");
    } catch (error) {
      setWriteState(errorMessage(error));
    }
  }

  if (!firebaseReady) {
    return (
      <main className="shell centered">
        <section className="auth-card">
          <p className="eyebrow">MapSupervision Sync</p>
          <h1>Thiếu cấu hình Firebase</h1>
          <p className="muted">
            Vui lòng cấu hình các biến môi trường <code>NEXT_PUBLIC_FIREBASE_*</code> trong file <code>webapp/.env.local</code> và khởi động lại ứng dụng.
          </p>
        </section>
      </main>
    );
  }

  if (!authReady) {
    return (
      <main className="shell centered">
        <section className="auth-card" style={{ textAlign: "center" }}>
          <p className="eyebrow loading-pulse">Đang kết nối</p>
          <h1>Đang xác thực tài khoản...</h1>
        </section>
      </main>
    );
  }

  if (!user) {
    return (
      <main className="shell centered">
        <section className="auth-card">
          <p className="eyebrow">Firebase Auth</p>
          <h1>{authMode === "register" ? "Đăng ký tài khoản" : "Đăng nhập hệ thống"}</h1>
          <p className="muted">
            {authMode === "register"
              ? "Tạo tài khoản bằng email và mật khẩu. Sau khi đăng ký, tài khoản vẫn cần được admin phân quyền trong Firestore để đọc dữ liệu dự án."
              : "Sử dụng email và mật khẩu được cấp để truy cập hệ thống giám sát. Tài khoản cần được phân quyền trong Firestore để đọc dữ liệu dự án."}
          </p>
          <label>
            Địa chỉ Email
            <input
              value={email}
              type="email"
              placeholder="name@company.com"
              autoComplete="email"
              disabled={authBusy}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label>
            Mật khẩu
            <input
              value={password}
              type="password"
              placeholder="••••••••"
              autoComplete={authMode === "register" ? "new-password" : "current-password"}
              disabled={authBusy}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {authMode === "register" ? (
            <label>
              Xác nhận mật khẩu
              <input
                value={confirmPassword}
                type="password"
                placeholder="••••••••"
                autoComplete="new-password"
                disabled={authBusy}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </label>
          ) : null}
          {authError ? <p className="error">{authError}</p> : null}
          {authNotice ? <p className="success">{authNotice}</p> : null}
          <button
            className="primary-button"
            type="button"
            disabled={authBusy || !email.trim() || !password || (authMode === "register" && !confirmPassword)}
            onClick={() => void handleAuthSubmit()}
          >
            {authBusy ? "Đang xử lý..." : authMode === "register" ? "Tạo tài khoản" : "Đăng nhập"}
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={authBusy}
            onClick={() => {
              setAuthMode((current) => (current === "register" ? "signIn" : "register"));
              setAuthError("");
              setAuthNotice("");
              setConfirmPassword("");
            }}
          >
            {authMode === "register" ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký"}
          </button>
        </section>
      </main>
    );
  }

  return (
    <div className={`app-layout ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
      {sidebarCollapsed && (
        <button
          type="button"
          className="sidebar-expand-floating-btn"
          onClick={() => setSidebarCollapsed(false)}
          aria-label="Mở rộng sidebar"
        >
          ☰
        </button>
      )}

      <aside className="sidebar-panel">
        <div className="sidebar-header">
          <div className="sidebar-title-wrapper">
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <p className="eyebrow" style={{ margin: 0 }}>Android x Firebase Bridge</p>
              <span style={{
                fontSize: "10px",
                fontFamily: "var(--font-mono)",
                fontWeight: 600,
                color: "var(--accent)",
                background: "var(--accent-soft)",
                border: "1px solid var(--accent-border)",
                padding: "1px 6px",
                borderRadius: "4px",
                letterSpacing: "0.02em"
              }}>
                {appVersion}
              </span>
            </div>
            <h1>MapSupervision</h1>
          </div>
          <button
            type="button"
            className="sidebar-toggle-btn"
            onClick={() => setSidebarCollapsed(true)}
            aria-label="Thu gọn sidebar"
          >
            ◀
          </button>
        </div>

        <div className="sidebar-content">
          <div className="account-card">
            <span className="account-email">{user.email ?? "Tài khoản Firebase"}</span>
            <code className="account-uid">{user.uid}</code>
            <button className="ghost-button" type="button" onClick={() => void handleSignOut()}>
              Đăng xuất
            </button>
          </div>

          <div className="sidebar-project-section">
            <label>
              Chọn dự án hoạt động
              <select value={selectedProjectId} onChange={(event) => selectProject(event.target.value)}>
                <option value="">Chưa chọn dự án</option>
                {projects.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}{item.projectCode ? ` - ${item.projectCode}` : ""}
                  </option>
                ))}
              </select>
              <span className="field-hint">{isAdmin ? "Admin" : "User"} - {projects.length} dự án đã tải</span>
              {accessError && <span className="field-error">{accessError} <button type="button" className="ghost-button" onClick={() => window.location.reload()}>Thử lại</button></span>}
              {isAdmin && catalogMigrationReport?.status === "COMPLETED_WITH_WARNINGS" && (
                <span className="field-warning">Migration catalog: {catalogMigrationReport.counts.warning ?? 0} cảnh báo, {catalogMigrationReport.counts.discrepancy ?? 0} sai lệch.</span>
              )}
            </label>

            <label>
              Mở nhanh bằng Project ID
              <div className="inline-control">
                <input
                  value={manualProjectId}
                  placeholder="Nhập mã dự án..."
                  onChange={(event) => setManualProjectId(event.target.value)}
                />
                <button type="button" className="secondary-button" onClick={openManualProject}>
                  Mở
                </button>
              </div>
            </label>

            <div className="status-box">
              <span className={accessError ? "status-dot danger" : selectedProjectId ? "status-dot ok" : "status-dot"} />
              <div>
                <strong>{(project?.name ?? selectedProjectId) || "Chưa chọn dự án"}</strong>
                <p>{accessError || (selectedProjectId ? `Cập nhật: ${formatDateTime(stats.latest)}` : "Đang chờ chọn dự án...")}</p>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <main className="shell">
        {deleteSuccessMsg ? (
          <div style={{
            padding: "12px 18px",
            marginBottom: "16px",
            background: "rgba(16, 185, 129, 0.15)",
            border: "1px solid rgba(16, 185, 129, 0.4)",
            borderRadius: "12px",
            color: "#34d399",
            fontWeight: "600",
            fontSize: "13px",
            display: "flex",
            alignItems: "center",
            gap: "10px"
          }}>
            <span>✓</span>
            <span>{deleteSuccessMsg}</span>
          </div>
        ) : null}

      {/* Tab selector */}
      <nav className="tab-container">
        <button
          className={`tab-btn ${activeTab === "overview" ? "active" : ""}`}
          type="button"
          onClick={() => setActiveTab("overview")}
        >
          Sơ đồ & Trạng thái
        </button>
        <button
          className={`tab-btn ${activeTab === "tasks" ? "active" : ""}`}
          type="button"
          onClick={() => setActiveTab("tasks")}
        >
          Nhiệm vụ & Nhật ký
        </button>
        <button
          className={`tab-btn ${activeTab === "media" ? "active" : ""}`}
          type="button"
          onClick={() => setActiveTab("media")}
        >
          Hình ảnh & Ghi chú
        </button>
        {isAdmin ? (
          <button
            className={`tab-btn ${activeTab === "admin" ? "active" : ""}`}
            type="button"
            onClick={() => setActiveTab("admin")}
          >
            Quản trị & Cấp quyền
          </button>
        ) : null}
      </nav>

      {/* Tab Content: Overview */}
      {activeTab === "overview" && (
        <>
          {accessError ? (
            <section className="notice danger" style={{ padding: "32px", display: "grid", gap: "16px" }}>
              <h2 style={{ margin: 0, fontSize: "16px", fontWeight: "800", color: "var(--danger)" }}>
                YÊU CẦU CẤP QUYỀN TRUY CẬP DỰ ÁN
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                Tài khoản của bạn chưa được cấp quyền đọc dữ liệu cho mã dự án <code>{selectedProjectId}</code>. 
                Vui lòng liên hệ Admin và gửi thông tin tài khoản dưới đây để được cấp quyền truy cập.
              </p>
              <div style={{ display: "grid", gap: "12px", background: "var(--surface-soft)", padding: "16px", border: "1px solid var(--line)", borderRadius: "var(--radius)" }}>
                <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: "8px", fontSize: "13px" }}>
                  <span style={{ color: "var(--muted)", fontWeight: "bold" }}>EMAIL:</span>
                  <strong style={{ fontFamily: "var(--font-mono)", color: "var(--ink)" }}>{user.email}</strong>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: "8px", fontSize: "13px" }}>
                  <span style={{ color: "var(--muted)", fontWeight: "bold" }}>UID USER:</span>
                  <strong style={{ fontFamily: "var(--font-mono)", color: "var(--accent)" }}>{user.uid}</strong>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: "8px", fontSize: "13px" }}>
                  <span style={{ color: "var(--muted)", fontWeight: "bold" }}>DỰ ÁN:</span>
                  <strong style={{ fontFamily: "var(--font-mono)", color: "var(--ink)" }}>{selectedProjectId}</strong>
                </div>
              </div>
              <div className="muted" style={{ fontSize: "13px" }}>
                Admin cần tạo document Firestore tại đường dẫn sau để cho phép bạn truy cập:
                <code style={{ display: "block", marginTop: "8px", padding: "10px", background: "var(--bg)", border: "1px solid var(--line)", borderRadius: "var(--radius)", color: "var(--ink)", overflowX: "auto" }}>
                  projects/{selectedProjectId}/projectMembers/{user.uid}
                </code>
              </div>
            </section>
          ) : !selectedProjectId ? (
            <section className="notice" style={{ padding: "32px", textAlign: "center" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>CHƯA CHỌN DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án từ danh sách phía trên hoặc nhập mã dự án để mở bảng dữ liệu giám sát realtime.
              </p>
            </section>
          ) : (
            <div className="map-tab-container">
              <div className="map-view-wrapper">
                <div className="map-canvas-container">
                  <GisWebMap
                    nodes={visibleCollections.gis_node}
                    routes={visibleCollections.gis_route}
                    selected={selectedMapObject}
                    onSelect={setSelectedMapObject}
                    searchQuery={mapSearchQuery}
                    onSearchQueryChange={setMapSearchQuery}
                    filterContractor={mapFilterContractor}
                    onFilterContractorChange={setMapFilterContractor}
                    filterWork={mapFilterWork}
                    onFilterWorkChange={setMapFilterWork}
                    contractorOptions={contractorOptions}
                    workNameOptions={workNameOptions}
                  />
                </div>
              </div>

              <div className="map-right-panel">
                {/* Stats Summary cards */}
                <section className="stat-grid" style={{ display: "grid", gridTemplateColumns: "1fr", gap: "12px" }}>
                  <StatCard label="Điểm thiết kế (Node)" value={visibleCollections.gis_node.length} detail={`${visibleCollections.gis_route.length} Tuyến cáp`} />
                  <StatCard label="Nhiệm vụ đang mở" value={stats.openTasks} detail={`${visibleCollections.task.length} Nhiệm vụ tổng`} />
                  <StatCard label="Nhật ký tuần" value={visibleCollections.daily_log.length} detail={`${visibleCollections.note.length} Ghi chú nhanh`} />
                  <StatCard label="Hình ảnh thực địa" value={visibleCollections.site_photos.length} detail={`${visibleCollections.material_handover.length} Phiếu bàn giao`} />
                  <StatCard label="Tiến độ thi công" value={`${stats.materialPercent.toFixed(1)}%`} detail={`${formatNumber(stats.actualQty)} / ${formatNumber(stats.plannedQty)}`} progress={stats.materialPercent} />
                </section>

                <Panel title="Danh sách đối tượng" subtitle="Nhấp vào để định vị trên bản đồ">
                  <div className="object-list-scroller">
                    {visibleCollections.gis_node.map((node) => {
                      const code = text(node, "code", "nodeCode", "name");
                      const contractor = text(node, "contractor", "contractorName");
                      const isActive = selectedMapObject?.kind === "node" && selectedMapObject.code === code;
                      return (
                        <button
                          key={`node-${code}`}
                          type="button"
                          className={`object-list-item ${isActive ? "active" : ""}`}
                          onClick={() => setSelectedMapObject({ kind: "node", code })}
                        >
                          <span className="object-code">{code}</span>
                          <span className="object-contractor">{contractor || "Không rõ"}</span>
                        </button>
                      );
                    })}
                    {visibleCollections.gis_route.map((route) => {
                      const code = text(route, "code", "routeCode", "name");
                      const contractor = text(route, "contractor", "contractorName");
                      const isActive = selectedMapObject?.kind === "route" && selectedMapObject.code === code;
                      return (
                        <button
                          key={`route-${code}`}
                          type="button"
                          className={`object-list-item ${isActive ? "active" : ""}`}
                          onClick={() => setSelectedMapObject({ kind: "route", code })}
                        >
                          <span className="object-code">{code}</span>
                          <span className="object-contractor">{contractor || "Không rõ"}</span>
                        </button>
                      );
                    })}
                    {(!visibleCollections.gis_node.length && !visibleCollections.gis_route.length) && (
                      <div className="empty-state">Không có đối tượng nào.</div>
                    )}
                  </div>
                </Panel>
              </div>
            </div>
          )}
        </>
      )}

      {/* Tab Content: Tasks */}
      {activeTab === "tasks" && (
        <>
          {accessError ? (
            <section className="notice danger" style={{ padding: "32px", display: "grid", gap: "16px" }}>
              <h2 style={{ margin: 0, fontSize: "16px", fontWeight: "800", color: "var(--danger)" }}>
                YÊU CẦU CẤP QUYỀN TRUY CẬP DỰ ÁN
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng liên hệ Admin để cấp quyền truy cập dự án <code>{selectedProjectId}</code>.
              </p>
            </section>
          ) : !selectedProjectId ? (
            <section className="notice" style={{ padding: "32px", textAlign: "center" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>CHƯA CHỌN DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án từ danh sách phía trên hoặc nhập mã dự án để thực hiện nhiệm vụ.
              </p>
            </section>
          ) : (
            <>
              {/* Forms for writing back tasks and logs */}
              <section className="write-grid">
                <Panel title="Chỉ định nhiệm vụ mới" subtitle={writeState && writeState.includes("task") ? writeState : undefined}>
                  <TextInput label="Tiêu đề công việc" value={taskDraft.title} onChange={(value) => setTaskDraft((current) => ({ ...current, title: value }))} />
                  <TextArea label="Mô tả chi tiết" value={taskDraft.description} onChange={(value) => setTaskDraft((current) => ({ ...current, description: value }))} />
                  <button className="primary-button" type="button" disabled={writeBusy || !selectedProjectId || !taskDraft.title.trim()} onClick={() => void handleCreateTask()}>
                    Ghi công việc lên Firebase
                  </button>
                </Panel>

                <Panel title="Tạo nhật ký thi công" subtitle={writeState && writeState.includes("nhật ký") ? writeState : undefined}>
                  <TextInput label="Hạng mục công việc" value={dailyLogDraft.workItem} onChange={(value) => setDailyLogDraft((current) => ({ ...current, workItem: value }))} />
                  <div className="three-cols">
                    <TextInput label="Nhân công (người)" value={dailyLogDraft.manpower} onChange={(value) => setDailyLogDraft((current) => ({ ...current, manpower: value }))} />
                    <TextInput label="Khối lượng" value={dailyLogDraft.volume} onChange={(value) => setDailyLogDraft((current) => ({ ...current, volume: value }))} />
                    <TextInput label="Đơn vị tính" value={dailyLogDraft.unit} onChange={(value) => setDailyLogDraft((current) => ({ ...current, unit: value }))} />
                  </div>
                  <TextInput label="Phân nhóm hạng mục" value={dailyLogDraft.categoryName} onChange={(value) => setDailyLogDraft((current) => ({ ...current, categoryName: value }))} />
                  <TextInput label="Điều kiện thời tiết" value={dailyLogDraft.weather} onChange={(value) => setDailyLogDraft((current) => ({ ...current, weather: value }))} />
                  <TextArea label="Ghi chú thi công" value={dailyLogDraft.note} onChange={(value) => setDailyLogDraft((current) => ({ ...current, note: value }))} />
                  <button className="primary-button" type="button" disabled={writeBusy || !selectedProjectId || !dailyLogDraft.workItem.trim()} onClick={() => void handleCreateDailyLog()}>
                    Ghi nhật ký lên Firebase
                  </button>
                </Panel>
              </section>

              {/* Realtime Lists */}
              <section className="lists-grid">
                <Panel title="Danh sách công việc">
                  <List
                    rows={visibleCollections.task}
                    empty="Chưa có công việc nào được tạo."
                    render={(task) => (
                      <>
                        <strong>{text(task, "title") || "Công việc chưa đặt tên"}</strong>
                        <span>{text(task, "description") || "Không có mô tả chi tiết."}</span>
                        <div className="row-actions">
                          {(["TODO", "IN_PROGRESS", "DONE"] as const).map((status) => (
                            <button key={status} className={String(task.status ?? "TODO") === status ? "tiny-button active" : "tiny-button"} type="button" onClick={() => void handleTaskStatus(task, status)}>
                              {status}
                            </button>
                          ))}
                        </div>
                      </>
                    )}
                  />
                </Panel>
                <Panel title="Kế hoạch thi công">
                  <List
                    rows={visibleCollections.work_plan}
                    empty="Chưa có dữ liệu kế hoạch."
                    render={(plan) => (
                      <>
                        <strong>{text(plan, "title", "workName", "name") || "Kế hoạch thi công"}</strong>
                        <span>Vị trí: {text(plan, "nodeCode", "routeCode") || "Không xác định"} · Khối lượng: {formatNumber(plan.quantity, text(plan, "unit"))}</span>
                      </>
                    )}
                  />
                </Panel>
                <Panel title="Nhật ký thực địa">
                  <List
                    rows={visibleCollections.daily_log}
                    empty="Chưa ghi nhận nhật ký nào."
                    render={(log) => (
                      <>
                        <strong>{text(log, "workItem") || "Nhật ký thi công"}</strong>
                        <span>Nhân công: {formatNumber(log.manpower, "người")} · {text(log, "categoryName")} : {formatNumber(log.volume, text(log, "unit"))}</span>
                        <small>Thời gian: {formatDateTime(log.updatedAtEpochMs ?? log.createdAtEpochMs)}</small>
                      </>
                    )}
                  />
                </Panel>
                <Panel title="Vật tư & khối lượng">
                  <List
                    rows={[...visibleCollections.work_volume_progress, ...visibleCollections.material_declaration, ...visibleCollections.material_handover]}
                    empty="Chưa có dữ liệu kê khai vật tư."
                    render={(material) => (
                      <>
                        <strong>{text(material, "materialName", "workName", "name") || "Kê khai vật tư"}</strong>
                        <span>Vị trí: {text(material, "nodeCode") || "Không xác định"} · Số lượng: {formatNumber(material.quantity ?? material.actualQty, text(material, "unit"))}</span>
                      </>
                    )}
                  />
                </Panel>
              </section>
            </>
          )}
        </>
      )}

      {/* Tab Content: Media */}
      {activeTab === "media" && (
        <>
          {accessError ? (
            <section className="notice danger" style={{ padding: "32px", display: "grid", gap: "16px" }}>
              <h2 style={{ margin: 0, fontSize: "16px", fontWeight: "800", color: "var(--danger)" }}>
                YÊU CẦU CẤP QUYỀN TRUY CẬP DỰ ÁN
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng liên hệ Admin để cấp quyền truy cập dự án <code>{selectedProjectId}</code>.
              </p>
            </section>
          ) : !selectedProjectId ? (
            <section className="notice" style={{ padding: "32px", textAlign: "center" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>CHƯA CHỌN DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án từ danh sách phía trên hoặc nhập mã dự án để xem thư viện hình ảnh thực địa.
              </p>
            </section>
          ) : (
            <section className="media-gallery-section">
              {/* Media Toolbar */}
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
                    <div className="media-stat-pill">
                      <span>Thư mục (Node):</span>
                      <strong>{photoNodeGroups.length}</strong>
                    </div>
                    <div className="media-stat-pill">
                      <span>Tổng ảnh:</span>
                      <strong>{totalPhotosCount}</strong>
                    </div>
                    <div className="media-stat-pill">
                      <span>Đã sync Drive:</span>
                      <strong style={{ color: "var(--success)" }}>{syncedPhotosCount}</strong>
                    </div>
                    <button
                      type="button"
                      className="tiny-button"
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
                  </div>
                </div>

                {/* Tag Filter Pills */}
                {allAvailableTags.length > 0 && (
                  <div className="media-tag-pills-wrapper">
                    <span className="media-tag-pills-label">Lọc theo Tag:</span>
                    <button
                      type="button"
                      className={`media-tag-pill ${!selectedTagFilter ? "active" : ""}`}
                      onClick={() => setSelectedTagFilter("")}
                    >
                      Tất cả tag
                      <span className="pill-count">{totalPhotosCount}</span>
                    </button>
                    {allAvailableTags.map((tag) => {
                      const count = visibleCollections.site_photos.filter((p) => extractPhotoTags(p).includes(tag)).length;
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

              {/* Node Folders List */}
              {photoNodeGroups.length > 0 ? (
                <div className="node-folder-list">
                  {photoNodeGroups.map((group) => {
                    const isCollapsed = Boolean(collapsedNodes[group.nodeKey]);
                    const projectId = selectedProjectId;

                    return (
                      <div key={group.nodeKey} className="node-folder-card">
                        {/* Folder Header */}
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

                        {/* Folder Body */}
                        {!isCollapsed && (
                          <div className="node-folder-body">
                            {group.hasTags ? (
                              /* Case A: Node has tags -> Split into Tag Columns (Kanban) */
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
                                          <PhotoCardItem
                                            key={photo.id}
                                            photo={photo}
                                            onClick={() => {
                                              setActiveLightboxPhoto(photo);
                                              setActiveLightboxPlaylist(group.allPhotos);
                                            }}
                                          />
                                        ))}
                                      </div>
                                    </div>
                                  );
                                })}

                                {/* Untagged photos column if any */}
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
                                        <PhotoCardItem
                                          key={photo.id}
                                          photo={photo}
                                          onClick={() => {
                                            setActiveLightboxPhoto(photo);
                                            setActiveLightboxPlaylist(group.allPhotos);
                                          }}
                                        />
                                      ))}
                                    </div>
                                  </div>
                                )}
                              </div>
                            ) : (
                              /* Case B: Node has no tags -> Standard Grid View (No column split) */
                              <div className="photo-grid">
                                {group.allPhotos.map((photo) => (
                                  <PhotoCardItem
                                    key={photo.id}
                                    photo={photo}
                                    onClick={() => {
                                      setActiveLightboxPhoto(photo);
                                      setActiveLightboxPlaylist(group.allPhotos);
                                    }}
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
              ) : (
                <div className="empty-state" style={{ padding: "40px" }}>
                  {photoSearchKeyword || selectedTagFilter
                    ? "Không tìm thấy hình ảnh nào phù hợp với bộ lọc."
                    : "Chưa có hình ảnh nào được tải lên từ thực địa cho dự án này."}
                </div>
              )}

              {/* Quick Notes */}
              <Panel title="Ghi chú nhanh thực địa" subtitle="Đồng bộ ghi chú từ hiện trường">
                <List
                  rows={visibleCollections.note}
                  empty="Chưa có ghi chú nhanh nào."
                  render={(note) => (
                    <>
                      <strong>{text(note, "title", "objectCode") || "Ghi chú"}</strong>
                      <span>{text(note, "content", "note", "description")}</span>
                    </>
                  )}
                />
              </Panel>
            </section>
          )}

          {/* Lightbox Modal */}
          <PhotoLightboxModal
            photo={activeLightboxPhoto}
            allPhotos={activeLightboxPlaylist}
            onClose={() => setActiveLightboxPhoto(null)}
            onSelectPhoto={(next) => setActiveLightboxPhoto(next)}
          />
        </>
      )}

      {/* Tab Content: Admin */}
      {activeTab === "admin" && isAdmin && (
        <>
          <AdminApprovalQueue
            requests={accessRequests}
            projects={projects}
            usersDirectory={usersDirectory}
            writeState={accessRequestWriteState}
            busyRequestId={accessRequestBusyId}
            onTransition={(request, action, overrideScope) => void handleAccessRequestTransition(request, action, overrideScope)}
          />
          <section className="project-create-panel">
            <label>
              Tên dự án mới
              <input
                value={projectDraft.name}
                placeholder="Nhập tên dự án..."
                disabled={projectCreateBusy}
                onChange={(event) => setProjectDraft((current) => ({ ...current, name: event.target.value }))}
              />
            </label>
            <label>
              Mã dự án
              <input
                value={projectDraft.projectCode ?? ""}
                placeholder="Tùy chọn"
                disabled={projectCreateBusy}
                onChange={(event) => setProjectDraft((current) => ({ ...current, projectCode: event.target.value }))}
              />
            </label>
            <button
              className="primary-button"
              type="button"
              disabled={projectCreateBusy || !projectDraft.name.trim()}
              onClick={() => void handleCreateProject()}
            >
              {projectCreateBusy ? "Đang tạo..." : "Tạo dự án mới"}
            </button>
            {projectCreateState ? (
              <p className={projectCreateState.startsWith("Đã") ? "success" : "error"}>
                {projectCreateState}
              </p>
            ) : (
              <p className="muted">Chỉ tài khoản admin mới có thể tạo dự án trên Firestore.</p>
            )}
          </section>

          {selectedProjectId ? (
            <>
              <AdminAccessPanel
                users={filteredUsers}
                projectMembers={projectMembers}
                contractorOptions={contractorOptions}
                selectedManagedUid={selectedManagedUid}
                onSelectManagedUid={setSelectedManagedUid}
                memberDraft={memberDraft}
                onMemberDraftChange={setMemberDraft}
                memberSearch={memberSearch}
                onMemberSearchChange={setMemberSearch}
                memberWriteBusy={memberWriteBusy}
                memberWriteState={memberWriteState}
                onSaveMember={() => void handleSaveMember()}
                onDeleteMember={(uid) => void handleDeleteMember(uid)}
              />
              <ProjectDangerZone
                project={project}
                isAdmin={isAdmin}
                onOpenDeleteModal={() => {
                  setDeleteError(null);
                  setIsDeleteModalOpen(true);
                }}
              />
            </>
          ) : (
            <section className="notice" style={{ padding: "32px", textAlign: "center", marginTop: "24px" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>QUẢN LÝ THÀNH VIÊN & DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án cụ thể ở phía trên để quản lý thành viên và phân quyền truy cập nhà thầu.
              </p>
            </section>
          )}
        </>
      )}
    </main>

    <DeleteProjectModal
      isOpen={isDeleteModalOpen}
      project={project}
      busy={deleteBusy}
      error={deleteError}
      onClose={() => {
        if (!deleteBusy) {
          setIsDeleteModalOpen(false);
          setDeleteError(null);
        }
      }}
      onConfirm={handleDeleteProject}
    />
    </div>
  );
}

function StatCard({
  label,
  value,
  detail,
  progress
}: {
  label: string;
  value: string | number;
  detail: string;
  progress?: number;
}) {
  return (
    <section className="stat-card">
      <span>{label}</span>
      <strong>{value}</strong>
      {progress !== undefined && (
        <div className="progress-bar-container">
          <div className="progress-bar-fill" style={{ width: `${progress}%` }} />
        </div>
      )}
      <small>{detail}</small>
    </section>
  );
}

function Panel({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>{title}</h2>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
      {children}
    </section>
  );
}

function TextInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label style={{ display: "grid", gap: "6px" }}>
      {label}
      <input value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function TextArea({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label style={{ display: "grid", gap: "6px" }}>
      {label}
      <textarea value={value} rows={4} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function AdminAccessPanel({
  users,
  projectMembers,
  contractorOptions,
  selectedManagedUid,
  onSelectManagedUid,
  memberDraft,
  onMemberDraftChange,
  memberSearch,
  onMemberSearchChange,
  memberWriteBusy,
  memberWriteState,
  onSaveMember,
  onDeleteMember
}: {
  users: UserProfileRow[];
  projectMembers: ProjectMemberRow[];
  contractorOptions: string[];
  selectedManagedUid: string;
  onSelectManagedUid: (uid: string) => void;
  memberDraft: ProjectMemberRow | null;
  onMemberDraftChange: (member: ProjectMemberRow | null) => void;
  memberSearch: string;
  onMemberSearchChange: (value: string) => void;
  memberWriteBusy: boolean;
  memberWriteState: string;
  onSaveMember: () => void;
  onDeleteMember: (uid: string) => void;
}) {
  return (
    <section className="admin-grid">
      <Panel title="Nguoi dung da dang nhap" subtitle={`${users.length} tai khoan`}>
        <TextInput label="Tim theo email" value={memberSearch} onChange={onMemberSearchChange} />
        <div className="item-list compact-list">
          {users.slice(0, 12).map((row) => (
            <button
              key={row.uid}
              type="button"
              className={selectedManagedUid === row.uid ? "admin-list-item active" : "admin-list-item"}
              onClick={() => onSelectManagedUid(row.uid)}
            >
              <strong>{row.email}</strong>
              <span>{row.displayName || row.uid}</span>
              <small>{row.projectIds.length ? row.projectIds.join(", ") : "Chua co project"}</small>
            </button>
          ))}
          {!users.length ? <div className="empty-state">Chua co user nao dang nhap.</div> : null}
        </div>
      </Panel>

      <Panel title="Thanh vien project" subtitle={`${projectMembers.length} member`}>
        <div className="item-list compact-list">
          {projectMembers.map((member) => (
            <div key={member.uid} className="admin-list-item">
              <strong>{member.email}</strong>
              <span>{member.contractorScope} · {member.isActive ? "Active" : "Inactive"}</span>
              <small>{member.allowedContractors.length ? member.allowedContractors.join(", ") : "All contractors"}</small>
              <div className="row-actions">
                <button className="tiny-button" type="button" onClick={() => onSelectManagedUid(member.uid)}>
                  Chinh sua
                </button>
                <button className="tiny-button" type="button" onClick={() => onDeleteMember(member.uid)}>
                  Thu hoi
                </button>
              </div>
            </div>
          ))}
          {!projectMembers.length ? <div className="empty-state">Project chua co member nao.</div> : null}
        </div>
      </Panel>

      <Panel title="Cap quyen project" subtitle={memberWriteState || "Them user vao project va gan contractor scope"}>
        {memberDraft ? (
          <>
            <TextInput
              label="Email"
              value={memberDraft.email}
              onChange={(value) => onMemberDraftChange({ ...memberDraft, email: value })}
            />
            <TextInput
              label="Display name"
              value={memberDraft.displayName ?? ""}
              onChange={(value) => onMemberDraftChange({ ...memberDraft, displayName: value || null })}
            />
            <label>
              Trang thai
              <select
                value={memberDraft.isActive ? "ACTIVE" : "INACTIVE"}
                onChange={(event) => onMemberDraftChange({ ...memberDraft, isActive: event.target.value === "ACTIVE" })}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </label>
            <label>
              Contractor scope
              <select
                value={memberDraft.contractorScope}
                onChange={(event) =>
                  onMemberDraftChange({
                    ...memberDraft,
                    contractorScope: event.target.value as ContractorScope,
                    allowedContractors: event.target.value === "SCOPED" ? memberDraft.allowedContractors : []
                  })
                }
              >
                <option value="ALL">ALL</option>
                <option value="SCOPED">SCOPED</option>
              </select>
            </label>
            {memberDraft.contractorScope === "SCOPED" ? (
              <div className="contractor-picker">
                {contractorOptions.map((contractor) => {
                  const checked = memberDraft.allowedContractors.includes(contractor);
                  return (
                    <label key={contractor} className="contractor-option">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(event) => {
                          const nextValues = event.target.checked
                             ? [...memberDraft.allowedContractors, contractor]
                             : memberDraft.allowedContractors.filter((value) => value !== contractor);
                          onMemberDraftChange({ ...memberDraft, allowedContractors: nextValues });
                        }}
                      />
                      <span>{contractor}</span>
                    </label>
                  );
                })}
                {!contractorOptions.length ? <div className="empty-state">Project chua co contractor de gan scope.</div> : null}
              </div>
            ) : null}
            <div className="row-actions">
              <button className="primary-button" type="button" disabled={memberWriteBusy || !memberDraft.email.trim()} onClick={onSaveMember}>
                Luu phan quyen
              </button>
              <button className="secondary-button" type="button" disabled={memberWriteBusy} onClick={() => onSelectManagedUid("")}>
                Bo chon
              </button>
            </div>
          </>
        ) : (
          <div className="empty-state">Chon mot user o cot ben trai hoac tu danh sach member de chinh sua.</div>
        )}
      </Panel>
    </section>
  );
}

const STANDARD_DATA_GROUPS = [
  "DEFAULT",
  "MAP",
  "GIS",
  "MATERIALS",
  "REPORTING",
  "NOTES",
  "TASKS"
];

function AdminApprovalQueue({
  requests,
  projects,
  usersDirectory,
  writeState,
  busyRequestId,
  onTransition
}: {
  requests: ProjectAccessRequestRow[];
  projects: ProjectDoc[];
  usersDirectory: UserProfileRow[];
  writeState: string;
  busyRequestId: string | null;
  onTransition: (
    request: ProjectAccessRequestRow,
    action: AccessAdminAction,
    overrideScope?: {
      groups?: string[];
      contractorScope?: ContractorScope;
      contractors?: string[];
    }
  ) => void;
}) {
  const [statusFilter, setStatusFilter] = useState<"ALL" | AccessRequestStatus>("ALL");
  const [modalRequest, setModalRequest] = useState<ProjectAccessRequestRow | null>(null);
  const [selectedGroups, setSelectedGroups] = useState<string[]>(["DEFAULT"]);
  const [customGroupInput, setCustomGroupInput] = useState("");
  const [selectedScope, setSelectedScope] = useState<ContractorScope>("ALL");
  const [contractorsInput, setContractorsInput] = useState("");

  function openApprovalModal(request: ProjectAccessRequestRow) {
    setModalRequest(request);
    setSelectedGroups(request.allowedDataGroups.length ? request.allowedDataGroups : ["DEFAULT", "MAP", "GIS"]);
    setCustomGroupInput("");
    setSelectedScope(request.contractorScope || "ALL");
    setContractorsInput(request.allowedContractors.join(", "));
  }

  function closeModal() {
    setModalRequest(null);
  }

  function toggleDataGroup(group: string) {
    setSelectedGroups((current) =>
      current.includes(group) ? current.filter((g) => g !== group) : [...current, group]
    );
  }

  function addCustomGroup() {
    const trimmed = customGroupInput.trim();
    if (trimmed && !selectedGroups.includes(trimmed)) {
      setSelectedGroups((current) => [...current, trimmed]);
      setCustomGroupInput("");
    }
  }

  const isValidScope =
    selectedGroups.length > 0 &&
    (selectedScope === "ALL" || contractorsInput.split(",").map((c) => c.trim()).filter(Boolean).length > 0);

  function confirmApproval() {
    if (!modalRequest || !isValidScope) return;
    const finalContractors = contractorsInput.split(",").map((c) => c.trim()).filter(Boolean);
    onTransition(modalRequest, "APPROVE", {
      groups: selectedGroups,
      contractorScope: selectedScope,
      contractors: finalContractors
    });
    closeModal();
  }

  const filteredRequests = useMemo(() => {
    if (statusFilter === "ALL") return requests;
    return requests.filter((r) => r.status === statusFilter);
  }, [requests, statusFilter]);

  return (
    <section className="panel" style={{ marginBottom: "24px" }}>
      <div className="panel-heading">
        <h2>Hàng đợi phê duyệt truy cập Firebase (Admin)</h2>
        <p>{writeState || "Mọi quyết định phê duyệt/thu hồi được đồng bộ hai chiều với Android và lưu vết audit."}</p>
      </div>

      <div className="filter-tabs">
        {(["ALL", "PENDING", "APPROVED", "REJECTED", "REVOKED"] as const).map((status) => (
          <button
            key={status}
            type="button"
            className={`filter-tab-btn ${statusFilter === status ? "active" : ""}`}
            onClick={() => setStatusFilter(status)}
          >
            {status === "ALL"
              ? `Tất cả (${requests.length})`
              : status === "PENDING"
              ? `Chờ duyệt (${requests.filter((r) => r.status === "PENDING").length})`
              : status === "APPROVED"
              ? `Đã duyệt (${requests.filter((r) => r.status === "APPROVED").length})`
              : status === "REJECTED"
              ? `Từ chối (${requests.filter((r) => r.status === "REJECTED").length})`
              : `Thu hồi (${requests.filter((r) => r.status === "REVOKED").length})`}
          </button>
        ))}
      </div>

      <div className="item-list compact-list">
        {filteredRequests.map((request) => {
          const matchedUser = usersDirectory.find((u) => u.uid === request.userId);
          const matchedProject = projects.find((p) => p.id === request.projectId);
          const userLabel = matchedUser?.email || request.userId;
          const projectLabel = matchedProject?.name || request.projectId;

          return (
            <div key={request.requestId} className="admin-list-item">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "8px" }}>
                <div>
                  <strong>{projectLabel}</strong>
                  <span style={{ display: "block", color: "var(--muted)", fontSize: "12px" }}>
                    Người dùng: <strong style={{ color: "var(--ink)" }}>{userLabel}</strong> ({request.userId})
                  </span>
                </div>
                <span
                  className={`approval-status-badge ${
                    request.status === "PENDING"
                      ? "badge-status-pending"
                      : request.status === "APPROVED"
                      ? "badge-status-approved"
                      : request.status === "REJECTED"
                      ? "badge-status-rejected"
                      : "badge-status-revoked"
                  }`}
                >
                  {request.status === "PENDING"
                    ? "CHỜ DUYỆT"
                    : request.status === "APPROVED"
                    ? "ĐÃ DUYỆT"
                    : request.status === "REJECTED"
                    ? "TỪ CHỐI"
                    : "THU HỒI"}
                </span>
              </div>

              {request.status === "APPROVED" && (
                <div style={{ fontSize: "12px", color: "var(--ink-soft)", marginTop: "4px" }}>
                  <span>Nhóm dữ liệu: <strong>{request.allowedDataGroups.join(", ") || "Mặc định"}</strong></span> ·{" "}
                  <span>Nhà thầu: <strong>{request.contractorScope === "ALL" ? "Toàn bộ" : request.allowedContractors.join(", ") || "Chưa gán"}</strong></span>
                </div>
              )}

              <small>Cập nhật: {formatDateTime(request.updatedAtEpochMs)}</small>

              <div className="row-actions">
                {request.status === "PENDING" ? (
                  <>
                    <button
                      className="tiny-button"
                      type="button"
                      style={{ background: "var(--accent)", color: "#08090d", fontWeight: "700" }}
                      disabled={busyRequestId === request.requestId}
                      onClick={() => openApprovalModal(request)}
                    >
                      Phê duyệt
                    </button>
                    <button
                      className="tiny-button"
                      type="button"
                      disabled={busyRequestId === request.requestId}
                      onClick={() => onTransition(request, "REJECT")}
                    >
                      Từ chối
                    </button>
                  </>
                ) : null}

                {request.status === "APPROVED" ? (
                  <>
                    <button
                      className="tiny-button"
                      type="button"
                      disabled={busyRequestId === request.requestId}
                      onClick={() => openApprovalModal(request)}
                    >
                      Sửa phạm vi
                    </button>
                    <button
                      className="tiny-button"
                      type="button"
                      style={{ color: "var(--danger)" }}
                      disabled={busyRequestId === request.requestId}
                      onClick={() => onTransition(request, "REVOKE")}
                    >
                      Thu hồi quyền
                    </button>
                  </>
                ) : null}

                {request.status === "REJECTED" || request.status === "REVOKED" ? (
                  <button
                    className="tiny-button"
                    type="button"
                    disabled={busyRequestId === request.requestId}
                    onClick={() => openApprovalModal(request)}
                  >
                    Cấp lại quyền
                  </button>
                ) : null}
              </div>
            </div>
          );
        })}
        {!filteredRequests.length ? (
          <div className="empty-state">Không có yêu cầu nào trong trạng thái này.</div>
        ) : null}
      </div>

      {modalRequest && (
        <div className="modal-backdrop" onClick={closeModal}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <h3 style={{ margin: 0, fontSize: "16px", fontWeight: "800" }}>
              Cấu hình phạm vi & Phê duyệt quyền
            </h3>
            <p style={{ margin: 0, fontSize: "13px", color: "var(--muted)" }}>
              Dự án: <strong style={{ color: "var(--ink)" }}>{modalRequest.projectId}</strong> · Người dùng: <strong style={{ color: "var(--ink)" }}>{usersDirectory.find((u) => u.uid === modalRequest.userId)?.email || modalRequest.userId}</strong>
            </p>

            <div style={{ display: "grid", gap: "8px" }}>
              <label style={{ fontSize: "13px", fontWeight: "700" }}>
                Nhóm dữ liệu được phép truy cập (bắt buộc ít nhất 1 nhóm)
              </label>
              <div className="scope-chips-container">
                {STANDARD_DATA_GROUPS.map((group) => {
                  const isSelected = selectedGroups.includes(group);
                  return (
                    <span
                      key={group}
                      className={`scope-chip ${isSelected ? "selected" : ""}`}
                      onClick={() => toggleDataGroup(group)}
                    >
                      {group}
                    </span>
                  );
                })}
              </div>
              <div style={{ display: "flex", gap: "8px", marginTop: "4px" }}>
                <input
                  type="text"
                  placeholder="Thêm nhóm dữ liệu khác..."
                  value={customGroupInput}
                  onChange={(e) => setCustomGroupInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      addCustomGroup();
                    }
                  }}
                  style={{ flex: 1 }}
                />
                <button type="button" className="secondary-button" onClick={addCustomGroup}>
                  Thêm
                </button>
              </div>
              {!selectedGroups.length && (
                <small style={{ color: "var(--danger)" }}>Vui lòng chọn hoặc thêm ít nhất một nhóm dữ liệu.</small>
              )}
            </div>

            <div style={{ display: "grid", gap: "8px" }}>
              <label style={{ fontSize: "13px", fontWeight: "700" }}>
                Phạm vi nhà thầu (Contractor Scope)
              </label>
              <div style={{ display: "flex", gap: "16px" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer" }}>
                  <input
                    type="radio"
                    name="scopeRadio"
                    checked={selectedScope === "ALL"}
                    onChange={() => setSelectedScope("ALL")}
                  />
                  <span>Toàn bộ nhà thầu (ALL)</span>
                </label>
                <label style={{ display: "flex", alignItems: "center", gap: "6px", cursor: "pointer" }}>
                  <input
                    type="radio"
                    name="scopeRadio"
                    checked={selectedScope === "SCOPED"}
                    onChange={() => setSelectedScope("SCOPED")}
                  />
                  <span>Giới hạn nhà thầu (SCOPED)</span>
                </label>
              </div>
              {selectedScope === "SCOPED" && (
                <div style={{ display: "grid", gap: "4px" }}>
                  <input
                    type="text"
                    placeholder="Nhập mã/tên nhà thầu (phân cách bằng dấu phẩy)..."
                    value={contractorsInput}
                    onChange={(e) => setContractorsInput(e.target.value)}
                  />
                  {!contractorsInput.split(",").map((c) => c.trim()).filter(Boolean).length && (
                    <small style={{ color: "var(--danger)" }}>Phạm vi SCOPED yêu cầu nhập ít nhất một nhà thầu.</small>
                  )}
                </div>
              )}
            </div>

            <div className="row-actions" style={{ justifyContent: "flex-end", marginTop: "12px" }}>
              <button type="button" className="secondary-button" onClick={closeModal}>
                Hủy bỏ
              </button>
              <button
                type="button"
                className="primary-button"
                disabled={!isValidScope || busyRequestId === modalRequest.requestId}
                onClick={confirmApproval}
              >
                {busyRequestId === modalRequest.requestId ? "Đang xử lý..." : "Xác nhận phê duyệt"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function List<T extends Record<string, unknown>>({
  rows,
  render,
  empty
}: {
  rows: T[];
  render: (row: T) => React.ReactNode;
  empty: string;
}) {
  if (!rows.length) return <div className="empty-state">{empty}</div>;
  return (
    <div className="item-list">
      {rows.slice(0, 8).map((row, index) => (
        <article key={String(row.id ?? index)} className="list-item">
          {render(row)}
        </article>
      ))}
    </div>
  );
}

function ProjectDangerZone({
  project,
  isAdmin,
  onOpenDeleteModal
}: {
  project: ProjectDoc | null;
  isAdmin: boolean;
  onOpenDeleteModal: () => void;
}) {
  if (!isAdmin || !project) return null;
  return (
    <section className="danger-zone-container">
      <div className="danger-zone-header">
        <span className="danger-badge">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          Danger Zone
        </span>
        <h3 style={{ margin: 0, fontSize: "15px", fontWeight: "700", color: "#fca5a5" }}>
          Vùng nguy hiểm — Quản lý hủy bỏ dự án
        </h3>
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: "16px", marginTop: "12px" }}>
        <div style={{ maxWidth: "600px" }}>
          <p style={{ margin: "0 0 4px 0", fontSize: "13px", fontWeight: "600", color: "var(--ink)" }}>
            Xóa vĩnh viễn dự án &quot;{project.name}&quot; ({project.projectCode || project.id})
          </p>
          <p style={{ margin: 0, fontSize: "12px", color: "var(--muted)", lineHeight: "1.5" }}>
            Hành động này sẽ xóa hoàn toàn document dự án trên Firebase Firestore, hủy bỏ quyền của toàn bộ thành viên, dọn dẹp các subcollections (GIS, nhiệm vụ, nhật ký, ảnh) và gỡ bỏ khỏi Catalog. Thao tác này không thể hoàn tác.
          </p>
        </div>
        <button
          type="button"
          className="danger-button-primary"
          onClick={onOpenDeleteModal}
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            <line x1="10" y1="11" x2="10" y2="17"/>
            <line x1="14" y1="11" x2="14" y2="17"/>
          </svg>
          Xóa dự án này
        </button>
      </div>
    </section>
  );
}

function DeleteProjectModal({
  isOpen,
  project,
  busy,
  error,
  onClose,
  onConfirm
}: {
  isOpen: boolean;
  project: ProjectDoc | null;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onConfirm: (password: string, typedIdentity: string) => Promise<void>;
}) {
  const [typedIdentity, setTypedIdentity] = useState("");
  const [password, setPassword] = useState("");
  const [confirmedRisk, setConfirmedRisk] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setTypedIdentity("");
      setPassword("");
      setConfirmedRisk(false);
    }
  }, [isOpen]);

  if (!isOpen || !project) return null;

  const targetIdentifier = project.projectCode || project.name;
  const isMatch = typedIdentity.trim() === targetIdentifier.trim() || typedIdentity.trim() === project.name.trim() || typedIdentity.trim() === project.id.trim();
  const canDelete = isMatch && password.length >= 6 && confirmedRisk && !busy;

  return (
    <div className="glass-danger-modal-overlay" onClick={(e) => { if (e.target === e.currentTarget && !busy) onClose(); }}>
      <div className="glass-danger-modal-card">
        <div style={{ display: "flex", alignItems: "flex-start", gap: "14px" }}>
          <div style={{
            width: "42px",
            height: "42px",
            borderRadius: "12px",
            background: "rgba(225, 29, 72, 0.2)",
            border: "1px solid rgba(244, 63, 94, 0.5)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#ff6b81",
            flexShrink: 0
          }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div>
            <h3 style={{ margin: "0 0 4px 0", fontSize: "17px", fontWeight: "800", color: "#ffffff" }}>
              Xác nhận xóa vĩnh viễn dự án
            </h3>
            <p style={{ margin: 0, fontSize: "13px", color: "var(--muted)" }}>
              Bạn đang thực hiện yêu cầu xóa dự án <strong style={{ color: "#ff8f00" }}>{project.name}</strong>.
            </p>
          </div>
        </div>

        <div className="danger-impact-list">
          <div>⚠️ <strong>Cảnh báo mất dữ liệu vĩnh viễn:</strong></div>
          <div>• Toàn bộ bản ghi GIS, tuyến cáp, điểm mốc và tiến độ công trình sẽ bị xóa.</div>
          <div>• Nhật ký thi công, báo cáo giám sát và phân quyền thành viên sẽ bị hủy bỏ.</div>
          <div>• Bản ghi danh mục Cloud Catalog sẽ được gỡ bỏ ngay lập tức.</div>
        </div>

        {error ? (
          <div style={{
            padding: "10px 14px",
            borderRadius: "8px",
            background: "rgba(239, 68, 68, 0.2)",
            border: "1px solid rgba(239, 68, 68, 0.5)",
            color: "#fca5a5",
            fontSize: "12px",
            fontWeight: "600"
          }}>
            {error}
          </div>
        ) : null}

        <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
          <div>
            <label style={{ marginBottom: "6px", fontSize: "12px", color: "var(--ink)" }}>
              1. Nhập chính xác tên hoặc mã dự án (<code style={{ color: "#ff6b81", userSelect: "all" }}>{targetIdentifier}</code>):
            </label>
            <input
              type="text"
              className="danger-input-field"
              placeholder={`Nhập "${targetIdentifier}" để xác nhận...`}
              value={typedIdentity}
              disabled={busy}
              onChange={(e) => setTypedIdentity(e.target.value)}
            />
          </div>

          <div>
            <label style={{ marginBottom: "6px", fontSize: "12px", color: "var(--ink)" }}>
              2. Mật khẩu tài khoản Admin (xác thực quyền hạn):
            </label>
            <input
              type="password"
              className="danger-input-field"
              placeholder="Nhập mật khẩu tài khoản hiện tại..."
              value={password}
              disabled={busy}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <label style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", textTransform: "none", fontSize: "12px", color: "#fca5a5" }}>
            <input
              type="checkbox"
              checked={confirmedRisk}
              disabled={busy}
              onChange={(e) => setConfirmedRisk(e.target.checked)}
              style={{ width: "16px", height: "16px", accentColor: "#e11d48", cursor: "pointer" }}
            />
            <span>Tôi hiểu toàn bộ dữ liệu dự án này sẽ bị xóa vĩnh viễn và không thể phục hồi.</span>
          </label>
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "8px" }}>
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={onClose}
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            className="danger-button-primary"
            disabled={!canDelete}
            onClick={() => void onConfirm(password, typedIdentity)}
          >
            {busy ? (
              <>
                <svg className="animate-spin" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <circle cx="12" cy="12" r="10" strokeOpacity="0.25"/>
                  <path d="M12 2a10 10 0 0 1 10 10" strokeOpacity="1"/>
                </svg>
                Đang xóa dự án...
              </>
            ) : (
              "Tôi hiểu rủi ro, Xóa dự án này"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
