export const approvedJourneys = [
  "open-or-switch-project",
  "business-operation",
  "media-upload-preview"
] as const;

export type Journey = typeof approvedJourneys[number];

export const approvedRoutes = [
  "journey:total",
  "firestore:project-core",
  "firestore:map-geometry",
  "firestore:business-query",
  "firestore:business-write",
  "firestore:media-metadata",
  "api:media-upload",
  "drive:media-upload",
  "api:media-preview",
  "drive:media-preview"
] as const;

export type BenchmarkRoute = typeof approvedRoutes[number];
export type ErrorCategory = "none" | "application" | "environment";
export type PayloadSizeBucket = "small" | "medium" | "large" | "unknown";

export const telemetryFields = [
  "journey",
  "route",
  "durationMs",
  "success",
  "errorCategory",
  "firestoreOperationCount",
  "payloadSizeBucket",
  "timestamp"
] as const;

export type TelemetryEvent = {
  journey: Journey;
  route: BenchmarkRoute;
  durationMs: number;
  success: boolean;
  errorCategory: ErrorCategory;
  firestoreOperationCount: number;
  payloadSizeBucket: PayloadSizeBucket;
  timestamp: string;
};

export type DatasetCardinality = {
  projects: number;
  mapNodes: number;
  mapRoutes: number;
  tasks: number;
  diaries: number;
  mediaItems: number;
  adminRows: number;
};

export type PerformanceTestProfile = {
  schemaVersion: 1;
  profileId: string;
  frozenAt: string;
  appVersion: string;
  host: {
    operatingSystem: string;
    architecture: string;
    cpu: string;
    nodeVersion: string;
  };
  network: {
    probeTarget: "firestore-api";
    sampleCount: number;
    maximumFailureRate: number;
    latencyEnvelopeMs: {
      p50Min: number;
      p50Max: number;
      p95Min: number;
      p95Max: number;
    };
  };
  build: {
    revisionMethod: "git-head-plus-files-sha256";
    requiredFiles: string[];
  };
  emulator: {
    projectId: string;
    firestoreHost: string;
    authHost: string;
  };
  runPolicy: {
    warmupRuns: number;
    measuredRuns: number;
    minimumSuccessfulRuns: number;
    maximumEnvironmentErrorRate: number;
    concurrency: 1;
  };
  datasets: {
    largeSynthetic: {
      reference: string;
      fixtureVersion: 1;
      seed: number;
      cardinality: DatasetCardinality;
    };
    anonymizedReal: {
      reference: string;
      sourceAlias: string;
      expectedDocumentCount: number;
      persistedInRepository: false;
    };
  };
  journeys: Journey[];
  validityRules: string[];
};

export type BenchmarkStageContext = {
  journey: Journey;
  route: Exclude<BenchmarkRoute, "journey:total">;
  runIndex: number;
  warmup: boolean;
  datasetReference: string;
  payloadSizeBucket: PayloadSizeBucket;
};

export type BenchmarkStageResult = {
  success?: boolean;
  errorCategory?: ErrorCategory;
  firestoreOperationCount?: number;
};

export type BenchmarkAdapter = (context: BenchmarkStageContext) => Promise<BenchmarkStageResult>;

export type BenchmarkAdapters = {
  firestore: BenchmarkAdapter;
  api: BenchmarkAdapter;
  drive: BenchmarkAdapter;
};

export class FirestoreOperationCounter {
  #total = 0;

  read(count = 1): void {
    this.add(count);
  }

  write(count = 1): void {
    this.add(count);
  }

  get total(): number {
    return this.#total;
  }

  private add(count: number): void {
    if (!Number.isInteger(count) || count < 0) throw new Error("Firestore operation count must be a non-negative integer");
    this.#total += count;
  }
}

type PercentilePair = { p50: number | null; p95: number | null };

export type JourneySummary = {
  runCount: number;
  successCount: number;
  applicationErrorCount: number;
  environmentErrorCount: number;
  errorRate: number;
  applicationErrorRate: number;
  environmentErrorRate: number;
  durationMs: PercentilePair;
  firestoreOperationCount: PercentilePair & { total: number };
  apiDurationMs: PercentilePair;
  driveDurationMs: PercentilePair;
};

