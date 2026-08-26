process.env.NODE_ENV = "test";
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  readPublicProject,
  setDriveSnapshotReaderMock,
  resetPublicProjectCacheForTesting,
  KNOWN_PUBLIC_PROJECT_ID
} from "../lib/public-project";
import { pruneOldDriveSnapshots, SNAPSHOT_RETENTION_MAX_AGE_MS } from "../lib/google-drive-media";
import { setAdminDbMock } from "../lib/firebase-admin";

test("public-project: reads real project and subcollections without injecting synthetic records (Firestore fallback)", async () => {
  resetPublicProjectCacheForTesting();
  setDriveSnapshotReaderMock(async () => null); // Simulate no drive snapshot yet

  const fakeProjectData = {
    name: "Dự án 269 - 2026",
    code: "269-2026",
    slug: "269-2026",
    status: "ACTIVE"
  };

  const mockDb = {
    collection: (colName: string) => {
      if (colName === "projects") {
        return {
          doc: (docId: string) => ({
            get: async () => ({
              exists: true,
              id: docId,
              data: () => ({ ...fakeProjectData, id: docId })
            }),
            collection: (subCol: string) => ({
              get: async () => ({
                docs: [
                  {
                    id: `${subCol}-1`,
                    data: () => ({ id: `${subCol}-1`, title: `Real ${subCol} record` })
                  }
                ]
              })
            })
          }),
          get: async () => ({
            docs: [
              {
                id: KNOWN_PUBLIC_PROJECT_ID,
                data: () => ({ ...fakeProjectData, id: KNOWN_PUBLIC_PROJECT_ID })
              }
            ]
          })
        };
      }
      return {
        get: async () => ({ docs: [] })
      };
    }
  };

  setAdminDbMock(mockDb);

  const payload = await readPublicProject();
  assert.ok(payload);
  assert.equal(payload.project.name, "Dự án 269 - 2026");
  assert.equal(payload.collections.gis_node.length, 1);
  assert.equal(payload.collections.gis_node[0].id, "gis_node-1");
  assert.equal(payload.collections.daily_log.length, 1);
  assert.equal(payload.collections.daily_log[0].id, "daily_log-1");
});

test("public-project: prioritizes Google Drive Snapshot over Firestore with 0 Firestore reads", async () => {
  resetPublicProjectCacheForTesting();
  let firestoreAccessed = false;
  setAdminDbMock({
    collection: () => {
      firestoreAccessed = true;
      throw new Error("Firestore should NOT be touched when Drive snapshot exists!");
    }
  });

  const mockSnapshotPayload = {
    project: {
      id: KNOWN_PUBLIC_PROJECT_ID,
      name: "Dự án 269 - 2026 Drive Snapshot",
      code: "269-2026"
    },
    collections: {
      gis_node: [{ id: "drive-node-1", code: "N01" }],
      gis_route: [{ id: "drive-route-1", code: "R01" }]
    },
    updatedAtEpochMs: Date.now() + 5000
  };

  setDriveSnapshotReaderMock(async () => mockSnapshotPayload);

  const payload = await readPublicProject();
  assert.ok(payload);
  assert.equal(firestoreAccessed, false, "Firestore was never touched");
  assert.equal(payload.project.name, "Dự án 269 - 2026 Drive Snapshot");
  assert.equal(payload.collections.gis_node.length, 1);
  assert.equal(payload.collections.gis_node[0].id, "drive-node-1");
});

test("google-drive-media: pruneOldDriveSnapshots preserves newest snapshot and deletes files > 5 minutes", async () => {
  const now = Date.now();
  const deletedFiles: string[] = [];
  const fakeDrive: any = {
    files: {
      list: async () => ({
        data: {
          files: [
            { id: "snap-newest", name: "snapshot_latest.json", createdTime: new Date(now).toISOString() },
            { id: "snap-3min-old", name: "snapshot_3min.json", createdTime: new Date(now - 3 * 60 * 1000).toISOString() },
            { id: "snap-6min-old", name: "snapshot_6min.json", createdTime: new Date(now - 6 * 60 * 1000).toISOString() },
            { id: "snap-10min-old", name: "snapshot_10min.json", createdTime: new Date(now - 10 * 60 * 1000).toISOString() }
          ]
        }
      }),
      delete: async ({ fileId }: { fileId: string }) => {
        deletedFiles.push(fileId);
        return { data: {} };
      }
    }
  };

  const res = await pruneOldDriveSnapshots(fakeDrive, "folder-snapshots-123", SNAPSHOT_RETENTION_MAX_AGE_MS);
  assert.deepEqual(res.deletedFileIds, ["snap-6min-old", "snap-10min-old"]);
  assert.deepEqual(deletedFiles, ["snap-6min-old", "snap-10min-old"]);
});

