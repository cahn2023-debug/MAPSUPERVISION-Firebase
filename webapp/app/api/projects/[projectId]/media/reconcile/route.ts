import { NextRequest, NextResponse } from "next/server";
import { getAdminAuth, getAdminDb } from "@/lib/firebase-admin";
import {
  driveClient,
  configuredRootFolderId,
  uploadDriveSnapshot,
  type DiscoveredDrivePhoto
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
    await getAdminAuth().verifyIdToken(token);
    const body = await request.json().catch(() => ({}));
    const photos: DiscoveredDrivePhoto[] = Array.isArray(body?.photos) ? body.photos : [];

    if (photos.length === 0) {
      return NextResponse.json(
        { success: false, error: { code: "BAD_REQUEST", message: "No photos provided to reconcile." } },
        { status: 400 }
      );
    }

    const db = getAdminDb();
    const projectRef = db.collection("projects").doc(projectId);
    const projectDoc = await projectRef.get();
    const projectData = unpackProjectData(projectDoc.data());
    const projectName = typeof projectData.name === "string" ? projectData.name : projectId;

    let rootFolderId = "";
    if (typeof projectData.mediaStorageFolderId === "string" && projectData.mediaStorageFolderId.trim()) {
      rootFolderId = projectData.mediaStorageFolderId.trim();
    } else {
      rootFolderId = configuredRootFolderId();
    }

    // Write in chunks of 500 (Firestore batch limit)
    const chunkSize = 400;
    for (let i = 0; i < photos.length; i += chunkSize) {
      const chunk = photos.slice(i, i + chunkSize);
      const batch = db.batch();

      chunk.forEach((photo) => {
        const docRef = projectRef.collection("site_photos").doc(photo.id);
        const now = Date.now();
        const payload = {
          id: photo.id,
          projectId: projectId,
          objectCode: photo.objectCode,
          statusTag: photo.statusTag || null,
          filePath: photo.drivePath || `photos/${photo.objectCode}/${photo.name}`,
          thumbnailPath: photo.drivePath || `photos/${photo.objectCode}/${photo.name}`,
          capturedAtEpochMs: photo.capturedAtEpochMs || now,
          matchedAtEpochMs: 0,
          matchingTimeOffsetMs: 0,
          mediaType: photo.mediaType || "IMAGE",
          mimeType: photo.mimeType || "image/jpeg",
          durationMs: 0,
          address: photo.address || null,
          captureNote: photo.captureNote || null,
          matchedNodeId: null,
          matchedRouteId: null,
          updatedAtEpochMs: photo.capturedAtEpochMs || now,
          syncStatus: "DONE",
          remoteUrl: photo.remoteUrl,
          isDeleted: false
        };

        const envelope = {
          id: photo.id,
          projectId: projectId,
          tableName: "site_photos",
          data: payload,
          updatedAtEpochMs: photo.capturedAtEpochMs || now,
          isDeleted: false,
          sourceDeviceId: "webapp_drive_scanner",
          lastSyncedAtEpochMs: now
        };

        batch.set(docRef, envelope, { merge: true });
      });

      await batch.commit();
    }

    // Generate snapshot payload and update Drive Snapshot in background
    try {
      const subcollections = [
        "gis_node", "gis_route", "task", "note", "work_plan",
        "daily_log", "site_photos", "work_volume_progress",
        "material_declaration", "material_handover", "report_draft"
      ];

      const collectionsData: Record<string, any[]> = {};
      await Promise.all(
        subcollections.map(async (collName) => {
          const snap = await projectRef.collection(collName).get();
          collectionsData[collName] = snap.docs.map((d: any) => {
            const rowData = unpackProjectData(d.data());
            return { ...rowData, id: d.id };
          });
        })
      );

      const snapshotPayload = {
        project: { ...projectData, id: projectId },
        collections: collectionsData,
        updatedAtEpochMs: Date.now()
      };

      await uploadDriveSnapshot(
        driveClient(),
        rootFolderId,
        projectId,
        projectName,
        snapshotPayload
      );
    } catch (snapshotErr) {
      console.warn("[reconcile] Snapshot generation warning:", snapshotErr);
    }

    return NextResponse.json({
      success: true,
      data: {
        reconciledCount: photos.length
      }
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Failed to reconcile photos.";
    return NextResponse.json(
      { success: false, error: { code: "RECONCILE_FAILED", message } },
      { status: 500 }
    );
  }
}
