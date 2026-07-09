import { google, type drive_v3 } from "googleapis";
import { getAdminDb } from "./firebase-admin";
import {
  driveClient,
  driveFileIdFromUrl,
  buildDriveMediaFileName,
  sanitizeSegment,
  ensureProjectFolder,
  ensureFolderPath,
  configuredRootFolderId,
  findChildFile
} from "./google-drive-media";
import path from "node:path";

const folderMimeType = "application/vnd.google-apps.folder";

let migrationStarted = false;

function unpackData(source: any): any {
  if (!source || typeof source !== "object") return {};
  if (source.data && typeof source.data === "object") return source.data;
  return source;
}

function extensionForNameOrMime(fileName: string | undefined, mimeType: string | undefined): string {
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

async function resolveFolderInfo(drive: drive_v3.Drive, folderId: string) {
  const response = await drive.files.get({
    fileId: folderId,
    fields: "id,name,parents,mimeType",
    supportsAllDrives: true
  });
  return response.data;
}

async function resolveRelativeSegments(
  drive: drive_v3.Drive,
  parentId: string,
  rootFolderId: string,
  projectId: string,
  projectName: string
): Promise<string[] | null> {
  if (!parentId) return null;
  const segments: string[] = [];
  let currentId: string | null = parentId;
  while (currentId) {
    if (currentId === rootFolderId) return null;
    const folder = await resolveFolderInfo(drive, currentId);
    if (!folder || folder.mimeType !== folderMimeType) return null;
    const folderName = String(folder.name || "");
    if (folderName === projectName || folderName === projectId) {
      return segments.reverse();
    }
    segments.push(folderName);
    currentId = folder.parents && folder.parents[0] ? folder.parents[0] : null;
  }
  return null;
}

function defaultSegmentsForPhoto(photo: any): string[] {
  const objectType = String(photo.objectType || "NODE").toUpperCase() === "ROUTE" ? "Routes" : "Nodes";
  const objectCode = sanitizeSegment(photo.objectCode || "unknown");
  const mediaType = String(photo.mediaType || "IMAGE").toUpperCase();
  if (mediaType === "VIDEO") {
    return ["media", "videos", objectType, objectCode];
  }
  return ["photos", objectType, objectCode];
}

async function ensureUniqueName(
  drive: drive_v3.Drive,
  parentId: string,
  desiredName: string,
  currentFileId: string
): Promise<string> {
  const ext = path.extname(desiredName);
  const base = ext ? desiredName.slice(0, -ext.length) : desiredName;
  let attempt = 1;
  let candidate = desiredName;
  while (true) {
    const existingId = await findChildFile(drive, parentId, candidate);
    if (!existingId || existingId === currentFileId) {
      return candidate;
    }
    attempt += 1;
    candidate = `${base} (${attempt})${ext}`;
  }
}

async function migratePhoto(
  drive: drive_v3.Drive,
  rootFolderId: string,
  projectId: string,
  projectName: string,
  photoId: string,
  photo: any
) {
  const remoteUrl = String(photo.remoteUrl || "").trim();
  const fileId = driveFileIdFromUrl(remoteUrl);
  if (!fileId) {
    return { status: "skipped", reason: "missing_file_id" };
  }

  let file: any;
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

  const projectFolderId = await ensureProjectFolder(drive, rootFolderId, projectId, projectName);
  const currentParentId = file.parents && file.parents[0] ? file.parents[0] : "";
  const relativeSegments =
    (await resolveRelativeSegments(drive, currentParentId, rootFolderId, projectId, sanitizeSegment(projectName))) ||
    defaultSegmentsForPhoto(photo);

  const targetParentId = await ensureFolderPath(drive, projectFolderId, relativeSegments);
  const targetName = await ensureUniqueName(drive, targetParentId, desiredName, fileId);

  const requestBody: any = {};
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

  let changed = false;
  if (Object.keys(requestBody).length > 0) {
    await drive.files.update({
      fileId,
      requestBody,
      fields: "id,name,parents,appProperties",
      supportsAllDrives: true
    });
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

export async function runMigrationIfNeeded() {
  if (migrationStarted) return;
  migrationStarted = true;

  console.log("[Migration] Starting Google Drive media migration background job...");
  try {
    const rootFolderId = configuredRootFolderId();
    const firestore = getAdminDb();
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
        try {
          const result = await migratePhoto(drive, rootFolderId, projectId, projectName, photoDoc.id, photo);
          if (result.status === "updated") summary.updated += 1;
          else if (result.status === "unchanged") summary.unchanged += 1;
          else summary.skipped += 1;
        } catch (photoError) {
          console.error(`[Migration] Failed to migrate photo ${photoDoc.id} in project ${projectName}:`, photoError);
          summary.skipped += 1;
        }
      }
    }

    console.log("[Migration] Google Drive media migration complete:", JSON.stringify(summary));
  } catch (error) {
    console.error("[Migration] Google Drive media migration job encountered an error:", error);
  }
}
