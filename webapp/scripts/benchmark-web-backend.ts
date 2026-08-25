import { createHash, randomUUID } from "node:crypto";
import { execFile as execFileCallback, spawn, type ChildProcess } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { cert, deleteApp, getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { google, type drive_v3 } from "googleapis";
import {
  approvedJourneys,
  anonymizeBenchmarkValue,
  assertAnonymizedBenchmarkValue,
  assertSafeBenchmarkArtifact,
  FirestoreOperationCounter,
  parsePerformanceTestProfile,
  percentile,
  runBenchmark,
  type BenchmarkAdapters,
  type BenchmarkStageContext,
  type BenchmarkStageResult,
  type PerformanceTestProfile
} from "../lib/performance-benchmark";

const projectCollections = [
  "gis_node", "gis_route", "task", "note", "work_plan", "daily_log", "site_photos",
  "work_volume_progress", "material_declaration", "material_handover", "report_draft"
] as const;
const webappRoot = path.resolve(import.meta.dirname, "..");
const workspaceRoot = path.resolve(webappRoot, "..");
const profilePath = path.join(webappRoot, "benchmarks", "performance-test-profile.json");
const resultsDirectory = path.join(webappRoot, "benchmarks", "results");
const execFile = promisify(execFileCallback);

type SafeDocument = { id: string; data: Record<string, unknown> };
type SafeDataset = {
  anonymized: boolean;
  reference: string;
  fingerprint: string;
  counts: Record<string, number>;
  project: Record<string, unknown>;
  collections: Record<string, SafeDocument[]>;
};
type NetworkEvidence = {
  targetCategory: "firestore-api";
  sampleCount: number;
  successCount: number;
  failureRate: number;
  latencyMs: { p50: number | null; p95: number | null };
};

function loadEnvFile(filePath: string): void {
  if (!existsSync(filePath)) return;
  for (const rawLine of readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const separator = line.indexOf("=");
    if (separator < 1) continue;
    const key = line.slice(0, separator).trim();
    if (process.env[key]) continue;
    let value = line.slice(separator + 1).trim();
    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    process.env[key] = value;
  }
}

function serviceAccount(prefix: "FIREBASE" | "GOOGLE"): Record<string, string> {
  const raw = process.env[`${prefix}_SERVICE_ACCOUNT_JSON`]?.trim();
  const configuredFile = process.env[`${prefix}_SERVICE_ACCOUNT_FILE`]?.trim();
  let parsed: Record<string, string> | null = null;
  const candidates = [
    configuredFile ? path.resolve(webappRoot, configuredFile) : "",
    configuredFile ? path.resolve(workspaceRoot, configuredFile) : "",
    configuredFile ? path.resolve(configuredFile) : ""
  ].filter(Boolean);
  const credentialFile = candidates.find(candidate => existsSync(candidate));
  if (credentialFile) parsed = JSON.parse(readFileSync(credentialFile, "utf8")) as Record<string, string>;
  else if (raw) {
    const first = JSON.parse(raw) as Record<string, string> | string;
    parsed = typeof first === "string" ? JSON.parse(first) as Record<string, string> : first;
  } else if (prefix === "FIREBASE") {
    const fallback = path.join(workspaceRoot, "mapsupervision-3d985eee34f0.json");
    if (existsSync(fallback)) parsed = JSON.parse(readFileSync(fallback, "utf8")) as Record<string, string>;
  }
  if (!parsed) throw new Error(`${prefix} service account is not configured`);
  if (parsed.private_key) parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
  return parsed;
}

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function sha(value: unknown): string {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

async function readAnonymizedSource(profile: PerformanceTestProfile, sourceProjectId: string): Promise<SafeDataset> {
  const app = initializeApp({
    credential: cert(serviceAccount("FIREBASE")),
    projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "mapsupervision"
  }, `benchmark-source-${randomUUID()}`);
  try {
    const db = getFirestore(app);
    const projectRef = db.collection("projects").doc(sourceProjectId);
    const [projectSnapshot, ...snapshots] = await Promise.all([
      projectRef.get(),
      ...projectCollections.map(name => projectRef.collection(name).orderBy("__name__").get())
    ]);
    if (!projectSnapshot.exists) throw new Error("Selected source project is not available");
    const collections: Record<string, SafeDocument[]> = {};
    const counts: Record<string, number> = {};
    snapshots.forEach((snapshot, collectionIndex) => {
      const name = projectCollections[collectionIndex];
      collections[name] = snapshot.docs.map((document, documentIndex) => ({
        id: `d${String(documentIndex + 1).padStart(6, "0")}`,
        data: anonymizeBenchmarkValue(document.data(), (collectionIndex + 1) * 100000 + documentIndex) as Record<string, unknown>
      }));
      collections[name].forEach(document => assertAnonymizedBenchmarkValue(document.data));
      counts[name] = snapshot.size;
    });
    const documentCount = Object.values(counts).reduce((sum, count) => sum + count, 0);
    if (documentCount !== profile.datasets.anonymizedReal.expectedDocumentCount) {
      throw new Error(`Source cardinality changed: expected ${profile.datasets.anonymizedReal.expectedDocumentCount}, received ${documentCount}`);
    }
    const project = anonymizeBenchmarkValue(projectSnapshot.data(), 1) as Record<string, unknown>;
    assertAnonymizedBenchmarkValue(project);
    counts.projects = 1;
    const fingerprint = sha({ project, collections });
    return { anonymized: true, reference: profile.datasets.anonymizedReal.reference, fingerprint, counts, project, collections };
  } finally {
    await deleteApp(app);
  }
}

function syntheticDocument(collection: string, index: number): SafeDocument {
  const id = `d${String(index + 1).padStart(6, "0")}`;
  return { id, data: { id, projectId: "benchmark-project", updatedAtEpochMs: 1700000000000 + index, isDeleted: false, data: { id, projectId: "benchmark-project", updatedAtEpochMs: 1700000000000 + index, isDeleted: false, value: index, type: collection } } };
}

function createLargeSynthetic(profile: PerformanceTestProfile): SafeDataset {
  const size = profile.datasets.largeSynthetic.cardinality;
  const counts: Record<string, number> = {
    projects: size.projects,
    gis_node: size.mapNodes, gis_route: size.mapRoutes, task: size.tasks, note: Math.ceil(size.tasks / 2),
    work_plan: Math.ceil(size.tasks / 2), daily_log: size.diaries, site_photos: size.mediaItems,
    work_volume_progress: size.diaries, material_declaration: Math.ceil(size.adminRows / 2),
    material_handover: Math.ceil(size.adminRows / 2), report_draft: size.adminRows
  };
  const collections = Object.fromEntries(projectCollections.map(name => [name, Array.from({ length: counts[name] }, (_, index) => syntheticDocument(name, index))]));
  return {
    anonymized: false,
    reference: profile.datasets.largeSynthetic.reference,
    fingerprint: sha({ seed: profile.datasets.largeSynthetic.seed, counts }),
    counts,
    project: { data: { name: "Benchmark Project", updatedAtEpochMs: 1700000000000, isDeleted: false } },
    collections
  };
}

async function waitForPort(host: string, timeoutMs = 30000): Promise<void> {
  const [hostname, portText] = host.split(":");
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const open = await new Promise<boolean>(resolve => {
      const socket = net.createConnection({ host: hostname, port: Number(portText) });
      socket.once("connect", () => { socket.destroy(); resolve(true); });
      socket.once("error", () => resolve(false));
      socket.setTimeout(500, () => { socket.destroy(); resolve(false); });
    });
    if (open) return;
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`Emulator did not start on ${host}`);
}

async function reservePort(host: string): Promise<net.Server> {
  const [hostname, portText] = host.split(":");
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", () => reject(new Error(`Emulator port ${host} is already owned`)));
    server.listen({ host: hostname, port: Number(portText), exclusive: true }, () => resolve(server));
  });
}

