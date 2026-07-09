import { cert, getApps, initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

function readCredential() {
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

export function getAdminAuth() {
  return getAuth(adminApp());
}

export function getAdminDb() {
  return getFirestore(adminApp());
}
