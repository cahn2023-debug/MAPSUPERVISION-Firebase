import { Readable } from "node:stream";
import { google, type drive_v3 } from "googleapis";

export type DriveMediaObjectType = "NODE" | "ROUTE";
export type DriveMediaType = "IMAGE" | "VIDEO";

export type DriveMediaUpload = {
  projectId: string;
  photoId: string;
  objectType: DriveMediaObjectType;
  objectCode: string;
  mediaType: DriveMediaType;
  mimeType: string;
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

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} is not configured.`);
  }
  return value;
}

function serviceAccountCredentials() {
  const parsed = JSON.parse(requiredEnv("GOOGLE_SERVICE_ACCOUNT_JSON"));
  if (typeof parsed.private_key === "string") {
    parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
  }
  return parsed;
}

function driveClient(): drive_v3.Drive {
  if (cachedDrive) return cachedDrive;
  const auth = new google.auth.GoogleAuth({
    credentials: serviceAccountCredentials(),
    scopes: ["https://www.googleapis.com/auth/drive"]
  });
  cachedDrive = google.drive({ version: "v3", auth });
  return cachedDrive;
}

function escapeDriveQuery(value: string): string {
  return value.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
}

function sanitizeSegment(value: string): string {
  return value.trim().replace(/[\\/]+/g, "-").replace(/\s+/g, " ").slice(0, 120) || "unknown";
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

async function ensureFolderPath(drive: drive_v3.Drive, rootFolderId: string, segments: string[]): Promise<string> {
  let currentFolderId = rootFolderId;
  for (const segment of segments) {
    currentFolderId = await ensureChildFolder(drive, currentFolderId, segment);
  }
  return currentFolderId;
}

async function findChildFile(drive: drive_v3.Drive, parentId: string, name: string): Promise<string | null> {
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
  name: string,
  mimeType: string,
  bytes: Buffer
): Promise<string> {
  const media = {
    mimeType,
    body: Readable.from(bytes)
  };
  const existingId = await findChildFile(drive, parentId, name);
  if (existingId) {
    await drive.files.update({
      fileId: existingId,
      media,
      fields: "id",
      supportsAllDrives: true
    });
    await ensurePublicReader(drive, existingId);
    return existingId;
  }

  const created = await drive.files.create({
    requestBody: {
      name,
      parents: [parentId]
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

function publicDriveUrl(fileId: string): string {
  return `https://drive.google.com/uc?export=view&id=${encodeURIComponent(fileId)}`;
}

export async function uploadProjectMedia(input: DriveMediaUpload): Promise<DriveMediaUploadResult> {
  const drive = driveClient();
  const projectFolder = sanitizeSegment(input.projectId);
  const objectFolder = sanitizeSegment(input.objectCode);
  const rootFolderId = requiredEnv("GOOGLE_DRIVE_ROOT_FOLDER_ID");
  const folderSegments = input.mediaType === "VIDEO"
    ? [projectFolder, "media", "videos", input.objectType === "ROUTE" ? "Routes" : "Nodes", objectFolder]
    : [projectFolder, "photos", input.objectType === "ROUTE" ? "Routes" : "Nodes", objectFolder];
  const parentId = await ensureFolderPath(drive, rootFolderId, folderSegments);
  const originalExtension = input.original.extension || extensionForMime(input.mimeType, input.mediaType === "VIDEO" ? "mp4" : "jpg");
  const originalName = `${sanitizeSegment(input.photoId)}-original.${originalExtension}`;
  const originalId = await upsertFile(drive, parentId, originalName, input.mimeType, input.original.bytes);

  let thumbnailUrl: string | undefined;
  if (input.thumbnail) {
    const thumbnailExtension = input.thumbnail.extension || extensionForMime(input.thumbnail.mimeType, "jpg");
    const thumbnailId = await upsertFile(
      drive,
      parentId,
      `${sanitizeSegment(input.photoId)}-thumbnail.${thumbnailExtension}`,
      input.thumbnail.mimeType,
      input.thumbnail.bytes
    );
    thumbnailUrl = publicDriveUrl(thumbnailId);
  }

  return {
    remoteUrl: publicDriveUrl(originalId),
    thumbnailUrl,
    driveFileId: originalId,
    drivePath: folderSegments.concat(originalName).join("/")
  };
}