async function closeReservation(server: net.Server): Promise<void> {
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
}

async function verifyEmulatorEndpoint(host: string, pathName: string): Promise<void> {
  const response = await fetch(`http://${host}${pathName}`);
  if (response.status >= 500) throw new Error(`Emulator readiness check failed on ${host}`);
}

async function startEmulators(profile: PerformanceTestProfile): Promise<ChildProcess> {
  const reservations: net.Server[] = [];
  try {
    reservations.push(await reservePort(profile.emulator.firestoreHost));
    reservations.push(await reservePort(profile.emulator.authHost));
  } catch (error) {
    await Promise.all(reservations.map(closeReservation));
    throw error;
  }
  await Promise.all(reservations.map(closeReservation));
  let child: ChildProcess | null = null;
  try {
    child = spawn("firebase", ["emulators:start", "--only", "auth,firestore", "--project", profile.emulator.projectId], {
      cwd: workspaceRoot,
      shell: true,
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true
    });
    let output = "";
    child.stdout?.on("data", chunk => { output += String(chunk); });
    child.stderr?.on("data", chunk => { output += String(chunk); });
    await Promise.all([waitForPort(profile.emulator.firestoreHost), waitForPort(profile.emulator.authHost)]);
    const deadline = Date.now() + 30000;
    while (!output.includes("All emulators ready") && Date.now() < deadline && child.exitCode == null) {
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    if (child.exitCode != null || !output.includes("All emulators ready")) {
      throw new Error("Spawned Emulator process did not report readiness");
    }
    await Promise.all([
      verifyEmulatorEndpoint(profile.emulator.firestoreHost, `/v1/projects/${profile.emulator.projectId}/databases/(default)/documents?pageSize=1`),
      verifyEmulatorEndpoint(profile.emulator.authHost, `/emulator/v1/projects/${profile.emulator.projectId}/config`)
    ]);
    return child;
  } catch (error) {
    if (child) await stopEmulators(child);
    throw error;
  }
}

async function stopEmulators(child: ChildProcess): Promise<void> {
  if (!child.pid) return;
  if (process.platform === "win32") {
    const exitCode = await new Promise<number | null>((resolve, reject) => {
      const cleanup = spawn("taskkill", ["/PID", String(child.pid), "/T", "/F"], { stdio: "ignore", windowsHide: true });
      cleanup.once("close", code => resolve(code));
      cleanup.once("error", reject);
    });
    if (exitCode !== 0 && child.exitCode == null) throw new Error(`Failed to stop Emulator process tree (taskkill exit ${exitCode ?? "unknown"})`);
  } else if (child.exitCode == null) {
    child.kill("SIGINT");
  }
  if (child.exitCode == null) {
    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Timed out waiting for Emulator process shutdown")), 30000);
      child.once("close", () => { clearTimeout(timer); resolve(); });
      child.once("error", error => { clearTimeout(timer); reject(error); });
    });
  }
}

