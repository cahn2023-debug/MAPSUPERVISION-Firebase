---
title: Project-Scoped Database Pattern
description: Why MapSupervisionDatabase (v48) is instantiated per-project, how ProjectScopedDatabaseProvider works, and migration implications.
tags: [room, database, pattern, migrations]
---

# Project-Scoped Database Pattern

Verified against source on 2026-08-22.

## The pattern

`MapSupervisionDatabase` is declared at `data/src/main/java/com/mapsupervision/data/db/MapSupervisionDatabase.kt:99` with `version = 48`, but it is **not** a single app-wide singleton. `ProjectScopedDatabaseProvider` opens a separate Room instance per supervision project. Consequences:

- Multiple DB files can be live during a session; switching projects swaps the provider's active instance.
- DAOs are resolved through the provider, not injected as plain singletons — inject the provider (or a project-scoped entry-point wrapper), never the database class directly.
- Any long-running worker must re-resolve the current instance rather than caching a DAO across project switches.

## Migration rules

1. Bump `version` in `MapSupervisionDatabase.kt` and write the migration for **all** existing per-project DB files — every project's local file migrates independently on next open.
2. Test migrations against a copy of a real multi-project device state when possible; a migration that works on one DB still has to run N times on devices with many projects.
3. Destructive fallback is unacceptable by default — construction records are irreplaceable field data.

## Related storage layer

`:storage-core` (`ProjectStorageManager`, `ProjectPackageService`) manages on-disk project packages including media; `:storage-crypto` (`ProjectCryptoManager`) handles encryption concerns. Storage layout changes must stay synchronized with the DB provider lifecycle — see @doc/architecture-overview for module adjacency.