export type BenchmarkResult = {
  schemaVersion: 1;
  profileReference: string;
  datasetReference: string;
  generatedAt: string;
  valid: boolean;
  invalidReasons: string[];
  events: TelemetryEvent[];
  summaries: Record<Journey, JourneySummary>;
};

const routeSet = new Set<string>(approvedRoutes);
const journeySet = new Set<string>(approvedJourneys);
const errorCategories = new Set<string>(["none", "application", "environment"]);
const payloadBuckets = new Set<string>(["small", "medium", "large", "unknown"]);

function finiteNonNegative(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0) {
    throw new Error(`${field} must be a finite non-negative number`);
  }
  return value;
}

function stringField(value: unknown, field: string): string {
  if (typeof value !== "string" || !value.trim()) throw new Error(`${field} must be non-empty`);
  return value;
}

export function projectTelemetry(input: Record<string, unknown>): TelemetryEvent {
  const journey = stringField(input.journey, "journey");
  const route = stringField(input.route, "route");
  const errorCategory = stringField(input.errorCategory, "errorCategory");
  const payloadSizeBucket = stringField(input.payloadSizeBucket, "payloadSizeBucket");
  if (!journeySet.has(journey)) throw new Error("journey is not allowlisted");
  if (!routeSet.has(route)) throw new Error("route is not allowlisted");
  if (!errorCategories.has(errorCategory)) throw new Error("errorCategory is not allowlisted");
  if (!payloadBuckets.has(payloadSizeBucket)) throw new Error("payloadSizeBucket is not allowlisted");
  if (typeof input.success !== "boolean") throw new Error("success must be boolean");
  const timestamp = stringField(input.timestamp, "timestamp");
  if (!Number.isFinite(Date.parse(timestamp))) throw new Error("timestamp must be ISO-8601");

  return {
    journey: journey as Journey,
    route: route as BenchmarkRoute,
    durationMs: finiteNonNegative(input.durationMs, "durationMs"),
    success: input.success,
    errorCategory: errorCategory as ErrorCategory,
    firestoreOperationCount: finiteNonNegative(input.firestoreOperationCount, "firestoreOperationCount"),
    payloadSizeBucket: payloadSizeBucket as PayloadSizeBucket,
    timestamp
  };
}

const prohibitedKey = /^(?:token|authorization|email|content|businessContent|mediaUrl|url|uid|userId|projectId|credential|secret|driveId)$/i;
const prohibitedValue = /(?:bearer\s+\S+|https?:\/\/|-----BEGIN [A-Z ]*PRIVATE KEY-----|\b[^\s@]+@[^\s@]+\.[^\s@]+\b)/i;

export function assertSafeBenchmarkArtifact(value: unknown, path = "artifact"): void {
  if (typeof value === "string") {
    if (prohibitedValue.test(value)) throw new Error(`${path} contains a prohibited value`);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertSafeBenchmarkArtifact(item, `${path}[${index}]`));
    return;
  }
  if (!value || typeof value !== "object") return;
  for (const [key, nested] of Object.entries(value)) {
    if (prohibitedKey.test(key)) throw new Error(`${path} contains prohibited key ${key}`);
    assertSafeBenchmarkArtifact(nested, `${path}.${key}`);
  }
}

const preservedBenchmarkSchemaKeys = new Set([
  "data", "id", "projectId", "updatedAtEpochMs", "createdAtEpochMs", "isDeleted", "name",
  "status", "type", "value", "nested", "customMap", "code", "objectCode", "objectType", "mediaType",
  "geometry", "coordinates", "latitude", "longitude", "lat", "lng", "points", "properties", "features",
  "title", "description", "note", "notes", "address", "quantity", "unit", "category", "priority",
  "startDate", "endDate", "date", "progress", "percentage", "items", "children", "tags", "members"
]);

export function anonymizeBenchmarkValue(value: unknown, ordinal: number): unknown {
  if (Array.isArray(value)) {
    return value.map((item, index) => anonymizeBenchmarkValue(item, ordinal + index));
  }
  if (value instanceof Date) return 1700000000000 + ordinal;
  if (value && typeof value === "object") {
    const source = value as Record<string, unknown>;
    if (typeof source.toMillis === "function") return 1700000000000 + ordinal;
    return Object.fromEntries(Object.entries(source).map(([key, nested], index) => [
      preservedBenchmarkSchemaKeys.has(key) ? key : `field_${index + 1}`,
      anonymizeBenchmarkValue(nested, ordinal + index)
    ]));
  }
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return Number.isFinite(value) ? ordinal : 0;
  if (typeof value === "string") return "x".repeat(Math.max(1, Math.min(value.length, 256)));
  return null;
}

