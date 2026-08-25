import { test } from "node:test";
import assert from "node:assert";
import { sanitizePrivateKey, sanitizeServiceAccount } from "../lib/firebase-admin";

test("sanitizePrivateKey handles CRLF, double-escaped newlines, and surrounding quotes", () => {
  const rawKey = "\"-----BEGIN PRIVATE KEY-----\\r\\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3\\r\\nxyz\\r\\n-----END PRIVATE KEY-----\\r\\n\"";
  const sanitized = sanitizePrivateKey(rawKey);

  assert.ok(sanitized.startsWith("-----BEGIN PRIVATE KEY-----\n"));
  assert.ok(sanitized.endsWith("-----END PRIVATE KEY-----\n"));
  assert.ok(!sanitized.includes("\r"));
  assert.ok(!sanitized.includes("\\n"));
  assert.ok(sanitized.includes("MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3\nxyz"));
});

test("sanitizePrivateKey formats multi-space separated PEM bodies cleanly", () => {
  const rawKey = "-----BEGIN PRIVATE KEY-----  line1   line2   line3  -----END PRIVATE KEY-----";
  const sanitized = sanitizePrivateKey(rawKey);

  assert.strictEqual(
    sanitized,
    "-----BEGIN PRIVATE KEY-----\nline1\nline2\nline3\n-----END PRIVATE KEY-----\n"
  );
});

test("sanitizeServiceAccount normalizes parsed json private_key", () => {
  const sa = {
    project_id: "demo-project",
    client_email: "test@demo-project.iam.gserviceaccount.com",
    private_key: "-----BEGIN PRIVATE KEY-----\\nline1\\nline2\\n-----END PRIVATE KEY-----\\n"
  };

  const sanitized = sanitizeServiceAccount(sa);
  assert.strictEqual(
    sanitized.private_key,
    "-----BEGIN PRIVATE KEY-----\nline1\nline2\n-----END PRIVATE KEY-----\n"
  );
});
