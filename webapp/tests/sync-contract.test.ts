import { test } from "node:test";
import assert from "node:assert/strict";
import { buildTombstone, shouldApplySyncUpdate } from "../lib/sync";

test("sync contract accepts newer and equal timestamps, rejects stale updates", () => {
  assert.equal(shouldApplySyncUpdate(100, 101), true);
  assert.equal(shouldApplySyncUpdate(100, 100), true);
  assert.equal(shouldApplySyncUpdate(100, 99), false);
  assert.equal(shouldApplySyncUpdate(100, Number.NaN), false);
});

test("sync contract builds a project-scoped tombstone without changing the record id", () => {
  const tombstone = buildTombstone(
    { id: "note-1", projectId: "old-project", content: "old", updatedAtEpochMs: 10 },
    "project-1",
    20
  );

  assert.deepEqual(tombstone, {
    id: "note-1",
    projectId: "project-1",
    content: "old",
    updatedAtEpochMs: 20,
    isDeleted: true,
    deletedAtEpochMs: 20
  });
});

test("sync contract rejects tombstones without an id", () => {
  assert.throws(
    () => buildTombstone({ projectId: "project-1" }, "project-1", 20),
    /Không thể xóa bản ghi không có mã/
  );
});
