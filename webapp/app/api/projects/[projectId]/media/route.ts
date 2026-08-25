import { NextRequest, NextResponse } from "next/server";
import { getAdminAuth, getAdminDb } from "@/lib/firebase-admin";
import { Readable } from "node:stream";
import {
  downloadDriveFile,
  driveFileIdFromUrl,
  uploadProjectMedia,
  type DriveMediaObjectType,
  type DriveMediaType
} from "@/lib/google-drive-media";

export const runtime = "nodejs";

type ErrorCode =
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "BAD_REQUEST"
  | "CONFIGURATION_ERROR"
  | "UPLOAD_FAILED";

type ProjectAccess = {
  hasAccess: boolean;
  mediaStorageFolderId: string;
  projectName: string;
};

function apiError(status: number, code: ErrorCode, message: string) {
  return NextResponse.json(
    {
      success: false,
      error: { code, message }
    },
    { status }
  );
}

function textField(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value.trim() : "";
}

function fileField(form: FormData, name: string): File | null {
  const value = form.get(name);
  return value instanceof File ? value : null;
}

function fileExtension(file: File, fallback: string): string {
  const namePart = file.name.split(".").pop()?.trim().toLowerCase();
  if (namePart && /^[a-z0-9]{1,8}$/.test(namePart)) return namePart;
  return fallback;
}

function unpackProjectData(source: Record<string, unknown> | undefined): Record<string, unknown> {
  const data = source?.data;
  return data && typeof data === "object" ? data as Record<string, unknown> : source ?? {};
}

function readBearerToken(request: NextRequest): string {
  const authHeader = request.headers.get("authorization") ?? "";
  return authHeader.startsWith("Bearer ") ? authHeader.slice("Bearer ".length).trim() : "";
}

async function verifyProjectAccess(projectId: string, token: string): Promise<ProjectAccess> {
  const decoded = await getAdminAuth().verifyIdToken(token);
  const projectRef = getAdminDb().collection("projects").doc(projectId);
  const accessRef = getAdminDb().collection("accessRequests").doc(`${projectId}__${decoded.uid}`);
  const [project, accessRequest] = await Promise.all([
    projectRef.get(),
    accessRef.get()
  ]);
  const projectData = unpackProjectData(project.data());
  const accessData = accessRequest.data();
  const hasApprovedAccess = accessData?.status === "APPROVED";
  return {
    hasAccess: decoded.admin === true || hasApprovedAccess,
    mediaStorageFolderId: projectData.mediaStorageFolderId ? String(projectData.mediaStorageFolderId) : "",
    projectName: projectData.name ? String(projectData.name).trim() : projectId
  };
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ projectId: string }> }
) {
  const { projectId } = await context.params;
  const token = readBearerToken(request);
  if (!token) {
    return apiError(401, "UNAUTHORIZED", "Missing Firebase ID token.");
  }

  let access: ProjectAccess;
  try {
    access = await verifyProjectAccess(projectId, token);
  } catch {
    return apiError(401, "UNAUTHORIZED", "Invalid Firebase ID token.");
  }
  if (!access.hasAccess) {
    return apiError(403, "FORBIDDEN", "User does not have access to this project.");
  }

  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return apiError(400, "BAD_REQUEST", "Request must be multipart/form-data.");
  }

  const photoId = textField(form, "photoId");
  const objectCode = textField(form, "objectCode");
  const objectType = textField(form, "objectType") as DriveMediaObjectType;
  const mediaType = textField(form, "mediaType") as DriveMediaType;
  const statusTag = textField(form, "statusTag") || undefined;
  const mimeType = textField(form, "mimeType") || "application/octet-stream";
  const capturedAtEpochMs = Number(textField(form, "capturedAtEpochMs") || Date.now());
  const address = textField(form, "address") || undefined;
  const captureNote = textField(form, "captureNote") || undefined;
  const original = fileField(form, "original");
  const thumbnail = fileField(form, "thumbnail");

  if (!photoId || !objectCode || !original) {
    return apiError(400, "BAD_REQUEST", "photoId, objectCode and original file are required.");
  }
  if (objectType !== "NODE" && objectType !== "ROUTE") {
    return apiError(400, "BAD_REQUEST", "objectType must be NODE or ROUTE.");
  }
  if (mediaType !== "IMAGE" && mediaType !== "VIDEO") {
    return apiError(400, "BAD_REQUEST", "mediaType must be IMAGE or VIDEO.");
  }

  try {
    const result = await uploadProjectMedia({
      projectId,
      projectName: access.projectName,
      rootFolderId: access.mediaStorageFolderId,
      photoId,
      objectCode,
      statusTag,
      objectType,
      mediaType,
      mimeType,
      capturedAtEpochMs,
      address,
      captureNote,
      original: {
        bytes: Buffer.from(await original.arrayBuffer()),
        extension: fileExtension(original, mediaType === "VIDEO" ? "mp4" : "jpg")
      },
      thumbnail: thumbnail
        ? {
            bytes: Buffer.from(await thumbnail.arrayBuffer()),
            extension: fileExtension(thumbnail, "jpg"),
            mimeType: thumbnail.type || "image/jpeg"
          }
        : undefined
    });
    return NextResponse.json({ success: true, data: result });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Failed to upload media.";
    const code: ErrorCode = message.includes("GOOGLE_") ? "CONFIGURATION_ERROR" : "UPLOAD_FAILED";
    return apiError(code === "CONFIGURATION_ERROR" ? 500 : 502, code, message);
  }
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ projectId: string }> }
) {
  const { projectId } = await context.params;
  const token = readBearerToken(request);
  if (!token) {
    return apiError(401, "UNAUTHORIZED", "Missing Firebase ID token.");
  }

  let access: ProjectAccess;
  try {
    access = await verifyProjectAccess(projectId, token);
  } catch {
    return apiError(401, "UNAUTHORIZED", "Invalid Firebase ID token.");
  }
  if (!access.hasAccess) {
    return apiError(403, "FORBIDDEN", "User does not have access to this project.");
  }

  try {
    const nextUrl = (request as any).nextUrl;
    const url = nextUrl || new URL(request.url);
    const photoId = url.searchParams.get("photoId")?.trim() || "";
    if (!photoId) {
      return apiError(400, "BAD_REQUEST", "photoId is required.");
    }

    const snapshot = await getAdminDb()
      .collection("projects")
      .doc(projectId)
      .collection("site_photos")
      .doc(photoId)
      .get();
    if (!snapshot.exists) {
      return apiError(404, "BAD_REQUEST", "Media record not found.");
    }

    const photoData = unpackProjectData(snapshot.data() as Record<string, unknown> | undefined);
    const remoteUrl = typeof photoData.remoteUrl === "string" ? photoData.remoteUrl.trim() : "";
    const fileId = driveFileIdFromUrl(remoteUrl);
    if (!fileId) {
      return apiError(404, "BAD_REQUEST", "Media file is not available.");
    }

    const { stream, contentType } = await downloadDriveFile(fileId);
    return new NextResponse(Readable.toWeb(stream) as ReadableStream, {
      headers: {
        "Content-Type": contentType,
        "Cache-Control": "private, max-age=300"
      }
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Failed to read media.";
    const code: ErrorCode = message.includes("GOOGLE_") ? "CONFIGURATION_ERROR" : "UPLOAD_FAILED";
    return apiError(code === "CONFIGURATION_ERROR" ? 500 : 502, code, message);
  }
}
