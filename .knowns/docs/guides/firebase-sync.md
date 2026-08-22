---
title: Firebase Sync & Security Model
description: How the outbox→WorkManager→Firestore sync works, why Firebase is confined to :data, and how Firestore/Storage rules gate access.
tags: [firebase, sync, security, firestore, storage]
---

# Firebase Sync & Security Model

Verified against source on 2026-08-22.

## Ownership boundary

Firebase SDK lives only in `:data` — exactly three files under `data/src/main/java/com/mapsupervision/data/sync/`: `FirebaseRuntime.kt`, `FirebaseSyncRepositoryImpl.kt`, `FirebaseAccessRepositoryImpl.kt`. Everything else reaches Firebase through domain interfaces. `firebase.json` wires Firestore rules + indexes, `storage.rules`, and emulators (Auth :9099, Firestore :8080, Storage :9199, UI :4000); `.firebaserc` pins project `mapsupervision`.

## Offline-first flow

1. Local Room DB (per-project instance) is the source of truth while offline.
2. Mutations append to a persisted **event outbox** (Room-backed writer in `:data`).
3. A WorkManager dispatcher drains the outbox → Firestore; media goes through `DriveMediaUploadClient` (`DriveMediaUploadClient.kt`) for Drive-backed upload with later `migrate:drive-media` support in webapp.
4. Sync table shape is centralized in `FirebaseSyncTableCatalog.kt`.

Never call Firestore from ViewModels or feature modules — extend a repository interface in `:domain`, implement in `:data/sync`.

## Firestore rules (`firestore.rules`)

- Role check via custom claim: admin claim + helper functions at top of file.
- Project membership = document in `/projects/{pid}/projectMembers/{uid}` subcollection with `isActive != false`.
- `/users/{uid}`: self read/write only, with strict field-shape validation (profile fields validated inline in rules).
- `/projects/{pid}`: members read/write project doc; membership writes are admin-only.
- Adding any new collection = add matching rule + think about member-vs-admin split before shipping.

## Storage rules (`storage.rules`)

Media writes gated on `/projects/{pid}/media/**` paths by an exists() lookup into Firestore membership — same member predicate as Firestore, evaluated cross-service.

## Webapp/admin side

`webapp/` (Next.js 15, React 19) uses firebase-admin 13 + googleapis for privileged operations: `bootstrap:admins` seeds admin custom claims, `migrate:drive-media` migrates Drive media references. Service-account material is local-only (never commit; never print contents).

## Emulator workflow

Run emulators via `firebase emulators:start` (ports above), point debug builds at them per existing config when testing sync locally. Auth must be faked through the emulator's user seeding — rules rely on real Auth UIDs.
