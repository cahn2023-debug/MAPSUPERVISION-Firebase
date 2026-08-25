---
id: lrhtrp
title: "[web-backend-performance-optimization-03] Optimize media and protect privileged APIs"
status: todo
priority: high
labels:
  - from-spec
  - spec:web-backend-performance-optimization
  - spec-date:2026-08-25
createdAt: '2026-08-25T06:50:46.924Z'
updatedAt: '2026-08-25T06:50:59.094Z'
timeSpent: 0
spec: specs/2026-08-25/web-backend-performance-optimization
fulfills:
  - AC-7
  - AC-8
  - AC-9
  - AC-11
order: 30
---
# [web-backend-performance-optimization-03] Optimize media and protect privileged APIs

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Make media loading demand-driven and add tested App Check, rate limiting, safe telemetry, and existing authorization guarantees to privileged Next.js APIs while preserving deletion lifecycle behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Media metadata is paginated and preview requests occur only when media is needed; upload and preview preserve Firebase token and project authorization plus defined error mappings.
- [ ] #2 App Check and rate limiting support observe-only and enforce modes with tests for valid, missing, invalid, over-limit, and rollback paths.
- [ ] #3 API and media telemetry uses only the approved non-PII allowlist and never exposes credentials or Drive identifiers beyond the existing contract.
- [ ] #4 Project deletion behavior and tests remain compliant with the accepted local-first administrator decision.
<!-- AC:END -->

