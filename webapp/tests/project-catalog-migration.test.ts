import test from "node:test";
import assert from "node:assert/strict";
import {
  buildCatalogMigrationPlan,
  normalizeProjectDocument,
  validateFallbackOwnerUid
} from "../lib/project-catalog-migration";

test("normalizes legacy metadata and fallback owner deterministically", () => {
  const normalized = normalizeProjectDocument(
    { id: "legacy-123456", data: { data: { slug: "legacy-project", isArchived: true } } },
    "fallback-owner"
  );

  assert.equal(normalized.entry?.projectName, "legacy-123456");
  assert.equal(normalized.entry?.projectCode, "legacy-project");
  assert.equal(normalized.entry?.createdByUid, "fallback-owner");
  assert.equal(normalized.entry?.updatedAtEpochMs, 0);
  assert.equal(normalized.entry?.status, "ARCHIVED");
  assert.equal(normalized.warnings.length, 3);
});

test("metadata fallbacks produce a completed-with-warnings report status", () => {
  const plan = buildCatalogMigrationPlan({
    projects: [{ id: "legacy-1", data: { createdByUid: "owner" } }],
    catalog: [],
  });

  assert.equal(plan.status, "COMPLETED_WITH_WARNINGS");
  assert.ok(plan.counts.warning > 0);
});

test("missing owner without fallback becomes a discrepancy", () => {
  const plan = buildCatalogMigrationPlan({
    projects: [{ id: "ownerless", data: { name: "Ownerless" } }],
    catalog: []
  });

  assert.equal(plan.status, "COMPLETED_WITH_WARNINGS");
  assert.equal(plan.counts.discrepancy, 1);
  assert.equal(plan.operations.length, 0);
});

test("matching catalog is idempotent and tombstoned catalog is deleted", () => {
  const plan = buildCatalogMigrationPlan({
    projects: [{ id: "active", data: { name: "Active", projectCode: "A-1", createdByUid: "owner", updatedAtEpochMs: 10 } }],
    catalog: [
      { projectId: "active", projectName: "Active", projectCode: "A-1", createdByUid: "owner", updatedAtEpochMs: 10, status: "ACTIVE" },
      { projectId: "deleted", projectName: "Deleted", projectCode: "D-1", createdByUid: "owner", updatedAtEpochMs: 10, status: "ACTIVE" }
    ],
    tombstoneIds: new Set(["deleted"])
  });

  assert.equal(plan.counts.unchanged, 1);
  assert.deepEqual(plan.operations, [{ kind: "delete", projectId: "deleted" }]);
  assert.equal(plan.status, "COMPLETED");
});

test("deletion state removes catalog even before a tombstone is written", () => {
  const plan = buildCatalogMigrationPlan({
    projects: [{ id: "deleting", data: { name: "Deleting", deletionState: "DELETING" } }],
    catalog: [{ projectId: "deleting", projectName: "Deleting", projectCode: "D-1", createdByUid: "owner", updatedAtEpochMs: 10, status: "ACTIVE" }]
  });

  assert.deepEqual(plan.operations, [{ kind: "delete", projectId: "deleting" }]);
});

test("existing catalog owner is preserved and discrepancy is reported", () => {
  const plan = buildCatalogMigrationPlan({
    projects: [{ id: "project-1", data: { name: "Project", createdByUid: "source-owner", updatedAtEpochMs: 20 } }],
    catalog: [{ projectId: "project-1", projectName: "Old", projectCode: "PROJECT-1", createdByUid: "catalog-owner", updatedAtEpochMs: 10, status: "ACTIVE" }]
  });

  assert.equal(plan.operations[0]?.kind, "update");
  assert.equal(plan.operations[0]?.kind === "update" ? plan.operations[0].entry.createdByUid : "", "catalog-owner");
  assert.equal(plan.status, "COMPLETED_WITH_WARNINGS");
});

test("fallback owner validation rejects blank values", () => {
  assert.equal(validateFallbackOwnerUid("  "), null);
  assert.equal(validateFallbackOwnerUid("owner-1"), "owner-1");
});
