const rawDriveFileIdPattern = /^[A-Za-z0-9_-]+$/;

function isGoogleDriveUrl(value: string): boolean {
  try {
    const host = new URL(value).hostname.toLowerCase();
    return host === "drive.google.com" || host === "www.drive.google.com" || host === "docs.google.com";
  } catch {
    return false;
  }
}

export function driveFileIdFromUrl(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "";
  try {
    const parsed = new URL(trimmed);
    const id = parsed.searchParams.get("id");
    if (id) return id.trim();
    const pathMatch = parsed.pathname.match(/\/d\/([^/]+)/);
    return pathMatch?.[1]?.trim() || "";
  } catch {
    return "";
  }
}

export function googleDriveImageUrl(fileId: string, width = 1000): string | undefined {
  const normalizedId = fileId.trim();
  if (!rawDriveFileIdPattern.test(normalizedId)) return undefined;
  const normalizedWidth = Number.isInteger(width) && width > 0 ? width : 1000;
  return `https://lh3.googleusercontent.com/d/${encodeURIComponent(normalizedId)}=w${normalizedWidth}?authuser=0`;
}

export function imageSourceUrl(value: string, width = 1000): string | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;

  if (/^https?:\/\//i.test(trimmed)) {
    if (!isGoogleDriveUrl(trimmed)) return trimmed;
    return googleDriveImageUrl(driveFileIdFromUrl(trimmed), width);
  }

  return googleDriveImageUrl(trimmed, width);
}
