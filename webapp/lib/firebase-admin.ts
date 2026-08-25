import fs from "node:fs";
import path from "node:path";
import { cert, getApps, initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

export function sanitizePrivateKey(rawKey: string): string {
  if (!rawKey) return rawKey;
  let key = rawKey.trim();

  if ((key.startsWith('"') && key.endsWith('"')) || (key.startsWith("'") && key.endsWith("'"))) {
    key = key.slice(1, -1).trim();
  }

  key = key.replace(/\\r/g, "").replace(/\r/g, "");
  key = key.replace(/\\\\n/g, "\n").replace(/\\n/g, "\n");

  const header = "-----BEGIN PRIVATE KEY-----";
  const footer = "-----END PRIVATE KEY-----";

  if (key.includes(header) && key.includes(footer)) {
    const startIndex = key.indexOf(header);
    const endIndex = key.indexOf(footer);
    const pemContent = key.slice(startIndex + header.length, endIndex).trim();
    const bodyLines = pemContent.split(/\s+/).filter(Boolean).join("\n");
    key = `${header}\n${bodyLines}\n${footer}\n`;
  }

  return key;
}

export function sanitizeServiceAccount(parsed: Record<string, unknown>): Record<string, unknown> {
  const result = { ...parsed };
  if (typeof result.private_key === "string") {
    result.private_key = sanitizePrivateKey(result.private_key);
  }
  return result;
}

function parseServiceAccountJson(raw: string): any {
  let clean = raw.trim();
  if ((clean.startsWith("'") && clean.endsWith("'")) || (clean.startsWith('"') && clean.endsWith('"'))) {
    clean = clean.slice(1, -1).trim();
  }
  try {
    return JSON.parse(clean);
  } catch {
    try {
      const decoded = Buffer.from(clean, "base64").toString("utf8");
      return JSON.parse(decoded);
    } catch {
      throw new Error("Unable to parse service account JSON string");
    }
  }
}

function readCredential() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  if (raw) {
    try {
      const parsed = sanitizeServiceAccount(parseServiceAccountJson(raw));
      return cert(parsed as any);
    } catch (err) {
      console.warn("[FirebaseAdmin] Could not parse FIREBASE_SERVICE_ACCOUNT_JSON:", err);
    }
  }

  const filePath = process.env.FIREBASE_SERVICE_ACCOUNT_FILE?.trim();
  const candidatePaths = [
    filePath ? path.resolve(process.cwd(), filePath) : null,
    filePath ? path.resolve(filePath) : null,
    path.resolve(process.cwd(), "../mapsupervision-3d985eee34f0.json"),
    path.resolve(process.cwd(), "mapsupervision-3d985eee34f0.json"),
    path.resolve(__dirname, "../../mapsupervision-3d985eee34f0.json"),
    path.resolve(__dirname, "../mapsupervision-3d985eee34f0.json")
  ].filter((p): p is string => typeof p === "string" && p.length > 0);

  for (const candidate of candidatePaths) {
    try {
      if (fs.existsSync(candidate)) {
        const content = fs.readFileSync(candidate, "utf8");
        const parsed = sanitizeServiceAccount(JSON.parse(content));
        return cert(parsed as any);
      }
    } catch (err) {
      console.warn(`[FirebaseAdmin] Failed reading ${candidate}:`, err);
    }
  }

  return applicationDefault();
}

function adminApp() {
  if (getApps().length) {
    return getApps()[0];
  }
  return initializeApp({
    credential: readCredential(),
    projectId: process.env.FIREBASE_PROJECT_ID || process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "mapsupervision"
  });
}

// ponytail: test mock hooks
let authMock: any = null;
let dbMock: any = null;

export function getAdminAuth() {
  if (process.env.NODE_ENV === "test" && authMock) return authMock;
  return getAuth(adminApp());
}

export function getAdminDb() {
  if (process.env.NODE_ENV === "test" && dbMock) return dbMock;
  return getFirestore(adminApp());
}

export function setAdminAuthMock(mock: any) {
  authMock = mock;
}

export function setAdminDbMock(mock: any) {
  dbMock = mock;
}
