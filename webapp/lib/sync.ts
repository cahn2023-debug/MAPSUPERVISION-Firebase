import {
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  doc,
  documentId,
  onSnapshot,
  orderBy,
  limit,
  query,
  serverTimestamp,
  setDoc,
  runTransaction,
  updateDoc,
  where,
  writeBatch,
  type DocumentData,
  type Firestore,
  type QuerySnapshot,
  type Unsubscribe
} from "firebase/firestore";

export type SyncEnvelope<T extends Record<string, unknown>> = {
  id?: string;
  projectId?: string;
  tableName?: string;
  data?: T;
  updatedAtEpochMs?: number;
  isDeleted?: boolean;
  sourceDeviceId?: string;
  lastSyncedAtEpochMs?: number;
};

export type ProjectDoc = {
  id: string;
  name: string;
  slug: string;
  projectCode: string | null;
  mediaStorageProvider: "GOOGLE_DRIVE";
  mediaStorageFolderId: string;
  mediaStorageFolderUrl: string;
  mediaStorageUpdatedAtEpochMs: number;
  updatedAtEpochMs: number;
  isDeleted: boolean;
};

export type ProjectDraft = {
  name: string;
  projectCode?: string;
};

export type CatalogMigrationReport = {
  status: string;
  counts: { warning?: number; discrepancy?: number };
  warnings?: string[];
  discrepancies?: string[];
};

export type ProjectRow = {
  id: string;
  createdByUid: string;
  name: string;
  slug: string;
  projectCode: string | null;
  isArchived: boolean;
  createdAtEpochMs: number;
  metadataVersion: 3;
  updatedAtEpochMs: number;
  storageMode: "PROJECT_DB";
  projectDbPath: string;
  mediaStorageProvider: "GOOGLE_DRIVE";
  mediaStorageFolderId: string;
  mediaStorageFolderUrl: string;
  mediaStorageUpdatedAtEpochMs: number;
  isDeleted: boolean;
  deletedAtEpochMs: number | null;
};

export type ContractorScope = "ALL" | "SCOPED";

export type AccessRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "REVOKED";
export type AccessAdminAction = "APPROVE" | "REJECT" | "REVOKE";

export type ProjectAccessRequestRow = {
  requestId: string;
  projectId: string;
  userId: string;
  status: AccessRequestStatus;
  allowedDataGroups: string[];
  contractorScope: ContractorScope;
  allowedContractors: string[];
  approvedBy: string | null;
  approvedAtEpochMs: number | null;
  requestedAtEpochMs: number | null;
  updatedAtEpochMs: number;
};

export type UserProfileRow = {
  uid: string;
  email: string;
  displayName: string | null;
  emailVerified: boolean;
  createdAtEpochMs: number;
  lastLoginAtEpochMs: number;
  updatedAtEpochMs: number;
  isDisabled: boolean;
  projectIds: string[];
};

export type ProjectMemberRow = {
  uid: string;
  email: string;
  displayName: string | null;
  role: "MEMBER";
  isActive: boolean;
  contractorScope: ContractorScope;
  allowedContractors: string[];
  grantedByUid: string;
  grantedAtEpochMs: number;
  updatedAtEpochMs: number;
};

export type TaskRow = {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: "TODO" | "IN_PROGRESS" | "DONE" | string;
  createdAtEpochMs: number;
  completedAtEpochMs: number | null;
  objectNodeId: string | null;
  objectRouteId: string | null;
  updatedAtEpochMs: number;
  isDeleted: boolean;
  deletedAtEpochMs: number | null;
};

export type DailyLogRow = {
  id: string;
  projectId: string;
  workItem: string;
  manpower: number;
  note: string;
  createdAtEpochMs: number;
  weather: string;
  temperature: number;
  dateEpochDay: number;
  volume: number;
  unit: string;
  categoryName: string;
  batchGroupId: string;
  linkedWorkPlanId: string | null;
  plannedWorkName: string;
  plannedQuantity: number;
  plannedUnit: string;
  photoMatchOffsetMinutes: number;
  nodeId: string | null;
  routeId: string | null;
  plannedNodeId: string | null;
  plannedRouteId: string | null;
  updatedAtEpochMs: number;
  isDeleted: boolean;
  deletedAtEpochMs: number | null;
};

