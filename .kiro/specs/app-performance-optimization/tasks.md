# Implementation Plan

## Overview

Fix 4 performance bugs in MapSupervision (Android/Kotlin) that cause UI freeze and ANR on low-end devices. All bugs share the same root cause: heavy computation running on the main thread. The fix offloads each computation to the correct dispatcher (`Dispatchers.Default` or `Dispatchers.IO`) and exposes pre-computed results via `StateFlow`.

## Task Dependency Graph

```json
{
  "waves": [
    { "wave": 1, "tasks": ["1", "2"] },
    { "wave": 2, "tasks": ["3.1"] },
    { "wave": 3, "tasks": ["3.2", "3.3", "3.4", "3.5"] },
    { "wave": 4, "tasks": ["3.6", "3.7"] },
    { "wave": 5, "tasks": ["4"] }
  ]
}
```

## Tasks

- [ ] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Heavy Computation On Main Thread
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate each of the 4 bug conditions
  - **Scoped PBT Approach**: For each bug condition, scope the property to the concrete failing call site to ensure reproducibility
  - **Bug 1 — Filter on main thread**: Call `workspaceViewModel.getFilteredDesignNodesForMap()` with a list of 500+ `GisNode` objects and assert the call does NOT execute on `Thread.currentThread().name == "main"` — will FAIL because the function runs synchronously on the caller's thread (main thread in Composable context)
  - **Bug 2 — AI orchestrator without IO dispatcher**: Spy on `aiOrchestrator.execute()` inside `refresh()` and assert it is invoked on a thread backed by `Dispatchers.IO` — will FAIL because there is no `withContext(Dispatchers.IO)` wrapping the call
  - **Bug 3 — buildDashboard on main thread**: Call `updateMaterialProgress()` or `addConstructionProgress()` and assert `buildDashboard()` does NOT run on `Dispatchers.Main` — will FAIL because `viewModelScope.launch` defaults to `Dispatchers.Main`
  - **Bug 4 — flushThreshold too large**: Simulate importing 5 batches of 400 nodes each and assert `pendingNodeUpserts.size` never exceeds 500 at any point — will FAIL because `flushThreshold = 2_000`
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct — it proves the bugs exist)
  - Document counterexamples found:
    - Bug 1: `getFilteredDesignNodesForMap()` executes on `Thread[main,...]`
    - Bug 2: `aiOrchestrator.execute()` not called on IO thread
    - Bug 3: `buildDashboard()` executes on `Thread[main,...]`
    - Bug 4: `pendingNodeUpserts.size` reaches 2000 before flush
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Filter Results And Dashboard Numbers Unchanged
  - **IMPORTANT**: Follow observation-first methodology — run UNFIXED code with non-buggy inputs first, observe outputs, then encode as property tests
  - **Scope**: All inputs where the bug condition does NOT apply — i.e., correctness of results, not which thread they run on
  - **Observation 1 — Filter result preservation**: For any `List<GisNode>`, `searchQuery: String`, and `filterContractor: String?`, observe that `getFilteredDesignNodes()` returns a specific subset; write property-based test asserting `filteredNodesForMap` StateFlow emits the identical list (same elements, same order) for all generated inputs
  - **Observation 2 — Route filter preservation**: Same as above for `List<GisRoute>` and `getFilteredDesignRoutes()` / `filteredRoutes` StateFlow
  - **Observation 3 — Dashboard accuracy preservation**: For any `List<GisNode>`, `List<NodeProgress>`, `List<MaterialProgress>`, observe that `buildDashboard()` returns a `DashboardState` with `totalDesignNodes == nodes.size` and `totalActualQty == sum(materialRows.actualQty)`; write property-based test asserting this invariant holds for all generated inputs
  - **Observation 4 — Empty state**: Observe that `designNodes = emptyList()` produces `filteredNodesForMap = emptyList()` without crash; encode as edge-case test
  - **Observation 5 — flushThreshold batch invariant**: For any batch of imported files, observe that `pendingNodeUpserts.size <= flushThreshold` at every flush check point; encode as property test (this will PASS on unfixed code only when total nodes < 2000 — document the boundary)
  - Verify all preservation tests PASS on UNFIXED code before proceeding
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 3. Fix: Offload heavy computation off main thread

  - [ ] 3.1 Add `filteredNodesForMap` and `filteredRoutes` StateFlow to `WorkspaceViewModel`
    - In `WorkspaceViewModel.kt`, add two new `StateFlow` properties using `_state.map { ... }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())`
    - `filteredNodesForMap`: map `_state` → filter `designNodes` by `mapUi.filterContractor` and `mapUi.searchQuery` using the same normalize/match logic as the existing `getFilteredDesignNodes()` — do NOT change the filter logic itself
    - `filteredRoutes`: same pattern for `designRoutes` using existing route filter logic
    - Keep existing `getFilteredDesignNodesForMap()` and `getFilteredDesignRoutes()` functions intact (do not delete — they will be superseded by the StateFlow in the UI layer)
    - _Bug_Condition: `isBugCondition("MainActivity Composable calling getFilteredDesignNodesForMap()")` — filter runs synchronously on main thread_
    - _Expected_Behavior: Composable collects pre-computed StateFlow value; no filter computation on main thread_
    - _Preservation: Filter logic (normalize Vietnamese, contractor match, code/name match) must produce identical results to existing `getFilteredDesignNodes()` / `getFilteredDesignRoutes()`_
    - _Requirements: 2.1, 2.2, 3.2_

  - [ ] 3.2 Update `MainActivity.kt` to collect StateFlow instead of calling synchronous filter functions
    - In `MainShell`, replace the two direct function calls in the `Tab.MAP` branch:
      - `workspaceViewModel.getFilteredDesignNodesForMap()` → `workspaceViewModel.filteredNodesForMap.collectAsState().value`
      - `workspaceViewModel.getFilteredDesignRoutes()` → `workspaceViewModel.filteredRoutes.collectAsState().value`
    - No other changes to `MainActivity.kt`
    - _Bug_Condition: `isBugCondition("MainActivity Composable calling getFilteredDesignNodesForMap()")` and `isBugCondition("MainActivity Composable calling getFilteredDesignRoutes()")`_
    - _Expected_Behavior: Composable only reads already-computed value from StateFlow; main thread is not blocked_
    - _Requirements: 2.1, 2.2_

  - [ ] 3.3 Wrap `aiOrchestrator.execute()` in `withContext(Dispatchers.IO)` inside `refresh()`
    - In `WorkspaceViewModel.kt`, `refresh()` function, wrap the `runCatching { aiOrchestrator.execute<OpsRecommendationResult>(...) }` block with `withContext(Dispatchers.IO) { ... }`
    - No other changes to `refresh()`
    - _Bug_Condition: `isBugCondition("refresh() calling aiOrchestrator.execute() without withContext(Dispatchers.IO)")`_
    - _Expected_Behavior: `aiOrchestrator.execute()` always runs on IO dispatcher; main thread is never blocked by AI computation or I/O_
    - _Preservation: `refresh()` must continue to set all fields of `WorkspaceState` (nodes, routes, dashboard, aiOps, materialProgress) after completion_
    - _Requirements: 2.3, 3.3_

  - [ ] 3.4 Wrap `buildDashboard()` in `withContext(Dispatchers.Default)` in `updateMaterialProgress()` and `addConstructionProgress()`
    - In `WorkspaceViewModel.kt`, `updateMaterialProgress()`: extract the `buildDashboard(...)` call into `val newDashboard = withContext(Dispatchers.Default) { buildDashboard(...) }`, then use `newDashboard` in the subsequent `_state.value = _state.value.copy(dashboard = newDashboard)`
    - Apply the same pattern in `addConstructionProgress()` wherever `buildDashboard()` is called before `_state.value = ...`
    - No other changes to these functions
    - _Bug_Condition: `isBugCondition("updateMaterialProgress() calling buildDashboard() without withContext(Dispatchers.Default)")` and `isBugCondition("addConstructionProgress() calling buildDashboard() without withContext(Dispatchers.Default)")`_
    - _Expected_Behavior: `buildDashboard()` runs on `Dispatchers.Default`; `_state.value` is updated on main thread only after computation completes_
    - _Preservation: `DashboardState` produced must be numerically identical to the result of calling `buildDashboard()` with the same inputs_
    - _Requirements: 2.4, 3.4_

  - [ ] 3.5 Reduce `flushThreshold` from `2_000` to `500` in `importDesignFiles()`
    - In `WorkspaceViewModel.kt`, `importDesignFiles()`, change `val flushThreshold = 2_000` to `val flushThreshold = 500`
    - Single-line change; no other modifications
    - _Bug_Condition: `isBugCondition("importDesignFiles() with flushThreshold = 2_000 on low-RAM device")`_
    - _Expected_Behavior: Pending geometry is flushed to DB after every 500 upserts, reducing peak memory footprint_
    - _Preservation: Import logic (parse, dedup, upsert) must continue to produce the same final set of nodes/routes in DB; no data loss_
    - _Requirements: 2.6, 3.1_

  - [ ] 3.6 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Heavy Computation Off Main Thread
    - **IMPORTANT**: Re-run the SAME tests from task 1 — do NOT write new tests
    - The tests from task 1 encode the expected behavior; when they pass, the fix is confirmed
    - Run all 4 bug condition checks from step 1 on the fixed code
    - **EXPECTED OUTCOME**: All 4 tests PASS (confirms all bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_

  - [ ] 3.7 Verify preservation tests still pass
    - **Property 2: Preservation** - Filter Results And Dashboard Numbers Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run all preservation property tests from step 2 on the fixed code
    - **EXPECTED OUTCOME**: All tests PASS (confirms no regressions in filter logic, dashboard accuracy, or import correctness)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 4. Checkpoint — Ensure all tests pass
  - Run the full unit test suite (`./gradlew :app:testDebugUnitTest`)
  - Confirm all tests from tasks 1, 2, 3.6, and 3.7 pass
  - Confirm no pre-existing tests were broken by the changes
  - Ask the user if any questions arise about edge cases or test failures

## Notes

- Tasks 1 and 2 are standalone property-based test tasks that MUST be completed before any implementation begins. This is the exploratory bugfix methodology: understand the bug through failing tests, establish a preservation baseline, then fix.
- The 5 implementation sub-tasks (3.1–3.5) are independent of each other and can be applied in any order, but all must be complete before running verification in 3.6 and 3.7.
- The existing `getFilteredDesignNodesForMap()` and `getFilteredDesignRoutes()` functions are intentionally kept — removing them is out of scope for this bugfix.
- `flushThreshold = 500` is a conservative value. If performance profiling later shows DB write overhead is significant, it can be tuned upward — but that is a separate task.
