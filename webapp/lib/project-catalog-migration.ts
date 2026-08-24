export type CatalogStatus = "ACTIVE" | "ARCHIVED";

export type CatalogEntry = {
  projectId: string;
  projectName: string;
  projectCode: string;
  createdByUid: string;
  updatedAtEpochMs: number;
  status: CatalogStatus;
};

export type CatalogProjectDocument = {
  id: string;
  data: Record<string, unknown>;
};

export type CatalogDocument = {
  projectId: string;
  projectName?: string;
  projectCode?: string;
  createdByUid?: string;
  updatedAtEpochMs?: number;
  status?: CatalogStatus;
};

export type CatalogOperation =
  | { kind: "create" | "update"; entry: CatalogEntry }
  | { kind: "delete"; projectId: string };

export type CatalogMigrationPlan = {
  operations: CatalogOperation[];
  counts: {
    eligible: number;
    create: number;
    update: number;
    unchanged: number;
    delete: number;
    warning: number;
    discrepancy: number;
  };
  warnings: string[];
  discrepancies: string[];
  status: "COMPLETED" | "COMPLETED_WITH_WARNINGS";
};

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function nonBlank(value: unknown): string | null {
  const normalized = typeof value === "string" ? value.trim() : "";
  return normalized || null;
}

function numberValue(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? Math.trunc(value) : null;
}

function projectPayload(data: Record<string, unknown>): Record<string, unknown> {
  const envelope = record(data.payload ?? data.data);
  return Object.keys(envelope).length > 0 ? envelope : data;
}

function isDeleted(data: Record<string, unknown>, payload: Record<string, unknown>): boolean {
  const state = nonBlank(payload.deletionState ?? data.deletionState);
  return payload.tombstone === true || data.tombstone === true ||
    payload.isDeleted === true || data.isDeleted === true ||
    state === "DELETING" || state === "DELETED";
}

export function catalogEntryFields(entry: CatalogEntry): Record<string, unknown> {
  return {
    projectName: entry.projectName,
    projectCode: entry.projectCode,
    createdByUid: entry.createdByUid,
    updatedAtEpochMs: entry.updatedAtEpochMs,
    status: entry.status
  };
}

export function normalizeProjectDocument(
  project: CatalogProjectDocument,
  fallbackOwnerUid?: string
): { entry: CatalogEntry | null; warnings: string[]; discrepancy?: string; excluded?: boolean } {
  const projectId = project.id.trim();
  const payload = projectPayload(project.data);
  const warnings: string[] = [];
  if (!projectId) return { entry: null, warnings, discrepancy: "project document has no id" };
  if (isDeleted(project.data, payload)) return { entry: null, warnings, excluded: true };

  const rawName = nonBlank(payload.name ?? payload.projectName ?? project.data.name ?? project.data.projectName);
  const rawSlug = nonBlank(payload.slug ?? project.data.slug);
  const rawCode = nonBlank(payload.projectCode ?? project.data.projectCode);
  const rawUpdated = numberValue(payload.updatedAtEpochMs ?? project.data.updatedAtEpochMs);
  const rawCreated = numberValue(payload.createdAtEpochMs ?? project.data.createdAtEpochMs);
  const rawOwner = nonBlank(payload.createdByUid ?? project.data.createdByUid);
  const owner = rawOwner ?? nonBlank(fallbackOwnerUid);

  const projectName = rawName ?? projectId;
  const projectCode = rawCode ?? rawSlug ?? projectId.slice(0, 8).toUpperCase();
  const updatedAtEpochMs = rawUpdated ?? rawCreated ?? 0;
  const status: CatalogStatus = payload.isArchived === true || project.data.isArchived === true ? "ARCHIVED" : "ACTIVE";

  if (!rawName) warnings.push(`${projectId}: projectName fallback used`);
  if (!rawCode && !rawSlug) warnings.push(`${projectId}: projectCode fallback used`);
  if (rawUpdated == null && rawCreated == null) warnings.push(`${projectId}: updatedAtEpochMs fallback used`);
  if (!rawOwner && owner) warnings.push(`${projectId}: fallback owner assigned`);
  if (!owner) return { entry: null, warnings, discrepancy: `${projectId}: createdByUid is missing and no fallback owner was supplied` };

  return {
    entry: { projectId, projectName, projectCode, createdByUid: owner, updatedAtEpochMs, status },
    warnings
  };
}

function sameEntry(left: CatalogDocument, right: CatalogEntry): boolean {
  return left.projectName === right.projectName &&
    left.projectCode === right.projectCode &&
    left.createdByUid === right.createdByUid &&
    left.updatedAtEpochMs === right.updatedAtEpochMs &&
    left.status === right.status;
}

export function buildCatalogMigrationPlan(input: {
  projects: CatalogProjectDocument[];
  catalog: CatalogDocument[];
  tombstoneIds?: Set<string>;
  fallbackOwnerUid?: string;
}): CatalogMigrationPlan {
  const existingById = new Map(input.catalog.map(entry => [entry.projectId, entry]));
  const operations: CatalogOperation[] = [];
  const warnings: string[] = [];
  const discrepancies: string[] = [];
  const eligibleIds = new Set<string>();
  let create = 0;
  let update = 0;
  let unchanged = 0;
  let deletionCount = 0;

  for (const project of input.projects) {
    const normalized = normalizeProjectDocument(project, input.fallbackOwnerUid);
    warnings.push(...normalized.warnings);
    if (normalized.discrepancy) {
      discrepancies.push(normalized.discrepancy);
      continue;
    }
    if (!normalized.entry) {
      if (normalized.excluded || input.tombstoneIds?.has(project.id)) {
        const existing = existingById.get(project.id);
        if (existing) {
          operations.push({ kind: "delete", projectId: project.id });
          deletionCount += 1;
        }
      }
      continue;
    }

    const desired = normalized.entry;
    eligibleIds.add(desired.projectId);
    const existing = existingById.get(desired.projectId);
    if (!existing) {
      operations.push({ kind: "create", entry: desired });
      create += 1;
      continue;
    }

    if (existing.createdByUid && existing.createdByUid !== desired.createdByUid) {
      warnings.push(`${desired.projectId}: existing createdByUid preserved`);
      discrepancies.push(`${desired.projectId}: source owner differs from catalog owner`);
      desired.createdByUid = existing.createdByUid;
    }
    if (sameEntry(existing, desired)) unchanged += 1;
    else {
      operations.push({ kind: "update", entry: desired });
      update += 1;
    }
  }

  for (const existing of input.catalog) {
    if (input.tombstoneIds?.has(existing.projectId) && !eligibleIds.has(existing.projectId)) {
      if (!operations.some(operation => operation.kind === "delete" && operation.projectId === existing.projectId)) {
        operations.push({ kind: "delete", projectId: existing.projectId });
        deletionCount += 1;
      }
    }
  }

  return {
    operations,
    counts: {
      eligible: eligibleIds.size,
      create,
      update,
      unchanged,
      delete: deletionCount,
      warning: warnings.length,
      discrepancy: discrepancies.length
    },
    warnings,
    discrepancies,
    status: warnings.length > 0 || discrepancies.length > 0 ? "COMPLETED_WITH_WARNINGS" : "COMPLETED"
  };
}

export function validateFallbackOwnerUid(value: string | undefined): string | null {
  return nonBlank(value);
}
