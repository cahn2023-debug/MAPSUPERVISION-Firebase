---
name: kn-module
description: Use when adding a Gradle module, changing module dependency edges, or touching build files in MapSupervision - keeps the enforced boundary map and quarantine rules intact
---

# Module & Build-File Work in MapSupervision

**Announce:** "Using kn-module for [module/edge]."

**Core principle:** EDIT THE BOUNDARY MAP FIRST — the enforcement task reads `allowedProjectDependencies`, not your build file.

## The Boundary Contract

- Source of truth: `allowedProjectDependencies` map at root `build.gradle.kts:19`.
- Enforcer: `EnforceModuleBoundariesTask` (`buildSrc/src/main/kotlin/`) fails builds on undeclared edges; root aggregate `check` registers it.
- Full adjacency table lives in @doc/architecture-overview.

## Adding a New Module

1. Create the module directory + `build.gradle.kts` (use template `gradle-module`: `mcp_knowns_templates({ "action": "run", "name": "gradle-module", ... })` after dry run).
2. Add `include(":name")` to `settings.gradle.kts`.
3. Add `":name" to setOf(...)` to the map in root `build.gradle.kts` — minimal edges only.
4. Declare matching `implementation(project(":x"))` lines in the new module.
5. Run `./gradlew check` to confirm boundary compliance before anything else.

## Changing an Edge

1. Confirm the edge respects layering: feature → domain/core only; implementations flow through data/AI modules (@doc/architecture-overview).
2. Edit the map first, then the consuming module's build file.
3. Re-run boundary check, then touched-module tests via /kn-build order.

## Quarantines — Stop-and-Discuss Edges

These dependencies may NOT cross their owning module without an explicit architecture decision:

- `com.google.firebase.*` → only `:data` (3 sanctioned files, see /kn-firebase)
- `org.maplibre.gl` → only `:gis-maplibre` (`gis-maplibre/build.gradle.kts:33`)
- Vendor AI SDKs (generativeai, mediapipe, litertlm, mlkit, tensorflow) → only `:ai-model`

If a request requires breaking a quarantine, route to `/kn-spec <feature>` instead of editing build files.

## Version Discipline

- Prefer `gradle/libs.versions.toml`; but match the target module's existing style — several modules pin versions inline (recorded skew list in @doc/build-conventions).
- Known skews (coroutines 1.7.3 vs 1.8.1; CameraX 1.4.0 vs 1.6.1) are recorded — aligning them is a deliberate task with regression run, never a drive-by bump.

## Final Response Contract

Return information in this order:

1. **Goal/result** — module added / edge changed / blocked at a quarantine.
2. **Key details** — settings + map edits, boundary-check result, version-catalog decisions.
3. **Next action** — one command only when a natural handoff exists (usually `/kn-build`).

Keep this concise for CLI use. Do not manage platform-synced skill copies; this source defines the project-skill contract.

## Related Skills

- `/kn-build` - Verification order after any build-file change
- `/kn-firebase` - When the requested edge would carry Firebase across modules
- `/kn-spec <feature>` - Required routing for quarantine-breaking requests

## Red Flags

- Editing the module build file without updating `allowedProjectDependencies`
- Adding an edge "temporarily" that crosses a quarantine
- Bumping skewed versions inline while doing unrelated work
- Creating a module that depends on `:app`

## Checklist

- [ ] settings.gradle.kts include added
- [ ] allowedProjectDependencies entry added BEFORE consumer deps
- [ ] Boundary check green
- [ ] No quarantine crossed (or spec routed)