async function waitForPortClosed(host: string, timeoutMs = 30000): Promise<void> {
  const [hostname, portText] = host.split(":");
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const open = await new Promise<boolean>(resolve => {
      const socket = net.createConnection({ host: hostname, port: Number(portText) });
      socket.once("connect", () => { socket.destroy(); resolve(true); });
      socket.once("error", () => resolve(false));
      socket.setTimeout(500, () => { socket.destroy(); resolve(false); });
    });
    if (!open) return;
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`Emulator port ${host} remained occupied after shutdown`);
}

async function seedDataset(db: Firestore, projectId: string, dataset: SafeDataset): Promise<void> {
  if (dataset.anonymized) {
    assertAnonymizedBenchmarkValue(dataset.project);
    for (const documents of Object.values(dataset.collections)) {
      documents.forEach(document => assertAnonymizedBenchmarkValue(document.data));
    }
  }
  const projectRef = db.collection("projects").doc(projectId);
  await projectRef.set(dataset.project);
  const projectCount = dataset.counts.projects ?? 1;
  for (let index = 0; index < projectCount; index += 1) {
    const rootId = index === 0 ? projectId : `${projectId}-catalog-${index}`;
    await Promise.all([
      db.collection("projects").doc(rootId).set({ data: { name: `Benchmark Project ${index + 1}`, updatedAtEpochMs: 1700000000000 + index, isDeleted: false } }, { merge: true }),
      db.collection("projectCatalog").doc(rootId).set({ projectName: `Benchmark Project ${index + 1}`, projectCode: `B-${index + 1}`, createdByUid: "benchmark-user", updatedAtEpochMs: 1700000000000 + index, status: "ACTIVE" })
    ]);
  }
  const entries = Object.entries(dataset.collections).flatMap(([collection, docs]) => docs.map(doc => ({ collection, doc })));
  for (let index = 0; index < entries.length; index += 400) {
    const batch = db.batch();
    for (const entry of entries.slice(index, index + 400)) batch.set(projectRef.collection(entry.collection).doc(entry.doc.id), entry.doc.data);
    await batch.commit();
  }
}

