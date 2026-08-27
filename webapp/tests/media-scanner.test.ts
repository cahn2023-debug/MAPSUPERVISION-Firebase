import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseMediaFileName,
  scanProjectDriveMedia,
  setDriveClientMock
} from "../lib/google-drive-media";

test("parseMediaFileName parses standard timestamp and segments correctly", () => {
  const fileName = "2026-08-27 14.30.00 - 123 Nguyen Trai - Cot dien bi nghieng.jpg";
  const result = parseMediaFileName(fileName);

  assert.strictEqual(result.extension, "jpg");
  assert.strictEqual(result.address, "123 Nguyen Trai");
  assert.strictEqual(result.captureNote, "Cot dien bi nghieng");
  assert.strictEqual(result.capturedAtEpochMs > 0, true);

  const date = new Date(result.capturedAtEpochMs);
  assert.strictEqual(date.getUTCFullYear(), 2026);
  assert.strictEqual(date.getUTCMonth(), 7); // 0-indexed: August = 7
  assert.strictEqual(date.getUTCDate(), 27);
});

test("parseMediaFileName falls back gracefully for non-standard file names", () => {
  const fileName = "custom_image_dc05.png";
  const createdTime = "2026-08-25T10:00:00.000Z";
  const result = parseMediaFileName(fileName, createdTime);

  assert.strictEqual(result.extension, "png");
  assert.strictEqual(result.address, "custom_image_dc05");
  assert.strictEqual(result.capturedAtEpochMs, new Date(createdTime).getTime());
});

test("scanProjectDriveMedia filters out thumbnails and detects missing photos", async () => {
  const mockDrive = {
    files: {
      list: async (params: any) => {
        if (params.q?.includes("appProperties has")) {
          return { data: { files: [{ id: "proj-folder-1", name: "Project 269" }] } };
        }
        return {
          data: {
            files: [
              {
                id: "file-photo-1",
                name: "2026-08-27 10.00.00 - 123 Hang Bai - Kiem tra mong.jpg",
                mimeType: "image/jpeg",
                createdTime: "2026-08-27T10:00:00Z"
              },
              {
                id: "file-photo-2",
                name: "2026-08-27 11.00.00 - 456 Pho Hue - Hoan thanh lap dat.jpg",
                mimeType: "image/jpeg",
                createdTime: "2026-08-27T11:00:00Z"
              },
              {
                id: "file-photo-thumb",
                name: "2026-08-27 10.00.00__thumb.jpg",
                mimeType: "image/jpeg"
              },
              {
                id: "file-snapshot",
                name: "snapshot_proj1_12345.json",
                mimeType: "application/json"
              }
            ]
          }
        };
      },
      create: async () => ({ data: { id: "mock-created-folder" } })
    }
  };

  const existingPhotos = [
    {
      id: "photo-1",
      remoteUrl: "https://lh3.googleusercontent.com/d/file-photo-1=w1000?authuser=0",
      objectCode: "DC-01"
    }
  ];

  const scanResult = await scanProjectDriveMedia(
    mockDrive as any,
    "proj-1",
    "root-folder",
    "Project 269",
    existingPhotos
  );

  assert.strictEqual(scanResult.totalDriveFiles, 2); // only photo 1 & photo 2 (skips thumb and snapshot)
  assert.strictEqual(scanResult.matchedCount, 1); // photo 1 is matched
  assert.strictEqual(scanResult.discoveredPhotos.length, 1); // photo 2 is missing
  assert.strictEqual(scanResult.discoveredPhotos[0].driveFileId, "file-photo-2");
  assert.strictEqual(scanResult.discoveredPhotos[0].address, "456 Pho Hue");
});
