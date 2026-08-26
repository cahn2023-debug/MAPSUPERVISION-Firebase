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
      headers: { "Cache-Control": "no-store" }
    });
  } catch (error) {
    console.error("[api/public/269-2026] Error fetching public project:", error);
    return NextResponse.json(
      { error: error instanceof Error ? error.message : "Đã xảy ra lỗi khi tải dữ liệu dự án công khai." },
      { status: 500 }
    );
  }
}
