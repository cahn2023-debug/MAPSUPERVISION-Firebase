import { NextRequest, NextResponse } from "next/server";
import { getAdminAuth, getAdminDb } from "@/lib/firebase-admin";
import {
  driveClient,
  configuredRootFolderId,
  scanProjectDriveMedia
} from "@/lib/google-drive-media";

export const runtime = "nodejs";

function readBearerToken(request: NextRequest): string {
  const authHeader = request.headers.get("authorization") ?? "";
  return authHeader.startsWith("Bearer ") ? authHeader.slice("Bearer ".length).trim() : "";
}

function unpackProjectData(source: Record<string, unknown> | undefined): Record<string, unknown> {
  const data = source?.data;
  return data && typeof data === "object" ? data as Record<string, unknown> : source ?? {};
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ projectId: string }> }
) {
  const { projectId } = await context.params;
  const token = readBearerToken(request);
  if (!token) {
    return NextResponse.json(
      { success: false, error: { code: "UNAUTHORIZED", message: "Missing Firebase ID token." } },
      { status: 401 }
    );
  }

  try {
    const decoded = await getAdminAuth().verifyIdToken(token);
    const projectRef = getAdminDb().collection("projects").doc(projectId);
    const projectDoc = await projectRef.get();
    const projectData = unpackProjectData(projectDoc.data());
    const projectName = typeof projectData.name === "string" ? projectData.name : projectId;

    let rootFolderId = "";
    if (typeof projectData.mediaStorageFolderId === "string" && projectData.mediaStorageFolderId.trim()) {
      rootFolderId = projectData.mediaStorageFolderId.trim();
    } else {
      rootFolderId = configuredRootFolderId();
    }

    // Load existing site_photos
    const photosSnapshot = await projectRef.collection("site_photos").get();
    const existingPhotos = photosSnapshot.docs.map((doc: any) => {
      const data = unpackProjectData(doc.data());
      return {
        id: doc.id,
        remoteUrl: typeof data.remoteUrl === "string" ? data.remoteUrl : undefined,
        objectCode: typeof data.objectCode === "string" ? data.objectCode : undefined,
        capturedAtEpochMs: typeof data.capturedAtEpochMs === "number" ? data.capturedAtEpochMs : undefined
      };
    });

    const scanResult = await scanProjectDriveMedia(
      driveClient(),
      projectId,
      rootFolderId,
      projectName,
      existingPhotos
    );

    return NextResponse.json({ success: true, data: scanResult });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Failed to scan Google Drive media.";
    return NextResponse.json(
      { success: false, error: { code: "SCAN_FAILED", message } },
      { status: 500 }
    );
  }
}