export function assertAnonymizedBenchmarkValue(value: unknown, path = "snapshot"): void {
  if (typeof value === "string") {
    if (!/^x{1,256}$/.test(value)) throw new Error(`${path} contains a non-anonymized string`);
    return;
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw new Error(`${path} contains a non-finite number`);
    return;
  }
  if (typeof value === "boolean" || value == null) return;
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertAnonymizedBenchmarkValue(item, `${path}[${index}]`));
    return;
  }
  if (typeof value !== "object") throw new Error(`${path} contains an unsupported value`);
  for (const [key, nested] of Object.entries(value as Record<string, unknown>)) {
    if (!preservedBenchmarkSchemaKeys.has(key) && !/^field_\d+$/.test(key)) {
      throw new Error(`${path} contains a non-anonymized key`);
    }
    assertAnonymizedBenchmarkValue(nested, `${path}.${key}`);
  }
}

export function percentile(values: number[], quantile: number): number | null {
  if (values.length === 0) return null;
  if (!Number.isFinite(quantile) || quantile <= 0 || quantile > 1) {
    throw new Error("quantile must be greater than 0 and at most 1");
  }
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.ceil(sorted.length * quantile) - 1];
}

function percentiles(values: number[]): PercentilePair {
  return { p50: percentile(values, 0.5), p95: percentile(values, 0.95) };
}

export function summarizeJourney(events: TelemetryEvent[], journey: Journey): JourneySummary {
  const totals = events.filter(item => item.journey === journey && item.route === "journey:total");
  const failures = totals.filter(item => !item.success);
  const applicationErrors = failures.filter(item => item.errorCategory === "application");
  const environmentErrors = failures.filter(item => item.errorCategory === "environment");
  const divisor = totals.length || 1;
  const firestoreCounts = totals.map(item => item.firestoreOperationCount);
  const apiDurations = events
    .filter(item => item.journey === journey && item.route.startsWith("api:"))
    .map(item => item.durationMs);
  const driveDurations = events
    .filter(item => item.journey === journey && item.route.startsWith("drive:"))
    .map(item => item.durationMs);

  return {
    runCount: totals.length,
    successCount: totals.length - failures.length,
    applicationErrorCount: applicationErrors.length,
    environmentErrorCount: environmentErrors.length,
    errorRate: failures.length / divisor,
    applicationErrorRate: applicationErrors.length / divisor,
    environmentErrorRate: environmentErrors.length / divisor,
    durationMs: percentiles(totals.map(item => item.durationMs)),
    firestoreOperationCount: {
      total: firestoreCounts.reduce((sum, count) => sum + count, 0),
      ...percentiles(firestoreCounts)
    },
    apiDurationMs: percentiles(apiDurations),
    driveDurationMs: percentiles(driveDurations)
  };
}

function positiveInteger(value: unknown): boolean {
  return typeof value === "number" && Number.isInteger(value) && value > 0;
}

