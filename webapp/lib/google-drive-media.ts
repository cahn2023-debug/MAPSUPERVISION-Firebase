import { Readable } from "node:stream";
import fs from "node:fs";
import { google, type drive_v3 } from "googleapis";
import { sanitizePrivateKey, sanitizeServiceAccount } from "./firebase-admin";

import { driveFileIdFromUrl } from "./google-drive-image";
export { driveFileIdFromUrl } from "./google-drive-image";

export type DriveMediaObjectType = "NODE" | "ROUTE";
export type DriveMediaType = "IMAGE" | "VIDEO";

export type DriveMediaUpload = {
  projectId: string;
  projectName: string;
  rootFolderId?: string;
  photoId: string;
  objectType: DriveMediaObjectType;
  objectCode: string;
  statusTag?: string;
  mediaType: DriveMediaType;
  mimeType: string;
  capturedAtEpochMs: number;
  address?: string;
  captureNote?: string;
  original: {
    bytes: Buffer;
    extension: string;
  };
  thumbnail?: {
    bytes: Buffer;
    extension: string;
    mimeType: string;
  };
};

export type DriveMediaUploadResult = {
  remoteUrl: string;
  thumbnailUrl?: string;
  driveFileId: string;
  drivePath: string;
};

const folderMimeType = "application/vnd.google-apps.folder";

let cachedDrive: drive_v3.Drive | null = null;

function stripWrappingQuotes(value: string): string {
  const trimmed = value.trim();
  if (
    trimmed.length >= 2 &&
    ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
      (trimmed.startsWith("'") && trimmed.endsWith("'")))
  ) {
    return trimmed.slice(1, -1).trim();
  }
  return trimmed;
}

function requiredEnv(name: string): string {
  const value = stripWrappingQuotes(process.env[name] || "");
  if (!value) {
    throw new Error(`${name} is not configured.`);
  }
  return value;
}

