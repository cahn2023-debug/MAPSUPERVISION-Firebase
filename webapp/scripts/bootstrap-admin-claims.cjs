const admin = require("firebase-admin");

const ADMIN_EMAILS = [
  "buiducthanh2@gmail.com",
  "cahn2023@gmail.com",
  "thanh.bd@tfsc.com.vn"
];
const FIREBASE_PROJECT_ID = "mapsupervision";

function readCredential() {
  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    return admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON));
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

async function promoteEmail(auth, email) {
  try {
    const user = await auth.getUserByEmail(email);
    const currentClaims = user.customClaims || {};
    if (currentClaims.admin === true) {
      return { email, status: "unchanged", uid: user.uid };
    }

    await auth.setCustomUserClaims(user.uid, {
      ...currentClaims,
      admin: true
    });
    return { email, status: "updated", uid: user.uid };
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

  console.log("Bootstrapping Firebase admin claims...");
  const results = [];
  for (const email of ADMIN_EMAILS) {
    const result = await promoteEmail(auth, email);
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
