---
id: 6zebli
title: "[web-backend-performance-optimization-01] Establish reproducible performance baseline"
status: in-progress
priority: high
labels:
  - from-spec
  - spec:web-backend-performance-optimization
  - spec-date:2026-08-25
createdAt: '2026-08-25T06:50:27.975Z'
updatedAt: '2026-08-25T08:55:53.940Z'
timeSpent: 0
assignee: '@me'
spec: specs/2026-08-25/web-backend-performance-optimization
fulfills:
  - AC-1
  - AC-9
order: 10
---
# [web-backend-performance-optimization-01] Establish reproducible performance baseline

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the frozen performance profile, repeatable benchmark harness, safe telemetry contract, and pre-optimization baseline required by @doc/specs/2026-08-25/web-backend-performance-optimization.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A frozen Performance Test Profile defines host, network, anonymized-real-data reference, large synthetic fixture, journeys, run count, and validity rules.
- [ ] #2 A repeatable harness reports P50, P95, Firestore read or operation counts, API and Drive durations, and error rate for all three journeys.
- [x] #3 Telemetry is constrained by an enforced allowlist and tests prove prohibited PII, secrets, business content, media URLs, and raw user or project IDs are not emitted.
- [ ] #4 A pre-optimization baseline report is generated for both required datasets before optimization results are evaluated.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Keep the tested telemetry allowlist/statistics primitives, replace modeled benchmark assumptions with wall-clock measurements and explicit operation counters; write integration-focused tests first.
2. Add a frozen profile that captures host, measured network evidence, emulator ports, source provenance fingerprint, dataset cardinalities, run count, and validity gates without raw IDs.
3. Implement a one-run pipeline that loads credentials only in memory, reads the selected production source, anonymizes each document before writing any snapshot, creates both anonymized-real and large-synthetic datasets, and seeds isolated Firebase Emulator projects.
4. Execute all three journeys with actual Firestore Admin queries/writes against the Emulator and the real Google Drive upload/download helpers; create a dedicated temporary Drive folder under the configured root and delete it in finally.
5. Emit safe aggregate JSON/Markdown reports for both datasets only after network/host/run validity checks; never serialize source IDs, payloads, URLs, credentials, or Drive IDs.
6. Run unit/integration tests, TypeScript, existing web tests, both real baselines, artifact safety checks, and Knowns validation. Check remaining ACs and complete only if both reports are valid; otherwise record the concrete external blocker.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan approved under the active kn-flow authorization after the user requested continue on the approved spec. No benchmark source project has been selected yet; real-data baseline remains a fail-closed gate.
Implemented frozen profile, deterministic large synthetic fixture, injectable three-journey benchmark harness, exact telemetry allowlist/safety enforcement, P50/P95 and error/operation aggregation, CLI, JSON/Markdown reporting, and package scripts. Verified: `npm run test:performance` 9/9 pass; `npx tsc --noEmit` pass; synthetic baseline command produced a valid report; anonymized-real command fails closed when BENCHMARK_ANONYMIZED_REAL_DATASET is absent; task validation 0 issues. Existing full web tests: 39/40 pass; unrelated `tests/project-deletion.test.ts:51` fails because REAUTH_REQUIRED is not thrown (file untouched/out of owned scope). AC 4 remains unchecked and task remains in-progress because no external anonymized-real source was provided. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — implementation follows the approved spec/profile and introduces no additional durable project guidance.
Review correction: the initial CLI used hard-coded stage models or replayed numeric samples and mislabeled them as a valid pre-optimization baseline. kn-review verdict BLOCKED (P1). The modeled CLI, fixture, frozen profile, and generated reports were removed. Prior D1/D2/D3 pass wording is superseded: D1 and D2 are not evidenced until real Firestore/API/Drive journeys run against an approved benchmark target; D3 is outside this task's evidence; D4 telemetry allowlist remains supported. AC 1 and AC 2 were reopened; AC 3 remains verified. The existing project-deletion test failure is unrelated but remains a spec release gate.
User selected option A: use the current Firebase project as the controlled anonymized-real source. Resume by discovering ACTIVE project candidates without exporting business payloads.
Current Firebase discovery found three ACTIVE candidates using metadata counts only. Selected source project e58d246f-a049-4c17-8c36-c04d0d72bd97 because it has the largest and broadest active dataset (616 documents across geometry, task, note, work plan, media, progress, and material collections). Raw payload must never be committed; anonymization occurs before any persisted temporary snapshot. Benchmark target is Firebase Emulator plus a temporary Drive benchmark folder that must be deleted after the run.
Implementation resumed after P1 review correction. Replaced the obsolete modeled/replay plan with an actual source→anonymize→Emulator→Firestore/Drive wall-clock benchmark plan; source identifiers remain runtime-only and must not be persisted.
Real-path correction implemented: stage durations now come only from a monotonic wall clock around awaited adapters; adapters cannot supply durations. Added frozen v2 profile with measured network probe requirements, source alias/cardinality, Emulator hosts, run rules, and no source identifier. CLI reads the selected source through a named production Admin app, anonymizes every scalar and document ID in memory before Emulator persistence, materializes the anonymized-real and large-synthetic documents, runs actual Firestore query/write stages, invokes the real media POST/GET route plus direct Drive upload/download, and deletes the dedicated Drive folder and Emulator process tree in finally. Verification: `npm run test:performance` 9 pass/1 skipped without Emulator; `npm run test:performance:integration` 10/10 pass against Firestore Emulator; `npx tsc --noEmit` pass; `git diff --check -- webapp` pass. Live run with the selected runtime-only source reached source read/anonymization, network probing, and Emulator startup, then failed first at the configured Drive root with `File not found` for the configured folder. Credential identity and configured root ID were independently confirmed; this is an external Drive access/root-folder blocker. No baseline artifact was emitted, no alternate root was used, and ports 8080/9099 were verified free after cleanup. AC 1, 2, and 4 remain unchecked; AC 3 remains checked. Spec Decision Compliance: D1=blocked (Drive path unavailable), D2=blocked (neither valid baseline may be claimed), D3=not-evaluated by this baseline task, D4=pass for telemetry/privacy boundary. System Decision Impact: none — implementation follows the approved spec and adds no guidance beyond its locked benchmark/privacy rules.
Latest implementation/review pass: P1 privacy, media timing, operation counts, emulator ownership, and Drive cleanup issues fixed. Added defensive runtime profile parser, frozen network latency envelope with HTTP/timeout checks, expanded application revision hashing across executed route/Drive/config files, verified Emulator shutdown/port release, shared baseline run ID, atomic staged publication rollback, and schema-aware anonymization keys. Verification: npm run test:performance 11 pass/1 emulator-gated skip; npm run test:performance:integration 11/11 pass; npx tsc --noEmit pass; full web suite 42 pass/1 unrelated project-deletion failure. Task remains in-progress: live baseline still blocked by configured Google Drive root inaccessible/not found; no reports emitted.
Post-review hardening: probe now treats expected Firestore API 4xx/404 as reachable and rejects only 5xx/timeout; profile envelope narrowed to P50 <=300ms/P95 <=600ms. Application revision now records git HEAD, hashes every tracked webapp/firebase file plus required runtime files, and hashes dirty status. A live rerun with source project did not emit reports before the command timeout; Emulator processes were manually terminated and ports 8080/9099 confirmed free. Existing Drive-root access failure remains the known external unblock; no alternate root used.
Final review hardening: network probe performs a warmup, rejects 5xx/timeouts while accepting expected unauthenticated Firestore 404 reachability, and frozen envelope is P50 20-100ms/P95 20-200ms. Revision provenance is structured (git HEAD, complete tracked webapp/firebase content hash, dirty-state hash). Publication rollback now treats final renames as commit point and preserves backups on restore failure. Latest unit/integration/TypeScript checks pass; task remains blocked only on producing live baseline reports with accessible Drive root.
Spec Decision Compliance: D1=blocked: configured Google Drive root is inaccessible; D2=blocked: valid baseline reports are not generated; D3=not-evaluated: optimization task has not started; D4=pass: telemetry/privacy allowlist is enforced. System Decision Impact: none — no durable guidance change; implementation follows the approved spec decisions.
<!-- SECTION:NOTES:END -->

