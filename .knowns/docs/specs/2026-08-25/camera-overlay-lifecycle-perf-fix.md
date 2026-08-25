---
id: doc-3b9e3c4c2e90c727a7e931f9495bb64f
title: camera-overlay-lifecycle-perf-fix
description: 'CameraOverlay Lifecycle, Stability & Performance Fixes'
createdAt: '2026-08-25T11:52:52.998Z'
updatedAt: '2026-08-25T11:52:52.998Z'
tags: []
---

# Specification: CameraOverlay Lifecycle, Stability & Performance Fixes

## Overview

Fix critical P0 race condition & data loss bugs in `CameraOverlay` during video recording finalization and close events. Fix P1 performance bottlenecks including camera unbind on zoom/stamp toggle, high-frequency sensor video sampler thrashing, redundant GIS conversions, unnecessary OSM tile downloads when stamp is disabled, and excessive Compose state allocation. Maintain helper API signatures and clean up unused imports.

## Locked Decisions

- **D1: Video Lifecycle & Finalize State Machine**: Use internal state machine with `isFinalizingRecording` & `dismissAfterRecording`. When user taps Stop or Close (X) during recording:
  - Disable/lock all UI controls (`controlsEnabled = !isRecording && !isFinalizingRecording && !isProcessingVideoStamp && !photoCaptureSession.isCapturingPhoto`).
  - Do NOT reset `isRecording` or `activeRecording` synchronously upon `stop()`.
  - Keep `CameraOverlay` mounted and intact until `VideoRecordEvent.Finalize` + `postProcessRecordedVideo` completely finishes.
  - If `dismissAfterRecording == true` (Close was tapped during recording), invoke `onDismiss()` only after post-processing and cleanup finish in `Finalize`.
- **D2: CameraX Binding & Runtime Zoom/Stamp Isolation**:
  - Key `LaunchedEffect(cameraProvider, cameraSelector, isVideoMode)` strictly for CameraX use case lifecycle binding.
  - Remove `zoomRatio` and `stampEnabled` from CameraX lifecycle bind keys.
  - Apply zoom directly via `cameraControl.setZoomRatio(clampedZoom)` without triggering `provider.unbindAll()`.
  - Stamp toggle updates overlay rendering without affecting CameraX use case bindings.
- **D3: Video Sampler Cadence & StatusTag Integrity**:
  - Key `LaunchedEffect(isRecording, recordingStartElapsedMs, stampEnabled)` for the video stamp sampler loop.
  - Inside `while(isRecording)` loop, read latest references (`liveLocation`, `liveAddress`, `noteText`, `bearing`, `selectedStatusTag`, `recordingTimelineTileBitmap`) and delay for a genuine `VIDEO_STAMP_SAMPLE_INTERVAL_MS` (250ms).
  - Ensure all timeline samples (initial sample, periodic samples, and final sample created on Stop) explicitly carry `statusTag = selectedStatusTag`.
- **D4: GIS Memory & Recomposition Optimization**:
  - Precompute `CaptureStampMapNode` via `remember(nodes)` and `CaptureStampMapRoute` via `remember(routes)`.
  - Include `nodes` and `routes` in the preview stamp `remember(...)` dependencies to ensure preview reflects dynamic route/node changes without remapping GIS objects on high-frequency compass updates.
- **D5: OSM Tile Fetching, Memory Caching & Collection States**:
  - Skip `fetchOsmTile()` when `stampEnabled == false`, while continuing location and reverse-geocoding updates.
  - Remove redundant duplicate `cachedTileBitmap` snapshot copy (maintain single `currentTileBitmap`).
  - Convert `recordingTimelineTileBitmaps` and `recordingTimelineSamples` from `mutableStateListOf` to `mutableListOf` to eliminate unused Compose snapshot bookkeeping overhead.
- **D6: Helper Signature & Import Cleanup**:
  - Preserve `buildPreviewStampOverlayBitmap` public/internal signature (including `aspectRatio` default param) for API compatibility.
  - Maintain `resolveLatchedMinimapZoom` bounds `[MINIMAP_MIN_ZOOM, MINIMAP_MAX_ZOOM]`.
  - Remove unused imports (`Toast`, `Image`, etc.).