async function probeNetwork(profile: PerformanceTestProfile): Promise<NetworkEvidence> {
  const durations: number[] = [];
  let failures = 0;
  try {
    await fetch("https://firestore.googleapis.com", { method: "HEAD", cache: "no-store", signal: AbortSignal.timeout(5000) });
  } catch {
    // The measured samples below determine validity; this request only warms the connection.
  }
  for (let index = 0; index < profile.network.sampleCount; index += 1) {
    const started = performance.now();
    try {
      const response = await fetch("https://firestore.googleapis.com", {
        method: "HEAD",
        cache: "no-store",
        signal: AbortSignal.timeout(5000)
      });
      // The unauthenticated Firestore API root intentionally returns 404; any
      // HTTP response below 500 still proves the endpoint and network path are reachable.
      if (response.status >= 500) {
        failures += 1;
      } else {
        durations.push(performance.now() - started);
      }
    } catch { failures += 1; }
  }
  return { targetCategory: "firestore-api", sampleCount: profile.network.sampleCount, successCount: durations.length, failureRate: failures / profile.network.sampleCount, latencyMs: { p50: percentile(durations, 0.5), p95: percentile(durations, 0.95) } };
}

type ApplicationRevision = { gitHead: string; filesHash: string; dirtyHash: string; fileCount: number };

async function applicationRevision(profile: PerformanceTestProfile): Promise<ApplicationRevision> {
  const [{ stdout: gitHead }, { stdout: trackedFiles }, { stdout: status }] = await Promise.all([
    execFile("git", ["rev-parse", "HEAD"], { cwd: workspaceRoot }),
    execFile("git", ["ls-files", "-z"], { cwd: workspaceRoot }),
    execFile("git", ["status", "--porcelain=v1", "--untracked-files=all"], { cwd: workspaceRoot })
  ]);
  const tracked = trackedFiles.split("\0").filter(file => file.startsWith("webapp/") || file === "firebase.json");
  const paths = [...new Set([...tracked, ...profile.build.requiredFiles])];
  const files = await Promise.all(paths.map(async relativePath => {
    const filePath = path.resolve(workspaceRoot, relativePath);
    const content = await readFile(filePath);
    return [relativePath, sha(content.toString("base64"))] as const;
  }));
  return { gitHead: gitHead.trim(), filesHash: sha(files), dirtyHash: sha(status), fileCount: files.length };
}

function driveClient(): drive_v3.Drive {
  return google.drive({ version: "v3", auth: new google.auth.GoogleAuth({ credentials: serviceAccount("GOOGLE"), scopes: ["https://www.googleapis.com/auth/drive"] }) });
}

