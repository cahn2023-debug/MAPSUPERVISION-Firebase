# CameraX & Media Stamping Memory

## Real-Time Watermark HUD & Anti-Fraud
- `CameraOverlay`: Renders real-time HUD over the camera preview and burns watermarks into captured photos (GPS, chainage, node code, weather, project ID, timestamp).
- Anti-fraud verification: Checks `Location.isFromMockProvider()` to prevent GPS spoofing.
- `DirectCaptureSaveDeduper`: Prevents duplicate file writes and redundant background processing on rapid capture triggers.
