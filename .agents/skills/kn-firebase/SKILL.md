---
name: kn-firebase
description: Use when touching Firebase surface - Firestore/Storage rules, sync outbox, emulator workflow, or anything that would add a com.google.firebase import outside :data
---

# Firebase Work in MapSupervision

**Announce:** "Using kn-firebase for [scope]."

**Core principle:** FIREBASE LIVES IN :data ONLY — 3 files, enforced by architecture and rules.

## Hard Boundaries

- Only these files may import `com.google.firebase`: `data/src/main/java/com/mapsupervision/data/sync/FirebaseRuntime.kt`, `FirebaseSyncRepositoryImpl.kt`, `FirebaseAccessRepositoryImpl.kt` (@doc/guides/firebase-sync).
- New backend capability = new method on a `:domain` repository interface + implementation in `:data/sync`. A feature module asking for direct Firebase access is an architecture decision — stop and discuss.
- UI/ViewModel code never calls Firestore directly; mutations flow through the event outbox → WorkManager dispatcher.

## Rules Work (`firestore.rules`, `storage.rules`)

Before editing rules, map the access matrix:

- Admin = custom claim; membership = `/projects/{pid}/projectMembers/{uid}` with `isActive != false`.
- `/users/{uid}` self-access only with strict field-shape validation.
- Project doc RW = members; projectMembers writes = admin-only.
- Storage media under `/projects/{pid}/media/**` gates via cross-service exists() on the same membership.

After any rule change: validate locally against emulators (Auth :9099, Firestore :8080, Storage :9199), test both member and admin personas plus an anonymous outsider. Deploy needs the Firebase CLI authenticated to `.firebaserc` project `mapsupervision`.

## Sync Changes

- Table shape changes route through `FirebaseSyncTableCatalog.kt`.
- Media upload path is `DriveMediaUploadClient`; webapp-side migration lives in `webapp/` scripts (`migrate:drive-media`).
- Never drain or mutate the outbox from app UI code paths.

## Secrets & Keys

- `.env` and `mapsupervision-3d985eee34f0.json` (service account) exist locally — never print their contents, never commit them. Note existence only.
- Webapp admin operations (`bootstrap:admins`) run via firebase-admin in `webapp/`, not from the Android app.

## Emulator Workflow

```bash
firebase emulators:start   # auth:9099 firestore:8080 storage:9199 ui:4000
```

Seed emulator Auth users before testing rules — rules evaluate real UIDs.

## Final Response Contract

Return information in this order:

1. **Goal/result** — what was added/changed/validated or blocked at a boundary.
2. **Key details** — files touched, boundary compliance, rule-test coverage (personas tested), emulator status.
3. **Next action** — one command only when a natural handoff exists.

Keep this concise for CLI use. Do not manage platform-synced skill copies; this source defines the project-skill contract.

## Related Skills

- `/kn-build` - Verification order including emulator-based sync testing
- `/kn-debug` - When rules reject expected access
- `/kn-doc` - Update @doc/guides/firebase-sync when the model changes

## Red Flags

- Adding `com.google.firebase` imports outside the three sanctioned `:data/sync` files
- Writing Firestore directly from ViewModels instead of the outbox flow
- Shipping rules without testing member/admin/outsider personas
- Printing or committing service-account material

## Checklist

- [ ] No Firebase import leaked outside :data/sync
- [ ] Domain interface extended first, data impl second
- [ ] Rules tested for member/admin/outsider personas on emulators
- [ ] Outbox path preserved (no direct writes)
