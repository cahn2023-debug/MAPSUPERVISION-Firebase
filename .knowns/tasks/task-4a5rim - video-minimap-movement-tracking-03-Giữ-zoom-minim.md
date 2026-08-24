---
id: 4a5rim
title: "[video-minimap-movement-tracking-04] Giữ zoom minimap theo phạm vi hành trình"
status: done
priority: high
labels:
  - from-spec
  - spec:video-minimap-movement-tracking
  - spec-date:2026-08-24
createdAt: '2026-08-24T07:48:49.497Z'
updatedAt: '2026-08-24T08:30:34.928Z'
completedAt: '2026-08-24T08:06:16.335Z'
timeSpent: 1032
assignee: '@me'
spec: specs/2026-08-24/video-minimap-movement-tracking
fulfills:
  - AC-10
  - AC-11
  - AC-12
  - AC-13
---
# [video-minimap-movement-tracking-04] Giữ zoom minimap theo phạm vi hành trình

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Điều chỉnh chính sách zoom cho minimap live và minimap đóng dấu video: giữ zoom 18 trong viewport ban đầu, chỉ zoom out khi vị trí tiến gần biên viewport thực tế, sau đó giữ mức zoom đủ hiển thị toàn bộ hành trình và không zoom lại khi quay về gần điểm đầu.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Đoạn di chuyển ngắn vẫn trong viewport zoom 18 giữ nguyên zoom 18.
- [x] #2 Khi vị trí tiến gần biên viewport zoom 18, chỉ zoom out vừa đủ để giữ marker hiện tại và toàn bộ movement path.
- [x] #3 Sau khi zoom out, quay lại gần vị trí đầu không làm zoom trở lại; toàn bộ tuyến vẫn hiển thị.
- [x] #4 Phiên CameraOverlay mới reset zoom policy và movement path.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend the temporary CaptureStamp map-scene contract with an optional latched minimap zoom, preserving existing design routes and movement-path data.
2. Add a small pure zoom-policy helper in CameraOverlay that starts at zoom 18, accepts only zoom-out as the accumulated path approaches/exceeds the current viewport fit, and never zooms back in during the session.
3. Update the live location polling and preview/timeline stamp builders to carry the latched zoom and use the same zoom-selected tile/frame for live minimap and stamped video samples; reset zoom and path when a new CameraOverlay session starts.
4. Update PhotoStampRenderer viewport resolution to honor the latched zoom as an upper bound while retaining adaptive fitting for the current marker, movement path, camera cone, and configured GIS content.
5. Add focused app/photo tests for zoom 18 retention, one-way zoom-out behavior, return-to-start retention, timeline propagation, and new-session reset; run targeted tests and module-boundary validation.
6. Validate task/spec compliance, record D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, and record System Decision Impact as none because this implements approved spec behavior without new durable guidance.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Planning assumptions: preserve the existing adaptive viewport calculation and use the latched zoom as a monotonic cap; carry the selected zoom through CaptureStampMapScene so live preview and video timeline share the same state. Spec Decision Compliance for the plan: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass.
Implementation complete: added monotonic minimap zoom state starting at zoom 18, preserved the latched zoom in CaptureStampMapScene, synchronized preview and video timeline samples, and kept the existing adaptive path fitting as the zoom-out candidate. Review verdict: PASS; no P1/P2 findings. Verification: :app:testDebugUnitTest passed; :photo:testDebugUnitTest --tests com.mapsupervision.photo.worker.PhotoStampRendererTest passed; enforceModuleBoundaries passed; git diff --check passed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass. System Decision Impact: none — implements approved minimap zoom behavior without adding durable project guidance.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
Extracted reusable pattern: @doc/learnings/monotonic-session-viewport-zoom-for-temporal-map-overlays
<!-- SECTION:NOTES:END -->

