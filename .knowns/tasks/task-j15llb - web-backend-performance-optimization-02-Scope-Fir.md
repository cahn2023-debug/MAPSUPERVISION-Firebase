---
id: j15llb
title: "[web-backend-performance-optimization-02] Scope Firestore data loading to active work"
status: todo
priority: high
labels:
  - from-spec
  - spec:web-backend-performance-optimization
  - spec-date:2026-08-25
createdAt: '2026-08-25T06:50:46.487Z'
updatedAt: '2026-08-25T06:50:58.626Z'
timeSpent: 0
spec: specs/2026-08-25/web-backend-performance-optimization
fulfills:
  - AC-3
  - AC-4
  - AC-5
  - AC-6
order: 20
---
# [web-backend-performance-optimization-02] Scope Firestore data loading to active work

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Optimize project switching, active-tab realtime subscriptions, fresh core loading, complete map geometry, and stable pagination without changing shared Firebase contracts in @doc/specs/2026-08-25/web-backend-performance-optimization.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Switching projects or leaving a tab unsubscribes obsolete listeners, prevents stale callbacks from changing state, and does not leak reads across repeated switches.
- [ ] #2 Only fresh core data for the active screen gates readiness; inactive collections are not loaded and stale cache is not presented as current.
- [ ] #3 The map receives the complete required node and route geometry while large task, log, media, and admin collections use stable bounded queries or pagination without duplicate or missing rows.
- [ ] #4 Admin and catalog data refresh on defined events or explicit requests and do not retain unnecessary listeners while inactive.
<!-- AC:END -->

