"use client";

import { initializeApp, getApps } from "firebase/app";
import {
  createUserWithEmailAndPassword,
  getAuth,
  getIdTokenResult,
  onAuthStateChanged,
  sendEmailVerification,
  signInWithEmailAndPassword,
  signOut,
  type User
} from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";

const config = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID
};

export const firebaseReady = Boolean(
  config.apiKey && config.projectId && config.storageBucket && config.appId
);

const app = firebaseReady ? (getApps()[0] ?? initializeApp(config)) : null;

export const db = app ? getFirestore(app) : null;
export const storage = app ? getStorage(app) : null;
export const auth = app ? getAuth(app) : null;

export type FirebaseUser = User;

export function observeAuth(callback: (user: User | null) => void): () => void {
  if (!auth) {
    callback(null);
    return () => undefined;
  }
  return onAuthStateChanged(auth, callback);
}

export async function signInWithEmail(email: string, password: string): Promise<User> {
  if (!auth) {
    throw new Error("Firebase chưa được cấu hình cho webapp.");
  }
  const credential = await signInWithEmailAndPassword(auth, email.trim(), password);
  return credential.user;
}

export async function registerWithEmail(email: string, password: string): Promise<User> {
  if (!auth) {
    throw new Error("Firebase chưa được cấu hình cho webapp.");
  }
  const credential = await createUserWithEmailAndPassword(auth, email.trim(), password);
  return credential.user;
}

export async function sendVerificationEmail(user: User): Promise<void> {
  await sendEmailVerification(user);
}

export async function signOutCurrentUser(): Promise<void> {
  if (!auth) return;
  await signOut(auth);
}

export async function getFirebaseUserAdminClaim(user: User, forceRefresh = true): Promise<boolean> {
  const token = await getIdTokenResult(user, forceRefresh);
  return token.claims.admin === true || token.claims.superAdmin === true || token.claims.role === "super-admin";
}

