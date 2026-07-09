const fs = require("node:fs");
const path = require("node:path");
const { google } = require("googleapis");
const { cert, getApps, initializeApp, applicationDefault } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");

const folderMimeType = "application/vnd.google-apps.folder";
const workspaceRoot = path.resolve(__dirname, "..", "..");
const webappRoot = path.resolve(__dirname, "..");

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return;
  for (const rawLine of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const separatorIndex = line.indexOf("=");
    if (separatorIndex === -1) continue;
    const key = line.slice(0, separatorIndex).trim();
    if (!key || process.env[key]) continue;
    let value = line.slice(separatorIndex + 1).trim();
    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    process.env[key] = value;
  }
}

function requiredEnv(name) {
  const value = process.env[name] && process.env[name].trim();
  if (!value) throw new Error(`${name} is not configured.`);
  return value;
}

function normalizeDriveFolderInput(value) {
  const trimmed = value.trim();
  if (!trimmed) throw new Error("Google Drive root folder is empty.");
  let folderId = trimmed;
  try {
    const parsed = new URL(trimmed);
    const folderMatch = parsed.pathname.match(/\/folders\/([^/?#]+)/);
    folderId = folderMatch && folderMatch[1] ? folderMatch[1] : parsed.searchParams.get("id") || trimmed;
  } catch {
    const folderMatch = trimmed.match(/\/folders\/([^/?#]+)/);
    folderId = folderMatch && folderMatch[1] ? folderMatch[1] : trimmed;
  }
  folderId = decodeURIComponent(folderId).trim();
  if (!/^[A-Za-z0-9_-]{10,}$/.test(folderId)) {
    throw new Error("Google Drive folder URL/ID is not valid.");
  }
  return folderId;
}

function configuredRootFolderId() {
  const folderId = process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID && process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID.trim();
  if (folderId) return folderId;
  const folderUrl = process.env.GOOGLE_DRIVE_ROOT_FOLDER_URL && process.env.GOOGLE_DRIVE_ROOT_FOLDER_URL.trim();
  if (folderUrl) return normalizeDriveFolderInput(folderUrl);
  throw new Error("GOOGLE_DRIVE_ROOT_FOLDER_ID or GOOGLE_DRIVE_ROOT_FOLDER_URL is not configured.");
}

function serviceAccountFromEnv(prefix) {
  const fileKey = `${prefix}_SERVICE_ACCOUNT_FILE`;
  const jsonKey = `${prefix}_SERVICE_ACCOUNT_JSON`;
  const filePath = process.env[fileKey] && process.env[fileKey].trim();
  if (filePath) {
    const resolved = path.isAbsolute(filePath) ? filePath : path.resolve(workspaceRoot, filePath);
    const parsed = JSON.parse(fs.readFileSync(resolved, "utf8"));
    if (typeof parsed.private_key === "string") {
      parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
    }
    return parsed;
  }
  const raw = process.env[jsonKey];
  if (!raw) return null;
  const parsed = JSON.parse(raw);
  if (typeof parsed.private_key === "string") {
    parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
  }
  return parsed;
}

function adminApp() {
  if (getApps().length) return getApps()[0];
  const firebaseCredentials = serviceAccountFromEnv("FIREBASE") || serviceAccountFromEnv("GOOGLE");
  return initializeApp({
    credential: firebaseCredentials ? cert(firebaseCredentials) : applicationDefault(),
    projectId: process.env.FIREBASE_PROJECT_ID || process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID
  });
}

function driveClient() {
  const credentials = serviceAccountFromEnv("GOOGLE");
  if (!credentials) throw new Error("GOOGLE_SERVICE_ACCOUNT_FILE or GOOGLE_SERVICE_ACCOUNT_JSON is not configured.");
  const auth = new google.auth.GoogleAuth({
    credentials,
    scopes: ["https://www.googleapis.com/auth/drive"]
  });
  return google.drive({ version: "v3", auth });
}

function unpackData(source) {
  if (!source || typeof source !== "object") return {};
  if (source.data && typeof source.data === "object") return source.data;
  return source;
}

function escapeDriveQuery(value) {
  return String(value).replace(/\\/g, "\\\\").replace(/'/g, "\\'");
}

function sanitizeSegment(value, fallback = "unknown") {
  const sanitized = String(value || "")
    .trim()
    .replace(/[\\/:*?"<>|]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120);
  return sanitized || fallback;
}

function sanitizeOptionalSegment(value) {
  return value ? sanitizeSegment(value, "") : "";
}

function extensionForNameOrMime(fileName, mimeType) {
  const ext = path.extname(String(fileName || "")).replace(/^\./, "").trim().toLowerCase();
  if (ext) return ext;
  const normalized = String(mimeType || "").toLowerCase();
  if (normalized.includes("jpeg") || normalized.includes("jpg")) return "jpg";
  if (normalized.includes("png")) return "png";
  if (normalized.includes("webp")) return "webp";
  if (normalized.includes("mp4")) return "mp4";
  if (normalized.includes("quicktime")) return "mov";
  return "bin";
}

function formatDriveTimestamp(epochMs) {
  const date = new Date(Number(epochMs || Date.now()));
  const parts = new Intl.DateTimeFormat("sv-SE", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    timeZone: "Asia/Bangkok"
  }).formatToParts(date);
  const map = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${map.year}-${map.month}-${map.day} ${map.hour}.${map.minute}.${map.second}`;
}

function buildDriveMediaFileName(input) {
  const segments = [
    formatDriveTimestamp(input.capturedAtEpochMs),
    sanitizeOptionalSegment(input.address),
    sanitizeOptionalSegment(input.captureNote)
  ].filter(Boolean);
  return `${segments.join(" - ")}.${String(input.extension).replace(/^\./, "")}`;
}

async function listSingleFile(drive, query) {
  const response = await drive.files.list({
    q: query,
    fields: "files(id,name,parents,mimeType,appProperties)",
    pageSize: 10,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });
  return response.data.files || [];
}

async function findChildFolder(drive, parentId, name) {
  const files = await listSingleFile(
    drive,
    [
      `'${escapeDriveQuery(parentId)}' in parents`,
      `name = '${escapeDriveQuery(name)}'`,
      `mimeType = '${folderMimeType}'`,
      "trashed = false"
    ].join(" and ")
  );
  return files[0] ? files[0].id : null;
}

async function ensureChildFolder(drive, parentId, name) {
  const existing = await findChildFolder(drive, parentId, name);
  if (existing) return existing;
  const created = await drive.files.create({
    requestBody: {
      name,
      mimeType: folderMimeType,
      parents: [parentId]
    },
    fields: "id",
    supportsAllDrives: true
  });
  if (!created.data.id) throw new Error(`Failed to create folder ${name}.`);
  return created.data.id;
}

async function ensureFolderPath(drive, rootFolderId, segments) {
  let currentFolderId = rootFolderId;
  for (const segment of segments) {
    currentFolderId = await ensureChildFolder(drive, currentFolderId, segment);
  }
  return currentFolderId;
}

async function resolveFolderInfo(drive, folderId) {
  const response = await drive.files.get({
    fileId: folderId,
    fields: "id,name,parents,mimeType",
    supportsAllDrives: true
  });
  return response.data;
}

async function resolveRelativeSegments(drive, parentId, rootFolderId, projectId, projectName) {
  if (!parentId) return null;
  const segments = [];
  let currentId = parentId;
  while (currentId) {
    const folder = await resolveFolderInfo(drive, currentId);
    if (!folder || folder.mimeType !== folderMimeType) return null;
    const folderName = String(folder.name || "");
    if (currentId === rootFolderId) return null;
    if (folderName === projectName || folderName === projectId) {
      return segments.reverse();
    }
    segments.push(folderName);
    currentId = folder.parents && folder.parents[0] ? folder.parents[0] : null;
  }
  return null;
}

function defaultSegmentsForPhoto(photo) {
  const objectType = String(photo.objectType || "NODE").toUpperCase() === "ROUTE" ? "Routes" : "Nodes";
  const objectCode = sanitizeSegment(photo.objectCode || "unknown");
  const mediaType = String(photo.mediaType || "IMAGE").toUpperCase();
  if (mediaType === "VIDEO") {
    return ["media", "videos", objectType, objectCode];
  }
  return ["photos", objectType, objectCode];
}

async function ensureUniqueName(drive, parentId, desiredName, currentFileId) {
  const ext = path.extname(desiredName);
  const base = ext ? desiredName.slice(0, -ext.length) : desiredName;
  let attempt = 1;
  let candidate = desiredName;
  while (true) {
    const matches = await listSingleFile(
      drive,
      [
        `'${escapeDriveQuery(parentId)}' in parents`,
        `name = '${escapeDriveQuery(candidate)}'`,
        `mimeType != '${folderMimeType}'`,
        "trashed = false"
      ].join(" and ")
    );
    const conflict = matches.find((file) => file.id !== currentFileId);
    if (!conflict) return candidate;
    attempt += 1;
    candidate = `${base} (${attempt})${ext}`;
  }
}

function driveFileIdFromUrl(value) {
  const trimmed = String(value || "").trim();
  if (!trimmed) return "";
  try {
    const parsed = new URL(trimmed);
    return parsed.searchParams.get("id") || (parsed.pathname.match(/\/d\/([^/]+)/) || [])[1] || "";
  } catch {
    return "";
  }
}

async function migratePhoto(drive, rootFolderId, projectId, projectName, photoId, photo) {
  const remoteUrl = String(photo.remoteUrl || "").trim();
  const fileId = driveFileIdFromUrl(remoteUrl);
  if (!fileId) {
    return { status: "skipped", reason: "missing_file_id" };
  }

  let file;
  try {
    const response = await drive.files.get({
      fileId,
      fields: "id,name,parents,mimeType,appProperties",
      supportsAllDrives: true
    });
    file = response.data;
  } catch {
    return { status: "skipped", reason: "file_not_found", fileId };
  }

  const extension = extensionForNameOrMime(file.name, file.mimeType);
  const desiredName = buildDriveMediaFileName({
    capturedAtEpochMs: Number(photo.capturedAtEpochMs || photo.updatedAtEpochMs || Date.now()),
    address: photo.address || "",
    captureNote: photo.captureNote || "",
    extension
  });
  const currentParentId = file.parents && file.parents[0] ? file.parents[0] : "";
  const relativeSegments =
    await resolveRelativeSegments(drive, currentParentId, rootFolderId, projectId, sanitizeSegment(projectName)) ||
    defaultSegmentsForPhoto(photo);
  const targetParentId = await ensureFolderPath(drive, rootFolderId, [sanitizeSegment(projectName), ...relativeSegments]);
  const targetName = await ensureUniqueName(drive, targetParentId, desiredName, fileId);

  const requestBody = {};
  if (file.name !== targetName) {
    requestBody.name = targetName;
  }
  const currentPhotoProperty = file.appProperties && file.appProperties.mapsupervisionPhotoId;
  if (currentPhotoProperty !== photoId) {
    requestBody.appProperties = {
      ...(file.appProperties || {}),
      mapsupervisionPhotoId: photoId
    };
  }

  const updateRequest = {
    fileId,
    requestBody,
    fields: "id,name,parents,appProperties",
    supportsAllDrives: true
  };

  let changed = false;
  if (Object.keys(requestBody).length > 0) {
    await drive.files.update(updateRequest);
    changed = true;
  }

  const needsMove = currentParentId !== targetParentId;
  if (needsMove) {
    await drive.files.update({
      fileId,
      addParents: targetParentId,
      removeParents: currentParentId || undefined,
      fields: "id,parents",
      supportsAllDrives: true
    });
    changed = true;
  }

  return {
    status: changed ? "updated" : "unchanged",
    fileId,
    targetName,
    targetParentId
  };
}

async function main() {
  loadEnvFile(path.join(workspaceRoot, ".env"));
  loadEnvFile(path.join(webappRoot, ".env.local"));

  const rootFolderId = configuredRootFolderId();
  const firestore = getFirestore(adminApp());
  const drive = driveClient();
  const projectsSnapshot = await firestore.collection("projects").get();

  const summary = {
    projects: 0,
    photos: 0,
    updated: 0,
    unchanged: 0,
    skipped: 0
  };

  for (const projectDoc of projectsSnapshot.docs) {
    const project = unpackData(projectDoc.data());
    const projectId = projectDoc.id;
    const projectName = sanitizeSegment(project.name || projectId);
    summary.projects += 1;

    const photosSnapshot = await firestore.collection("projects").doc(projectId).collection("site_photos").get();
    for (const photoDoc of photosSnapshot.docs) {
      const photo = unpackData(photoDoc.data());
      if (photo.isDeleted === true) continue;
      summary.photos += 1;
      const result = await migratePhoto(drive, rootFolderId, projectId, projectName, photoDoc.id, photo);
      if (result.status === "updated") summary.updated += 1;
      else if (result.status === "unchanged") summary.unchanged += 1;
      else summary.skipped += 1;
      console.log(`[${projectName}] ${photoDoc.id}: ${result.status}${result.reason ? ` (${result.reason})` : ""}`);
    }
  }

  console.log("");
  console.log("Drive media migration complete.");
  console.log(JSON.stringify(summary, null, 2));
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack || error.message : error);
  process.exitCode = 1;
});
