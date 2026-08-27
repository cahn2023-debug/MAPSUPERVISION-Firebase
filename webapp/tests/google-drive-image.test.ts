import { test } from "node:test";
import assert from "node:assert/strict";
import { driveFileIdFromUrl, googleDriveImageUrl, imageSourceUrl } from "../lib/google-drive-image";

test("googleDriveImageUrl uses the default width and authuser", () => {
  assert.strictEqual(
    googleDriveImageUrl("1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB"),
    "https://lh3.googleusercontent.com/d/1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB=w1000?authuser=0"
  );
});

test("googleDriveImageUrl uses the component width", () => {
  assert.strictEqual(
    googleDriveImageUrl("file-123", 600),
    "https://lh3.googleusercontent.com/d/file-123=w600?authuser=0"
  );
});

test("imageSourceUrl accepts raw IDs and legacy Google Drive URLs", () => {
  assert.strictEqual(
    imageSourceUrl("file-123", 600),
    "https://lh3.googleusercontent.com/d/file-123=w600?authuser=0"
  );
  assert.strictEqual(
    imageSourceUrl("https://drive.google.com/uc?export=view&id=file-123"),
    "https://lh3.googleusercontent.com/d/file-123=w1000?authuser=0"
  );
});

test("imageSourceUrl preserves non-Google image URLs", () => {
  const url = "https://cdn.example.com/photo.jpg";
  assert.strictEqual(imageSourceUrl(url), url);
});

test("invalid image IDs do not create a Google URL", () => {
  assert.strictEqual(googleDriveImageUrl(""), undefined);
  assert.strictEqual(googleDriveImageUrl("not a file id"), undefined);
  assert.strictEqual(imageSourceUrl(""), undefined);
});

test("driveFileIdFromUrl extracts legacy Drive IDs and lh3 IDs", () => {
  assert.strictEqual(driveFileIdFromUrl("https://drive.google.com/file/d/file-456/view"), "file-456");
  assert.strictEqual(
    driveFileIdFromUrl("https://lh3.googleusercontent.com/d/1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB=w1000?authuser=0"),
    "1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB"
  );
  assert.strictEqual(
    imageSourceUrl("https://lh3.googleusercontent.com/d/1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB=w600?authuser=0"),
    "https://lh3.googleusercontent.com/d/1HuIw8yd_XRx3MvTCkPBOokZ97EFxD9uB=w1000?authuser=0"
  );
});
