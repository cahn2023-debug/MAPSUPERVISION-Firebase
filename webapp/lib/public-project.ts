import fs from "fs";
import path from "path";
import { getAdminDb } from "./firebase-admin";

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
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes cache

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

export async function readPublicProject(): Promise<PublicProjectPayload | null> {
  const now = Date.now();

  // Return fresh in-memory cache if valid (0 reads!)
  if (inMemoryCache && now - inMemoryCache.timestamp < CACHE_TTL_MS) {
    return inMemoryCache.payload;
  }

  try {
    const project = await findPublicProject();
    if (!project) {
      if (inMemoryCache) return { ...inMemoryCache.payload, isCached: true };
      const disk = loadDiskCache();
      if (disk) return { ...disk, isCached: true };
      return null;
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

    // Fallback gracefully to memory cache or disk cache
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

    throw error;
  }
}
