---
id: doc-069736e9a9ecf7a7546c1d5ed91b6b14
title: Monotonic Session Viewport Zoom for Temporal Map Overlays
description: Reusable pattern for retaining map viewport coverage across live and timeline-rendered movement sessions.
createdAt: '2026-08-24T08:30:12.768Z'
updatedAt: '2026-08-24T08:30:12.768Z'
tags:
  - pattern
  - camera
  - minimap
  - video
---

# Monotonic Session Viewport Zoom for Temporal Map Overlays

## Pattern

For a live map overlay that is also rendered into a timeline (such as a stamped video), keep the session zoom monotonic: start at the close zoom, compute the highest zoom that fits the current marker and accumulated path in the real minimap viewport, then resolve the session zoom as the smaller of the candidate and the previously latched zoom. Because lower zoom numbers show a wider area, this permits zooming out as the route expands while preventing an accidental zoom-in when the user returns near the starting point.

Carry the resolved zoom together with the movement path in every timeline scene/sample. This keeps live preview, cached tile selection, and exported frames on the same viewport state. Initialize both path and zoom in the camera-session owner so a new session starts cleanly.

## Implementation Guidance

- Keep movement-path state separate from designed GIS routes.
- Ignore invalid location snapshots without changing the last marker, path, or zoom.
- Compute the candidate from viewport fitting rather than a fixed distance threshold.
- Clamp the latched zoom to the renderer's supported range.
- Test close-zoom retention, one-way zoom-out, return-to-start retention, timeline propagation, and session reset.

## Provenance

- Source task: @task-4a5rim
- Source spec: @doc/specs/2026-08-24/video-minimap-movement-tracking
- Implementation: `app/src/main/java/com/mapsupervision/app/CameraOverlay.kt`, `domain/src/main/java/com/mapsupervision/domain/model/CaptureStampMapScene.kt`, and `photo/src/main/java/com/mapsupervision/photo/worker/PhotoStampRenderer.kt`
