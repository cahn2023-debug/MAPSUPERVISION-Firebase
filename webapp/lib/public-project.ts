import fs from "fs";
import path from "path";
import { getAdminDb } from "./firebase-admin";
import { seedSnapshot269 } from "./seed-snapshot-269";
import {
  driveClient,
  configuredRootFolderId,
  ensureProjectFolder,
  findProjectFolderIdByNameOrId,
  getLatestDriveSnapshot,
  getLatestDriveSnapshotByProjectIdOrFolder,
  pruneOldDriveSnapshots,
  findSnapshotsFolder
} from "./google-drive-media";

export const PUBLIC_PROJECT_SLUG = "269-2026";
export const KNOWN_PUBLIC_PROJECT_ID = "6874375a-3366-4457-a978-b8ee71c4e461";

export const publicProjectTables = [
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
] as const;

export type PublicProjectTable = typeof publicProjectTables[number];
export type RecordValue = Record<string, unknown>;

export interface PublicProjectPayload {
  project: RecordValue;
  collections: Record<string, RecordValue[]>;
  updatedAtEpochMs: number;
  isCached?: boolean;
  quotaExceeded?: boolean;
}

// In-Memory Cache (Global across warm serverless invocations)
let inMemoryCache: { payload: PublicProjectPayload; timestamp: number } | null = null;
const CACHE_TTL_MS = 30 * 1000; // 30 seconds cache for near real-time sync with Firestore

export function resetPublicProjectCacheForTesting() {
  inMemoryCache = null;
}

function getCacheFilePath(): string {
  const tmpDir = process.env.TMPDIR || process.env.TEMP || "/tmp";
  return path.join(tmpDir, "mapsupervision-public-269-2026-cache.json");
}

function loadDiskCache(): PublicProjectPayload | null {
  try {
    const file = getCacheFilePath();
    if (fs.existsSync(file)) {
      const content = fs.readFileSync(file, "utf-8");
      return JSON.parse(content);
    }
  } catch (err) {
    console.warn("[public-project] Could not read disk cache:", err);
  }
  return null;
}

function saveDiskCache(data: PublicProjectPayload) {
  try {
    const file = getCacheFilePath();
    fs.writeFileSync(file, JSON.stringify(data), "utf-8");
  } catch (err) {
    console.warn("[public-project] Could not save disk cache:", err);
  }
}

function normalize(value: unknown): string {
  return String(value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "")
    .toLowerCase();
}

function unpackDocument(id: string, source: RecordValue): RecordValue {
  const envelopeData = source.data;
  const data = envelopeData && typeof envelopeData === "object" && !Array.isArray(envelopeData)
    ? envelopeData as RecordValue
    : source;
  return { ...data, id: String(data.id ?? source.id ?? id) };
}

function jsonValue(value: unknown): unknown {
  if (value == null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") return value;
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "object" && "toMillis" in value && typeof (value as { toMillis?: unknown }).toMillis === "function") {
    return (value as { toMillis: () => number }).toMillis();
  }
  if (Array.isArray(value)) return value.map(jsonValue);
  if (typeof value === "object") {
    return Object.fromEntries(Object.entries(value as RecordValue).map(([key, item]) => [key, jsonValue(item)]));
  }
  return String(value);
}

function isProjectDeleted(data: RecordValue): boolean {
  if (data.isDeleted === true || data.isDeleted === 1) return true;
  if (data.deletionState === "DELETED") return true;
  if (data.tombstone === true) return true;
  return false;
}

export async function findPublicProject(slug = PUBLIC_PROJECT_SLUG) {
  const target = normalize(slug);

  // 1. First attempt: Direct document lookup by known project ID (1 read only)
  try {
    const directDoc = await getAdminDb().collection("projects").doc(KNOWN_PUBLIC_PROJECT_ID).get();
    if (directDoc.exists) {
      const data = unpackDocument(directDoc.id, directDoc.data() as RecordValue);
      if (!isProjectDeleted(data)) {
        const norm = normalize(data.name || data.projectName || data.projectCode || data.slug || directDoc.id);
        if (norm === target || norm.includes(target) || (norm.includes("269") && norm.includes("2026"))) {
          return { id: directDoc.id, data };
        }
      }
    }
  } catch (err) {
    console.warn("[findPublicProject] Direct doc lookup failed, trying collection scan:", err);
  }

  // 2. Second attempt: Collection scan fallback
  const snapshot = await getAdminDb().collection("projects").get();
  const activeProjects: Array<{ id: string; data: RecordValue }> = snapshot.docs
    .map((item: { id: string; data: () => unknown }) => ({
      id: item.id,
      data: unpackDocument(item.id, item.data() as RecordValue)
    }))
    .filter((entry: { id: string; data: RecordValue }) => !isProjectDeleted(entry.data));

  const matched = activeProjects.find(({ id, data }) => {
    return [id, data.id, data.name, data.projectName, data.projectCode, data.slug].some((value) => {
      const norm = normalize(value);
      return norm === target || norm.includes(target) || (norm.includes("269") && norm.includes("2026"));
    });
  });

  return matched || null;
}

