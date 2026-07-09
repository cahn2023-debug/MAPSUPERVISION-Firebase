# Production Ready Roadmap

Goal: hoan thien DATA Hub thanh Data Integration Platform theo huong P0-P11, uu tien phan nao da co nen tang san.

## Checklist

- [x] P0 - Audit kien truc, dependency, database, sync flow
- [x] P1 - Chuan hoa domain model va canonical feature
- [x] P2 - Chuan hoa database, UUID/FK, audit tables, migration
- [x] P3 - Tach import engine thanh pipeline co contract ro rang
- [x] P4 - Chuan hoa geometry va input data ve Feature/Geometry
- [x] P5 - Refactor state, memory, filtering, paging nen
- [x] P6 - Event-driven synchronization co DomainEventBus
- [x] P7 - Garbage collector, cascade delete, versioning, rollback
- [x] P8 - AI integration, conflict resolution engine, confidence routing
- [x] P9 - Performance: streaming parser, spatial index, cache, batch writes
- [x] P10 - Test suite: unit, integration, stress, recovery, migration
- [x] P11 - Documentation, release checklist, verification gates

## Current gaps

- Event outbox writer da co, nhung dispatcher/consumer van can hardening neu muon sync ngoai tien trinh hien tai.
- P11 runbook release / rollback da duoc dong goi trong `docs/release_gate_runbook.md`.
- P11 gate da duoc gan vao `.github/workflows/android.yml` thong qua `scripts/release_gate.sh`.
- `data:testDebugUnitTest` da xanh sau khi chinh legacy migration expectations len schema 42.
