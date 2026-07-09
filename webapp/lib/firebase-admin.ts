import fs from "node:fs";
import { cert, getApps, initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

function readCredential() {
  const filePath = process.env.FIREBASE_SERVICE_ACCOUNT_FILE?.trim();
  if (filePath) {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    if (typeof parsed.private_key === "string") {
      parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
    }
    return cert(parsed);
  }
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    return applicationDefault();
  }
  const parsed = JSON.parse(raw);
  if (typeof parsed.private_key === "string") {
    parsed.private_key = parsed.private_key.replace(/\\n/g, "\n");
  }
  return cert(parsed);
}

function adminApp() {
  if (getApps().length) {
    return getApps()[0];
  }
  return initializeApp({
    credential: readCredential(),
    projectId: process.env.FIREBASE_PROJECT_ID || process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID
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
