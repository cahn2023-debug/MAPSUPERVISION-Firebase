import { Readable } from "node:stream";
import fs from "node:fs";
import { google, type drive_v3 } from "googleapis";

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
  const filePath = stripWrappingQuotes(process.env.GOOGLE_SERVICE_ACCOUNT_FILE || "");
  if (filePath) {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    if (typeof parsed.private_key === "string") {
      parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
    }
    return parsed;
  }
  const parsed = JSON.parse(requiredEnv("GOOGLE_SERVICE_ACCOUNT_JSON"));
  if (typeof parsed.private_key === "string") {
    parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
  }
  return parsed;
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
  return `https://drive.google.com/uc?export=view&id=${encodeURIComponent(fileId)}`;
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