function recordValue(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

export function parsePerformanceTestProfile(value: unknown): PerformanceTestProfile {
  if (!recordValue(value)) throw new Error("Performance profile must be an object");
  const requiredObjects = ["host", "network", "build", "emulator", "runPolicy", "datasets"];
  for (const field of requiredObjects) {
    if (!recordValue(value[field])) throw new Error(`Performance profile ${field} must be an object`);
  }
  const datasets = value.datasets as Record<string, unknown>;
  if (!recordValue(datasets.largeSynthetic) || !recordValue(datasets.anonymizedReal)) {
    throw new Error("Performance profile datasets are incomplete");
  }
  const synthetic = datasets.largeSynthetic as Record<string, unknown>;
  if (!recordValue(synthetic.cardinality)) throw new Error("Performance profile synthetic cardinality must be an object");
  const network = value.network as Record<string, unknown>;
  if (!recordValue(network.latencyEnvelopeMs)) throw new Error("Performance profile network latency envelope must be an object");
  const build = value.build as Record<string, unknown>;
  if (!Array.isArray(build.requiredFiles) || !build.requiredFiles.every(file => typeof file === "string")) {
    throw new Error("Performance profile build.requiredFiles must be a string array");
  }
  if (!Array.isArray(value.journeys) || !Array.isArray(value.validityRules)) {
    throw new Error("Performance profile journeys and validityRules must be arrays");
  }
  const invalidFields: string[] = [];
  const requireString = (source: Record<string, unknown>, field: string, path: string) => {
    if (typeof source[field] !== "string") invalidFields.push(path);
  };
  requireString(value, "profileId", "profileId");
  requireString(value, "frozenAt", "frozenAt");
  requireString(value, "appVersion", "appVersion");
  const host = value.host as Record<string, unknown>;
  for (const field of ["operatingSystem", "architecture", "cpu", "nodeVersion"]) requireString(host, field, `host.${field}`);
  const networkData = value.network as Record<string, unknown>;
  if (networkData.probeTarget !== "firestore-api") invalidFields.push("network.probeTarget");
  if (!Number.isFinite(networkData.sampleCount)) invalidFields.push("network.sampleCount");
  if (!Number.isFinite(networkData.maximumFailureRate)) invalidFields.push("network.maximumFailureRate");
  const envelope = networkData.latencyEnvelopeMs as Record<string, unknown>;
  for (const field of ["p50Min", "p50Max", "p95Min", "p95Max"]) if (!Number.isFinite(envelope[field])) invalidFields.push(`network.latencyEnvelopeMs.${field}`);
  const buildData = value.build as Record<string, unknown>;
  requireString(buildData, "revisionMethod", "build.revisionMethod");
  const emulator = value.emulator as Record<string, unknown>;
  for (const field of ["projectId", "firestoreHost", "authHost"]) requireString(emulator, field, `emulator.${field}`);
  const runPolicy = value.runPolicy as Record<string, unknown>;
  for (const field of ["warmupRuns", "measuredRuns", "minimumSuccessfulRuns", "maximumEnvironmentErrorRate"]) if (!Number.isFinite(runPolicy[field])) invalidFields.push(`runPolicy.${field}`);
  const syntheticData = datasets.largeSynthetic as Record<string, unknown>;
  requireString(syntheticData, "reference", "datasets.largeSynthetic.reference");
  if (!Number.isFinite(syntheticData.seed)) invalidFields.push("datasets.largeSynthetic.seed");
  const anonymized = datasets.anonymizedReal as Record<string, unknown>;
  requireString(anonymized, "reference", "datasets.anonymizedReal.reference");
  requireString(anonymized, "sourceAlias", "datasets.anonymizedReal.sourceAlias");
  if (!Number.isFinite(anonymized.expectedDocumentCount)) invalidFields.push("datasets.anonymizedReal.expectedDocumentCount");
  if (!(value.journeys as unknown[]).every(item => typeof item === "string")) invalidFields.push("journeys");
  if (!(value.validityRules as unknown[]).every(item => typeof item === "string")) invalidFields.push("validityRules");
  if (invalidFields.length > 0) throw new Error(`Invalid performance profile fields: ${invalidFields.join(", ")}`);
  const profile = value as PerformanceTestProfile;
  const errors = validatePerformanceProfile(profile);
  if (errors.length > 0) throw new Error(`Invalid performance profile: ${errors.join("; ")}`);
  return profile;
}

export function validatePerformanceProfile(profile: PerformanceTestProfile): string[] {
  const errors: string[] = [];
  if (profile.schemaVersion !== 1) errors.push("schemaVersion must be 1");
  if (!profile.profileId.trim()) errors.push("profileId must be non-empty");
  if (!Number.isFinite(Date.parse(profile.frozenAt))) errors.push("frozenAt must be an ISO-8601 timestamp");
  if (!profile.appVersion.trim()) errors.push("appVersion must be non-empty");
  for (const [field, value] of Object.entries(profile.host)) {
    if (typeof value !== "string" || !value.trim()) errors.push(`host.${field} must be non-empty`);
  }
  if (profile.network.probeTarget !== "firestore-api") errors.push("network.probeTarget must be firestore-api");
  if (!positiveInteger(profile.network.sampleCount)) errors.push("network.sampleCount must be a positive integer");
  if (profile.network.maximumFailureRate < 0 || profile.network.maximumFailureRate > 1) {
    errors.push("network.maximumFailureRate must be between 0 and 1");
  }
  const envelope = profile.network.latencyEnvelopeMs;
  if (![envelope.p50Min, envelope.p50Max, envelope.p95Min, envelope.p95Max].every(value => Number.isFinite(value) && value >= 0) ||
      envelope.p50Min > envelope.p50Max || envelope.p95Min > envelope.p95Max) {
    errors.push("network.latencyEnvelopeMs must define ordered non-negative bounds");
  }
  if (profile.build.revisionMethod !== "git-head-plus-files-sha256") {
    errors.push("build.revisionMethod must be git-head-plus-files-sha256");
  }
  if (profile.build.requiredFiles.length === 0 || profile.build.requiredFiles.some(file => !file.trim())) {
    errors.push("build.requiredFiles must not be empty");
  }
  for (const [field, value] of Object.entries(profile.emulator)) {
    if (!value.trim()) errors.push(`emulator.${field} must be non-empty`);
  }
  if (!positiveInteger(profile.runPolicy.measuredRuns)) errors.push("runPolicy.measuredRuns must be a positive integer");
  if (!Number.isInteger(profile.runPolicy.warmupRuns) || profile.runPolicy.warmupRuns < 0) {
    errors.push("runPolicy.warmupRuns must be a non-negative integer");
  }
  if (!positiveInteger(profile.runPolicy.minimumSuccessfulRuns) ||
      profile.runPolicy.minimumSuccessfulRuns > profile.runPolicy.measuredRuns) {
    errors.push("runPolicy.minimumSuccessfulRuns must be between 1 and measuredRuns");
  }
  if (profile.runPolicy.maximumEnvironmentErrorRate < 0 || profile.runPolicy.maximumEnvironmentErrorRate > 1) {
    errors.push("runPolicy.maximumEnvironmentErrorRate must be between 0 and 1");
  }
  for (const [field, value] of Object.entries(profile.datasets.largeSynthetic.cardinality)) {
    if (!positiveInteger(value)) {
      errors.push(`datasets.largeSynthetic.cardinality.${field} must be a positive integer`);
    }
  }
  if (!profile.datasets.largeSynthetic.reference.trim()) {
    errors.push("datasets.largeSynthetic.reference must be non-empty");
  }
  if (!Number.isInteger(profile.datasets.largeSynthetic.seed)) {
    errors.push("datasets.largeSynthetic.seed must be an integer");
  }
  if (!profile.datasets.anonymizedReal.reference.trim()) {
    errors.push("datasets.anonymizedReal.reference must be non-empty");
  }
  if (!profile.datasets.anonymizedReal.sourceAlias.trim()) {
    errors.push("datasets.anonymizedReal.sourceAlias must be non-empty");
  }
  if (!positiveInteger(profile.datasets.anonymizedReal.expectedDocumentCount)) {
    errors.push("datasets.anonymizedReal.expectedDocumentCount must be a positive integer");
  }
  if (profile.journeys.length !== approvedJourneys.length ||
      new Set(profile.journeys).size !== approvedJourneys.length ||
      approvedJourneys.some(journey => !profile.journeys.includes(journey))) {
    errors.push("journeys must contain each approved journey exactly once");
  }
  if (profile.datasets.anonymizedReal.persistedInRepository !== false) {
    errors.push("datasets.anonymizedReal.persistedInRepository must be false");
  }
  if (profile.runPolicy.concurrency !== 1) errors.push("runPolicy.concurrency must be 1");
  if (profile.validityRules.length === 0 || profile.validityRules.some(rule => !rule.trim())) {
    errors.push("validityRules must not be empty");
  }
  return errors;
}

const journeyStages: Record<Journey, Array<{
  adapter: keyof BenchmarkAdapters;
  route: Exclude<BenchmarkRoute, "journey:total">;
  payloadSizeBucket: PayloadSizeBucket;
}>> = {
  "open-or-switch-project": [
    { adapter: "firestore", route: "firestore:project-core", payloadSizeBucket: "small" },
    { adapter: "firestore", route: "firestore:map-geometry", payloadSizeBucket: "large" }
  ],
  "business-operation": [
    { adapter: "firestore", route: "firestore:business-query", payloadSizeBucket: "medium" },
    { adapter: "firestore", route: "firestore:business-write", payloadSizeBucket: "small" }
  ],
  "media-upload-preview": [
    { adapter: "firestore", route: "firestore:media-metadata", payloadSizeBucket: "medium" },
    { adapter: "api", route: "api:media-upload", payloadSizeBucket: "large" },
    { adapter: "drive", route: "drive:media-upload", payloadSizeBucket: "large" },
    { adapter: "api", route: "api:media-preview", payloadSizeBucket: "small" },
    { adapter: "drive", route: "drive:media-preview", payloadSizeBucket: "large" }
  ]
};

export async function runBenchmark(
  profile: PerformanceTestProfile,
  datasetReference: string,
  adapters: BenchmarkAdapters,
  options: { now?: () => Date; monotonicNow?: () => number } = {}
): Promise<BenchmarkResult> {
  const profileErrors = validatePerformanceProfile(profile);
  if (profileErrors.length > 0) throw new Error(`Invalid performance profile: ${profileErrors.join("; ")}`);
  const allowedDatasets = [profile.datasets.largeSynthetic.reference, profile.datasets.anonymizedReal.reference];
  if (!allowedDatasets.includes(datasetReference)) throw new Error("datasetReference is not defined by the profile");

  const now = options.now ?? (() => new Date());
  const monotonicNow = options.monotonicNow ?? (() => performance.now());
  const events: TelemetryEvent[] = [];
  const totalRuns = profile.runPolicy.warmupRuns + profile.runPolicy.measuredRuns;
  for (const journey of profile.journeys) {
    for (let runIndex = 0; runIndex < totalRuns; runIndex += 1) {
      const warmup = runIndex < profile.runPolicy.warmupRuns;
      const stageEvents: TelemetryEvent[] = [];
      for (const stage of journeyStages[journey]) {
        const startedAt = monotonicNow();
        let result: BenchmarkStageResult;
        try {
          result = await adapters[stage.adapter]({
            journey,
            route: stage.route,
            runIndex,
            warmup,
            datasetReference,
            payloadSizeBucket: stage.payloadSizeBucket
          });
        } catch {
          result = { success: false, errorCategory: "environment" };
        }
        const durationMs = Math.max(0, monotonicNow() - startedAt);
        const success = result.success ?? true;
        const errorCategory = success ? "none" : result.errorCategory ?? "application";
        const stageEvent = projectTelemetry({
          journey,
          route: stage.route,
          durationMs,
          success,
          errorCategory,
          firestoreOperationCount: result.firestoreOperationCount ?? 0,
          payloadSizeBucket: stage.payloadSizeBucket,
          timestamp: now().toISOString()
        });
        stageEvents.push(stageEvent);
        if (!success) break;
      }
      if (warmup) continue;
      events.push(...stageEvents);
      const failedStage = stageEvents.find(item => !item.success);
      const journeyTotalEvents = stageEvents.filter(item => !item.route.startsWith("drive:"));
      events.push(projectTelemetry({
        journey,
        route: "journey:total",
        durationMs: journeyTotalEvents.reduce((sum, item) => sum + item.durationMs, 0),
        success: !failedStage,
        errorCategory: failedStage?.errorCategory ?? "none",
        firestoreOperationCount: stageEvents.reduce((sum, item) => sum + item.firestoreOperationCount, 0),
        payloadSizeBucket: journey === "media-upload-preview" ? "large" : "medium",
        timestamp: now().toISOString()
      }));
    }
  }

  const summaries = Object.fromEntries(
    profile.journeys.map(journey => [journey, summarizeJourney(events, journey)])
  ) as Record<Journey, JourneySummary>;
  const invalidReasons: string[] = [];
  for (const journey of profile.journeys) {
    const summary = summaries[journey];
    if (summary.successCount < profile.runPolicy.minimumSuccessfulRuns) {
      invalidReasons.push(`${journey} has fewer than the minimum successful runs`);
    }
    if (summary.environmentErrorRate > profile.runPolicy.maximumEnvironmentErrorRate) {
      invalidReasons.push(`${journey} exceeds the maximum environment error rate`);
    }
  }
  const result: BenchmarkResult = {
    schemaVersion: 1,
    profileReference: profile.profileId,
    datasetReference,
    generatedAt: now().toISOString(),
    valid: invalidReasons.length === 0,
    invalidReasons,
    events,
    summaries
  };
  assertSafeBenchmarkArtifact(result);
  return result;
}
