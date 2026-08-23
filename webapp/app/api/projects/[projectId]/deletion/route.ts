import { NextRequest, NextResponse } from "next/server";
import { randomUUID } from "node:crypto";
import type { DecodedIdToken } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { getAdminAuth, getAdminDb } from "@/lib/firebase-admin";
import {
  PROJECT_DELETION_COLLECTIONS,
  nextDeletionCheckpoint,
  normalizeDeletionRequestId,
  mergeProjectData,
  validateDeletionAuthorization,
  type ProjectDeletionState
} from "@/lib/project-deletion";

export const runtime = "nodejs";

type ErrorCode =
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "BAD_REQUEST"
  | "ACTIVE_PROJECT"
  | "REAUTH_REQUIRED"
  | "DELETION_IN_PROGRESS"
  | "DELETE_FAILED";

const WORKER_LEASE_MS = 5 * 60 * 1000;

function apiError(status: number, code: ErrorCode, message: string) {
  return NextResponse.json({ success: false, error: { code, message } }, { status });
}

function readBearerToken(request: NextRequest): string {
  const header = request.headers.get("authorization") ?? "";
  return header.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : "";
}

function projectData(source: Record<string, unknown> | undefined): Record<string, unknown> {
  return mergeProjectData(source);
}

function deletionState(value: unknown): ProjectDeletionState {
  return value === "DELETING" || value === "DELETE_FAILED" || value === "DELETED" ? value : "ACTIVE";
}

async function deleteCollection(
  firestore: FirebaseFirestore.Firestore,
  projectId: string,
  collectionName: string
): Promise<void> {
  await firestore.recursiveDelete(firestore.collection("projects").doc(projectId).collection(collectionName));
}

async function deleteAccessRequests(
  firestore: FirebaseFirestore.Firestore,
  projectId: string
): Promise<void> {
  const snapshot = await firestore.collection("accessRequests").where("projectId", "==", projectId).get();
  for (let offset = 0; offset < snapshot.docs.length; offset += 400) {
    const batch = firestore.batch();
    snapshot.docs.slice(offset, offset + 400).forEach((item) => batch.delete(item.ref));
    await batch.commit();
  }
}

async function removeProjectFromUsers(
  firestore: FirebaseFirestore.Firestore,
  projectId: string
): Promise<void> {
  const snapshot = await firestore.collection("users").where("projectIds", "array-contains", projectId).get();
  for (let offset = 0; offset < snapshot.docs.length; offset += 400) {
    const batch = firestore.batch();
    snapshot.docs.slice(offset, offset + 400).forEach((item) => batch.update(item.ref, {
      projectIds: FieldValue.arrayRemove(projectId),
      updatedAtEpochMs: Date.now()
    }));
    await batch.commit();
  }
}

