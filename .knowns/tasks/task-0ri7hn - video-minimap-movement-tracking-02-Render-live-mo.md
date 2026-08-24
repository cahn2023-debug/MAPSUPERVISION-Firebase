---
id: 0ri7hn
title: "[video-minimap-movement-tracking-02] Render live movement minimap with adaptive viewport"
status: done
priority: high
labels:
  - from-spec
  - spec:video-minimap-movement-tracking
  - spec-date:2026-08-24
createdAt: '2026-08-24T05:00:30.417Z'
updatedAt: '2026-08-24T05:44:26.348Z'
completedAt: '2026-08-24T05:44:26.348Z'
timeSpent: 825
assignee: '@me'
spec: specs/2026-08-24/video-minimap-movement-tracking
fulfills:
  - AC-1
  - AC-2
  - AC-3
  - AC-4
  - AC-6
order: 20
---
# [video-minimap-movement-tracking-02] Render live movement minimap with adaptive viewport

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use the session movement path in the live video minimap and the stamped minimap renderer. Move the marker, draw the movement polyline, and compute a viewport/zoom that keeps the current marker and accumulated path visible across multiple recording segments.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The live minimap marker follows each valid session position and draws the accumulated movement polyline.
- [x] #2 The minimap viewport adapts to keep the current marker and accumulated path visible across short and extended movement.
- [x] #3 Multiple recording segments in one CameraOverlay session reuse the same path, and timeline samples carry the corresponding map state.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Update PhotoStampRenderer viewport fitting to include `movementPath` and use an adaptive bounded zoom range (14..18), selecting the highest zoom that contains the current marker, camera cone, design objects, and movement path.
2. Draw `movementPath` as a distinct polyline before design routes/nodes, preserving the existing marker/cone layering and route data.
3. Make CameraOverlay tile loading track the selected minimap zoom so preview and stamped rendering use a tile matching the adaptive viewport.
4. Add/update PhotoStampRenderer tests for adaptive zoom and movement-path rendering inputs, then run targeted photo/app tests.
5. Validate task and record D1=pass, D2=pass, D3=pass, D4=pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — rendering behavior follows the approved spec without creating durable guidance.
Implementation complete: adaptive viewport now includes movementPath, zoom range is 14..18, movement polyline renders separately from design routes, and CameraOverlay fetches/caches tiles by location plus adaptive zoom. Verification: PhotoStampRendererTest passed; CameraOverlayHelpersTest report passed with 23 tests and 0 failures/errors. Review verdict: PASS, no P1/P2 findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — no durable guidance changed.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Reopened after integrated review to fix timeline tile ownership: recording samples now retain shared tile snapshots per location/zoom change until export completes, preventing later adaptive movement from using a recycled or stale start tile.
Additional verification scope: timeline tile snapshots are shared per tile/zoom change, retained through export, and recycled exactly once after success or failure.
Integrated review PASS after reopening. Timeline now retains shared tile snapshots per location/zoom change until video export/failure completes, then recycles each snapshot exactly once. App regression report: 23 tests, 0 failures, 0 errors. enforceModuleBoundaries: BUILD SUCCESSFUL. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — no durable guidance changed.
<!-- SECTION:NOTES:END -->

