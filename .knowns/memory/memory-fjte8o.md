---
id: fjte8o
title: Firebase Project Catalog Envelope Name Synchronization
layer: project
category: pattern
status: proposed
tags:
  - firebase
  - sync
  - catalog
createdAt: '2026-08-26T08:01:47.962Z'
updatedAt: '2026-08-26T08:01:47.962Z'
---

Projects synced from Android are stored in SyncEnvelope maps under field 'data'. Parser and catalog synchronization must extract from 'data', 'payload', and root docData, and self-heal missing/corrupted catalog records so project names and codes are always unified between local and cloud.
