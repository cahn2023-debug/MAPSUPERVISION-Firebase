---
id: y5nh55
title: "[camera-fix-03] Precompute GIS geometry, optimize OSM tile fetching and clean up collections/imports"
status: done
priority: medium
labels:
  - from-spec
  - spec:camera-overlay-lifecycle-perf-fix
  - spec-date:2026-08-25
createdAt: '2026-08-25T11:36:33.095Z'
updatedAt: '2026-08-25T12:17:01.575Z'
completedAt: '2026-08-25T11:41:55.985Z'
timeSpent: 174
assignee: '@me'
spec: specs/2026-08-25/camera-overlay-lifecycle-perf-fix
fulfills:
  - AC-7
  - AC-8
  - AC-9
order: 30
---
# [camera-fix-03] Precompute GIS geometry, optimize OSM tile fetching and clean up collections/imports

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Precompute GIS geometry using remember(nodes) and remember(routes), skip OSM tile fetch when stampEnabled=false, remove cachedTileBitmap copy, switch timeline lists to mutableListOf, clean up unused imports.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Skip OSM tile fetch when stamp is disabled
- [x] #2 Precompute GIS nodes and routes via remember
- [x] #3 Recording buffers use standard mutableListOf
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Precomputed GIS geometry using remember(nodes) & remember(routes), skipped tile download when stamp is off, removed duplicate cachedTileBitmap, converted timeline buffers to mutableListOf, cleaned unused imports. Spec Decision Compliance: D4=pass, D5=pass, D6=pass. System Decision Impact: none
<!-- SECTION:NOTES:END -->

