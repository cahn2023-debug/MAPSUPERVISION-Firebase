---
id: v918yt
title: Web Firebase sync group mapping and theme contract
layer: project
category: pattern
status: proposed
tags:
  - firebase
  - sync
  - permissions
  - theme
  - webapp
createdAt: '2026-08-25T17:27:07.444Z'
updatedAt: '2026-08-25T17:27:07.444Z'
---

Web project-scoped writes use the shared envelope with top-level updatedAtEpochMs, projectId path validation, and tombstones for task, daily_log, and note. Firestore maps TASKS/NOTES/DEFAULT to the corresponding collections, and the web theme bootstraps from localStorage or system preference with mapsupervision-theme persistence.
