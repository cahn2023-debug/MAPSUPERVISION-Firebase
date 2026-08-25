import test from "node:test";
import assert from "node:assert/strict";
import {
  anonymizeBenchmarkValue,
  assertAnonymizedBenchmarkValue,
  assertSafeBenchmarkArtifact,
  percentile,
  projectTelemetry,
  runBenchmark,
  summarizeJourney,
  telemetryFields,
  validatePerformanceProfile,
  FirestoreOperationCounter,
  parsePerformanceTestProfile,
  type BenchmarkAdapters,
  type PerformanceTestProfile,
  type TelemetryEvent
} from "../lib/performance-benchmark";

const validProfile: PerformanceTestProfile = {
  schemaVersion: 1,
  profileId: "web-backend-performance-v1",
  frozenAt: "2026-08-25T00:00:00.000Z",
  appVersion: "0.1.0",
  host: {
    operatingSystem: "Microsoft Windows 10 Pro",
    architecture: "x64",
    cpu: "Intel64 Family 6 Model 141",
    nodeVersion: "24"
  },
  network: {
    probeTarget: "firestore-api",
    sampleCount: 10,
    maximumFailureRate: 0.1,
    latencyEnvelopeMs: { p50Min: 0, p50Max: 5000, p95Min: 0, p95Max: 10000 }
  },
  build: {
    revisionMethod: "git-head-plus-files-sha256",
    requiredFiles: ["webapp/lib/performance-benchmark.ts"]
  },
  emulator: {
    projectId: "mapsupervision-benchmark",
    firestoreHost: "127.0.0.1:8080",
    authHost: "127.0.0.1:9099"
  },
  runPolicy: {
    warmupRuns: 2,
    measuredRuns: 20,
    minimumSuccessfulRuns: 19,
    maximumEnvironmentErrorRate: 0.05,
    concurrency: 1
  },
  datasets: {
    largeSynthetic: {
      reference: "large-synthetic-v1",
      fixtureVersion: 1,
      seed: 20260825,
      cardinality: {
        projects: 40,
        mapNodes: 10000,
        mapRoutes: 2500,
        tasks: 12000,
        diaries: 8000,
        mediaItems: 16000,
        adminRows: 2000
      }
    },
    anonymizedReal: {
      reference: "anonymized-real-v1",
      sourceAlias: "selected-active-largest-v1",
      expectedDocumentCount: 616,
      persistedInRepository: false
    }
  },
  journeys: ["open-or-switch-project", "business-operation", "media-upload-preview"],
  validityRules: [
    "Use the same profile file before and after optimization.",
    "Keep environment failures separate from application failures."
  ]
};

function event(overrides: Partial<TelemetryEvent> = {}): TelemetryEvent {
  return {
    journey: "open-or-switch-project",
    route: "journey:total",
    durationMs: 10,
    success: true,
    errorCategory: "none",
    firestoreOperationCount: 1,
    payloadSizeBucket: "large",
    timestamp: "2026-08-25T00:00:00.000Z",
    ...overrides
  };
}

test("telemetry projection emits the exact allowlist and excludes sensitive input", () => {
  const projected = projectTelemetry({
    ...event(),
    token: "Bearer secret",
    email: "person@example.com",
    businessContent: "inspection details",
    mediaUrl: "https://drive.example/media",
    userId: "raw-user-id",
    projectId: "raw-project-id"
  });

  assert.deepEqual(Object.keys(projected), telemetryFields);
  assert.deepEqual(projected, event());
});

test("telemetry route values are constrained so URLs and raw identifiers cannot leak", () => {
  assert.throws(
    () => projectTelemetry({ ...event(), route: "https://drive.example/raw-project-id" }),
    /route is not allowlisted/
  );
});

test("artifact safety check rejects prohibited keys and secret-like string values", () => {
  assert.throws(
    () => assertSafeBenchmarkArtifact({ report: { projectId: "raw-project-id" } }),
    /prohibited key projectId/
  );
  assert.throws(
    () => assertSafeBenchmarkArtifact({ report: { note: "Bearer very-secret-token" } }),
    /prohibited value/
  );
});

