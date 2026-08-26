import { NextRequest, NextResponse } from "next/server";
import { Readable } from "node:stream";
import { downloadDriveFile, driveFileIdFromUrl } from "@/lib/google-drive-media";
import { findPublicProject } from "@/lib/public-project";

export const dynamic = "force-dynamic";

export async function GET(
  _request: NextRequest,
  context: { params: Promise<{ photoId: string }> }
) {
  try {
    const { photoId } = await context.params;
    const project = await findPublicProject();
    if (!project) return NextResponse.json({ error: "Không tìm thấy dự án công khai." }, { status: 404 });

    const snapshot = await (await import("@/lib/firebase-admin")).getAdminDb()
      .collection("projects")
      .doc(project.id)
      .collection("site_photos")
      .doc(photoId)
      .get();
    if (!snapshot.exists) return NextResponse.json({ error: "Không tìm thấy ảnh." }, { status: 404 });

    const source = snapshot.data() as Record<string, unknown>;
    const data = source.data && typeof source.data === "object" ? source.data as Record<string, unknown> : source;
    const fileId = driveFileIdFromUrl(String(data.remoteUrl ?? "").trim());
    if (!fileId) return NextResponse.json({ error: "Ảnh chưa sẵn sàng." }, { status: 404 });

    const { stream, contentType } = await downloadDriveFile(fileId);
    return new NextResponse(Readable.toWeb(stream) as ReadableStream, {
      headers: { "Content-Type": contentType, "Cache-Control": "public, max-age=300" }
    });
  } catch (error) {
    console.error("[api/public/media] Error streaming photo:", error);
    return NextResponse.json(
      { error: error instanceof Error ? error.message : "Lỗi khi tải ảnh hiện trường." },
      { status: 500 }
    );
  }
}
