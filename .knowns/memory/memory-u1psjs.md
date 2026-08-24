---
id: u1psjs
title: Firestore Catalog Exact-Shape Drift Can Masquerade as an Empty State
layer: project
category: failure
status: proposed
tags:
  - firebase
  - firestore
  - catalog
  - rules
  - empty-state
  - migration
createdAt: '2026-08-24T02:33:39.190Z'
updatedAt: '2026-08-24T02:33:39.190Z'
---

When projectCatalog writers include a field that Firestore exact-shape rules reject (observed with createdByUid), catalog projection writes/backfills can fail while the read query still succeeds with an empty collection. Do not swallow projection write errors as debug-only; align rules, all writers, migration schema, and UI error states, and cover the exact payload with Emulator rules tests.
