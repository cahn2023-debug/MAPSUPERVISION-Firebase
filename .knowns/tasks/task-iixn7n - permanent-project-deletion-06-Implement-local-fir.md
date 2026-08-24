---
id: iixn7n
title: "[permanent-project-deletion-06] Implement local-first deletion classification and local lifecycle"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
  - revision
createdAt: '2026-08-24T08:22:09.577Z'
updatedAt: '2026-08-24T09:52:38.625Z'
completedAt: '2026-08-24T08:44:55.696Z'
timeSpent: 1258
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-1
  - AC-2
  - AC-5
  - AC-8
  - AC-12
order: 60
---
# [permanent-project-deletion-06] Implement local-first deletion classification and local lifecycle

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the revised Android/domain/data contract: distinguish never-uploaded local-only projects from confirmed Cloud projects, delete local data first for uploaded projects, persist decision-pending/local-failure/restore metadata, preserve other project roots and Google Drive references, and keep retries safe.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A project with no confirmed Cloud data is purged locally and does not create a Cloud deletion request.
- [x] #2 A project with confirmed Cloud data is locally purged first and remains as an idempotent Cloud-decision record.
- [x] #3 Local purge failure persists a retryable local-failure state without changing Cloud data or other projects.
- [x] #4 Room migrations, imports, and storage cleanup preserve Google Drive references and other project roots.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend the shared project deletion model and Room schema with explicit local-first decision/restore states and a confirmed-Cloud-data marker.
2. Add DAO/repository operations that classify projects, purge local data first, preserve the project decision record for uploaded projects, complete local-only deletion without a Cloud request, and persist retryable local failures.
3. Propagate the new fields through project import/storage migration and mark projects as Cloud-confirmed only when downloaded or otherwise confirmed by Cloud.
4. Update focused repository/DAO/migration tests for both branches, local failure, retry state, and cross-project/media isolation.
5. Run targeted Android tests/compilation, review the real diff, validate the task, and record D1-D13 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-implement for the first sequential revision task. Owned scope: domain/data/storage local-first contract and tests; do not modify unrelated worktree changes.
Implementation complete and reviewed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass. Verification: :data:testDebugUnitTest pass (all 122 tests); :data:compileDebugKotlin pass; :project:compileDebugKotlin pass; :app:compileDebugKotlin pass. Review verdict PASS with no P1/P2 findings. System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked to this task and documents the durable local-first classification, state, migration, and local/Cloud separation contract.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass
System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked evidence confirms the approved local-first lifecycle.
<!-- SECTION:NOTES:END -->

