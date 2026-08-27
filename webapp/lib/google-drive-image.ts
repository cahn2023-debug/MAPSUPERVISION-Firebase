const rawDriveFileIdPattern = /^[A-Za-z0-9_-]+$/;

function isGoogleDriveUrl(value: string): boolean {
  try {
    const host = new URL(value).hostname.toLowerCase();
    return (
      host === "drive.google.com" ||
      host === "www.drive.google.com" ||
      host === "docs.google.com" ||
      host.endsWith("googleusercontent.com")
    );
  } catch {
    return false;
  }
}

export function driveFileIdFromUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  if (rawDriveFileIdPattern.test(trimmed)) return trimmed;
  try {
    const parsed = new URL(trimmed);
    const id = parsed.searchParams.get("id");
    if (id) {
      const cleanId = id.replace(/=w\d+.*$/, "").replace(/\?.*$/, "").trim();
      if (rawDriveFileIdPattern.test(cleanId)) return cleanId;
    }
    const pathMatch = parsed.pathname.match(/\/d\/([^/?#&=]+)/);
    if (pathMatch?.[1]?.trim()) {
      const cleanId = pathMatch[1].trim();
      if (rawDriveFileIdPattern.test(cleanId)) return cleanId;
    }
  } catch {
    const cleanId = trimmed.replace(/=w\d+.*$/, "").replace(/\?.*$/, "").trim();
    if (rawDriveFileIdPattern.test(cleanId)) return cleanId;
  }
  return "";
}

export function googleDriveImageUrl(fileId: string, width = 1000): string | undefined {
  const trimmed = fileId.trim();
  if (!trimmed) return undefined;
  let normalizedId = trimmed;
  if (/^https?:\/\//i.test(trimmed) || trimmed.includes("/") || trimmed.includes("=") || trimmed.includes("?")) {
    normalizedId = driveFileIdFromUrl(trimmed);
  }
  if (!normalizedId || !rawDriveFileIdPattern.test(normalizedId)) return undefined;
  const normalizedWidth = Number.isInteger(width) && width > 0 ? width : 1000;
  return `https://lh3.googleusercontent.com/d/${encodeURIComponent(normalizedId)}=w${normalizedWidth}?authuser=0`;
}

export function imageSourceUrl(value: string, width = 1000): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;

  if (/^https?:\/\//i.test(trimmed)) {
    if (isGoogleDriveUrl(trimmed)) {
      const extractedId = driveFileIdFromUrl(trimmed);
      if (extractedId) {
        return googleDriveImageUrl(extractedId, width);
      }
    }
    return trimmed;
  }

  return googleDriveImageUrl(trimmed, width);
}
