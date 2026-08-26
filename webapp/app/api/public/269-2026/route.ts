import { NextResponse } from "next/server";
import { readPublicProject } from "@/lib/public-project";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const bypassCache = searchParams.get("refresh") === "true" || searchParams.get("refresh") === "1" || searchParams.get("bypass") === "1";
    const payload = await readPublicProject(bypassCache);
    if (!payload) {
      return NextResponse.json({ error: "Không tìm thấy dự án công khai 269 - 2026." }, { status: 404 });
    }
    return NextResponse.json(payload, {
      headers: {
        "Cache-Control": "public, s-maxage=10, stale-while-revalidate=30",
        "CDN-Cache-Control": "public, s-maxage=10, stale-while-revalidate=30",
        "Vercel-CDN-Cache-Control": "public, s-maxage=10, stale-while-revalidate=30"
      }
    });
  } catch (error) {
    console.error("[api/public/269-2026] Error fetching public project:", error);
    const msg = error instanceof Error ? error.message : "Đã xảy ra lỗi khi tải dữ liệu dự án từ Firestore.";
    return NextResponse.json(
      { error: msg },
      { status: 500 }
    );
  }
}