function driveRootId(): string {
  const direct = process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID?.trim();
  if (direct) return direct;
  const parsed = new URL(requiredEnv("GOOGLE_DRIVE_ROOT_FOLDER_URL"));
  return parsed.pathname.match(/\/folders\/([^/?#]+)/)?.[1] || parsed.searchParams.get("id") || "";
}

async function createTemporaryDriveFolder(drive: drive_v3.Drive): Promise<string> {
  const created = await drive.files.create({ requestBody: { name: `MAPSUPERVISION Benchmark ${Date.now()}`, mimeType: "application/vnd.google-apps.folder", parents: [driveRootId()] }, fields: "id", supportsAllDrives: true });
  if (!created.data.id) throw new Error("Could not create the temporary Drive benchmark folder");
  return created.data.id;
}

async function deleteCurrentDriveTree(drive: drive_v3.Drive, folderId: string): Promise<void> {
  let pageToken: string | undefined;
  do {
    const response = await drive.files.list({
      q: `'${folderId.replace(/'/g, "\\'")}' in parents and trashed = false`,
      fields: "nextPageToken,files(id,mimeType)",
      pageSize: 1000,
      pageToken,
      supportsAllDrives: true,
      includeItemsFromAllDrives: true
    });
    for (const child of response.data.files ?? []) {
      if (!child.id) continue;
      if (child.mimeType === "application/vnd.google-apps.folder") await deleteCurrentDriveTree(drive, child.id);
      else await drive.files.delete({ fileId: child.id, supportsAllDrives: true });
    }
    pageToken = response.data.nextPageToken ?? undefined;
  } while (pageToken);
  await drive.files.delete({ fileId: folderId, supportsAllDrives: true });
  try {
    await drive.files.get({ fileId: folderId, fields: "id", supportsAllDrives: true });
  } catch (error) {
    const status = (error as { code?: number; response?: { status?: number } }).response?.status ??
      (error as { code?: number }).code;
    if (status === 404) return;
    throw error;
  }
  throw new Error("Temporary Drive benchmark folder still exists after cleanup");
}

async function createEmulatorToken(projectId: string): Promise<{ token: string; cleanup: () => Promise<void> }> {
  const { getAuth } = await import("firebase-admin/auth");
  const { initializeApp: initializeClient, deleteApp: deleteClient } = await import("firebase/app");
  const { connectAuthEmulator, getAuth: getClientAuth, signInWithCustomToken } = await import("firebase/auth");
  const adminAuth = getAuth();
  const uid = `benchmark-${randomUUID()}`;
  await adminAuth.createUser({ uid });
  await adminAuth.setCustomUserClaims(uid, { admin: true });
  const clientApp = initializeClient({ apiKey: "benchmark-api-key", projectId }, `benchmark-client-${randomUUID()}`);
  const clientAuth = getClientAuth(clientApp);
  connectAuthEmulator(clientAuth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  const credential = await signInWithCustomToken(clientAuth, await adminAuth.createCustomToken(uid));
  return { token: await credential.user.getIdToken(), cleanup: async () => { await adminAuth.deleteUser(uid); await deleteClient(clientApp); } };
}

async function consumeNodeStream(stream: NodeJS.ReadableStream): Promise<void> {
  for await (const _chunk of stream) { /* consume real response bytes */ }
}

async function adaptersFor(db: Firestore, datasetProjectId: string, token: string, temporaryDriveFolderId: string): Promise<BenchmarkAdapters> {
  const { NextRequest } = await import("next/server");
  const mediaRoute = await import("../app/api/projects/[projectId]/media/route");
  const driveMedia = await import("../lib/google-drive-media");
  const apiUploads = new Map<number, { photoId: string; remoteUrl: string }>();
  const directUploads = new Map<number, string>();
  const mediaBytes = new Uint8Array(4096).fill(66);
  const projectRef = db.collection("projects").doc(datasetProjectId);

  const firestore = async (context: BenchmarkStageContext): Promise<BenchmarkStageResult> => {
    const counter = new FirestoreOperationCounter();
    if (context.route === "firestore:project-core") {
      const [project, catalog] = await Promise.all([projectRef.get(), db.collection("projectCatalog").limit(20).get()]);
      counter.read(Number(project.exists) + catalog.size);
      return { firestoreOperationCount: counter.total };
    }
    if (context.route === "firestore:map-geometry") {
      const [nodes, routes] = await Promise.all([projectRef.collection("gis_node").get(), projectRef.collection("gis_route").get()]);
      counter.read(nodes.size + routes.size);
      return { firestoreOperationCount: counter.total };
    }
    if (context.route === "firestore:business-query") {
      const [tasks, diaries] = await Promise.all([projectRef.collection("task").orderBy("updatedAtEpochMs", "desc").limit(100).get(), projectRef.collection("daily_log").orderBy("updatedAtEpochMs", "desc").limit(100).get()]);
      counter.read(tasks.size + diaries.size);
      return { firestoreOperationCount: counter.total };
    }
    if (context.route === "firestore:business-write") {
      await projectRef.collection("task").doc(`benchmark-write-${context.runIndex}`).set({ data: { id: `benchmark-write-${context.runIndex}`, projectId: datasetProjectId, updatedAtEpochMs: Date.now(), isDeleted: false } });
      counter.write();
      return { firestoreOperationCount: counter.total };
    }
    const media = await projectRef.collection("site_photos").orderBy("updatedAtEpochMs", "desc").limit(100).get();
    counter.read(media.size);
    return { firestoreOperationCount: counter.total };
  };

  const api = async (context: BenchmarkStageContext): Promise<BenchmarkStageResult> => {
    const counter = new FirestoreOperationCounter();
    const authorization = `Bearer ${token}`;
    if (context.route === "api:media-upload") {
      const photoId = `api-photo-${context.runIndex}`;
      const form = new FormData();
      form.set("photoId", photoId); form.set("objectCode", "BENCHMARK-NODE"); form.set("objectType", "NODE");
      form.set("mediaType", "IMAGE"); form.set("mimeType", "image/jpeg"); form.set("capturedAtEpochMs", String(1700000000000 + context.runIndex));
      form.set("original", new File([mediaBytes], "benchmark.jpg", { type: "image/jpeg" }));
      const response = await mediaRoute.POST(new NextRequest(`http://localhost/api/projects/${datasetProjectId}/media`, { method: "POST", headers: { authorization }, body: form }), { params: Promise.resolve({ projectId: datasetProjectId }) });
      counter.read(2);
      if (!response.ok) return { success: false, errorCategory: "application" };
      const body = await response.json() as { data?: { remoteUrl?: string } };
      if (!body.data?.remoteUrl) return { success: false, errorCategory: "application" };
      apiUploads.set(context.runIndex, { photoId, remoteUrl: body.data.remoteUrl });
      await projectRef.collection("site_photos").doc(photoId).set({ data: { remoteUrl: body.data.remoteUrl } });
      counter.write();
      return { firestoreOperationCount: counter.total };
    }
    const upload = apiUploads.get(context.runIndex);
    if (!upload) return { success: false, errorCategory: "application" };
    const response = await mediaRoute.GET(new NextRequest(`http://localhost/api/projects/${datasetProjectId}/media?photoId=${upload.photoId}`, { headers: { authorization } }), { params: Promise.resolve({ projectId: datasetProjectId }) });
    counter.read(3);
    if (!response.ok) return { success: false, errorCategory: "application" };
    await response.arrayBuffer();
    return { firestoreOperationCount: counter.total };
  };

  const drive = async (context: BenchmarkStageContext): Promise<BenchmarkStageResult> => {
    if (context.route === "drive:media-upload") {
      const result = await driveMedia.uploadProjectMedia({ projectId: datasetProjectId, projectName: `Benchmark ${datasetProjectId}`, rootFolderId: temporaryDriveFolderId, photoId: `drive-photo-${context.runIndex}`, objectCode: "BENCHMARK-NODE", objectType: "NODE", mediaType: "IMAGE", mimeType: "image/jpeg", capturedAtEpochMs: 1700000000000 + context.runIndex, original: { bytes: Buffer.from(mediaBytes), extension: "jpg" } });
      directUploads.set(context.runIndex, result.driveFileId);
      return {};
    }
    const fileId = directUploads.get(context.runIndex);
    if (!fileId) return { success: false, errorCategory: "application" };
    await consumeNodeStream((await driveMedia.downloadDriveFile(fileId)).stream);
    return {};
  };
  return { firestore, api, drive };
}

function hostErrors(profile: PerformanceTestProfile): string[] {
  const errors: string[] = [];
  if (process.platform !== profile.host.operatingSystem) errors.push("host operating system differs from profile");
  if (process.arch !== profile.host.architecture) errors.push("host architecture differs from profile");
  if ((os.cpus()[0]?.model || "") !== profile.host.cpu) errors.push("host CPU differs from profile");
  if (process.versions.node.split(".")[0] !== profile.host.nodeVersion) errors.push("Node major differs from profile");
  return errors;
}

function markdownReport(artifact: any): string {
  const lines = ["# Pre-optimization Web Backend Baseline", "", `- Baseline run: ${artifact.baselineRunId}`, `- Profile: ${artifact.profileReference}`, `- Application git HEAD: ${artifact.applicationRevision.gitHead}`, `- Application files hash: ${artifact.applicationRevision.filesHash}`, `- Application dirty-state hash: ${artifact.applicationRevision.dirtyHash}`, `- Dataset: ${artifact.datasetReference}`, `- Dataset fingerprint: ${artifact.datasetFingerprint}`, `- Network probe P50/P95: ${artifact.networkEvidence.latencyMs.p50?.toFixed(2) ?? "n/a"}/${artifact.networkEvidence.latencyMs.p95?.toFixed(2) ?? "n/a"} ms`, "", "| Journey | Runs | P50 ms | P95 ms | Firestore operations | API P95 ms | Drive P95 ms | Error rate |", "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"];
  for (const journey of approvedJourneys) {
    const summary = artifact.summaries[journey];
    lines.push(`| ${journey} | ${summary.runCount} | ${summary.durationMs.p50?.toFixed(2) ?? "n/a"} | ${summary.durationMs.p95?.toFixed(2) ?? "n/a"} | ${summary.firestoreOperationCount.total} | ${summary.apiDurationMs.p95?.toFixed(2) ?? "n/a"} | ${summary.driveDurationMs.p95?.toFixed(2) ?? "n/a"} | ${summary.errorRate.toFixed(4)} |`);
  }
  return `${lines.join("\n")}\n`;
}

type PendingBaseline = { baseName: string; json: string; markdown: string; runId: string };

async function buildBaseline(profile: PerformanceTestProfile, dataset: SafeDataset, networkEvidence: NetworkEvidence, adapters: BenchmarkAdapters, revision: ApplicationRevision, runId: string): Promise<PendingBaseline> {
  const result = await runBenchmark(profile, dataset.reference, adapters);
  const envelope = profile.network.latencyEnvelopeMs;
  const latencyInvalid = networkEvidence.latencyMs.p50 == null || networkEvidence.latencyMs.p95 == null ||
    networkEvidence.latencyMs.p50 < envelope.p50Min || networkEvidence.latencyMs.p50 > envelope.p50Max ||
    networkEvidence.latencyMs.p95 < envelope.p95Min || networkEvidence.latencyMs.p95 > envelope.p95Max;
  const invalidReasons = [...hostErrors(profile), ...(networkEvidence.failureRate > profile.network.maximumFailureRate ? ["network failure rate exceeds profile"] : []), ...(latencyInvalid ? ["network latency is outside the frozen profile envelope"] : []), ...result.invalidReasons];
  if (invalidReasons.length > 0) throw new Error(`Invalid ${dataset.reference} baseline: ${invalidReasons.join("; ")}`);
  const artifact = { artifactVersion: 2, baselineKind: "pre-optimization", baselineRunId: runId, profileReference: result.profileReference, profileHash: sha(profile), applicationRevision: revision, datasetReference: dataset.reference, datasetFingerprint: dataset.fingerprint, datasetDocumentCount: Object.values(dataset.counts).reduce((sum, count) => sum + count, 0), collectionCounts: dataset.counts, networkEvidence, generatedAt: result.generatedAt, summaries: result.summaries, telemetry: result.events };
  assertSafeBenchmarkArtifact(artifact);
  const markdown = markdownReport(artifact);
  assertSafeBenchmarkArtifact(markdown);
  return {
    baseName: `pre-optimization-${dataset.reference}`,
    json: `${JSON.stringify(artifact, null, 2)}\n`,
    markdown,
    runId
  };
}

async function publishBaselines(baselines: PendingBaseline[]): Promise<void> {
  const runId = baselines[0]?.runId ?? randomUUID();
  await mkdir(resultsDirectory, { recursive: true });
  const temporaryFiles = baselines.flatMap(baseline => [
    { temporary: path.join(resultsDirectory, `.${baseline.baseName}-${runId}.json.tmp`), final: path.join(resultsDirectory, `${baseline.baseName}.json`), content: baseline.json },
    { temporary: path.join(resultsDirectory, `.${baseline.baseName}-${runId}.md.tmp`), final: path.join(resultsDirectory, `${baseline.baseName}.md`), content: baseline.markdown }
  ]);
  const backups = temporaryFiles.map(file => ({ ...file, backup: `${file.final}.${runId}.bak` }));
  const published: string[] = [];
  try {
    await Promise.all(temporaryFiles.map(file => writeFile(file.temporary, file.content, "utf8")));
    for (const file of backups) {
      if (existsSync(file.final)) await rename(file.final, file.backup);
    }
    for (const file of backups) {
      await rename(file.temporary, file.final);
      published.push(file.final);
    }
    // Final renames are the commit point; stale backups are harmless if cleanup is interrupted.
    await Promise.allSettled(backups.map(file => rm(file.backup, { force: true })));
  } catch (error) {
    await Promise.all([...temporaryFiles.map(file => file.temporary), ...published].map(file => rm(file, { force: true })));
    const restores = await Promise.allSettled(backups.map(async file => {
      if (existsSync(file.backup)) await rename(file.backup, file.final);
    }));
    await Promise.all(backups.map((file, index) => restores[index]?.status === "fulfilled" ? rm(file.backup, { force: true }) : Promise.resolve()));
    const restoreFailure = restores.find(result => result.status === "rejected");
    if (restoreFailure) throw new AggregateError([error, restoreFailure.reason], "Baseline publication rollback failed");
    throw error;
  }
}

async function main(): Promise<void> {
  loadEnvFile(path.join(webappRoot, ".env.local"));
  const profile = parsePerformanceTestProfile(JSON.parse(await readFile(profilePath, "utf8")));
  const revision = await applicationRevision(profile);
  const [anonymizedReal, networkEvidence] = await Promise.all([readAnonymizedSource(profile, requiredEnv("BENCHMARK_SOURCE_PROJECT_ID")), probeNetwork(profile)]);
  const datasets = [anonymizedReal, createLargeSynthetic(profile)];
  const emulator = await startEmulators(profile);
  let driveFolderId = "";
  let tokenCleanup: (() => Promise<void>) | null = null;
  const pendingBaselines: PendingBaseline[] = [];
  const baselineRunId = randomUUID();
  let failure: unknown = null;
  try {
    process.env.FIRESTORE_EMULATOR_HOST = profile.emulator.firestoreHost;
    process.env.FIREBASE_AUTH_EMULATOR_HOST = profile.emulator.authHost;
    process.env.FIREBASE_PROJECT_ID = profile.emulator.projectId;
    const defaultApp = initializeApp({ credential: cert(serviceAccount("FIREBASE")), projectId: profile.emulator.projectId });
    const db = getFirestore(defaultApp);
    const drive = driveClient();
    driveFolderId = await createTemporaryDriveFolder(drive);
    const auth = await createEmulatorToken(profile.emulator.projectId);
    tokenCleanup = auth.cleanup;
    for (const [index, dataset] of datasets.entries()) {
      const datasetProjectId = `benchmark-dataset-${index + 1}`;
      await seedDataset(db, datasetProjectId, dataset);
      await db.collection("projects").doc(datasetProjectId).set({ ...dataset.project, data: { ...((dataset.project.data as Record<string, unknown>) ?? {}), name: `Benchmark Dataset ${index + 1}`, mediaStorageFolderId: driveFolderId } });
      pendingBaselines.push(await buildBaseline(profile, dataset, networkEvidence, await adaptersFor(db, datasetProjectId, auth.token, driveFolderId), revision, baselineRunId));
    }
  } catch (error) {
    failure = error;
  } finally {
    const cleanupSteps: Array<() => Promise<void>> = [];
    if (tokenCleanup) cleanupSteps.push(tokenCleanup);
    if (driveFolderId) cleanupSteps.push(() => deleteCurrentDriveTree(driveClient(), driveFolderId));
    cleanupSteps.push(async () => {
      for (const app of getApps()) await deleteApp(app);
    });
    cleanupSteps.push(() => stopEmulators(emulator));
    cleanupSteps.push(() => waitForPortClosed(profile.emulator.firestoreHost));
    cleanupSteps.push(() => waitForPortClosed(profile.emulator.authHost));
    for (const cleanup of cleanupSteps) {
      try {
        await cleanup();
      } catch (error) {
        failure ??= error;
      }
    }
  }
  if (failure) throw failure;
  if (pendingBaselines.length !== datasets.length) throw new Error("Both datasets must complete before publication");
  await publishBaselines(pendingBaselines);
}

main().catch(error => { console.error(error instanceof Error ? error.message : String(error)); process.exitCode = 1; });
