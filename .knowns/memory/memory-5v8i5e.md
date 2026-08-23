---
id: 5v8i5e
title: 'CameraX Watermark HUD & Anti-Fraud GPS Stamping'
layer: project
category: pattern
status: active
tags:
  - camera
  - camerax
  - watermark
  - gps
createdAt: '2026-08-22T16:48:18.022Z'
updatedAt: '2026-08-22T16:48:18.022Z'
---

CameraOverlay provides real-time viewfinder overlay and burns hardware-level metadata onto captured images (GPS coordinates, station chainage, timestamp, weather, project code). Anti-fraud detection checks Location.isFromMockProvider(). DirectCaptureSaveDeduper prevents duplicate filesystem writes and multiple thumbnail generations.
