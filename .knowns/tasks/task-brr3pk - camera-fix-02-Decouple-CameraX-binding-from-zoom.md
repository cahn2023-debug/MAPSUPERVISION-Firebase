---
id: brr3pk
title: "[camera-fix-02] Decouple CameraX binding from zoom/stamp and stabilize video sampler"
status: done
priority: high
labels:
  - from-spec
  - spec:camera-overlay-lifecycle-perf-fix
  - spec-date:2026-08-25
createdAt: '2026-08-25T11:36:29.986Z'
updatedAt: '2026-08-25T11:52:15.996Z'
completedAt: '2026-08-25T11:38:47.001Z'
timeSpent: 35
assignee: '@me'
spec: specs/2026-08-25/camera-overlay-lifecycle-perf-fix
fulfills:
  - AC-3
  - AC-4
  - AC-5
  - AC-6
order: 20
---
# [camera-fix-02] Decouple CameraX binding from zoom/stamp and stabilize video sampler

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Isolate CameraX binding to cameraProvider/cameraSelector/isVideoMode only. Remove zoomRatio and stampEnabled from bind keys. Run 250ms periodic video sampler loop and populate statusTag on final stop sample.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decoupled CameraX binding from zoom and stamp toggles. Fixed video sampler 250ms cadence and populated statusTag. Spec Decision Compliance: D2=pass, D3=pass. System Decision Impact: none
<!-- SECTION:NOTES:END -->

