---
id: 8rtjd0
title: 'Project-Scoped Database Provider & Multi-Project Isolation'
layer: project
category: pattern
status: active
tags:
  - database
  - isolation
  - wal
createdAt: '2026-08-22T16:48:04.974Z'
updatedAt: '2026-08-22T16:48:04.974Z'
---

ProjectScopedDatabaseProvider isolates project data into separate SQLite files (context.filesDir/projects/{projectId}/db/MapSupervision_{projectId}.db). It enforces WAL journal mode, synchronous=NORMAL, foreign_keys=ON, and memory temp_store. Open databases are safely cached in holders and evicted after 5 minutes of idle time. Initial dictionaries are seeded via ProjectBridgeNormalization.
