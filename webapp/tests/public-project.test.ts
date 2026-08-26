process.env.NODE_ENV = "test";
import { test } from "node:test";
import assert from "node:assert/strict";
import { readPublicProject, KNOWN_PUBLIC_PROJECT_ID } from "../lib/public-project";
import { setAdminDbMock } from "../lib/firebase-admin";

test("public-project: reads real project and subcollections without injecting synthetic records", async () => {
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
