import { test } from "node:test";
import assert from "node:assert";
import {
  mergeProjectData,
  nextDeletionCheckpoint,
  normalizeDeletionRequestId,
  validateDeletionAuthorization
} from "../lib/project-deletion";

test("deletion authorization requires creator/admin, recent auth, identity and inactive project", () => {
  assert.throws(() => validateDeletionAuthorization({
    actorUid: "member",
    isAdmin: false,
    ownerUid: "creator",
    currentState: "ACTIVE",
    isActiveOnDevice: false,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "Alpha",
    authTimeEpochSeconds: 1_000,
    nowEpochSeconds: 1_400
  }), /FORBIDDEN/);

  assert.throws(() => validateDeletionAuthorization({
    actorUid: "creator",
    isAdmin: false,
    ownerUid: "creator",
    currentState: "ACTIVE",
    isActiveOnDevice: true,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "Alpha",
    authTimeEpochSeconds: 1_390,
    nowEpochSeconds: 1_400
  }), /ACTIVE_PROJECT/);

  assert.throws(() => validateDeletionAuthorization({
    actorUid: "creator",
    isAdmin: false,
    ownerUid: "creator",
    currentState: "ACTIVE",
    isActiveOnDevice: false,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "wrong",
    authTimeEpochSeconds: 1_390,
    nowEpochSeconds: 1_400
  }), /IDENTITY_MISMATCH/);

  assert.throws(() => validateDeletionAuthorization({
    actorUid: "creator",
    isAdmin: false,
    ownerUid: "creator",
    currentState: "ACTIVE",
    isActiveOnDevice: false,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "Alpha",
    authTimeEpochSeconds: 1_000,
    nowEpochSeconds: 1_400
  }), /REAUTH_REQUIRED/);
});

test("admin authorization and retry states are accepted, while checkpoint is idempotent", () => {
  assert.doesNotThrow(() => validateDeletionAuthorization({
    actorUid: "super-admin",
    isAdmin: true,
    ownerUid: "creator",
    currentState: "DELETE_FAILED",
    isActiveOnDevice: false,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "ALPHA-1",
    authTimeEpochSeconds: 1_390,
    nowEpochSeconds: 1_400
  }));
  assert.deepStrictEqual(nextDeletionCheckpoint(["gis_node"], "gis_node"), ["gis_node"]);
  assert.deepStrictEqual(nextDeletionCheckpoint(["gis_node"], "task"), ["gis_node", "task"]);
  assert.throws(() => validateDeletionAuthorization({
    actorUid: "super-admin",
    isAdmin: true,
    ownerUid: "creator",
    currentState: "DELETING",
    isActiveOnDevice: false,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "Alpha",
    authTimeEpochSeconds: 1_390,
    nowEpochSeconds: 1_400
  }), /DELETION_IN_PROGRESS/);
  assert.doesNotThrow(() => validateDeletionAuthorization({
    actorUid: "super-admin",
    isAdmin: true,
    ownerUid: "creator",
    currentState: "DELETING",
    requestIdMatches: true,
    isActiveOnDevice: false,
    projectName: "Alpha",
    projectCode: "ALPHA-1",
    typedIdentity: "Alpha",
    authTimeEpochSeconds: 1_390,
    nowEpochSeconds: 1_400
  }));
});

test("request IDs are bounded and envelope data is readable", () => {
  assert.strictEqual(normalizeDeletionRequestId("request-1234"), "request-1234");
  assert.throws(() => normalizeDeletionRequestId("short"), /INVALID_REQUEST_ID/);
  assert.deepStrictEqual(mergeProjectData({ data: { name: "Alpha", projectCode: "A1" } }), {
    name: "Alpha",
    projectCode: "A1",
    data: { name: "Alpha", projectCode: "A1" }
  });
});
