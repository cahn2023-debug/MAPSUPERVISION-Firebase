# Implementation Plan - Network, Fiber Route, Center Path, Signal Map Highlight

## Overview
Confirm, test, and verify features for Version 2.1 of MapSupervision. This includes:
- **Network fields on nodes:** `ipAddress`, `subnet`, `gateway`, `signalStatus`
- **Fiber fields on routes:** `fiberCoreCount`, `fiberConnection`
- **Center-node routing:** Select an existing node as project center and compute BFS path back to center
- **Map rendering:** Node signal highlighting (brighter color/halo) and popup cards containing the new properties

---

## Project Type
**MOBILE** (Android / Compose / Room / MapLibre)

---

## User Review Required
> [!IMPORTANT]
> The database migration schema for version 45 to 46 is already present on disk (`45.json` and `46.json` under `data/schemas`), and the Room migration logic `MIGRATION_45_46` is defined in `MapSupervisionDatabase.kt`.
> This plan focuses on validating that the migration succeeds without data loss and verifying the UI display and BFS calculation.

---

## Open Questions
1. **Tool Integration:** Would you like to run `pip install code-review-graph` to build a local map and cut token usage for this project?
2. **Signal Highlights:** Should the halo rendering on nodes with signal have a customizable pulse animation or remains a static highlight overlay?

---

## Tech Stack
- **Database:** Room DB (SQLite) v46 with custom `MIGRATION_45_46`
- **GIS Rendering:** MapLibre SDK (via `:gis-maplibre` bridge)
- **UI:** Jetpack Compose, state managed in `WorkspaceViewModel` (BFS routing computed via `buildCenterPathSummary`)

---

## File Structure

### Data & Domain Layer
- [GisNode.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/domain/src/main/java/com/mapsupervision/domain/model/GisNode.kt) - GIS node model
- [GisRoute.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/domain/src/main/java/com/mapsupervision/domain/model/GisRoute.kt) - GIS route model
- [GisNodeEntity.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/data/src/main/java/com/mapsupervision/data/db/entity/GisNodeEntity.kt) - Room entity for nodes
- [GisRouteEntity.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/data/src/main/java/com/mapsupervision/data/db/entity/GisRouteEntity.kt) - Room entity for routes
- [MapSupervisionDatabase.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/data/src/main/java/com/mapsupervision/data/db/MapSupervisionDatabase.kt) - Database class containing `MIGRATION_45_46`

### UI & ViewModel Layer
- [WorkspaceViewModel.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/workspace/WorkspaceViewModel.kt) - State holder and coordinator
- [WorkspaceMapProgressActions.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/workspace/WorkspaceMapProgressActions.kt) - Map actions and BFS routing implementation
- [MapHubScreen.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/workspace/MapHubScreen.kt) - Detail cards UI and map components
- [MapBridgeInstaller.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/gis-maplibre/src/main/java/com/mapsupervision/gis/maplibre/MapBridgeInstaller.kt) - MapLibre bridge coordinates rendering

---

## Task Breakdown

### Task 1: Add Room Migration tests for 45 -> 46
- **Agent:** `test-engineer`
- **Skill:** `testing-patterns`
- **Priority:** P0
- **Dependencies:** None
- **INPUT:** `MapSupervisionDatabaseMigrationTest.kt`
- **OUTPUT:** Added test `migration 45 to 46 compiles and validates successfully` in `MapSupervisionDatabaseMigrationTest.kt`
- **VERIFY:** Run `./gradlew :data:testDebugUnitTest --tests com.mapsupervision.data.db.MapSupervisionDatabaseMigrationTest` and check if it passes.

### Task 2: Add Repository Mapper unit tests for round-tripping
- **Agent:** `test-engineer`
- **Skill:** `testing-patterns`
- **Priority:** P1
- **Dependencies:** Task 1
- **INPUT:** `GisRepositoryImplTest.kt`
- **OUTPUT:** Added round-trip tests asserting that new network/fiber fields save and retrieve correctly via `GisRepositoryImpl`
- **VERIFY:** Run `./gradlew :data:testDebugUnitTest --tests com.mapsupervision.data.repository.GisRepositoryImplTest`

### Task 3: Add Importer mapping tests for Excel/KML new fields
- **Agent:** `test-engineer`
- **Skill:** `testing-patterns`
- **Priority:** P1
- **Dependencies:** Task 2
- **INPUT:** `storage-import` unit tests
- **OUTPUT:** Test coverage for Excel optional columns and GeoJSON metadata extraction mapping to `ipAddress`, `subnet`, `gateway`, `signalStatus`, `fiberCoreCount`, `fiberConnection`
- **VERIFY:** Run `./gradlew :storage-import:testDebugUnitTest`

### Task 4: Add Unit Tests for BFS center-route path-finding
- **Agent:** `test-engineer`
- **Skill:** `testing-patterns`
- **Priority:** P1
- **Dependencies:** None
- **INPUT:** `WorkspaceMapProgressActions.kt`
- **OUTPUT:** Test suite verifying `buildCenterPathSummary` with connected, disconnected, missing center, and circular route graphs
- **VERIFY:** Run `./gradlew :app:testDebugUnitTest --tests com.mapsupervision.app.workspace.CenterPathBfsTest`

---

## Phase X: Final Verification

### Automated Tests
- `./gradlew :data:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :storage-import:testDebugUnitTest`

### Manual Verification
- Start the app on emulator.
- Import an Excel file containing network columns.
- Click a node, check the detail card for IP, Subnet, Gateway, Signal status.
- Tap "Đặt trung tâm" on a node, then tap another node and check if the BFS path is correctly displayed.
- Verify nodes with signal (`HAS_SIGNAL`) render with a bright overlay/halo on MapLibre.
