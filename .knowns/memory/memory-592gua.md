---
id: 592gua
title: 'Deadlock & Memory Leak Prevention in Background Media Uploads'
layer: project
category: failure
status: active
tags:
  - worker
  - upload
  - mutex
  - media
createdAt: '2026-08-22T16:48:41.982Z'
updatedAt: '2026-08-22T16:48:41.982Z'
---

Avoid holding SQLite database locks or long-lived Mutex locks across network calls during media upload. DirectCaptureSaveDeduper and Dispatchers.IO must be used with supervisor scopes. WorkManager retry policies must implement exponential backoff with a max retry cap (default: 5 attempts) to prevent battery drain on poor construction site connectivity.
