import { getAdminDb } from "./firebase-admin";

export const PUBLIC_PROJECT_SLUG = "269-2026";

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

type PublicProjectTable = typeof publicProjectTables[number];
type RecordValue = Record<string, unknown>;

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
  const snapshot = await getAdminDb().collection("projects").get();
  const target = normalize(slug);

  const activeProjects: Array<{ id: string; data: RecordValue }> = snapshot.docs
    .map((item: { id: string; data: () => unknown }) => ({
      id: item.id,
      data: unpackDocument(item.id, item.data() as RecordValue)
    }))
    .filter((entry: { id: string; data: RecordValue }) => !isProjectDeleted(entry.data));

  // 1. Exact match or includes normalized target (e.g. 'duan2692026'.includes('2692026'))
  const matched = activeProjects.find(({ id, data }) => {
    return [id, data.id, data.name, data.projectName, data.projectCode, data.slug].some((value) => {
      const norm = normalize(value);
      return norm === target || norm.includes(target) || (norm.includes("269") && norm.includes("2026"));
    });
  });

  if (!matched) return null;
  return matched;
}

export async function readPublicProject() {
  const project = await findPublicProject();
  if (!project) return null;

  const entries = await Promise.all(publicProjectTables.map(async (tableName) => {
    const snapshot = await getAdminDb().collection("projects").doc(project.id).collection(tableName).get();
    return [
      tableName,
      snapshot.docs.map((item: { id: string; data: () => unknown }) => unpackDocument(item.id, item.data() as RecordValue))
    ] as const;
  }));

  return jsonValue({
    project: project.data,
    collections: Object.fromEntries(entries),
    updatedAtEpochMs: Date.now()
  });
}
