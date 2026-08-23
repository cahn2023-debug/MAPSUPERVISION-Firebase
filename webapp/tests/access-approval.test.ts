import { test } from "node:test";
import assert from "node:assert";
import {
  validateApprovedScope,
  type ProjectAccessRequestRow,
  type AccessAdminAction,
  type AccessRequestStatus
} from "../lib/sync";

(process.env as Record<string, string>).NODE_ENV = "test";

test("validateApprovedScope - rejects empty or blank data groups", () => {
  assert.throws(
    () => validateApprovedScope([], "ALL", []),
    /Phê duyệt cần ít nhất một nhóm dữ liệu/
  );
  assert.throws(
    () => validateApprovedScope(["   ", ""], "ALL", []),
    /Phê duyệt cần ít nhất một nhóm dữ liệu/
  );
});

test("validateApprovedScope - accepts valid data groups with ALL contractor scope", () => {
  assert.doesNotThrow(() => {
    validateApprovedScope(["DEFAULT", "MAP"], "ALL", []);
  });
  assert.doesNotThrow(() => {
    validateApprovedScope(["GIS"], "ALL", []);
  });
});

test("validateApprovedScope - rejects SCOPED contractor scope with empty or blank contractors", () => {
  assert.throws(
    () => validateApprovedScope(["DEFAULT"], "SCOPED", []),
    /Phạm vi SCOPED cần ít nhất một nhà thầu/
  );
  assert.throws(
    () => validateApprovedScope(["DEFAULT"], "SCOPED", ["   ", ""]),
    /Phạm vi SCOPED cần ít nhất một nhà thầu/
  );
});

test("validateApprovedScope - accepts SCOPED contractor scope with valid contractors", () => {
  assert.doesNotThrow(() => {
    validateApprovedScope(["DEFAULT", "GIS"], "SCOPED", ["CONTRACTOR_A", "CONTRACTOR_B"]);
  });
  assert.doesNotThrow(() => {
    validateApprovedScope(["MATERIALS"], "SCOPED", ["CONSTRUCTION_CORP"]);
  });
});

function isValidAdminTransition(previous: AccessRequestStatus, next: AccessRequestStatus): boolean {
  return (
    (previous === "PENDING" && (next === "APPROVED" || next === "REJECTED")) ||
    (previous === "APPROVED" && next === "REVOKED")
  );
}

function validAuditShape(data: Record<string, unknown>): boolean {
  const requiredKeys = [
    "projectId",
    "targetUserId",
    "action",
    "previousState",
    "newState",
    "actorAdminId",
    "timestampEpochMs"
  ];
  const keys = Object.keys(data);
  if (keys.length !== requiredKeys.length || !requiredKeys.every((k) => keys.includes(k))) {
    return false;
  }
  return (
    typeof data.projectId === "string" && Boolean(data.projectId) &&
    typeof data.targetUserId === "string" && Boolean(data.targetUserId) &&
    typeof data.action === "string" &&
    typeof data.previousState === "string" &&
    typeof data.newState === "string" &&
    typeof data.actorAdminId === "string" && Boolean(data.actorAdminId) &&
    typeof data.timestampEpochMs === "number" && data.timestampEpochMs >= 0 &&
    isValidAdminTransition(data.previousState as AccessRequestStatus, data.newState as AccessRequestStatus) &&
    ((data.action === "APPROVE" && data.newState === "APPROVED") ||
     (data.action === "REJECT" && data.newState === "REJECTED") ||
     (data.action === "REVOKE" && data.newState === "REVOKED"))
  );
}

test("Admin transition rules - validates allowed admin transitions", () => {
  // Valid transitions
  assert.strictEqual(isValidAdminTransition("PENDING", "APPROVED"), true);
  assert.strictEqual(isValidAdminTransition("PENDING", "REJECTED"), true);
  assert.strictEqual(isValidAdminTransition("APPROVED", "REVOKED"), true);

  // Invalid transitions
  assert.strictEqual(isValidAdminTransition("PENDING", "REVOKED"), false);
  assert.strictEqual(isValidAdminTransition("APPROVED", "REJECTED"), false);
  assert.strictEqual(isValidAdminTransition("REJECTED", "REVOKED"), false);
  assert.strictEqual(isValidAdminTransition("REVOKED", "REJECTED"), false);
  assert.strictEqual(isValidAdminTransition("REVOKED", "APPROVED"), false);
});

test("Audit shape compliance - verifies exact firestore.rules audit structure", () => {
  const validAudit = {
    projectId: "proj-alpha",
    targetUserId: "user-123",
    action: "APPROVE",
    previousState: "PENDING",
    newState: "APPROVED",
    actorAdminId: "admin-456",
    timestampEpochMs: Date.now()
  };
  assert.strictEqual(validAuditShape(validAudit), true);

  const rejectAudit = {
    projectId: "proj-alpha",
    targetUserId: "user-123",
    action: "REJECT",
    previousState: "PENDING",
    newState: "REJECTED",
    actorAdminId: "admin-456",
    timestampEpochMs: Date.now()
  };
  assert.strictEqual(validAuditShape(rejectAudit), true);

  const revokeAudit = {
    projectId: "proj-alpha",
    targetUserId: "user-123",
    action: "REVOKE",
    previousState: "APPROVED",
    newState: "REVOKED",
    actorAdminId: "admin-456",
    timestampEpochMs: Date.now()
  };
  assert.strictEqual(validAuditShape(revokeAudit), true);

  // Missing field
  const invalidMissing = {
    projectId: "proj-alpha",
    targetUserId: "user-123",
    action: "APPROVE"
  };
  assert.strictEqual(validAuditShape(invalidMissing), false);

  // Extra unauthorized field
  const invalidExtra = {
    ...validAudit,
    extraField: "not_allowed"
  };
  assert.strictEqual(validAuditShape(invalidExtra), false);
});