test("source values are anonymized before emulator persistence", () => {
  const source = {
    id: "raw-project-id",
    owner: "person@example.com",
    remoteUrl: "https://drive.example/raw-file-id",
    note: "private inspection business content",
    nested: [{ count: 42, active: true }],
    customMap: { "raw-user-key": "raw-dynamic-value" }
  };

  const anonymized = anonymizeBenchmarkValue(source, 10);
  const serialized = JSON.stringify(anonymized);

  assert.equal(serialized.includes("raw-project-id"), false);
  assert.equal(serialized.includes("person@example.com"), false);
  assert.equal(serialized.includes("https://"), false);
  assert.equal(serialized.includes("private inspection"), false);
  assert.equal(serialized.includes("raw-user-key"), false);
  assert.equal(serialized.includes("raw-dynamic-value"), false);
  assert.equal((anonymized as typeof source).nested.length, 1);
  assert.doesNotThrow(() => assertAnonymizedBenchmarkValue(anonymized));
});

test("Firestore operation counter accounts for route-internal reads and writes", () => {
  const counter = new FirestoreOperationCounter();
  counter.read(2);
  counter.write();
  counter.read(3);

  assert.equal(counter.total, 6);
});

test("percentiles use the nearest-rank method", () => {
  assert.equal(percentile([5, 1, 4, 2, 3], 0.5), 3);
  assert.equal(percentile([5, 1, 4, 2, 3], 0.95), 5);
  assert.equal(percentile([], 0.95), null);
});

test("journey summary reports latency, operation counts, and separate error rates", () => {
  const events = [
    event({ durationMs: 10, firestoreOperationCount: 2 }),
    event({ durationMs: 20, firestoreOperationCount: 3 }),
    event({ durationMs: 30, success: false, errorCategory: "application", firestoreOperationCount: 4 }),
    event({ durationMs: 40, success: false, errorCategory: "environment", firestoreOperationCount: 5 }),
    event({ route: "api:media-upload", durationMs: 7, firestoreOperationCount: 0 }),
    event({ route: "drive:media-upload", durationMs: 11, firestoreOperationCount: 0 })
  ];

  const summary = summarizeJourney(events, "open-or-switch-project");

  assert.deepEqual(summary.durationMs, { p50: 20, p95: 40 });
  assert.deepEqual(summary.firestoreOperationCount, { total: 14, p50: 3, p95: 5 });
  assert.equal(summary.errorRate, 0.5);
  assert.equal(summary.applicationErrorRate, 0.25);
  assert.equal(summary.environmentErrorRate, 0.25);
  assert.deepEqual(summary.apiDurationMs, { p50: 7, p95: 7 });
  assert.deepEqual(summary.driveDurationMs, { p50: 11, p95: 11 });
});

test("performance profile validation enforces all frozen reproducibility fields", () => {
  assert.deepEqual(validatePerformanceProfile(validProfile), []);

  const invalid = structuredClone(validProfile);
  invalid.host.cpu = "";
  invalid.runPolicy.minimumSuccessfulRuns = invalid.runPolicy.measuredRuns + 1;
  invalid.datasets.largeSynthetic.cardinality.mapNodes = 0;
  invalid.journeys = ["open-or-switch-project"];

  assert.deepEqual(validatePerformanceProfile(invalid), [
    "host.cpu must be non-empty",
    "runPolicy.minimumSuccessfulRuns must be between 1 and measuredRuns",
    "datasets.largeSynthetic.cardinality.mapNodes must be a positive integer",
    "journeys must contain each approved journey exactly once"
  ]);
});

