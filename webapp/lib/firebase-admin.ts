import fs from "node:fs";
import path from "node:path";
import { cert, getApps, initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

function readCredential() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
  if (raw) {
    try {
      let clean = raw;
      if ((clean.startsWith("'") && clean.endsWith("'")) || (clean.startsWith('"') && clean.endsWith('"'))) {
        clean = clean.slice(1, -1).trim();
      }
      const parsed = JSON.parse(clean);
      if (typeof parsed.private_key === "string") {
        parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
      }
      return cert(parsed);
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
        const parsed = JSON.parse(content);
        if (typeof parsed.private_key === "string") {
          parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
        }
        return cert(parsed);
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
