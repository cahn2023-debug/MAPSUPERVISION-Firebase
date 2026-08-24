const admin = require("firebase-admin");

const ADMIN_EMAILS = [
  "buiducthanh2@gmail.com",
  "cahn2023@gmail.com",
  "thanh.bd@tfsc.com.vn"
];
const FIREBASE_PROJECT_ID = "mapsupervision";

const fs = require("fs");
const path = require("path");

function readCredential() {
  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON.trim();
    const parsed = raw.startsWith("'") && raw.endsWith("'") ? raw.slice(1, -1) : raw;
    return admin.credential.cert(JSON.parse(parsed));
  }

  const candidatePaths = [
    path.resolve(__dirname, "../../mapsupervision-3d985eee34f0.json"),
    path.resolve(__dirname, "../mapsupervision-3d985eee34f0.json"),
    path.resolve(process.cwd(), "../mapsupervision-3d985eee34f0.json"),
    path.resolve(process.cwd(), "mapsupervision-3d985eee34f0.json")
  ];

  for (const filePath of candidatePaths) {
    if (fs.existsSync(filePath)) {
      const content = fs.readFileSync(filePath, "utf-8");
      return admin.credential.cert(JSON.parse(content));
    }
  }

  const envLocalPath = path.resolve(__dirname, "../.env.local");
  if (fs.existsSync(envLocalPath)) {
    const envContent = fs.readFileSync(envLocalPath, "utf-8");
    const match = envContent.match(/FIREBASE_SERVICE_ACCOUNT_JSON=(.+)/);
    if (match) {
      let raw = match[1].trim();
      if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith('"') && raw.endsWith('"'))) {
        raw = raw.slice(1, -1);
      }
      return admin.credential.cert(JSON.parse(raw));
    }
  }

  return admin.credential.applicationDefault();
}

function initializeAdminApp() {
  if (admin.apps.length) {
    return admin.app();
  }

  return admin.initializeApp({
    credential: readCredential(),
    projectId: FIREBASE_PROJECT_ID
  });
}

async function promoteEmail(auth, db, email) {
  try {
    let user;
    let isNewUser = false;
    try {
      user = await auth.getUserByEmail(email);
    } catch (err) {
      if (err.code === "auth/user-not-found") {
        user = await auth.createUser({
          email: email,
          emailVerified: true,
          displayName: email.split("@")[0]
        });
        isNewUser = true;
      } else {
        throw err;
      }
    }

    const currentClaims = user.customClaims || {};
    const updatedClaims = {
      ...currentClaims,
      admin: true,
      superAdmin: true,
      role: "super-admin"
    };

    await auth.setCustomUserClaims(user.uid, updatedClaims);

    // Sync to Firestore /users/{uid}
    if (db) {
      const userRef = db.collection("users").doc(user.uid);
      const userSnap = await userRef.get();
      const now = Date.now();
      if (!userSnap.exists) {
        await userRef.set({
          uid: user.uid,
          email: user.email,
          displayName: user.displayName || email.split("@")[0],
          emailVerified: user.emailVerified ?? true,
          createdAtEpochMs: now,
          lastLoginAtEpochMs: now,
          updatedAtEpochMs: now,
          isDisabled: false,
          projectIds: []
        });
      } else {
        await userRef.set({
          updatedAtEpochMs: now,
          isDisabled: false
        }, { merge: true });
      }
    }

    const status = isNewUser ? "created & promoted" : "updated";
    return { email, status, uid: user.uid, claims: updatedClaims };
  } catch (error) {
    return {
      email,
      status: "failed",
      message: error instanceof Error ? error.message : String(error)
    };
  }
}

async function main() {
  initializeAdminApp();
  const auth = admin.auth();
  const db = admin.firestore();

  console.log("Bootstrapping Firebase admin claims...");
  const results = [];
  for (const email of ADMIN_EMAILS) {
    const result = await promoteEmail(auth, db, email);
    results.push(result);
    if (result.status === "failed") {
      console.error(`[FAILED] ${result.email}: ${result.message}`);
      continue;
    }
    console.log(`[${result.status.toUpperCase()}] ${result.email} -> ${result.uid}`);
  }

  const failed = results.filter((item) => item.status === "failed");
  console.log("");
  console.log(`Done. Success: ${results.length - failed.length}, Failed: ${failed.length}`);
  console.log("Nguoi dung can dang xuat va dang nhap lai de token moi nhan custom claim admin.");

  if (failed.length) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error("Bootstrap admin claims failed:", error);
  process.exit(1);
});