test("performance profile validation rejects incomplete identity, network, and dataset rules", () => {
  const invalid = structuredClone(validProfile);
  invalid.profileId = "";
  invalid.frozenAt = "not-a-date";
  invalid.appVersion = "";
  invalid.network.sampleCount = 0;
  invalid.network.maximumFailureRate = 2;
  invalid.datasets.largeSynthetic.reference = "";
  invalid.datasets.anonymizedReal.sourceAlias = "";
  invalid.validityRules = [];

  assert.deepEqual(validatePerformanceProfile(invalid), [
    "profileId must be non-empty",
    "frozenAt must be an ISO-8601 timestamp",
    "appVersion must be non-empty",
    "network.sampleCount must be a positive integer",
    "network.maximumFailureRate must be between 0 and 1",
    "datasets.largeSynthetic.reference must be non-empty",
    "datasets.anonymizedReal.sourceAlias must be non-empty",
    "validityRules must not be empty"
  ]);
});

test("runtime profile parsing fails with a structured error for malformed JSON", () => {
  assert.throws(() => parsePerformanceTestProfile({ network: null }), /Performance profile host must be an object/);
  const malformed = structuredClone(validProfile) as unknown as Record<string, unknown>;
  malformed.profileId = 42;
  assert.throws(() => parsePerformanceTestProfile(malformed), /Invalid performance profile fields: profileId/);
});

test("benchmark executes all three journeys through injectable adapters", async () => {
  const calls: string[] = [];
  let elapsedMs = 0;
  const adapter = async ({ route }: { route: string }) => {
    calls.push(route);
    elapsedMs += route.startsWith("drive:") ? 5 : 2;
    return { success: true as const, errorCategory: "none" as const };
  };
  const adapters: BenchmarkAdapters = {
    firestore: async context => ({ ...(await adapter(context)), firestoreOperationCount: 3 }),
    api: adapter,
    drive: adapter
  };
  const profile = structuredClone(validProfile);
  profile.runPolicy = {
    ...profile.runPolicy,
    warmupRuns: 0,
    measuredRuns: 2,
    minimumSuccessfulRuns: 2
  };

  const result = await runBenchmark(profile, "large-synthetic-v1", adapters, {
    now: () => new Date("2026-08-25T00:00:00.000Z"),
    monotonicNow: () => elapsedMs
  });

  assert.deepEqual(Object.keys(result.summaries), profile.journeys);
  assert.equal(result.events.filter(item => item.route === "journey:total").length, 6);
  assert.ok(calls.some(route => route.startsWith("firestore:")));
  assert.ok(calls.some(route => route.startsWith("api:")));
  assert.ok(calls.some(route => route.startsWith("drive:")));
  assert.equal(result.summaries["open-or-switch-project"].durationMs.p95, 4);
  assert.equal(result.summaries["media-upload-preview"].durationMs.p95, 6);
  assert.equal(result.summaries["media-upload-preview"].driveDurationMs.p95, 5);
  assert.doesNotThrow(() => assertSafeBenchmarkArtifact(result));
});

test("benchmark adapters execute against the Firestore emulator", {
  skip: !process.env.FIRESTORE_EMULATOR_HOST
}, async () => {
  const { initializeApp, deleteApp } = await import("firebase-admin/app");
  const { getFirestore } = await import("firebase-admin/firestore");
  const app = initializeApp({ projectId: "performance-benchmark-test" }, `performance-${Date.now()}`);
  const db = getFirestore(app);
  const collection = db.collection("benchmark_touch");
  await collection.doc("seed").set({ value: 1 });
  const execute = async () => {
    const snapshot = await collection.get();
    await collection.doc("touched").set({ count: snapshot.size });
    await new Promise(resolve => setTimeout(resolve, 2));
    return { firestoreOperationCount: snapshot.size + 1 };
  };
  const profile = structuredClone(validProfile);
  profile.runPolicy = { ...profile.runPolicy, warmupRuns: 0, measuredRuns: 1, minimumSuccessfulRuns: 1 };

  try {
    const result = await runBenchmark(profile, "large-synthetic-v1", {
      firestore: execute,
      api: execute,
      drive: execute
    });
    assert.equal((await collection.doc("touched").get()).exists, true);
    assert.ok(result.summaries["open-or-switch-project"].firestoreOperationCount.total > 0);
    assert.ok((result.summaries["media-upload-preview"].durationMs.p95 ?? 0) > 0);
  } finally {
    await deleteApp(app);
  }
});
