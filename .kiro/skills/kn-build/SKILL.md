---
name: kn-build
description: Use when building, testing, or verifying MapSupervision - respects the serialized low-memory Gradle profile, module boundary enforcement, and targeted verification order
---

# Building & Verifying MapSupervision

**Announce:** "Using kn-build for [scope]."

**Core principle:** TARGETED MODULE TASKS FIRST, FULL BUILD LAST — the machine is memory-constrained by design.

## Preflight

- Read @doc/build-conventions before changing any Gradle setting.
- Never raise `org.gradle.parallel`, `org.gradle.workers.max`, `org.gradle.jvmargs`, or enable the daemon — see @doc/build-conventions for why.
- Identify the smallest module set that covers the change (module adjacency table in @doc/architecture-overview).

## Build Commands

```bash
# Single module compile+unit tests (preferred during iteration)
./gradlew :data:testDebugUnitTest
./gradlew :ai-agent:testDebugUnitTest

# Compile one module without tests
./gradlew :timeline:assembleDebug

# Full assembly (slow by design — run once at the end)
./gradlew assembleDebug

# Module boundary check (also runs as part of root `check`)
./gradlew check
```

## Verification Order

1. **Boundary check first** when a dependency edge changed — `EnforceModuleBoundariesTask` reads `allowedProjectDependencies` in root `build.gradle.kts:19`; an undeclared edge fails here, not at compile.
2. **Unit tests of touched modules**, then modules that depend on them.
3. **KSP/Hilt round-trip** — Hilt 2.57.2 + KSP failures surface at compile of consumers; if a DI graph breaks, build `:app` to catch it.
4. **Full `assembleDebug`** only before handoff/commit.
5. **Firebase-dependent work**: run emulators (`firebase emulators:start`) per @doc/guides/firebase-sync — never test sync against production project `mapsupervision`.

## Room Migration Check

If `MapSupervisionDatabase.kt` version bumped: verify a Migration object exists for every hop, then run data-module tests — every per-project DB file migrates independently (@doc/patterns/project-scoped-database).

## AI Engine Work

Engine/orchestrator changes must run `:ai-agent:testDebugUnitTest` (rich SummaryAggregator coverage lives there) plus `:ai-model` tests for engine-local behavior (@doc/patterns/ai-engine-orchestration).

## Final Response Contract

Return information in this order:

1. **Goal/result** — what compiled/tested/passed or what failed.
2. **Key details** — exact tasks run, failure excerpts (trimmed), boundary-check status, known-skew warnings if relevant.
3. **Next action** — one command only when a natural handoff exists (e.g. `/kn-commit`).

Keep this concise for CLI use. Skill-specific content may extend the key-details section but must not replace or reorder the shared structure.

Do not manage platform-synced skill copies; this source defines the project-skill contract.

## Related Skills

- `/kn-implement <task-id>` - Where this verification usually runs from
- `/kn-commit` - Commit gate after green verification
- `/kn-debug` - When verification fails

## Red Flags

- "Optimizing" gradle.properties parallelism/heap on your own initiative
- Running full builds in a loop while iterating on one module
- Skipping the boundary check after editing any `build.gradle.kts`
- Testing sync/Firestore against production instead of emulators

## Checklist

- [ ] Smallest sufficient module set identified
- [ ] Boundary check run if edges changed
- [ ] Touched-module unit tests green
- [ ] Full build run once before handoff
