# Firebase & Event Outbox Sync Memory

## Offline-First Event Outbox Pattern
- Local state changes commit to Room and append a record into `event_outbox`.
- Background `WorkManager` workers (`FirebaseMediaUploadWorker`, `ProjectSyncWorker`) process outbox events when network is available.
- Exponential backoff is configured with a maximum retry count to prevent battery exhaustion.

## Authentication & Security Rules
- `FirebaseAccessGate` ensures users are authenticated (Google One-Tap or Email/Password) and have project memberships.
- `firestore.rules` enforces role-based access control (`isProjectMember(projectId)` and `isAdmin()`).

## Foreign Key Invariant & Remote Pull Sync
- `FirebaseSyncRepositoryImpl` must use in-place `database.update` before `database.insert` in `upsertRow` to avoid SQLite `CONFLICT_REPLACE` triggering `DELETE` + `INSERT`, which fails `NO_ACTION` foreign key constraints when child rows exist (error code 787).
- `FirebaseSyncTableCatalog` must strictly maintain topological order (parent tables like `imported_files`, `site_photos`, `work_categories` before their dependent child tables).