export type SitePhotoRow = {
  id: string;
  projectId: string;
  objectCode: string;
  tagCodesCsv?: string | null;
  statusTag?: string | null;
  engineer: string;
  capturedAtEpochMs: number;
  updatedAtEpochMs: number;
  latitude?: number | null;
  longitude?: number | null;
  locationAccuracyM?: number | null;
  isGpsMocked?: boolean | number;
  locationStatus?: string | null;
  address: string | null;
  captureNote: string | null;
  matchedNodeId?: string | null;
  matchedRouteId?: string | null;
  remoteUrl: string | null;
  syncErrorMessage?: string | null;
  syncStatus: string;
  mimeType: string;
  mediaType: string;
  isDeleted: boolean;
};

export type ReportDraftRow = {
  id: string;
  projectId: string;
  title: string;
  executiveSummary: string;
  riskSection: string;
  recommendedActionsCsv: string;
  createdAtEpochMs: number;
  updatedAtEpochMs: number;
  isDeleted: boolean;
};

export type SyncTableName =
  | "gis_node"
  | "gis_route"
  | "task"
  | "note"
  | "work_plan"
  | "daily_log"
  | "site_photos"
  | "work_volume_progress"
  | "material_declaration"
  | "material_handover"
  | "report_draft";

export const syncTables: SyncTableName[] = [
  "gis_node",
  "gis_route",
  "task",
  "note",
  "work_plan",
  "daily_log",
  "site_photos",
  "work_volume_progress",
  "material_declaration",
  "material_handover",
  "report_draft"
];

export type ProjectCollections = Record<SyncTableName, Record<string, unknown>[]>;

export const emptyProjectCollections = (): ProjectCollections => ({
  gis_node: [],
  gis_route: [],
  task: [],
  note: [],
  work_plan: [],
  daily_log: [],
  site_photos: [],
  work_volume_progress: [],
  material_declaration: [],
  material_handover: [],
  report_draft: []
});

export function unpackEnvelope<T extends Record<string, unknown>>(
  documentId: string,
  source: DocumentData
): T {
  const envelope = source as SyncEnvelope<T>;
  const data = envelope.data ?? (source as T);
  return {
    ...data,
    id: String(data.id ?? envelope.id ?? documentId),
    projectId: String(data.projectId ?? envelope.projectId ?? ""),
    updatedAtEpochMs: Number(data.updatedAtEpochMs ?? envelope.updatedAtEpochMs ?? 0),
    isDeleted: Boolean(data.isDeleted ?? envelope.isDeleted ?? false)
  } as T;
}

export function createEnvelope<T extends Record<string, unknown>>(
  tableName: string,
  projectId: string,
  id: string,
  data: T,
  now: number
): SyncEnvelope<T> & { createdAt: ReturnType<typeof serverTimestamp> } {
  return {
    id,
    projectId,
    tableName,
    data,
    updatedAtEpochMs: now,
    isDeleted: Boolean(data.isDeleted),
    sourceDeviceId: "webapp",
    lastSyncedAtEpochMs: now,
    createdAt: serverTimestamp()
  };
}

