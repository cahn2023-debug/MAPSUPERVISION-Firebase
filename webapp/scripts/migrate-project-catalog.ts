import { randomUUID } from "node:crypto";
import { getAdminAuth, getAdminDb } from "../lib/firebase-admin";
import {
  buildCatalogMigrationPlan,
  catalogEntryFields,
  type CatalogDocument,
  type CatalogOperation,
  type CatalogProjectDocument,
  validateFallbackOwnerUid
} from "../lib/project-catalog-migration";

const PAGE_SIZE = 200;
const BATCH_SIZE = 400;

function args(): { mode: "dry-run" | "execute"; fallbackOwnerUid?: string; confirmed: boolean } {
  const values = process.argv.slice(2);
  const modes = [values.includes("--dry-run"), values.includes("--execute")].filter(Boolean).length;
  if (modes !== 1) throw new Error("Choose exactly one mode: --dry-run or --execute.");
  const mode = values.includes("--execute") ? "execute" : "dry-run";
  if (values.includes("--execute") && !values.includes("--confirm-production")) {
    throw new Error("--execute requires --confirm-production.");
  }
  const ownerIndex = values.indexOf("--fallback-owner-uid");
  const fallbackOwnerUid = ownerIndex >= 0 ? validateFallbackOwnerUid(values[ownerIndex + 1]) ?? undefined : undefined;
  return { mode, fallbackOwnerUid, confirmed: values.includes("--confirm-production") };
}

async function readCollection(collection: FirebaseFirestore.CollectionReference): Promise<FirebaseFirestore.QueryDocumentSnapshot[]> {
  const rows: FirebaseFirestore.QueryDocumentSnapshot[] = [];
  let query: FirebaseFirestore.Query = collection.orderBy("__name__").limit(PAGE_SIZE);
  while (true) {
    const snapshot = await query.get();
    rows.push(...snapshot.docs);
    if (snapshot.size < PAGE_SIZE) return rows;
    query = query.startAfter(snapshot.docs[snapshot.docs.length - 1]);
  }
}

function asProjectRows(docs: FirebaseFirestore.QueryDocumentSnapshot[]): CatalogProjectDocument[] {
  return docs.map(doc => ({ id: doc.id, data: doc.data() as Record<string, unknown> }));
}

function asCatalogRows(docs: FirebaseFirestore.QueryDocumentSnapshot[]): CatalogDocument[] {
  return docs.map(doc => {
    const data = doc.data();
    return {
      projectId: doc.id,
      projectName: typeof data.projectName === "string" ? data.projectName : undefined,
      projectCode: typeof data.projectCode === "string" ? data.projectCode : undefined,
      createdByUid: typeof data.createdByUid === "string" ? data.createdByUid : undefined,
      updatedAtEpochMs: typeof data.updatedAtEpochMs === "number" ? data.updatedAtEpochMs : undefined,
      status: data.status === "ACTIVE" || data.status === "ARCHIVED" ? data.status : undefined
    };
  });
}

async function applyOperations(db: FirebaseFirestore.Firestore, operations: CatalogOperation[]): Promise<void> {
  for (let index = 0; index < operations.length; index += BATCH_SIZE) {
    const batch = db.batch();
    for (const operation of operations.slice(index, index + BATCH_SIZE)) {
      const reference = db.collection("projectCatalog").doc(
        operation.kind === "delete" ? operation.projectId : operation.entry.projectId
      );
      if (operation.kind === "delete") batch.delete(reference);
      else batch.set(reference, catalogEntryFields(operation.entry));
    }
    await batch.commit();
  }
}

export async function runMigration(): Promise<void> {
  const options = args();
  const db = getAdminDb();
  const auth = getAdminAuth();
  const runId = randomUUID();
  const startedAtEpochMs = Date.now();
  if (options.fallbackOwnerUid) await auth.getUser(options.fallbackOwnerUid);

  const [projectDocs, catalogDocs, tombstoneDocs] = await Promise.all([
    readCollection(db.collection("projects")),
    readCollection(db.collection("projectCatalog")),
    readCollection(db.collection("projectDeletionTombstones"))
  ]);
  const plan = buildCatalogMigrationPlan({
    projects: asProjectRows(projectDocs),
    catalog: asCatalogRows(catalogDocs),
    tombstoneIds: new Set(tombstoneDocs.map(doc => doc.id)),
    fallbackOwnerUid: options.fallbackOwnerUid
  });
  const report = {
    runId,
    mode: options.mode,
    status: plan.status,
    startedAtEpochMs,
    completedAtEpochMs: Date.now(),
    fallbackOwnerUid: options.fallbackOwnerUid ?? null,
    counts: plan.counts,
    warnings: plan.warnings,
    discrepancies: plan.discrepancies
  };

  if (options.mode === "execute") {
    await applyOperations(db, plan.operations);
    await db.collection("catalogMigrations").doc(runId).set(report);
  }
  console.log(JSON.stringify(report, null, 2));
}

if (process.argv[1]?.endsWith("migrate-project-catalog.ts")) {
  runMigration().catch(error => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
