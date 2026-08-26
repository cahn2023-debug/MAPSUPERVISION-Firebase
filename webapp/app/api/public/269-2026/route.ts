import { NextResponse } from "next/server";
import { readPublicProject } from "@/lib/public-project";

export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const payload = await readPublicProject();
    if (!payload) {
      return NextResponse.json({ error: "Không tìm thấy dự án công khai 269 - 2026." }, { status: 404 });
    }
    return NextResponse.json(payload, {
      headers: {
        "Cache-Control": "public, s-maxage=300, stale-while-revalidate=86400",
        "CDN-Cache-Control": "public, s-maxage=300, stale-while-revalidate=86400",
        "Vercel-CDN-Cache-Control": "public, s-maxage=300, stale-while-revalidate=86400"
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
