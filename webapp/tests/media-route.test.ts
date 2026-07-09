import { test } from "node:test";
import assert from "node:assert";
import { Readable } from "node:stream";
import { GET, POST } from "../app/api/projects/[projectId]/media/route";
import { setAdminAuthMock, setAdminDbMock } from "../lib/firebase-admin";
import { driveFileIdFromUrl, setUploadProjectMediaMock, setDriveClientMock, uploadProjectMedia } from "../lib/google-drive-media";
import { normalizeGoogleDriveFolderInput } from "../lib/sync";

(process.env as Record<string, string>).NODE_ENV = "test";

function projectDbMock(
  memberExists: boolean,
  projectData: Record<string, unknown> = {},
  memberData: Record<string, unknown> | null = { isActive: true },
  photoDocs: Record<string, Record<string, unknown>> = {}
) {
  return {
    collection: () => ({
      doc: () => ({
        get: async () => ({ exists: true, data: () => ({ data: projectData }) }),
        collection: (name: string) => ({
          doc: (id: string) => ({
            get: async () => {
              if (name === "projectMembers") {
                return { exists: memberExists, data: () => memberExists ? memberData : null };
              }
              const photoData = photoDocs[id];
              return { exists: Boolean(photoData), data: () => photoData ? { data: photoData } : null };
            }
          })
        })
      })
    })
  };
}

test("normalizeGoogleDriveFolderInput accepts folder URL and ID", () => {
  const fromUrl = normalizeGoogleDriveFolderInput("https://drive.google.com/drive/folders/folder_1234567890?usp=sharing");
  assert.strictEqual(fromUrl.folderId, "folder_1234567890");
  assert.strictEqual(fromUrl.folderUrl, "https://drive.google.com/drive/folders/folder_1234567890");

  const fromId = normalizeGoogleDriveFolderInput("folder-abcdefghij");
  assert.strictEqual(fromId.folderId, "folder-abcdefghij");
});

