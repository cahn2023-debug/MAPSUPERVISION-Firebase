---
id: 2n512i
title: 'Hybrid Vector-Raster Minimap Scaling (15-32) & Real-Time Camera Tracking'
layer: project
category: pattern
status: proposed
tags:
  - camera
  - minimap
  - mercator
  - zoom
  - video-tracking
createdAt: '2026-08-26T05:12:24.742Z'
updatedAt: '2026-08-26T05:12:24.742Z'
---

When scaling minimap zoom beyond raster tile limits (zoom 19 up to 32), use a hybrid approach: fetch raster tiles at max available zoom (19) and upscale via canvas matrix while computing all vector features (camera cone, GIS nodes/routes, movement polyline, waypoint dots) with Double precision Mercator math to avoid Int32 overflow. For video recording, track camera coordinates in real-time with camera-centric panning, rendering past coordinates as waypoint dots connected by a glowing polyline, with lifecycle reset on each new recording session.
