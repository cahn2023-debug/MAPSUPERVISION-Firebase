---
id: k957f0
title: 'Offline-First Transactional Event Outbox & Firebase Cloud Sync'
layer: project
category: pattern
status: active
tags:
  - firebase
  - firestore
  - sync
  - outbox
createdAt: '2026-08-22T16:48:22.049Z'
updatedAt: '2026-08-22T16:48:22.049Z'
---

Mutations write locally to Room and stage an entry in 'event_outbox' within the same transaction. WorkManager workers (FirebaseMediaUploadWorker, ProjectSyncWorker) drain the outbox to Firestore when network is available. FirebaseAccessGate validates authentication before cloud synchronization. Firestore security rules enforce isProjectMember(projectId) and isAdmin().
