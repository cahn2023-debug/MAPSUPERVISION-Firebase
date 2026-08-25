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

## System Decision Impact

- Impact: none (Scoped UI component optimization and bugfix within `app` module).

## Requirements

### Functional Requirements

- **FR-1**: When user taps Close (X) while video is recording, the app MUST NOT drop the video or crash due to premature `CameraOverlay` unmounting; it must finalize recording, complete video stamp export, save the file, and dismiss.
- **FR-2**: When user taps Stop recording, all UI controls MUST remain locked (`isFinalizingRecording = true`) until `VideoRecordEvent.Finalize` finishes post-processing to prevent concurrent recordings or race conditions on timeline buffers.
- **FR-3**: Changing zoom ratio via slider or gestures MUST adjust camera zoom smoothly without flickering, unbinding, or re-initializing CameraX use cases.
- **FR-4**: Toggling Stamp ON/OFF MUST NOT restart or unbind the camera preview.
- **FR-5**: Video stamp timeline samples MUST be sampled at true ~250ms intervals regardless of high-frequency sensor updates.
- **FR-6**: The final timeline sample recorded on Stop MUST retain the selected `statusTag`.
- **FR-7**: When Stamp is disabled, background OSM tile network fetching MUST be skipped to conserve bandwidth and RAM.

### Non-Functional Requirements

- **NFR-1 (Performance & GC)**: Eliminate repeated GIS object allocation on bearing sensor events by memoizing map nodes and routes.
- **NFR-2 (Memory Safety)**: Prevent duplicate bitmap caching and replace `mutableStateListOf` with standard `mutableListOf` for non-UI-observed recording buffers.
- **NFR-3 (Regression Safety)**: All existing unit tests in `CameraOverlayHelpersTest` must continue to pass.

## Acceptance Criteria

- [ ] **AC-1**: Tapping Close (X) during recording sets `dismissAfterRecording = true`, calls `stop()`, waits for `VideoRecordEvent.Finalize` + `postProcessRecordedVideo`, and dismisses only after save completion.
- [ ] **AC-2**: While `isFinalizingRecording` is true, recording button, mode selector, aspect ratio, flash, and close buttons are disabled.
- [ ] **AC-3**: Dragging zoom slider calls `setZoomRatio()` without triggering `provider.unbindAll()`.
- [ ] **AC-4**: Toggling Stamp switch does not call `provider.unbindAll()`.
- [ ] **AC-5**: Periodic video sampling `LaunchedEffect` is keyed only on `isRecording, recordingStartElapsedMs, stampEnabled` and delays 250ms per iteration.
- [ ] **AC-6**: Stop button final timeline sample includes `statusTag = selectedStatusTag`.
- [ ] **AC-7**: Precomputed `remember(nodes)` and `remember(routes)` are used in `buildCaptureStamp` or preview stamp rendering.
- [ ] **AC-8**: OSM tile download is skipped when `!stampEnabled`.
- [ ] **AC-9**: `recordingTimelineTileBitmaps` and `recordingTimelineSamples` use `mutableListOf`.
- [ ] **AC-10**: `./gradlew testDebugUnitTest` passes cleanly.

## Scenarios

### Scenario 1: User closes camera during active video recording
**Given** video recording is active (`isRecording == true`)
**When** user clicks Close (X) icon in top bar
**Then** `dismissAfterRecording` is set to `true`, `activeRecording.stop()` is triggered, controls remain disabled, `CameraOverlay` remains active until `Finalize` + `postProcessRecordedVideo` finishes saving, and then `onDismiss()` is invoked.

### Scenario 2: Zoom slider adjustment during preview
**Given** Camera preview is running
**When** user drags the zoom slider from 1.0x to 3.0x
**Then** `cameraControl.setZoomRatio` smoothly zooms the preview without re-binding use cases or freezing the stream.

### Scenario 3: Stamp disabled by user
**Given** Camera is open and `stampEnabled` is toggled to `false`
**When** location poll executes
**Then** location and reverse geocoding update, but OSM tile fetch is skipped, current tile is cleared, and camera preview stays active without unbind.

## Technical Notes

- Target file: `app/src/main/java/com/mapsupervision/app/CameraOverlay.kt`
- Test file: `app/src/test/java/com/mapsupervision/app/CameraOverlayHelpersTest.kt`

## Task Links

- Generated after plan creation.

## Open Questions

None. (All gray areas resolved in Phase 0).
