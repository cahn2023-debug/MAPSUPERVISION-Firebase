---
id: qwr70h
title: "[permanent-project-deletion-01] Define deletion contract and local project lifecycle"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
createdAt: '2026-08-23T14:33:39.792Z'
updatedAt: '2026-08-23T17:12:42.385Z'
completedAt: '2026-08-23T15:18:30.650Z'
timeSpent: 2630
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-2
  - AC-3
  - AC-4
  - AC-8
  - AC-11
order: 10
---
# [permanent-project-deletion-01] Define deletion contract and local project lifecycle

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Thiết kế và triển khai contract domain/data cho permanent deletion: deletion states, idempotency key, active-project guard, local pending-outbox warning, tombstone metadata và lifecycle đóng/xóa project-scoped Room database/package chỉ sau cloud success. Bảo toàn dữ liệu project khác và Google Drive references.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Active project deletion is rejected before any local deletion state is persisted.
- [x] #2 A second deletion request cannot replace an existing DELETING request owner.
- [x] #3 Pending project outbox work can be counted for the confirmation warning.
- [x] #4 Local storage cleanup closes the scoped database and preserves other project roots and remote media references.
- [x] #5 Room v48 databases migrate to v49 with deletion state/request/error metadata.
- [x] #6 Local completion is rejected until cloud deletion completion is recorded and remains retryable after a cleanup failure.
- [x] #7 Project purge removes projectId rows across all Room project tables while retaining only the project tombstone row.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Map the existing ProjectRepository/ProjectDao/ProjectStorageManager/ProjectScopedDatabaseProvider contracts and define the smallest deletion lifecycle API without changing unrelated sync behavior.
2. Add project deletion metadata/state and idempotent local guard APIs, including active-project rejection and pending-outbox inspection.
3. Add safe close-and-remove local database/package operations that run only after an explicit cloud-success signal and preserve other project directories/media references.
4. Add repository/domain tests for active guard, idempotency, pending-work warning, post-cloud local cleanup, and cross-project isolation.
5. Run targeted tests, diagnostics, and Knowns validation; record D1=pass, D2=pass, D3=pass, D4=pass plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan saved for local deletion contract/lifecycle. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass (implementation gate; verify after tests).
Implemented deletion state contract, Room v48->v49 migration, idempotent DAO guards, pending outbox count, scoped DB close, and local storage cleanup. Added ProjectDeletionDaoTest and ProjectStorageManagerTest coverage. D1=pass, D2=pass, D3=pass, D4=pass. Verification: :domain:compileDebugKotlin, :storage-core:compileDebugKotlin, :data:kspDebugKotlin, :storage-core:testDebugUnitTest pass. Full :data:compileDebugKotlin remains blocked by pre-existing FirebaseAccessRepositoryImpl.kt:410 unresolved reference 'w'.
Verified AC-2 via ProjectDeletionDaoTest idempotency assertion; AC-4 via ProjectStorageManagerTest cleanup assertion; AC-5 via generated schema 49 and successful data KSP migration processing. AC-1/AC-3 remain covered by contract wiring and require repository/integration tests in later wave.
Review fixes: cloudDeletionCompletedAtEpochMs gate + markCloudDeletionCompleted, clearProjectRows separation to preserve retry state, purgeProjectRows across all project tables, provider re-check under mutex, and active-slug isolation guard. Data test suite now passes (including 2 ProjectDeletionDaoTest cases); migration target updated to v50.
Verified new AC-6 via ProjectDeletionDaoTest rejection before cloud marker and successful completion after marker; AC-7 via purgeProjectRows test covering all projectId table names while keeping the project row.
All task ACs verified by current tests and generated schema: ProjectRepositoryDeletionTest covers active guard/pending count; ProjectDeletionDaoTest covers owner idempotency, cloud-success gate, purge; ProjectStorageManagerTest covers local roots; migration suite validates v50. Full data unit suite passes.
Code review verdict PASS for task01. Shared-DB mutation guards remain explicitly deferred to task03. System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — adds durable deletion state, cloud completion gate, local purge/lifecycle, idempotency and storage isolation rules. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — implementation/verification preserves the approved permanent deletion lifecycle and its security/data-retention invariants.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — verification confirms the approved deletion lifecycle invariants
<!-- SECTION:NOTES:END -->

