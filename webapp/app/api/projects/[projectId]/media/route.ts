import { NextRequest, NextResponse } from "next/server";
import { getAdminAuth, getAdminDb } from "@/lib/firebase-admin";
import { uploadProjectMedia, type DriveMediaObjectType, type DriveMediaType } from "@/lib/google-drive-media";

export const runtime = "nodejs";

type ErrorCode =
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "BAD_REQUEST"
  | "CONFIGURATION_ERROR"
  | "UPLOAD_FAILED";

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

async function verifyProjectAccess(projectId: string, token: string): Promise<boolean> {
  const decoded = await getAdminAuth().verifyIdToken(token);
  const member = await getAdminDb()
    .collection("projects")
    .doc(projectId)
    .collection("projectMembers")
    .doc(decoded.uid)
    .get();
  return member.exists && member.data()?.isActive === true;
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ projectId: string }> }
) {
  const { projectId } = await context.params;
  const authHeader = request.headers.get("authorization") ?? "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice("Bearer ".length).trim() : "";
  if (!token) {
    return apiError(401, "UNAUTHORIZED", "Missing Firebase ID token.");
  }

  let hasAccess = false;
  try {
    hasAccess = await verifyProjectAccess(projectId, token);
  } catch {
    return apiError(401, "UNAUTHORIZED", "Invalid Firebase ID token.");
  }
  if (!hasAccess) {
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
  const mimeType = textField(form, "mimeType") || "application/octet-stream";
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
      photoId,
      objectCode,
      objectType,
      mediaType,
      mimeType,
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
