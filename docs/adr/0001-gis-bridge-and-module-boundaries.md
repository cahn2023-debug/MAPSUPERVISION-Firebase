# ADR 0001: Module Boundaries and GIS Bridge Contract

## Context
- The repo is a multi-module Android project with feature modules that should not depend on each other arbitrarily.
- `:gis` owns GIS-facing UI/state and exposes a bridge abstraction for rendering.
- `:gis-maplibre` is the rendering implementation and must not become a second source of business rules.

## Decision
- Enforce project-to-project dependencies from the Gradle root by explicit allowlist.
- Keep `GisMapBridge` as the public contract between `:gis` and `:gis-maplibre`.
- Keep `GisMapBridgeRegistry` as the runtime hook used by the app to install the bridge implementation.

## Consequences
- Module dependency violations fail in `check` and in CI.
- `:gis-maplibre` stays limited to rendering concerns and the bridge implementation.
- Future feature modules must be added to the allowlist deliberately instead of slipping in by accident.