let driveSnapshotReaderMock: (() => Promise<PublicProjectPayload | null>) | null = null;
export function setDriveSnapshotReaderMock(mock: (() => Promise<PublicProjectPayload | null>) | null) {
  driveSnapshotReaderMock = mock;
}

export let lastDriveDiagnostic: {
  configuredRoot?: string;
  projectFolderId?: string | null;
  snapshotRes?: any;
  error?: string;
} = {};

export async function readDriveSnapshot269(): Promise<PublicProjectPayload | null> {
  if (process.env.NODE_ENV === "test" && driveSnapshotReaderMock) {
    return driveSnapshotReaderMock();
  }
  lastDriveDiagnostic = {};
  try {
    const drive = driveClient();
    let projectFolderId: string | null = null;
    try {
      const rootFolderId = configuredRootFolderId();
      lastDriveDiagnostic.configuredRoot = rootFolderId;
      projectFolderId = await findProjectFolderIdByNameOrId(drive, rootFolderId, KNOWN_PUBLIC_PROJECT_ID, "Dự án 269 - 2026");
      lastDriveDiagnostic.projectFolderId = projectFolderId;
    } catch (folderErr) {
      lastDriveDiagnostic.error = "Folder error: " + (folderErr instanceof Error ? folderErr.message : String(folderErr));
      console.warn("[readDriveSnapshot269] root folder lookup error:", folderErr);
    }

    const snapshotRes = await getLatestDriveSnapshotByProjectIdOrFolder(drive, KNOWN_PUBLIC_PROJECT_ID, projectFolderId);
    lastDriveDiagnostic.snapshotRes = snapshotRes ? { fileId: snapshotRes.fileId, fileName: snapshotRes.fileName, hasPayload: !!snapshotRes.payload } : null;

    if (snapshotRes && snapshotRes.payload && snapshotRes.payload.project) {
      const payload = snapshotRes.payload as PublicProjectPayload;
      if (projectFolderId) {
        const snapshotsFolderId = await findSnapshotsFolder(drive, projectFolderId);
        if (snapshotsFolderId) {
          void pruneOldDriveSnapshots(drive, snapshotsFolderId);
        }
      }
      return payload;
    }
  } catch (driveErr) {
    lastDriveDiagnostic.error = "Drive client error: " + (driveErr instanceof Error ? driveErr.message : String(driveErr));
    console.warn("[readPublicProject] Google Drive snapshot lookup error:", driveErr);
  }
  return null;
}

export async function readPublicProject(bypassCache = false): Promise<PublicProjectPayload | null> {
  const now = Date.now();

  // Return fresh in-memory cache if valid (0 reads!)
  if (!bypassCache && inMemoryCache && now - inMemoryCache.timestamp < CACHE_TTL_MS) {
    return inMemoryCache.payload;
  }

  // 1. Priority 1: Read Latest Snapshot from Google Drive (Zero Firestore reads!)
  try {
    const driveSnapshot = await readDriveSnapshot269();
    if (driveSnapshot) {
      inMemoryCache = { payload: driveSnapshot, timestamp: now };
      saveDiskCache(driveSnapshot);
      return driveSnapshot;
    }
  } catch (driveError) {
    console.warn("[readPublicProject] Failed to resolve Google Drive snapshot, falling back:", driveError);
  }

  // 2. Priority 2: Fallback to Firestore query
  try {
    const project = await findPublicProject();
    if (!project) {
      if (inMemoryCache) return { ...inMemoryCache.payload, isCached: true };
      const disk = loadDiskCache();
      if (disk) return { ...disk, isCached: true };
      return { ...seedSnapshot269, updatedAtEpochMs: now, isCached: true, quotaExceeded: true };
    }

    const entries = await Promise.all(publicProjectTables.map(async (tableName) => {
      const snapshot = await getAdminDb().collection("projects").doc(project.id).collection(tableName).get();
      return [
        tableName,
        snapshot.docs.map((item: { id: string; data: () => unknown }) => unpackDocument(item.id, item.data() as RecordValue))
      ] as const;
    }));

    const payload = jsonValue({
      project: project.data,
      collections: Object.fromEntries(entries),
      updatedAtEpochMs: now
    }) as PublicProjectPayload;

    // Cache in memory and disk
    inMemoryCache = { payload, timestamp: now };
    saveDiskCache(payload);

    return payload;
  } catch (error) {
    console.error("[readPublicProject] Firestore query error / Quota Exceeded:", error);

    // Fallback gracefully to memory cache, disk cache, or seed snapshot
    if (inMemoryCache) {
      console.warn("[readPublicProject] Serving stale memory cache due to Firestore error.");
      return { ...inMemoryCache.payload, isCached: true, quotaExceeded: true };
    }

    const disk = loadDiskCache();
    if (disk) {
      console.warn("[readPublicProject] Serving disk cache due to Firestore error.");
      inMemoryCache = { payload: disk, timestamp: now };
      return { ...disk, isCached: true, quotaExceeded: true };
    }

    console.warn("[readPublicProject] Serving seed snapshot due to Firestore error.");
    return { ...seedSnapshot269, updatedAtEpochMs: now, isCached: true, quotaExceeded: true };
  }
}

