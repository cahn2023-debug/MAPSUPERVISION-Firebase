#!/usr/bin/env sh
set -eu

echo "[release-gate] Running required unit test gates"
./gradlew :app:testDebugUnitTest
./gradlew :storage-import:testDebugUnitTest
./gradlew :data:testDebugUnitTest

echo "[release-gate] Running repository verification gates"
./gradlew lint assembleDebug enforceModuleBoundaries

echo "[release-gate] Verifying release runbook and checklist exist"
test -f docs/release_gate_runbook.md
test -f docs/tab_nhap_lieu_data_hub.md
test -f production-ready-roadmap.md

echo "[release-gate] Release gate passed"