test("POST /api/projects/[projectId]/media - Missing Firebase token returns 401", async () => {
  const req = new Request("http://localhost/api/projects/proj-1/media", {
    method: "POST",
    headers: {}
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await POST(req as any, context);
  assert.strictEqual(res.status, 401);
  const body = await res.json();
  assert.strictEqual(body.success, false);
  assert.strictEqual(body.error.code, "UNAUTHORIZED");
});

test("POST /api/projects/[projectId]/media - Invalid token returns 401", async () => {
  setAdminAuthMock({
    verifyIdToken: async () => {
      throw new Error("Invalid token");
    }
  });

  const req = new Request("http://localhost/api/projects/proj-1/media", {
    method: "POST",
    headers: { "Authorization": "Bearer invalid-token" }
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await POST(req as any, context);
  assert.strictEqual(res.status, 401);
  const body = await res.json();
  assert.strictEqual(body.success, false);
  assert.strictEqual(body.error.code, "UNAUTHORIZED");
});

test("POST /api/projects/[projectId]/media - User without project membership returns 403", async () => {
  setAdminAuthMock({
    verifyIdToken: async () => ({ uid: "user-non-member" })
  });
  setAdminDbMock(projectDbMock(false));

  const req = new Request("http://localhost/api/projects/proj-1/media", {
    method: "POST",
    headers: { "Authorization": "Bearer non-member-token" }
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await POST(req as any, context);
  assert.strictEqual(res.status, 403);
  const body = await res.json();
  assert.strictEqual(body.success, false);
  assert.strictEqual(body.error.code, "FORBIDDEN");
});

test("POST /api/projects/[projectId]/media - Admin can upload without project membership", async () => {
  setAdminAuthMock({
    verifyIdToken: async () => ({ uid: "admin-user", admin: true })
  });
  setAdminDbMock(projectDbMock(false));
  setUploadProjectMediaMock(async () => ({
    remoteUrl: "https://drive.google.com/uc?export=view&id=file-admin",
    driveFileId: "file-admin",
    drivePath: "proj-1/photos/photo-admin"
  }));

  const formData = new FormData();
  formData.append("photoId", "photo-admin");
  formData.append("objectCode", "N-1");
  formData.append("objectType", "NODE");
  formData.append("mediaType", "IMAGE");
  formData.append("mimeType", "image/jpeg");
  formData.append("original", new File(["originalBytes"], "photo.jpg", { type: "image/jpeg" }));

  const req = new Request("http://localhost/api/projects/proj-1/media", {
    method: "POST",
    headers: { "Authorization": "Bearer admin-token" },
    body: formData
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await POST(req as any, context);
  assert.strictEqual(res.status, 200);
});

test("POST /api/projects/[projectId]/media - Member without isActive flag is treated as active", async () => {
  setAdminAuthMock({
    verifyIdToken: async () => ({ uid: "legacy-member" })
  });
  setAdminDbMock(projectDbMock(true, {}, {}));
  setUploadProjectMediaMock(async () => ({
    remoteUrl: "https://drive.google.com/uc?export=view&id=file-legacy",
    driveFileId: "file-legacy",
    drivePath: "proj-1/photos/photo-legacy"
  }));

  const formData = new FormData();
  formData.append("photoId", "photo-legacy");
  formData.append("objectCode", "N-1");
  formData.append("objectType", "NODE");
  formData.append("mediaType", "IMAGE");
  formData.append("mimeType", "image/jpeg");
  formData.append("original", new File(["originalBytes"], "photo.jpg", { type: "image/jpeg" }));

  const req = new Request("http://localhost/api/projects/proj-1/media", {
    method: "POST",
    headers: { "Authorization": "Bearer legacy-token" },
    body: formData
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await POST(req as any, context);
  assert.strictEqual(res.status, 200);
});

test("POST /api/projects/[projectId]/media - Valid request uploads media and returns 200", async () => {
  setAdminAuthMock({
    verifyIdToken: async () => ({ uid: "user-member" })
  });
  setAdminDbMock(projectDbMock(true, { name: "Du an A", mediaStorageFolderId: "project-folder-123" }));
  let receivedInput: any = null;
  setUploadProjectMediaMock(async (input: any) => {
    receivedInput = input;
    return {
      remoteUrl: "https://drive.google.com/uc?export=view&id=file-123",
      driveFileId: "file-123",
      drivePath: "proj-1/photos/photo-123"
    };
  });

  const formData = new FormData();
  formData.append("photoId", "photo-123");
  formData.append("objectCode", "N-1");
  formData.append("objectType", "NODE");
  formData.append("mediaType", "IMAGE");
  formData.append("mimeType", "image/jpeg");
  formData.append("original", new File(["originalBytes"], "photo.jpg", { type: "image/jpeg" }));

  const req = new Request("http://localhost/api/projects/proj-1/media", {
    method: "POST",
    headers: { "Authorization": "Bearer valid-token" },
    body: formData
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await POST(req as any, context);
  assert.strictEqual(res.status, 200);
  const body = await res.json();
  assert.strictEqual(body.success, true);
  assert.strictEqual(body.data.remoteUrl, "https://drive.google.com/uc?export=view&id=file-123");
  assert.strictEqual(receivedInput.rootFolderId, "project-folder-123");
  assert.strictEqual(receivedInput.projectName, "Du an A");
});

test("driveFileIdFromUrl extracts Google Drive file id", () => {
  assert.strictEqual(driveFileIdFromUrl("https://drive.google.com/uc?export=view&id=file-123"), "file-123");
  assert.strictEqual(driveFileIdFromUrl("https://drive.google.com/file/d/file-456/view"), "file-456");
  assert.strictEqual(driveFileIdFromUrl(""), "");
});

test("GET /api/projects/[projectId]/media - Missing Firebase token returns 401", async () => {
  const req = new Request("http://localhost/api/projects/proj-1/media?photoId=photo-1", { method: "GET" });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await GET(req as any, context);
  assert.strictEqual(res.status, 401);
});

test("GET /api/projects/[projectId]/media - Valid request streams image bytes", async () => {
  setAdminAuthMock({
    verifyIdToken: async () => ({ uid: "user-member" })
  });
  setAdminDbMock(projectDbMock(
    true,
    { mediaStorageFolderId: "project-folder-123" },
    { isActive: true },
    {
      "photo-1": {
        remoteUrl: "https://drive.google.com/uc?export=view&id=file-123"
      }
    }
  ));

  const mockDrive = new MockDrive();
  setDriveClientMock(mockDrive);
  mockDrive.fileBodies.set("file-123", {
    bytes: Buffer.from("image-content"),
    contentType: "image/jpeg"
  });

  const req = new Request("http://localhost/api/projects/proj-1/media?photoId=photo-1", {
    method: "GET",
    headers: { Authorization: "Bearer valid-token" }
  });
  const context = { params: Promise.resolve({ projectId: "proj-1" }) };
  const res = await GET(req as any, context);
  assert.strictEqual(res.status, 200);
  assert.strictEqual(res.headers.get("content-type"), "image/jpeg");
  const body = Buffer.from(await res.arrayBuffer()).toString("utf8");
  assert.strictEqual(body, "image-content");
});

class MockDrive {
  db = new Map<string, { id: string; name: string; mimeType: string; parents: string[] }>();
  fileBodies = new Map<string, { bytes: Buffer; contentType: string }>();
  idCounter = 0;

  files = {
    list: async (params: any) => {
      const q = params.q || "";
      const parentIdMatch = q.match(/'([^']+)' in parents/);
      const nameMatch = q.match(/name = '([^']+)'/);
      const mimeTypeMatch = q.match(/mimeType = '([^']+)'/);
      const parentId = parentIdMatch ? parentIdMatch[1] : null;
      const name = nameMatch ? nameMatch[1] : null;
      const mimeType = mimeTypeMatch ? mimeTypeMatch[1] : null;

      const matchedFiles = Array.from(this.db.values()).filter((file) => {
        if (parentId && !file.parents.includes(parentId)) return false;
        if (name && file.name !== name) return false;
        if (mimeType && mimeType.startsWith("!") && file.mimeType === mimeType.slice(1)) return false;
        if (mimeType && !mimeType.startsWith("!") && file.mimeType !== mimeType) return false;
        return true;
      });
      return { data: { files: matchedFiles } };
    },
    create: async (params: any) => {
      const name = params.requestBody?.name || "unnamed";
      const mimeType = params.requestBody?.mimeType || params.media?.mimeType || "application/octet-stream";
      const parents = params.requestBody?.parents || [];
      const id = `id-${++this.idCounter}`;
      this.db.set(id, { id, name, mimeType, parents });
      return { data: { id } };
    },
    update: async (params: any) => {
      return { data: { id: params.fileId } };
    },
    get: async (params: any, options?: any) => {
      const file = this.fileBodies.get(params.fileId);
      if (!file || options?.responseType !== "stream") {
        throw new Error("File not found");
      }
      return {
        data: Readable.from(file.bytes),
        headers: { "content-type": file.contentType }
      };
    }
  };

  permissions = {
    list: async () => {
      return { data: { permissions: [] } };
    },
    create: async () => {
      return { data: { id: "perm-id" } };
    }
  };
}

test("uploadProjectMedia retry updates existing file and does not duplicate", async () => {
  process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID = "root-folder-123";
  process.env.GOOGLE_SERVICE_ACCOUNT_JSON = JSON.stringify({
    type: "service_account",
    project_id: "test",
    private_key: "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3\n-----END PRIVATE KEY-----\n",
    client_email: "email"
  });

  setUploadProjectMediaMock(null);
  const mockDrive = new MockDrive();
  setDriveClientMock(mockDrive);

  let updateCount = 0;
  const originalUpdate = mockDrive.files.update;
  mockDrive.files.update = async (params: any) => {
    updateCount++;
    return originalUpdate.call(mockDrive.files, params);
  };

  const input = {
    projectId: "proj-1",
    projectName: "Du an Retry",
    photoId: "photo-retry",
    objectType: "NODE" as const,
    objectCode: "N-1",
    mediaType: "IMAGE" as const,
    mimeType: "image/jpeg",
    capturedAtEpochMs: Date.parse("2026-07-09T10:11:12+07:00"),
    original: {
      bytes: Buffer.from("original-content"),
      extension: "jpg"
    }
  };

  const res1 = await uploadProjectMedia(input);
  const res2 = await uploadProjectMedia(input);

  assert.ok(res1.remoteUrl);
  assert.ok(res2.remoteUrl);
  assert.strictEqual(res1.driveFileId, res2.driveFileId);
  assert.strictEqual(updateCount, 1);
});

test("uploadProjectMedia uses configured root folder when provided", async () => {
  setUploadProjectMediaMock(null);
  const mockDrive = new MockDrive();
  setDriveClientMock(mockDrive);

  await uploadProjectMedia({
    projectId: "proj-1",
    projectName: "Du an Root",
    rootFolderId: "project-root-999",
    photoId: "photo-project-root",
    objectType: "NODE",
    objectCode: "N-1",
    mediaType: "IMAGE",
    mimeType: "image/jpeg",
    capturedAtEpochMs: Date.parse("2026-07-09T10:11:12+07:00"),
    original: {
      bytes: Buffer.from("content"),
      extension: "jpg"
    }
  });

  const firstProjectFolder = Array.from(mockDrive.db.values()).find((file) => file.name === "Du an Root");
  assert.ok(firstProjectFolder);
  assert.deepStrictEqual(firstProjectFolder.parents, ["project-root-999"]);
});

test("uploadProjectMedia accepts GOOGLE_DRIVE_ROOT_FOLDER_URL from env", async () => {
  delete process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID;
  process.env.GOOGLE_DRIVE_ROOT_FOLDER_URL = "https://drive.google.com/drive/u/3/folders/1Kr-ZpdbFlh82CgTr-nvXv1Rpl4b-mJRN";
  setUploadProjectMediaMock(null);
  const mockDrive = new MockDrive();
  setDriveClientMock(mockDrive);

  await uploadProjectMedia({
    projectId: "proj-1",
    projectName: "Du an URL",
    photoId: "photo-env-url",
    objectType: "NODE",
    objectCode: "N-1",
    mediaType: "IMAGE",
    mimeType: "image/jpeg",
    capturedAtEpochMs: Date.parse("2026-07-09T10:11:12+07:00"),
    original: {
      bytes: Buffer.from("content"),
      extension: "jpg"
    }
  });

  const firstProjectFolder = Array.from(mockDrive.db.values()).find((file) => file.name === "Du an URL");
  assert.ok(firstProjectFolder);
  assert.deepStrictEqual(firstProjectFolder.parents, ["1Kr-ZpdbFlh82CgTr-nvXv1Rpl4b-mJRN"]);
  delete process.env.GOOGLE_DRIVE_ROOT_FOLDER_URL;
});

test("uploadProjectMedia accepts quoted GOOGLE_DRIVE_ROOT_FOLDER_ID from env", async () => {
  process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID = "'quoted-folder-12345'";
  delete process.env.GOOGLE_DRIVE_ROOT_FOLDER_URL;
  setUploadProjectMediaMock(null);
  const mockDrive = new MockDrive();
  setDriveClientMock(mockDrive);

  await uploadProjectMedia({
    projectId: "proj-1",
    projectName: "Du an Quoted",
    photoId: "photo-quoted-env",
    objectType: "NODE",
    objectCode: "N-1",
    mediaType: "IMAGE",
    mimeType: "image/jpeg",
    capturedAtEpochMs: Date.parse("2026-07-09T10:11:12+07:00"),
    original: {
      bytes: Buffer.from("content"),
      extension: "jpg"
    }
  });

  const firstProjectFolder = Array.from(mockDrive.db.values()).find((file) => file.name === "Du an Quoted");
  assert.ok(firstProjectFolder);
  assert.deepStrictEqual(firstProjectFolder.parents, ["quoted-folder-12345"]);
  delete process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID;
});