async function persistCheckpoint(
  firestore: FirebaseFirestore.Firestore,
  projectRef: FirebaseFirestore.DocumentReference,
  requestId: string,
  workerToken: string,
  checkpoint: string[]
): Promise<void> {
  await firestore.runTransaction(async (transaction: FirebaseFirestore.Transaction) => {
    const snapshot = await transaction.get(projectRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
    const data = projectData(snapshot.data());
    if (!snapshot.exists || data.deletionState !== "DELETING" || data.deletionRequestId !== requestId || data.deletionWorkerToken !== workerToken) {
      throw new Error("WORKER_LOST");
    }
    transaction.set(projectRef, {
      deletionCheckpoint: checkpoint,
      deletionWorkerStartedAtEpochMs: Date.now(),
      updatedAtEpochMs: Date.now()
    }, { merge: true });
  });
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ projectId: string }> }
) {
  const { projectId } = await context.params;
  const token = readBearerToken(request);
  if (!token) return apiError(401, "UNAUTHORIZED", "Missing Firebase ID token.");

  let decoded: DecodedIdToken;
  try {
    decoded = await getAdminAuth().verifyIdToken(token);
  } catch {
    return apiError(401, "UNAUTHORIZED", "Invalid Firebase ID token.");
  }

  let body: Record<string, unknown>;
  try {
    body = await request.json();
  } catch {
    return apiError(400, "BAD_REQUEST", "Request body must be JSON.");
  }

  let requestId: string;
  try {
    requestId = normalizeDeletionRequestId(body.requestId);
  } catch {
    return apiError(400, "BAD_REQUEST", "A valid idempotency requestId is required.");
  }

  const firestore = getAdminDb();
  const projectRef = firestore.collection("projects").doc(projectId);
  const userRef = firestore.collection("users").doc(decoded.uid);
  const now = Date.now();
  let checkpoint: string[] = [];
  let workerToken = "";
  let memberUids: string[] = [];
  const hasPendingOutbox = body.pendingOutboxCount !== undefined && Number(body.pendingOutboxCount) > 0;
  if (hasPendingOutbox && body.confirmPendingOutbox !== true) {
    return apiError(400, "BAD_REQUEST", "Confirm pending local changes before deleting the project.");
  }

  try {
    await firestore.runTransaction(async (transaction: FirebaseFirestore.Transaction) => {
      const snapshot = await transaction.get(projectRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
      const userSnapshot = await transaction.get(userRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
      if (!snapshot.exists) throw new Error("NOT_FOUND");
      const data = projectData(snapshot.data());
      const userData = userSnapshot.exists ? projectData(userSnapshot.data()) : {};
      memberUids = Array.isArray(data.deletionMemberUids)
        ? data.deletionMemberUids.filter((uid): uid is string => typeof uid === "string" && uid.length > 0)
        : [];
      const state = deletionState(data.deletionState);
      if (state === "DELETING" || state === "DELETE_FAILED") {
        if (typeof data.deletionRequestId === "string" && data.deletionRequestId && data.deletionRequestId !== requestId) {
          throw new Error("DELETION_IN_PROGRESS");
        }
      }
      validateDeletionAuthorization({
        actorUid: decoded.uid,
        isAdmin: decoded.admin === true,
        ownerUid: typeof data.createdByUid === "string" ? data.createdByUid : typeof data.ownerUid === "string" ? data.ownerUid : null,
        currentState: state,
        // The active project is persisted server-side; client body flags are ignored.
        isActiveOnDevice: userData.activeProjectId === projectId,
        projectName: String(data.name ?? data.projectName ?? projectId),
        projectCode: data.projectCode ? String(data.projectCode) : null,
        typedIdentity: typeof body.typedIdentity === "string" ? body.typedIdentity.trim() : "",
        authTimeEpochSeconds: decoded.auth_time,
        requestIdMatches: data.deletionRequestId === requestId
      });
      checkpoint = Array.isArray(data.deletionCheckpoint)
        ? data.deletionCheckpoint.filter((value): value is string => typeof value === "string")
        : [];
      const workerStartedAt = Number(data.deletionWorkerStartedAtEpochMs ?? 0);
      if (state === "DELETING" && typeof data.deletionWorkerToken === "string" && data.deletionWorkerToken &&
        now - workerStartedAt < WORKER_LEASE_MS) {
        throw new Error("DELETION_IN_PROGRESS");
      }
      workerToken = randomUUID();
      if (memberUids.length === 0) {
        const memberSnapshot = await projectRef.collection("projectMembers").get();
        memberUids = memberSnapshot.docs.map((member: { id: string }) => member.id).filter((uid: string) => uid.length > 0);
      }
      transaction.set(projectRef, {
        deletionState: "DELETING",
        deletionRequestId: requestId,
        deletionCheckpoint: checkpoint,
        deletionStartedAtEpochMs: now,
        deletionActorUid: decoded.uid,
        deletionWorkerUid: decoded.uid,
        deletionWorkerToken: workerToken,
        deletionWorkerStartedAtEpochMs: now,
        deletionMemberUids: memberUids,
        updatedAtEpochMs: now
      }, { merge: true });
    });
  } catch (error) {
    const code = error instanceof Error ? error.message : "BAD_REQUEST";
    if (code === "DELETION_IN_PROGRESS") return apiError(409, "DELETION_IN_PROGRESS", "Another deletion request is already running.");
    if (code === "ACTIVE_PROJECT") return apiError(409, "ACTIVE_PROJECT", "Switch away from the active project before deleting it.");
    if (code === "REAUTH_REQUIRED") return apiError(401, "REAUTH_REQUIRED", "Recent reauthentication is required.");
    if (code === "FORBIDDEN") return apiError(403, "FORBIDDEN", "Only the project creator or a super-admin can delete this project.");
    if (code === "NOT_FOUND") return apiError(404, "BAD_REQUEST", "Project not found.");
    if (code === "IDENTITY_MISMATCH") return apiError(400, "BAD_REQUEST", "Typed project identity does not match.");
    if (code === "ALREADY_DELETED") return apiError(409, "DELETION_IN_PROGRESS", "Project has already been deleted.");
    return apiError(400, "BAD_REQUEST", "Project deletion request was rejected.");
  }

  try {
    for (const collectionName of PROJECT_DELETION_COLLECTIONS) {
      if (checkpoint.includes(collectionName)) continue;
      await deleteCollection(firestore, projectId, collectionName);
      checkpoint = nextDeletionCheckpoint(checkpoint, collectionName);
      await persistCheckpoint(firestore, projectRef, requestId, workerToken, checkpoint);
    }
    if (!checkpoint.includes("accessRequests")) {
      await deleteAccessRequests(firestore, projectId);
      checkpoint = nextDeletionCheckpoint(checkpoint, "accessRequests");
      await persistCheckpoint(firestore, projectRef, requestId, workerToken, checkpoint);
    }
    if (!checkpoint.includes("users")) {
      await removeProjectFromUsers(firestore, projectId);
      checkpoint = nextDeletionCheckpoint(checkpoint, "users");
      await persistCheckpoint(firestore, projectRef, requestId, workerToken, checkpoint);
    }
    if (!checkpoint.includes("projectCatalog")) {
      await firestore.collection("projectCatalog").doc(projectId).delete();
      checkpoint = nextDeletionCheckpoint(checkpoint, "projectCatalog");
      await persistCheckpoint(firestore, projectRef, requestId, workerToken, checkpoint);
    }
    const completedAt = Date.now();
    const auditRef = firestore.collection("projectDeletionAudit").doc(`${projectId}__${requestId}`);
    const tombstoneRef = firestore.collection("projectDeletionTombstones").doc(projectId);
    await firestore.runTransaction(async (transaction: FirebaseFirestore.Transaction) => {
      const snapshot = await transaction.get(projectRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
      const data = projectData(snapshot.data());
      if (!snapshot.exists || data.deletionState !== "DELETING" || data.deletionRequestId !== requestId || data.deletionWorkerToken !== workerToken) {
        throw new Error("WORKER_LOST");
      }
      transaction.set(auditRef, {
      projectId,
      requestId,
      actorAdminId: decoded.uid,
      action: "DELETE",
      previousState: "DELETING",
      newState: "DELETED",
      timestampEpochMs: completedAt,
      mediaPreserved: true
      });
      transaction.set(tombstoneRef, {
      projectId,
      requestId,
      deletionState: "DELETED",
      actorAdminId: decoded.uid,
      deletedAtEpochMs: completedAt,
      checkpoint,
      mediaPreserved: true,
      memberUids
      });
      transaction.set(projectRef, {
      id: projectId,
      deletionState: "DELETED",
      deletionRequestId: requestId,
      cloudDeletionCompletedAtEpochMs: completedAt,
      deletedAtEpochMs: completedAt,
      updatedAtEpochMs: completedAt,
      tombstone: true,
      isDeleted: true
      }, { merge: false });
    });
    return NextResponse.json({ success: true, data: { projectId, requestId, deletionState: "DELETED", mediaPreserved: true } });
  } catch (error) {
    if (error instanceof Error && error.message === "WORKER_LOST") {
      return apiError(409, "DELETION_IN_PROGRESS", "Deletion was claimed by another worker; retry after it completes.");
    }
    const message = error instanceof Error ? error.message : "Cloud deletion failed.";
    try {
      await firestore.runTransaction(async (transaction: FirebaseFirestore.Transaction) => {
        const snapshot = await transaction.get(projectRef as any) as unknown as FirebaseFirestore.DocumentSnapshot;
        const data = projectData(snapshot.data());
        if (!snapshot.exists || data.deletionRequestId !== requestId || data.deletionWorkerToken !== workerToken) {
          throw new Error("WORKER_LOST");
        }
        transaction.set(projectRef, {
          deletionState: "DELETE_FAILED",
          deletionRequestId: requestId,
          deletionCheckpoint: checkpoint,
          deletionErrorCode: "CLOUD_DELETE_FAILED",
          deletionErrorMessage: message.slice(0, 500),
          deletionWorkerUid: FieldValue.delete(),
          deletionWorkerToken: FieldValue.delete(),
          deletionWorkerStartedAtEpochMs: FieldValue.delete(),
          updatedAtEpochMs: Date.now()
        }, { merge: true });
      });
    } catch (failure) {
      if (failure instanceof Error && failure.message === "WORKER_LOST") {
        return apiError(409, "DELETION_IN_PROGRESS", "Deletion was claimed by another worker; retry after it completes.");
      }
      throw failure;
    }
    return apiError(502, "DELETE_FAILED", "Cloud deletion failed; retry the same requestId to resume.");
  }
}
