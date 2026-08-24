---
id: ua65je
title: "[video-minimap-movement-tracking-01] Establish session movement path and timeline contract"
status: done
priority: high
labels:
  - from-spec
  - spec:video-minimap-movement-tracking
  - spec-date:2026-08-24
createdAt: '2026-08-24T05:00:30.324Z'
updatedAt: '2026-08-24T05:36:45.679Z'
completedAt: '2026-08-24T05:19:20.252Z'
timeSpent: 1068
assignee: '@me'
spec: specs/2026-08-24/video-minimap-movement-tracking
fulfills:
  - AC-1
  - AC-5
  - AC-6
  - AC-7
order: 10
---
# [video-minimap-movement-tracking-01] Establish session movement path and timeline contract

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the temporary CameraOverlay-session movement-path state and the shared data contract needed to carry valid GPS points, marker position, movement polyline, and viewport inputs into video timeline samples. Preserve the existing GIS design routes separately and ignore invalid GPS snapshots without clearing the last valid state.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A valid GPS sequence A -> B -> C is retained in chronological order for the current CameraOverlay session.
- [x] #2 Invalid snapshots do not add points or clear the last valid marker/path state; a later valid snapshot resumes from the retained path.
- [x] #3 A new CameraOverlay session starts with an empty movement path and existing GIS design routes remain separate.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend `CaptureStampMapScene` with a separate session movement-path field so movement points never overwrite configured GIS routes.
2. Add a small CameraOverlay session path helper that accepts only snapshots with both valid coordinates, preserves the last path across invalid GPS reads, de-duplicates unchanged points, and resets with a new session.
3. Thread the path through `buildCaptureStamp` and `buildVideoStampTimelineSample` so both live preview state and video timeline samples carry the same movement data.
4. Add focused unit tests for A -> B -> C ordering, invalid GPS retention and recovery, new-session reset, and preservation of configured design routes.
5. Run targeted app/domain tests and Knowns task/spec validation. Decisions: D1=pass, D2=pass, D3=pass (viewport deferred to task 2), D4=pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — introduces temporary session state and an existing timeline data flow, with no durable project guidance change.
Implementation complete: added session movement path state, preserved last valid GPS state on invalid snapshots, kept movementPath separate from design routes, and threaded it through preview/video stamp builders. Verification: :app:testDebugUnitTest --tests com.mapsupervision.app.CameraOverlayHelpersTest passed (BUILD SUCCESSFUL).
Review verdict: PASS. No P1 findings; the only P2 (missing assertion that timeline samples carry movementPath) was fixed and the targeted test passed again. All task ACs checked. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — no durable guidance changed.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
<!-- SECTION:NOTES:END -->

