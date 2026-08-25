import { NextRequest, NextResponse } from "next/server";
import type { DecodedIdToken } from "firebase-admin/auth";
import { getAdminAuth, getAdminDb } from "@/lib/firebase-admin";
import {
  mergeProjectData,
  normalizeCloudDecision,
  normalizeDeletionRequestId,
  type ProjectCloudDecision,
  type ProjectDeletionState
} from "@/lib/project-deletion";

export const runtime = "nodejs";

function errorResponse(status: number, code: string, message: string) {
  return NextResponse.json({ success: false, error: { code, message } }, { status });
}

function bearerToken(request: NextRequest): string {
  const value = request.headers.get("authorization") ?? "";
  return value.startsWith("Bearer ") ? value.slice("Bearer ".length).trim() : "";
}

function isProjectAdmin(uid: string, decoded: DecodedIdToken, data: Record<string, unknown>, member: Record<string, unknown> | undefined): boolean {
  if (decoded.admin === true) return true;
  if (data.createdByUid === uid || data.ownerUid === uid) return true;
  if (!member) return false;
  return member.isAdmin === true || ["admin", "owner", "creator", "super-admin"].includes(String(member.role ?? "").toLowerCase());
}

function state(value: unknown): ProjectDeletionState {
  if (value === "CLOUD_DECISION_PENDING" || value === "LOCAL_DELETE_FAILED" || value === "CLOUD_RETAINED" ||
      value === "RESTORE_PENDING" || value === "DELETING" || value === "DELETE_FAILED" || value === "DELETED") {
    return value;
  }
  return "ACTIVE";
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ projectId: string }> }
) {
  const { projectId } = await context.params;
  const token = bearerToken(request);
  if (!token) return errorResponse(401, "UNAUTHORIZED", "Missing Firebase ID token.");

  let decoded: DecodedIdToken;
  try {
    decoded = await getAdminAuth().verifyIdToken(token);
  } catch {
    return errorResponse(401, "UNAUTHORIZED", "Invalid Firebase ID token.");
  }

  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return errorResponse(400, "BAD_REQUEST", "Request body must be JSON.");
  }

  let requestId: string;
  let decision: ProjectCloudDecision;
  try {
    requestId = normalizeDeletionRequestId(body.requestId);
    decision = normalizeCloudDecision(body.decision);
  } catch (error) {
    return errorResponse(400, "BAD_REQUEST", error instanceof Error ? error.message : "Invalid decision request.");
  }

  const firestore = getAdminDb();
  const projectRef = firestore.collection("projects").doc(projectId);
  const memberRef = projectRef.collection("projectMembers").doc(decoded.uid);
  const auditRef = firestore.collection("projectDeletionAudit").doc(`${projectId}__${requestId}__decision`);

  try {
    const result = await firestore.runTransaction(async (transaction: FirebaseFirestore.Transaction) => {
      const projectSnapshot = await transaction.get(projectRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
      const memberSnapshot = await transaction.get(memberRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
      if (!projectSnapshot.exists) throw new Error("NOT_FOUND");
      const data = mergeProjectData(projectSnapshot.data());
      const currentState = state(data.deletionState);
      if (!isProjectAdmin(decoded.uid, decoded, data, memberSnapshot.exists ? memberSnapshot.data() : undefined)) {
        throw new Error("FORBIDDEN");
      }
      if (data.deletionRequestId !== requestId) throw new Error("REQUEST_MISMATCH");
      if (data.cloudDecision === "RETAIN" || data.cloudDecision === "DELETE") {
        return {
          projectId,
          requestId,
          decision: data.cloudDecision as ProjectCloudDecision,
          deletionState: state(data.deletionState)
        };
      }
      if (currentState !== "CLOUD_DECISION_PENDING" && currentState !== "LOCAL_DELETE_FAILED") {
        throw new Error("DECISION_NOT_PENDING");
      }

      if (decision === "DELETE") {
        const typedIdentity = typeof body.typedIdentity === "string" ? body.typedIdentity.trim() : "";
        const projectName = String(data.name ?? data.projectName ?? projectId);
        const projectCode = data.projectCode ? String(data.projectCode) : "";
        if (typedIdentity && typedIdentity !== projectName && typedIdentity !== projectCode && typedIdentity !== projectId) {
          throw new Error("IDENTITY_MISMATCH");
        }
      }

      const now = Date.now();
      const nextState: ProjectDeletionState = decision === "RETAIN" ? "CLOUD_RETAINED" : "DELETING";
      transaction.set(projectRef, {
        deletionState: nextState,
        cloudDecision: decision,
        cloudDecisionRequestId: requestId,
        cloudDecisionActorUid: decoded.uid,
        cloudDecisionAtEpochMs: now,
        updatedAtEpochMs: now
      }, { merge: true });
      transaction.set(auditRef, {
        projectId,
        requestId,
        actorAdminId: decoded.uid,
        action: decision === "RETAIN" ? "RETAIN_CLOUD" : "DELETE_CLOUD",
        previousState: currentState,
        newState: nextState,
        timestampEpochMs: now,
        mediaPreserved: true
      });
      return { projectId, requestId, decision, deletionState: nextState };
    });
    return NextResponse.json({ success: true, data: result });
  } catch (error) {
    const code = error instanceof Error ? error.message : "BAD_REQUEST";
    if (code === "NOT_FOUND") return errorResponse(404, code, "Project not found.");
    if (code === "FORBIDDEN") return errorResponse(403, code, "Only an authorized project administrator can decide Cloud retention or deletion.");
    if (code === "REQUEST_MISMATCH") return errorResponse(409, code, "The decision request does not match the local deletion request.");
    if (code === "DECISION_NOT_PENDING") return errorResponse(409, code, "The project is not waiting for a Cloud decision.");
    if (code === "IDENTITY_MISMATCH") return errorResponse(400, code, "Typed project identity does not match.");
    if (code === "REAUTH_REQUIRED") return errorResponse(401, code, "Recent reauthentication is required before Cloud deletion.");
    return errorResponse(400, "BAD_REQUEST", "Cloud decision was rejected.");
  }
}