function slugifyProjectName(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

export function normalizeGoogleDriveFolderInput(value: string): { folderId: string; folderUrl: string } {
  const trimmed = value.trim();
  if (!trimmed) return { folderId: "", folderUrl: "" };

  let folderId = trimmed;
  try {
    const parsed = new URL(trimmed);
    const folderMatch = parsed.pathname.match(/\/folders\/([^/?#]+)/);
    folderId = folderMatch?.[1] ?? parsed.searchParams.get("id") ?? trimmed;
  } catch {
    const folderMatch = trimmed.match(/\/folders\/([^/?#]+)/);
    folderId = folderMatch?.[1] ?? trimmed;
  }

  folderId = decodeURIComponent(folderId).trim();
  if (!/^[A-Za-z0-9_-]{10,}$/.test(folderId)) {
    throw new Error("Google Drive folder URL/ID khong hop le.");
  }

  return {
    folderId,
    folderUrl: `https://drive.google.com/drive/folders/${encodeURIComponent(folderId)}`
  };
}

export function buildProjectMediaPreviewUrl(projectId: string, photoId: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}/media?photoId=${encodeURIComponent(photoId)}`;
}

function projectDocFromRaw(id: string, raw: Record<string, unknown>): ProjectDoc {
  return {
    id,
    name: String(raw.name ?? id),
    slug: String(raw.slug ?? ""),
    projectCode: raw.projectCode ? String(raw.projectCode) : null,
    mediaStorageProvider: "GOOGLE_DRIVE",
    mediaStorageFolderId: raw.mediaStorageFolderId ? String(raw.mediaStorageFolderId) : "",
    mediaStorageFolderUrl: raw.mediaStorageFolderUrl ? String(raw.mediaStorageFolderUrl) : "",
    mediaStorageUpdatedAtEpochMs: Number(raw.mediaStorageUpdatedAtEpochMs ?? 0),
    updatedAtEpochMs: Number(raw.updatedAtEpochMs ?? 0),
    isDeleted: Boolean(raw.isDeleted ?? false)
  };
}

export async function createProjectDocument(
  firestore: Firestore,
  creator: { uid: string; email?: string | null; displayName?: string | null },
  draft: ProjectDraft
): Promise<ProjectDoc> {
  const now = Date.now();
  const ref = doc(collection(firestore, "projects"));
  const name = draft.name.trim();
  const projectCode = draft.projectCode?.trim() || null;
  const payload: ProjectRow = {
    id: ref.id,
    name,
    slug: slugifyProjectName(name) || ref.id.toLowerCase(),
    projectCode,
    createdByUid: creator.uid,
    isArchived: false,
    createdAtEpochMs: now,
    metadataVersion: 3,
    updatedAtEpochMs: now,
    storageMode: "PROJECT_DB",
    projectDbPath: "",
    mediaStorageProvider: "GOOGLE_DRIVE",
    mediaStorageFolderId: "",
    mediaStorageFolderUrl: "",
    mediaStorageUpdatedAtEpochMs: 0,
    isDeleted: false,
    deletedAtEpochMs: null
  };
  const batch = writeBatch(firestore);
  batch.set(ref, createEnvelope("projects", ref.id, ref.id, payload, now));
  batch.set(doc(firestore, "projects", ref.id, "projectMembers", creator.uid), {
    uid: creator.uid,
    email: creator.email?.trim() || "",
    displayName: creator.displayName?.trim() || null,
    role: "MEMBER",
    isActive: true,
    contractorScope: "ALL",
    allowedContractors: [],
    grantedByUid: creator.uid,
    grantedAtEpochMs: now,
    createdAtEpochMs: now,
    updatedAtEpochMs: now
  });
  batch.set(doc(firestore, "users", creator.uid), {
    uid: creator.uid,
    email: creator.email?.trim() || "",
    displayName: creator.displayName?.trim() || null,
    emailVerified: true,
    createdAtEpochMs: now,
    lastLoginAtEpochMs: now,
    updatedAtEpochMs: now,
    isDisabled: false,
    projectIds: arrayUnion(ref.id)
  }, { merge: true });
  batch.set(doc(firestore, "projectCatalog", ref.id), {
    projectName: name,
    projectCode: projectCode || payload.slug.toUpperCase(),
    createdByUid: creator.uid,
    updatedAtEpochMs: now,
    status: "ACTIVE"
  });
  await batch.commit();
  return {
    id: ref.id,
    name: payload.name,
    slug: payload.slug,
    projectCode: payload.projectCode,
    mediaStorageProvider: payload.mediaStorageProvider,
    mediaStorageFolderId: payload.mediaStorageFolderId,
    mediaStorageFolderUrl: payload.mediaStorageFolderUrl,
    mediaStorageUpdatedAtEpochMs: payload.mediaStorageUpdatedAtEpochMs,
    updatedAtEpochMs: payload.updatedAtEpochMs,
    isDeleted: payload.isDeleted
  };
}

export async function updateProjectMediaStorage(
  firestore: Firestore,
  projectId: string,
  folderInput: string
): Promise<void> {
  const now = Date.now();
  const normalized = normalizeGoogleDriveFolderInput(folderInput);
  const ref = doc(firestore, "projects", projectId);
  await updateDoc(ref, {
    "data.mediaStorageProvider": "GOOGLE_DRIVE",
    "data.mediaStorageFolderId": normalized.folderId,
    "data.mediaStorageFolderUrl": normalized.folderUrl,
    "data.mediaStorageUpdatedAtEpochMs": now,
    "data.updatedAtEpochMs": now,
    updatedAtEpochMs: now,
    lastSyncedAtEpochMs: now
  });
}

export async function upsertUserProfile(
  firestore: Firestore,
  user: { uid: string; email?: string | null; displayName?: string | null; emailVerified: boolean }
): Promise<void> {
  const now = Date.now();
  await setDoc(
    doc(firestore, "users", user.uid),
    {
      uid: user.uid,
      email: user.email?.trim() || "",
      displayName: user.displayName?.trim() || null,
      emailVerified: user.emailVerified,
      createdAtEpochMs: now,
      lastLoginAtEpochMs: now,
      updatedAtEpochMs: now,
      isDisabled: false
    },
    { merge: true }
  );
}

export async function setActiveProjectForUser(
  firestore: Firestore,
  uid: string,
  projectId: string | null
): Promise<void> {
  await setDoc(doc(firestore, "users", uid), {
    activeProjectId: projectId,
    updatedAtEpochMs: Date.now()
  }, { merge: true });
}

export function subscribeUsersDirectory(
  firestore: Firestore,
  onRows: (rows: UserProfileRow[]) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    query(collection(firestore, "users"), orderBy("lastLoginAtEpochMs", "desc")),
    (snapshot) => {
      const rows = snapshot.docs.map((userDoc) => {
        const raw = userDoc.data();
        return {
          uid: userDoc.id,
          email: String(raw.email ?? ""),
          displayName: raw.displayName ? String(raw.displayName) : null,
          emailVerified: Boolean(raw.emailVerified ?? false),
          createdAtEpochMs: Number(raw.createdAtEpochMs ?? 0),
          lastLoginAtEpochMs: Number(raw.lastLoginAtEpochMs ?? 0),
          updatedAtEpochMs: Number(raw.updatedAtEpochMs ?? 0),
          isDisabled: Boolean(raw.isDisabled ?? false),
          projectIds: Array.isArray(raw.projectIds) ? raw.projectIds.map((value) => String(value)) : []
        } satisfies UserProfileRow;
      });
      onRows(rows);
    },
    onError
  );
}

function parseAccessRequest(requestId: string, raw: DocumentData): ProjectAccessRequestRow | null {
  const projectId = String(raw.projectId ?? "").trim();
  const userId = String(raw.userId ?? "").trim();
  const status = String(raw.status ?? "").trim().toUpperCase() as AccessRequestStatus;
  if (!projectId || !userId || requestId !== `${projectId}__${userId}` ||
      !["PENDING", "APPROVED", "REJECTED", "REVOKED"].includes(status)) return null;
  const groups = Array.isArray(raw.allowedDataGroups) ? raw.allowedDataGroups.map(String).map((value) => value.trim()).filter(Boolean) : [];
  const contractors = Array.isArray(raw.allowedContractors) ? raw.allowedContractors.map(String).map((value) => value.trim()).filter(Boolean) : [];
  return {
    requestId,
    projectId,
    userId,
    status,
    allowedDataGroups: groups,
    contractorScope: raw.contractorScope === "SCOPED" ? "SCOPED" : "ALL",
    allowedContractors: contractors,
    approvedBy: raw.approvedBy ? String(raw.approvedBy) : null,
    approvedAtEpochMs: raw.approvedAtEpochMs == null ? null : Number(raw.approvedAtEpochMs),
    requestedAtEpochMs: raw.requestedAtEpochMs == null ? null : Number(raw.requestedAtEpochMs),
    updatedAtEpochMs: Number(raw.updatedAtEpochMs ?? 0)
  };
}

export function validateApprovedScope(
  allowedDataGroups: string[],
  contractorScope: ContractorScope,
  allowedContractors: string[]
): void {
  const validGroups = allowedDataGroups.map((value) => value.trim()).filter(Boolean);
  if (!validGroups.length) {
    throw new Error("Phê duyệt cần ít nhất một nhóm dữ liệu (data group).");
  }
  if (contractorScope === "SCOPED") {
    const validContractors = allowedContractors.map((value) => value.trim()).filter(Boolean);
    if (!validContractors.length) {
      throw new Error("Phạm vi SCOPED cần ít nhất một nhà thầu hợp lệ.");
    }
  }
}

export function subscribeProjectAccessRequests(
  firestore: Firestore,
  onRows: (rows: ProjectAccessRequestRow[]) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    query(collection(firestore, "accessRequests"), orderBy("updatedAtEpochMs", "desc"), orderBy(documentId(), "desc"), limit(100)),
    (snapshot) => onRows(snapshot.docs.map((item) => parseAccessRequest(item.id, item.data())).filter((item): item is ProjectAccessRequestRow => Boolean(item))),
    onError
  );
}

export async function transitionProjectAccessRequest(
  firestore: Firestore,
  adminUid: string,
  request: ProjectAccessRequestRow,
  action: AccessAdminAction,
  allowedDataGroups: string[],
  contractorScope: ContractorScope,
  allowedContractors: string[]
): Promise<ProjectAccessRequestRow> {
  const targetStatus: AccessRequestStatus = action === "APPROVE" ? "APPROVED" : action === "REJECT" ? "REJECTED" : "REVOKED";
  if (action === "APPROVE") {
    validateApprovedScope(allowedDataGroups, contractorScope, allowedContractors);
  }
  if (!((request.status === "PENDING" && (targetStatus === "APPROVED" || targetStatus === "REJECTED")) ||
        (request.status === "APPROVED" && targetStatus === "REVOKED"))) {
    throw new Error(`Không thể chuyển ${request.status} sang ${targetStatus}.`);
  }
  const ref = doc(firestore, "accessRequests", request.requestId);
  const now = Date.now();
  const next = {
    projectId: request.projectId,
    userId: request.userId,
    status: targetStatus,
    allowedDataGroups: action === "APPROVE" ? allowedDataGroups.map((value) => value.trim()).filter(Boolean) : request.allowedDataGroups,
    contractorScope: action === "APPROVE" ? contractorScope : request.contractorScope,
    allowedContractors: action === "APPROVE" ? allowedContractors.map((value) => value.trim()).filter(Boolean) : request.allowedContractors,
    approvedBy: action === "APPROVE" ? adminUid : request.approvedBy,
    approvedAtEpochMs: action === "APPROVE" ? now : request.approvedAtEpochMs,
    requestedAtEpochMs: request.requestedAtEpochMs,
    updatedAtEpochMs: now
  };
  await runTransaction(firestore, async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists()) throw new Error("Access request không còn tồn tại.");
    const current = parseAccessRequest(snapshot.id, snapshot.data());
    if (!current || current.status !== request.status) throw new Error("Yêu cầu đã thay đổi, hãy làm mới hàng đợi.");
    transaction.set(ref, next);
    transaction.set(doc(ref, "accessAudit", `${now}_${adminUid}`), {
      projectId: request.projectId,
      targetUserId: request.userId,
      action,
      previousState: request.status,
      newState: targetStatus,
      actorAdminId: adminUid,
      timestampEpochMs: now
    });
  });
  return { ...request, ...next, status: targetStatus };
}

export function subscribeProjectMembers(
  firestore: Firestore,
  projectId: string,
  onRows: (rows: ProjectMemberRow[]) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    query(collection(firestore, "projects", projectId, "projectMembers"), orderBy("updatedAtEpochMs", "desc")),
    (snapshot) => {
      const rows = snapshot.docs.map((memberDoc) => {
        const raw = memberDoc.data();
        return {
          uid: memberDoc.id,
          email: String(raw.email ?? ""),
          displayName: raw.displayName ? String(raw.displayName) : null,
          role: "MEMBER",
          isActive: Boolean(raw.isActive ?? true),
          contractorScope: raw.contractorScope === "SCOPED" ? "SCOPED" : "ALL",
          allowedContractors: Array.isArray(raw.allowedContractors) ? raw.allowedContractors.map((value) => String(value)) : [],
          grantedByUid: String(raw.grantedByUid ?? ""),
          grantedAtEpochMs: Number(raw.grantedAtEpochMs ?? 0),
          updatedAtEpochMs: Number(raw.updatedAtEpochMs ?? 0)
        } satisfies ProjectMemberRow;
      });
      onRows(rows);
    },
    onError
  );
}

export function subscribeCurrentProjectMember(
  firestore: Firestore,
  projectId: string,
  uid: string,
  onRow: (row: ProjectMemberRow | null) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    doc(firestore, "projects", projectId, "projectMembers", uid),
    (snapshot) => {
      if (!snapshot.exists()) {
        onRow(null);
        return;
      }
      const raw = snapshot.data();
      onRow({
        uid: snapshot.id,
        email: String(raw.email ?? ""),
        displayName: raw.displayName ? String(raw.displayName) : null,
        role: "MEMBER",
        isActive: Boolean(raw.isActive ?? true),
        contractorScope: raw.contractorScope === "SCOPED" ? "SCOPED" : "ALL",
        allowedContractors: Array.isArray(raw.allowedContractors) ? raw.allowedContractors.map((value) => String(value)) : [],
        grantedByUid: String(raw.grantedByUid ?? ""),
        grantedAtEpochMs: Number(raw.grantedAtEpochMs ?? 0),
        updatedAtEpochMs: Number(raw.updatedAtEpochMs ?? 0)
      });
    },
    onError
  );
}

export async function saveProjectMember(
  firestore: Firestore,
  projectId: string,
  adminUid: string,
  member: ProjectMemberRow
): Promise<void> {
  const now = Date.now();
  await setDoc(
    doc(firestore, "projects", projectId, "projectMembers", member.uid),
    {
      uid: member.uid,
      email: member.email.trim(),
      displayName: member.displayName?.trim() || null,
      role: "MEMBER",
      isActive: member.isActive,
      contractorScope: member.contractorScope,
      allowedContractors: member.contractorScope === "SCOPED" ? member.allowedContractors.map((value) => value.trim()).filter(Boolean) : [],
      grantedByUid: member.grantedByUid || adminUid,
      grantedAtEpochMs: member.grantedAtEpochMs || now,
      updatedAtEpochMs: now
    },
    { merge: true }
  );
  await setDoc(
    doc(firestore, "users", member.uid),
    {
      uid: member.uid,
      email: member.email.trim(),
      displayName: member.displayName?.trim() || null,
      updatedAtEpochMs: now,
      projectIds: arrayUnion(projectId)
    },
    { merge: true }
  );
}

export async function deleteProjectMemberRecord(
  firestore: Firestore,
  projectId: string,
  uid: string
): Promise<void> {
  await deleteDoc(doc(firestore, "projects", projectId, "projectMembers", uid));
  await setDoc(
    doc(firestore, "users", uid),
    {
      projectIds: arrayRemove(projectId),
      updatedAtEpochMs: Date.now()
    },
    { merge: true }
  );
}

export function subscribeProjects(
  firestore: Firestore,
  onRows: (rows: ProjectDoc[]) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    collection(firestore, "projects"),
    (snapshot) => {
      const rows = snapshot.docs
        .map((projectDoc) => {
          const raw = unpackEnvelope<Record<string, unknown>>(projectDoc.id, projectDoc.data());
          return projectDocFromRaw(projectDoc.id, raw);
        })
        .filter((project) => !project.isDeleted)
        .sort((left, right) => right.updatedAtEpochMs - left.updatedAtEpochMs);
      onRows(rows);
    },
    onError
  );
}

export function subscribeLatestCatalogMigration(
  firestore: Firestore,
  onReport: (report: CatalogMigrationReport | null) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    query(collection(firestore, "catalogMigrations"), orderBy("completedAtEpochMs", "desc"), limit(1)),
    (snapshot) => onReport((snapshot.docs[0]?.data() as CatalogMigrationReport | undefined) ?? null),
    onError
  );
}

export function subscribeProjectDocument(
  firestore: Firestore,
  projectId: string,
  onProject: (project: ProjectDoc | null) => void,
  onError: (error: Error) => void
): Unsubscribe {
  return onSnapshot(
    doc(firestore, "projects", projectId),
    (snapshot) => {
      if (!snapshot.exists()) {
        onProject(null);
        return;
      }
      const raw = unpackEnvelope<Record<string, unknown>>(snapshot.id, snapshot.data());
      onProject(projectDocFromRaw(snapshot.id, raw));
    },
    onError
  );
}

export function subscribeProjectTable(
  firestore: Firestore,
  projectId: string,
  tableName: SyncTableName,
  onRows: (tableName: SyncTableName, rows: Record<string, unknown>[]) => void,
  onError: (tableName: SyncTableName, error: Error) => void
): Unsubscribe {
  return onSnapshot(
    collection(doc(firestore, "projects", projectId), tableName),
    (snapshot: QuerySnapshot<DocumentData>) => {
      const rows = snapshot.docs
        .map((rowDoc) => unpackEnvelope<Record<string, unknown>>(rowDoc.id, rowDoc.data()))
        .filter((row) => !Boolean(row.isDeleted))
        .sort((left, right) => Number(right.updatedAtEpochMs ?? 0) - Number(left.updatedAtEpochMs ?? 0));
      onRows(tableName, rows);
    },
    (error) => onError(tableName, error)
  );
}

export async function createTaskDocument(
  firestore: Firestore,
  projectId: string,
  title: string,
  description: string
): Promise<void> {
  const now = Date.now();
  const ref = doc(collection(doc(collection(firestore, "projects"), projectId), "task"));
  const payload: TaskRow = {
    id: ref.id,
    projectId,
    title: title.trim(),
    description: description.trim(),
    status: "TODO",
    createdAtEpochMs: now,
    completedAtEpochMs: null,
    objectNodeId: null,
    objectRouteId: null,
    updatedAtEpochMs: now,
    isDeleted: false,
    deletedAtEpochMs: null
  };
  await setDoc(ref, createEnvelope("task", projectId, ref.id, payload, now));
}

export async function updateTaskStatusDocument(
  firestore: Firestore,
  projectId: string,
  task: Record<string, unknown>,
  status: TaskRow["status"]
): Promise<void> {
  const id = String(task.id ?? "");
  if (!id) return;
  const now = Date.now();
  const nextData = {
    ...task,
    id,
    projectId,
    status,
    completedAtEpochMs: status === "DONE" ? now : null,
    updatedAtEpochMs: now,
    isDeleted: Boolean(task.isDeleted ?? false),
    deletedAtEpochMs: task.deletedAtEpochMs ?? null
  };
  await updateDoc(doc(firestore, "projects", projectId, "task", id), {
    data: nextData,
    updatedAtEpochMs: now,
    isDeleted: Boolean(nextData.isDeleted),
    sourceDeviceId: "webapp",
    lastSyncedAtEpochMs: now
  });
}

export async function createDailyLogDocument(
  firestore: Firestore,
  projectId: string,
  draft: {
    workItem: string;
    note: string;
    manpower: string;
    volume: string;
    unit: string;
    categoryName: string;
    weather: string;
  },
  customEpochDay?: number
): Promise<void> {
  const now = Date.now();
  const ref = doc(collection(doc(collection(firestore, "projects"), projectId), "daily_log"));
  const payload: DailyLogRow = {
    id: ref.id,
    projectId,
    workItem: draft.workItem.trim(),
    manpower: Number(draft.manpower || 0),
    note: draft.note.trim(),
    createdAtEpochMs: now,
    weather: draft.weather.trim(),
    temperature: 0,
    dateEpochDay: customEpochDay ?? Math.floor(now / 86400000),
    volume: Number(draft.volume || 0),
    unit: draft.unit.trim(),
    categoryName: draft.categoryName.trim(),
    batchGroupId: ref.id,
    linkedWorkPlanId: null,
    plannedWorkName: "",
    plannedQuantity: 0,
    plannedUnit: "",
    photoMatchOffsetMinutes: 0,
    nodeId: null,
    routeId: null,
    plannedNodeId: null,
    plannedRouteId: null,
    updatedAtEpochMs: now,
    isDeleted: false,
    deletedAtEpochMs: null
  };
  await setDoc(ref, createEnvelope("daily_log", projectId, ref.id, payload, now));
}

export async function requestDeleteProjectApi(
  projectId: string,
  idToken: string,
  typedIdentity: string,
  confirmPendingOutbox: boolean = true
): Promise<{ success: boolean; error?: { code: string; message: string } }> {
  const res = await fetch(`/api/projects/${encodeURIComponent(projectId)}/deletion`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`
    },
    body: JSON.stringify({
      requestId: `del_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`,
      typedIdentity: typedIdentity.trim(),
      confirmPendingOutbox
    })
  });
  let data: any = null;
  try {
    data = await res.json();
  } catch {
    const rawText = await res.text().catch(() => "");
    throw new Error(`Lỗi phản hồi máy chủ (${res.status}): ${rawText.slice(0, 120) || "Không nhận được phản hồi hợp lệ."}`);
  }
  if (!res.ok || !data?.success) {
    const errorMsg = data?.error?.message || `Lỗi xử lý xóa dự án (Mã: ${res.status}).`;
    throw new Error(errorMsg);
  }
  return data;
}