function normalizeDriveFolderInput(value: string): string {
  const trimmed = stripWrappingQuotes(value);
  if (!trimmed) {
    return "";
  }

  let folderId = trimmed;
  try {
    const parsed = new URL(trimmed);
    const folderMatch = parsed.pathname.match(/\/folders\/([^/?#]+)/);
    folderId = folderMatch?.[1] ?? parsed.searchParams.get("id") ?? trimmed;
  } catch {
    const folderMatch = trimmed.match(/\/folders\/([^/?#]+)/);
    folderId = folderMatch?.[1] ?? trimmed;
  }

  folderId = decodeURIComponent(folderId).trim();
  if (!/^[A-Za-z0-9_-]{10,}$/.test(folderId)) {
    throw new Error("Google Drive folder URL/ID is not valid.");
  }
  return folderId;
}

export function configuredRootFolderId(): string {
  const folderId = stripWrappingQuotes(process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID || "");
  if (folderId) {
    return folderId;
  }

  const folderUrl = stripWrappingQuotes(process.env.GOOGLE_DRIVE_ROOT_FOLDER_URL || "");
  if (folderUrl) {
    return normalizeDriveFolderInput(folderUrl);
  }

  throw new Error("GOOGLE_DRIVE_ROOT_FOLDER_ID or GOOGLE_DRIVE_ROOT_FOLDER_URL is not configured.");
}

function serviceAccountCredentials() {
  const filePath = stripWrappingQuotes(process.env.GOOGLE_SERVICE_ACCOUNT_FILE || process.env.FIREBASE_SERVICE_ACCOUNT_FILE || "");
  if (filePath && fs.existsSync(filePath)) {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    return sanitizeServiceAccount(parsed);
  }

  const rawJson = process.env.GOOGLE_SERVICE_ACCOUNT_JSON ||
    process.env.FIREBASE_SERVICE_ACCOUNT_KEY ||
    process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

  if (rawJson) {
    let clean = stripWrappingQuotes(rawJson);
    let parsed: any;
    try {
      parsed = JSON.parse(clean);
    } catch {
      try {
        const decoded = Buffer.from(clean, "base64").toString("utf8");
        parsed = JSON.parse(decoded);
      } catch {
        // fallback
      }
    }
    if (parsed) {
      return sanitizeServiceAccount(parsed);
    }
  }

  const clientEmail = stripWrappingQuotes(process.env.FIREBASE_ADMIN_CLIENT_EMAIL || process.env.GOOGLE_CLIENT_EMAIL || "");
  const privateKey = stripWrappingQuotes(process.env.FIREBASE_ADMIN_PRIVATE_KEY || process.env.GOOGLE_PRIVATE_KEY || "");
  const projectId = stripWrappingQuotes(process.env.FIREBASE_ADMIN_PROJECT_ID || process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "");

  if (clientEmail && privateKey) {
    return {
      client_email: clientEmail,
      private_key: sanitizePrivateKey(privateKey),
      project_id: projectId
    };
  }

  throw new Error("GOOGLE_SERVICE_ACCOUNT_JSON is not configured.");
}

// ponytail: mock drive client for testing
let driveMock: any = null;
export function setDriveClientMock(mock: any) {
  driveMock = mock;
}

export function driveClient(): drive_v3.Drive {
  if (process.env.NODE_ENV === "test" && driveMock) return driveMock;
  if (cachedDrive) return cachedDrive;
  const auth = new google.auth.GoogleAuth({
    credentials: serviceAccountCredentials(),
    scopes: ["https://www.googleapis.com/auth/drive"]
  });
  cachedDrive = google.drive({ version: "v3", auth });
  return cachedDrive;
}

export function escapeDriveQuery(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
}

export function sanitizeSegment(value: string, fallback = "unknown"): string {
  return value
    .trim()
    .replace(/[\\/:*?"<>|]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120) || fallback;
}

function sanitizeOptionalSegment(value: string | undefined): string {
  return value ? sanitizeSegment(value, "") : "";
}

function extensionForMime(mimeType: string, fallback: string): string {
  const normalized = mimeType.toLowerCase();
  if (normalized.includes("jpeg") || normalized.includes("jpg")) return "jpg";
  if (normalized.includes("png")) return "png";
  if (normalized.includes("webp")) return "webp";
  if (normalized.includes("mp4")) return "mp4";
  if (normalized.includes("quicktime")) return "mov";
  return fallback;
}

async function findChildFolder(drive: drive_v3.Drive, parentId: string, name: string): Promise<string | null> {
  const response = await drive.files.list({
    q: [
      `'${escapeDriveQuery(parentId)}' in parents`,
      `name = '${escapeDriveQuery(name)}'`,
      `mimeType = '${folderMimeType}'`,
      "trashed = false"
    ].join(" and "),
    fields: "files(id,name)",
    pageSize: 1,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });
  return response.data.files?.[0]?.id ?? null;
}

async function ensureChildFolder(drive: drive_v3.Drive, parentId: string, name: string): Promise<string> {
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
  const id = created.data.id;
  if (!id) {
    throw new Error(`Failed to create Drive folder ${name}.`);
  }
  return id;
}

export async function ensureFolderPath(drive: drive_v3.Drive, rootFolderId: string, segments: string[]): Promise<string> {
  let currentFolderId = rootFolderId;
  for (const segment of segments) {
    currentFolderId = await ensureChildFolder(drive, currentFolderId, segment);
  }
  return currentFolderId;
}

export async function ensureProjectFolder(
  drive: drive_v3.Drive,
  rootFolderId: string,
  projectId: string,
  projectName: string
): Promise<string> {
  const query = [
    `'${escapeDriveQuery(rootFolderId)}' in parents`,
    `appProperties has { key='mapsupervisionProjectId' and value='${escapeDriveQuery(projectId)}' }`,
    `mimeType = '${folderMimeType}'`,
    "trashed = false"
  ].join(" and ");

  const listResponse = await drive.files.list({
    q: query,
    fields: "files(id,name)",
    pageSize: 1,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });

  const existingFolderId = listResponse.data.files?.[0]?.id;
  if (existingFolderId) {
    return existingFolderId;
  }

  const folderName = sanitizeSegment(projectName);
  const existingByNameId = await findChildFolder(drive, rootFolderId, folderName);
  if (existingByNameId) {
    await drive.files.update({
      fileId: existingByNameId,
      requestBody: {
        appProperties: {
          mapsupervisionProjectId: projectId
        }
      },
      fields: "id",
      supportsAllDrives: true
    });
    return existingByNameId;
  }

  const created = await drive.files.create({
    requestBody: {
      name: folderName,
      mimeType: folderMimeType,
      parents: [rootFolderId],
      appProperties: {
        mapsupervisionProjectId: projectId
      }
    },
    fields: "id",
    supportsAllDrives: true
  });

  const id = created.data.id;
  if (!id) {
    throw new Error(`Failed to create Project folder ${folderName}.`);
  }
  return id;
}

export async function findChildFile(drive: drive_v3.Drive, parentId: string, name: string): Promise<string | null> {
  const response = await drive.files.list({
    q: [
      `'${escapeDriveQuery(parentId)}' in parents`,
      `name = '${escapeDriveQuery(name)}'`,
      `mimeType != '${folderMimeType}'`,
      "trashed = false"
    ].join(" and "),
    fields: "files(id,name)",
    pageSize: 1,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });
  return response.data.files?.[0]?.id ?? null;
}

async function findFileByPhotoId(
  drive: drive_v3.Drive,
  photoId: string
): Promise<{ id: string; parents: string[] } | null> {
  const response = await drive.files.list({
    q: [
      `appProperties has { key='mapsupervisionPhotoId' and value='${escapeDriveQuery(photoId)}' }`,
      `mimeType != '${folderMimeType}'`,
      "trashed = false"
    ].join(" and "),
    fields: "files(id,parents)",
    pageSize: 1,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });
  const file = response.data.files?.[0];
  return file?.id ? { id: file.id, parents: file.parents ?? [] } : null;
}

async function findChildFileByPhotoId(
  drive: drive_v3.Drive,
  parentId: string,
  photoId: string
): Promise<{ id: string; parents: string[] } | null> {
  const response = await drive.files.list({
    q: [
      `'${escapeDriveQuery(parentId)}' in parents`,
      `appProperties has { key='mapsupervisionPhotoId' and value='${escapeDriveQuery(photoId)}' }`,
      `mimeType != '${folderMimeType}'`,
      "trashed = false"
    ].join(" and "),
    fields: "files(id,parents)",
    pageSize: 1,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });
  const file = response.data.files?.[0];
  return file?.id ? { id: file.id, parents: file.parents ?? [] } : null;
}

async function ensurePublicReader(drive: drive_v3.Drive, fileId: string): Promise<void> {
  const existing = await drive.permissions.list({
    fileId,
    fields: "permissions(id,type,role)",
    supportsAllDrives: true
  });
  const alreadyPublic = existing.data.permissions?.some((permission) =>
    permission.type === "anyone" && (permission.role === "reader" || permission.role === "writer")
  );
  if (alreadyPublic) {
    return;
  }
  await drive.permissions.create({
    fileId,
    requestBody: {
      type: "anyone",
      role: "reader"
    },
    fields: "id",
    supportsAllDrives: true
  });
}

async function upsertFile(
  drive: drive_v3.Drive,
  parentId: string,
  photoId: string,
  name: string,
  mimeType: string,
  bytes: Buffer,
  allowCrossFolderMove = false
): Promise<string> {
  const media = {
    mimeType,
    body: Readable.from(bytes)
  };
  const existing = await findChildFileByPhotoId(drive, parentId, photoId)
    ?? (allowCrossFolderMove ? await findFileByPhotoId(drive, photoId) : null);
  if (existing) {
    await drive.files.update({
      fileId: existing.id,
      addParents: parentId,
      removeParents: existing.parents.filter((parent) => parent !== parentId).join(",") || undefined,
      requestBody: {
        name,
        appProperties: {
          mapsupervisionPhotoId: photoId
        }
      },
      media,
      fields: "id",
      supportsAllDrives: true
    });
    await ensurePublicReader(drive, existing.id);
    return existing.id;
  }

  let resolvedName = name;
  let counter = 2;
  while (await findChildFile(drive, parentId, resolvedName)) {
    const parts = resolvedName.match(/^(.*?)(\.[^.]+)?$/);
    const base = parts?.[1] || resolvedName;
    const ext = parts?.[2] || "";
    resolvedName = `${base} (${counter})${ext}`;
    counter++;
  }

  const created = await drive.files.create({
    requestBody: {
      name: resolvedName,
      parents: [parentId],
      appProperties: {
        mapsupervisionPhotoId: photoId
      }
    },
    media,
    fields: "id",
    supportsAllDrives: true
  });
  const id = created.data.id;
  if (!id) {
    throw new Error(`Failed to create Drive file ${name}.`);
  }
  await ensurePublicReader(drive, id);
  return id;
}

function formatDriveTimestamp(epochMs: number): string {
  const date = new Date(epochMs);
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

export function buildDriveMediaFileName(input: {
  capturedAtEpochMs: number;
  address?: string;
  captureNote?: string;
  extension: string;
}): string {
  const segments = [
    formatDriveTimestamp(input.capturedAtEpochMs),
    sanitizeOptionalSegment(input.address),
    sanitizeOptionalSegment(input.captureNote)
  ].filter(Boolean);
  return `${segments.join(" - ")}.${input.extension.replace(/^\./, "")}`;
}

export function buildProjectMediaPreviewUrl(projectId: string, photoId: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}/media?photoId=${encodeURIComponent(photoId)}`;
}

function publicDriveUrl(fileId: string): string {
  return `https://lh3.googleusercontent.com/d/${encodeURIComponent(fileId)}=w1000?authuser=0`;
}

export async function downloadDriveFile(fileId: string): Promise<{ stream: Readable; contentType: string }> {
  const response = await driveClient().files.get(
    {
      fileId,
      alt: "media",
      supportsAllDrives: true
    },
    { responseType: "stream" }
  );
  return {
    stream: response.data as Readable,
    contentType: response.headers["content-type"]?.split(";")[0] || "application/octet-stream"
  };
}

export async function deleteDriveFile(fileId: string): Promise<void> {
  try {
    await driveClient().files.delete({ fileId, supportsAllDrives: true });
  } catch (error: any) {
    if (Number(error?.response?.status ?? error?.code) === 404) return;
    throw error;
  }
}

// ponytail: test mock hooks
let uploadMock: any = null;

export function setUploadProjectMediaMock(mock: any) {
  uploadMock = mock;
}

export async function uploadProjectMedia(input: DriveMediaUpload): Promise<DriveMediaUploadResult> {
  if (process.env.NODE_ENV === "test" && uploadMock) {
    return uploadMock(input);
  }
  const drive = driveClient();
  const projectFolder = sanitizeSegment(input.projectName);
  const objectFolder = sanitizeSegment(input.objectCode);
  const rootFolderId = input.rootFolderId?.trim() || configuredRootFolderId();
  const projectFolderId = await ensureProjectFolder(drive, rootFolderId, input.projectId, input.projectName);
  const folderSegments = input.mediaType === "VIDEO"
    ? ["media", "videos", input.objectType === "ROUTE" ? "Routes" : "Nodes", objectFolder]
    : ["photos", input.objectType === "ROUTE" ? "Routes" : "Nodes", objectFolder];
  const taggedSegments = input.statusTag?.trim()
    ? [...folderSegments, sanitizeSegment(input.statusTag)]
    : folderSegments;
  const parentId = await ensureFolderPath(drive, projectFolderId, taggedSegments);
  const originalExtension = input.original.extension || extensionForMime(input.mimeType, input.mediaType === "VIDEO" ? "mp4" : "jpg");
  const originalName = buildDriveMediaFileName({
    capturedAtEpochMs: input.capturedAtEpochMs,
    address: input.address,
    captureNote: input.captureNote,
    extension: originalExtension
  });
  const allowCrossFolderMove = Boolean(input.statusTag?.trim());
  const originalId = await upsertFile(
    drive,
    parentId,
    input.photoId,
    originalName,
    input.mimeType,
    input.original.bytes,
    allowCrossFolderMove
  );

  let thumbnailUrl: string | undefined;
  if (input.thumbnail) {
    const thumbnailExtension = input.thumbnail.extension || extensionForMime(input.thumbnail.mimeType, "jpg");
    const thumbnailId = await upsertFile(
      drive,
      parentId,
      `${input.photoId}__thumb`,
      buildDriveMediaFileName({
        capturedAtEpochMs: input.capturedAtEpochMs,
        address: input.address,
        captureNote: [input.captureNote, "thumbnail"].filter(Boolean).join(" - "),
        extension: thumbnailExtension
      }),
      input.thumbnail.mimeType,
      input.thumbnail.bytes,
      allowCrossFolderMove
    );
    thumbnailUrl = publicDriveUrl(thumbnailId);
  }

  return {
    remoteUrl: publicDriveUrl(originalId),
    thumbnailUrl,
    driveFileId: originalId,
    drivePath: [projectFolder, ...taggedSegments, originalName].join("/")
  };
}

export const SNAPSHOTS_FOLDER_NAME = "Snapshots";
export const SNAPSHOT_RETENTION_MAX_AGE_MS = 5 * 60 * 1000; // 5 minutes buffer

export async function findSnapshotsFolder(
  drive: drive_v3.Drive,
  projectFolderId: string
): Promise<string | null> {
  return findChildFolder(drive, projectFolderId, SNAPSHOTS_FOLDER_NAME);
}

export async function ensureSnapshotsFolder(
  drive: drive_v3.Drive,
  projectFolderId: string
): Promise<string> {
  return ensureChildFolder(drive, projectFolderId, SNAPSHOTS_FOLDER_NAME);
}

export async function findProjectFolderIdByNameOrId(
  drive: drive_v3.Drive,
  rootFolderId: string,
  projectId: string,
  projectName: string
): Promise<string | null> {
  // 1. By appProperties
  try {
    const listRes = await drive.files.list({
      q: [
        `'${escapeDriveQuery(rootFolderId)}' in parents`,
        `appProperties has { key='mapsupervisionProjectId' and value='${escapeDriveQuery(projectId)}' }`,
        `mimeType = '${folderMimeType}'`,
        "trashed = false"
      ].join(" and "),
      fields: "files(id,name)",
      pageSize: 1,
      supportsAllDrives: true,
      includeItemsFromAllDrives: true
    });
    if (listRes.data.files?.[0]?.id) return listRes.data.files[0].id;
  } catch (err) {
    console.warn("[findProjectFolderIdByNameOrId] appProperties query error:", err);
  }

  // 2. By exact sanitized folder name
  const folderName = sanitizeSegment(projectName);
  const byName = await findChildFolder(drive, rootFolderId, folderName);
  if (byName) return byName;

  // 3. Fallback: Scan root folder child folders for keyword matching
  try {
    const scanRes = await drive.files.list({
      q: [
        `'${escapeDriveQuery(rootFolderId)}' in parents`,
        `mimeType = '${folderMimeType}'`,
        "trashed = false"
      ].join(" and "),
      fields: "files(id,name)",
      pageSize: 50,
      supportsAllDrives: true,
      includeItemsFromAllDrives: true
    });
    const matched = scanRes.data.files?.find(f => {
      const n = (f.name || "").toLowerCase();
      return n.includes("269") && n.includes("2026");
    });
    if (matched?.id) return matched.id;
  } catch (scanErr) {
    console.warn("[findProjectFolderIdByNameOrId] scan error:", scanErr);
  }

  return null;
}

async function parseDriveFileStreamOrString(data: any): Promise<any> {
  if (typeof data === "string") {
    return JSON.parse(data);
  }
  if (Buffer.isBuffer(data)) {
    return JSON.parse(data.toString("utf8"));
  }
  if (data && typeof data === "object") {
    if (typeof (data as any)[Symbol.asyncIterator] === "function" || typeof (data as any).on === "function") {
      const chunks: Buffer[] = [];
      for await (const chunk of (data as any)) {
        chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
      }
      const text = Buffer.concat(chunks).toString("utf8");
      return JSON.parse(text);
    }
    return data;
  }
  return data;
}

export async function getLatestDriveSnapshot(
  drive: drive_v3.Drive,
  projectFolderId: string
): Promise<{ payload: any; fileId: string; fileName: string; createdTime?: string } | null> {
  const snapshotsFolderId = await findSnapshotsFolder(drive, projectFolderId);
  if (!snapshotsFolderId) return null;

  const listResponse = await drive.files.list({
    q: [
      `'${escapeDriveQuery(snapshotsFolderId)}' in parents`,
      `mimeType != '${folderMimeType}'`,
      "trashed = false"
    ].join(" and "),
    fields: "files(id,name,createdTime,modifiedTime)",
    orderBy: "createdTime desc",
    pageSize: 10,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });

  const files = listResponse.data.files || [];
  if (files.length === 0) return null;

  const latestFile = files[0];
  if (!latestFile.id) return null;

  const getRes = await drive.files.get(
    {
      fileId: latestFile.id,
      alt: "media",
      supportsAllDrives: true
    },
    { responseType: "text" }
  );

  const rawData = await parseDriveFileStreamOrString(getRes.data);
  return {
    payload: rawData,
    fileId: latestFile.id,
    fileName: latestFile.name || "",
    createdTime: latestFile.createdTime || undefined
  };
}

export async function getLatestDriveSnapshotByProjectIdOrFolder(
  drive: drive_v3.Drive,
  projectId: string,
  projectFolderId?: string | null
): Promise<{ payload: any; fileId: string; fileName: string; createdTime?: string } | null> {
  // Strategy 1: Direct search by file name across drives
  try {
    const directRes = await drive.files.list({
      q: [
        `name contains 'snapshot_${escapeDriveQuery(projectId)}'`,
        `mimeType != '${folderMimeType}'`,
        "trashed = false"
      ].join(" and "),
      fields: "files(id,name,createdTime,modifiedTime,parents)",
      orderBy: "createdTime desc",
      pageSize: 5,
      supportsAllDrives: true,
      includeItemsFromAllDrives: true,
      corpora: "allDrives"
    });
    const files = directRes.data.files || [];
    const target = files.find(f => typeof f.id === "string" && f.id.length > 0);
    if (target && target.id) {
      const getRes = await drive.files.get(
        {
          fileId: target.id,
          alt: "media",
          supportsAllDrives: true
        },
        { responseType: "text" }
      );
      const rawData = await parseDriveFileStreamOrString(getRes.data);
      return {
        payload: rawData,
        fileId: target.id,
        fileName: target.name || "",
        createdTime: target.createdTime || undefined
      };
    }
  } catch (directErr) {
    console.warn("[getLatestDriveSnapshotByProjectIdOrFolder] direct query warning:", directErr);
  }

  // Strategy 2: Search within project folder
  if (projectFolderId) {
    return getLatestDriveSnapshot(drive, projectFolderId);
  }

  return null;
}

export async function pruneOldDriveSnapshots(
  drive: drive_v3.Drive,
  snapshotsFolderId: string,
  maxAgeMs: number = SNAPSHOT_RETENTION_MAX_AGE_MS
): Promise<{ deletedFileIds: string[] }> {
  const deletedFileIds: string[] = [];
  try {
    const listResponse = await drive.files.list({
      q: [
        `'${escapeDriveQuery(snapshotsFolderId)}' in parents`,
        `mimeType != '${folderMimeType}'`,
        "trashed = false"
      ].join(" and "),
      fields: "files(id,name,createdTime)",
      orderBy: "createdTime desc",
      pageSize: 50,
      supportsAllDrives: true,
      includeItemsFromAllDrives: true
    });

    const files = listResponse.data.files || [];
    if (files.length <= 1) {
      return { deletedFileIds };
    }

    const now = Date.now();
    // Keep files[0] (the newest snapshot). Check others against retention window.
    for (let i = 1; i < files.length; i++) {
      const file = files[i];
      if (!file.id) continue;
      const fileTime = file.createdTime ? new Date(file.createdTime).getTime() : 0;
      if (now - fileTime >= maxAgeMs) {
        try {
          await drive.files.delete({
            fileId: file.id,
            supportsAllDrives: true
          });
          deletedFileIds.push(file.id);
        } catch (delErr) {
          console.warn(`[pruneOldDriveSnapshots] Failed to delete file ${file.id}:`, delErr);
        }
      }
    }
  } catch (err) {
    console.warn("[pruneOldDriveSnapshots] Error pruning snapshots:", err);
  }
  return { deletedFileIds };
}

export async function uploadDriveSnapshot(
  drive: drive_v3.Drive,
  rootFolderId: string,
  projectId: string,
  projectName: string,
  snapshotPayload: Record<string, unknown>
): Promise<{ fileId: string; fileName: string }> {
  const projectFolderId = await ensureProjectFolder(drive, rootFolderId, projectId, projectName);
  const snapshotsFolderId = await ensureSnapshotsFolder(drive, projectFolderId);
  const epochMs = (snapshotPayload.updatedAtEpochMs as number) || Date.now();
  const fileName = `snapshot_${projectId}_${epochMs}.json`;
  const content = JSON.stringify(snapshotPayload);

  const created = await drive.files.create({
    requestBody: {
      name: fileName,
      mimeType: "application/json",
      parents: [snapshotsFolderId]
    },
    media: {
      mimeType: "application/json",
      body: Readable.from([content])
    },
    fields: "id,name",
    supportsAllDrives: true
  });

  const fileId = created.data.id;
  if (!fileId) throw new Error("Failed to upload snapshot file to Google Drive.");

  // Run cleanup in background
  void pruneOldDriveSnapshots(drive, snapshotsFolderId, SNAPSHOT_RETENTION_MAX_AGE_MS);

  return { fileId, fileName };
}

export type DiscoveredDrivePhoto = {
  id: string;
  driveFileId: string;
  name: string;
  projectId: string;
  objectType: DriveMediaObjectType;
  objectCode: string;
  statusTag?: string;
  capturedAtEpochMs: number;
  address?: string;
  captureNote?: string;
  mediaType: DriveMediaType;
  mimeType: string;
  remoteUrl: string;
  drivePath: string;
};

export type DriveScanResult = {
  projectId: string;
  totalDriveFiles: number;
  matchedCount: number;
  discoveredPhotos: DiscoveredDrivePhoto[];
};

export function parseMediaFileName(
  fileName: string,
  createdTimeStr?: string
): {
  capturedAtEpochMs: number;
  address?: string;
  captureNote?: string;
  extension: string;
} {
  const cleanName = fileName.trim();
  const lastDot = cleanName.lastIndexOf(".");
  const ext = lastDot > 0 ? cleanName.slice(lastDot + 1).toLowerCase() : "jpg";
  const baseName = lastDot > 0 ? cleanName.slice(0, lastDot).trim() : cleanName;

  // Match timestamps formatted as "yyyy-MM-dd HH.mm.ss" or "yyyy-MM-dd HH:mm:ss" or "yyyyMMdd_HHmmss"
  let capturedAtEpochMs = 0;
  let remaining = baseName;

  const dateMatch = baseName.match(/^(\d{4}-\d{2}-\d{2})\s+(\d{2})[.:](\d{2})[.:](\d{2})/);
  if (dateMatch) {
    const [fullDateStr, datePart, hour, minute, second] = dateMatch;
    const isoString = `${datePart}T${hour}:${minute}:${second}Z`;
    const parsedTime = new Date(isoString).getTime();
    if (!isNaN(parsedTime) && parsedTime > 0) {
      capturedAtEpochMs = parsedTime;
      remaining = baseName.slice(fullDateStr.length).replace(/^[\s-]+/, "").trim();
    }
  } else {
    const compactMatch = baseName.match(/^(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})/);
    if (compactMatch) {
      const [fullDateStr, y, m, d, hh, mm, ss] = compactMatch;
      const isoString = `${y}-${m}-${d}T${hh}:${mm}:${ss}Z`;
      const parsedTime = new Date(isoString).getTime();
      if (!isNaN(parsedTime) && parsedTime > 0) {
        capturedAtEpochMs = parsedTime;
        remaining = baseName.slice(fullDateStr.length).replace(/^[\s_-]+/, "").trim();
      }
    }
  }

  if (!capturedAtEpochMs && createdTimeStr) {
    const parsed = new Date(createdTimeStr).getTime();
    if (!isNaN(parsed) && parsed > 0) {
      capturedAtEpochMs = parsed;
    }
  }
  if (!capturedAtEpochMs) {
    capturedAtEpochMs = Date.now();
  }

  const parts = remaining.split(" - ").map((s) => s.trim()).filter(Boolean);
  const address = parts[0] || undefined;
  const captureNote = parts.slice(1).join(" - ") || (parts.length === 1 ? undefined : undefined);

  return { capturedAtEpochMs, address, captureNote, extension: ext };
}

export async function scanProjectDriveMedia(
  drive: drive_v3.Drive,
  projectId: string,
  rootFolderId: string,
  projectName: string,
  existingPhotos: Array<{ id: string; remoteUrl?: string; objectCode?: string; capturedAtEpochMs?: number }> = []
): Promise<DriveScanResult> {
  const projectFolderId = await ensureProjectFolder(drive, rootFolderId, projectId, projectName);
  const existingDriveIds = new Set<string>();

  existingPhotos.forEach((p) => {
    if (p.remoteUrl) {
      const extracted = driveFileIdFromUrl(p.remoteUrl);
      if (extracted) existingDriveIds.add(extracted);
    }
  });

  // Query all non-folder files inside or under the project folder
  const response = await drive.files.list({
    q: [
      `mimeType != '${folderMimeType}'`,
      "trashed = false"
    ].join(" and "),
    fields: "files(id,name,mimeType,createdTime,parents)",
    pageSize: 1000,
    supportsAllDrives: true,
    includeItemsFromAllDrives: true
  });

  const files = response.data.files || [];
  const discoveredPhotos: DiscoveredDrivePhoto[] = [];
  let totalDriveFiles = 0;
  let matchedCount = 0;

  for (const file of files) {
    const fileId = file.id;
    const fileName = file.name || "";
    const mimeType = file.mimeType || "image/jpeg";

    if (!fileId || !fileName) continue;
    // Skip snapshots and thumbnails
    if (fileName.startsWith("snapshot_") || fileName.includes("__thumb") || fileName.includes("- thumbnail")) {
      continue;
    }

    const isImage = mimeType.startsWith("image/") || /\.(jpe?g|png|webp|heic)$/i.test(fileName);
    const isVideo = mimeType.startsWith("video/") || /\.(mp4|mov|m4v)$/i.test(fileName);
    if (!isImage && !isVideo) continue;

    totalDriveFiles += 1;

    if (existingDriveIds.has(fileId)) {
      matchedCount += 1;
      continue;
    }

    const { capturedAtEpochMs, address, captureNote } = parseMediaFileName(fileName, file.createdTime || undefined);
    const objectCode = sanitizeSegment(fileName.split("-")[0]?.trim() || "GENERAL", "UNKNOWN");
    const mediaType: DriveMediaType = isVideo ? "VIDEO" : "IMAGE";
    const remoteUrl = `https://lh3.googleusercontent.com/d/${encodeURIComponent(fileId)}=w1000?authuser=0`;

    discoveredPhotos.push({
      id: `drive_${fileId}`,
      driveFileId: fileId,
      name: fileName,
      projectId,
      objectType: "NODE",
      objectCode,
      capturedAtEpochMs,
      address,
      captureNote,
      mediaType,
      mimeType,
      remoteUrl,
      drivePath: `photos/${objectCode}/${fileName}`
    });
  }

  return {
    projectId,
    totalDriveFiles,
    matchedCount,
    discoveredPhotos
  };
}


