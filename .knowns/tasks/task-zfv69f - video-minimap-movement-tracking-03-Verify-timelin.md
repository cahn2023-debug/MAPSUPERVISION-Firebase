---
id: zfv69f
title: "[video-minimap-movement-tracking-03] Verify timeline export and minimap regression coverage"
status: done
priority: medium
labels:
  - from-spec
  - spec:video-minimap-movement-tracking
  - spec-date:2026-08-24
createdAt: '2026-08-24T05:00:30.497Z'
updatedAt: '2026-08-24T05:44:26.454Z'
completedAt: '2026-08-24T05:37:21.779Z'
timeSpent: 290
assignee: '@me'
spec: specs/2026-08-24/video-minimap-movement-tracking
fulfills:
  - AC-4
  - AC-5
  - AC-7
  - AC-8
  - AC-9
order: 30
---
# [video-minimap-movement-tracking-03] Verify timeline export and minimap regression coverage

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add focused unit/integration coverage for path accumulation, GPS loss/recovery, adaptive viewport, session reset, and multi-sample video stamping. Run targeted photo/app tests and the relevant project validation gates.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Unit tests cover path accumulation, invalid GPS retention, recovery, session reset, and adaptive viewport behavior.
- [x] #2 Video timeline tests prove different samples select different marker/path map states.
- [x] #3 Targeted tests and validation pass for the changed app/photo/domain areas.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend the video timeline selection test with samples carrying distinct movement paths and assert presentation times select the corresponding earlier sample.
2. Keep the existing CameraOverlay and PhotoStampRenderer tests as regression coverage for path accumulation, GPS loss/recovery, session reset, adaptive viewport, and route separation.
3. Run targeted app/photo test suites, then run enforceModuleBoundaries and the relevant Gradle verification gate.
4. Run Knowns task validation and SDD validation; record all ACs and D1=pass, D2=pass, D3=pass, D4=pass before completion.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — verification only, no durable guidance change.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Implementation/verification complete: timeline regression now carries distinct A/B/C movement paths and proves nearest-earlier sample selection. Verification passed: PhotoPipelineServiceTest, PhotoStampRendererTest, CameraOverlayHelpersTest, enforceModuleBoundaries, and SDD validation (0 errors, 0 warnings). Review verdict: PASS, no P1/P2 findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: none — verification added no durable guidance.
Post-fix integrated verification: CameraOverlayHelpersTest report remains 23 tests with 0 failures/errors and enforceModuleBoundaries passes after timeline tile ownership changes.
<!-- SECTION:NOTES:END -->

