export type ProjectDeletionState = "ACTIVE" | "DELETING" | "DELETE_FAILED" | "DELETED";

export const PROJECT_DELETION_COLLECTIONS = [
  "projectMembers",
  "gis_node",
  "gis_route",
  "node_progress",
  "work_volume_progress",
  "daily_log",
  "daily_log_line",
  "daily_log_nodes",
  "daily_log_photos",
  "work_categories",
  "work_plan",
  "note",
  "task",
  "site_photos",
  "photo_tags",
  "report_draft",
  "material_declaration",
  "material_handover",
  "imported_files",
  "import_session",
  "import_version",
  "import_conflict",
  "import_audit",
  "event_outbox",
  "chat_history",
  "ai_decision_cache",
  "ai_action_log",
  "rag_document_embedding"
] as const;

export type DeletionAuthorizationInput = {
  actorUid: string;
  isAdmin: boolean;
  ownerUid: string | null;
  currentState: ProjectDeletionState;
  isActiveOnDevice: boolean;
  projectName: string;
  projectCode: string | null;
  typedIdentity: string;
  authTimeEpochSeconds: number | undefined;
  requestIdMatches?: boolean;
  nowEpochSeconds?: number;
};

export function validateDeletionAuthorization(input: DeletionAuthorizationInput): void {
  if (!input.actorUid) throw new Error("UNAUTHORIZED");
  if (!input.isAdmin && input.ownerUid !== input.actorUid) throw new Error("FORBIDDEN");
  if (input.isActiveOnDevice) throw new Error("ACTIVE_PROJECT");
  if (input.currentState === "DELETED") throw new Error("ALREADY_DELETED");
  if (input.currentState === "DELETING" && !input.requestIdMatches) throw new Error("DELETION_IN_PROGRESS");
  if (input.typedIdentity !== input.projectName && input.typedIdentity !== (input.projectCode ?? "")) {
    throw new Error("IDENTITY_MISMATCH");
  }
  const now = input.nowEpochSeconds ?? Math.floor(Date.now() / 1000);
  if (!input.authTimeEpochSeconds || now - input.authTimeEpochSeconds > 300) {
    throw new Error("REAUTH_REQUIRED");
  }
}

export function mergeProjectData(source: Record<string, unknown> | undefined): Record<string, unknown> {
  if (!source) return {};
  const nested = source.data;
  if (!nested || typeof nested !== "object" || Array.isArray(nested)) return source;
  return { ...(nested as Record<string, unknown>), ...source };
}

export function normalizeDeletionRequestId(value: unknown): string {
  const requestId = typeof value === "string" ? value.trim() : "";
  if (!/^[A-Za-z0-9._:-]{8,120}$/.test(requestId)) throw new Error("INVALID_REQUEST_ID");
  return requestId;
}

export function nextDeletionCheckpoint(completed: string[], collection: string): string[] {
  return completed.includes(collection) ? completed : [...completed, collection];
}
