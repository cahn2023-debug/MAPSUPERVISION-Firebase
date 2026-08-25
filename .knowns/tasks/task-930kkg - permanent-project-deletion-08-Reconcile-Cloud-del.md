---
id: 930kkg
title: "[permanent-project-deletion-08] Reconcile Cloud deletion authorization and independent retry"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
  - revision
createdAt: '2026-08-24T08:22:09.675Z'
updatedAt: '2026-08-24T10:28:06.997Z'
completedAt: '2026-08-24T09:14:42.007Z'
timeSpent: 2836
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-3
  - AC-6
  - AC-7
  - AC-8
  - AC-9
  - AC-10
  - AC-12
order: 80
---
# [permanent-project-deletion-08] Reconcile Cloud deletion authorization and independent retry

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Update Firebase/web Cloud deletion to accept any authorized project administrator for the decision, preserve first-write-wins semantics, require reauthentication and typed identity for destructive Cloud deletion, and run checkpointed Cloud cleanup independently from local cleanup while preserving tombstone/audit and Google Drive media.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A project-member administrator can execute the recorded DELETE decision; a non-admin project member cannot.
- [x] #2 The first Cloud decision wins and only one worker request/checkpoint owner is active for a deletion request.
- [x] #3 Cloud cleanup resumes after failure and preserves tombstone/audit and Google Drive media.
- [x] #4 The initiating Android device completes local cleanup only after Cloud success and retains retryable failure states otherwise.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend the server deletion authorization contract to recognize project-member administrator roles and all revised lifecycle states.
2. Ensure the DELETE decision path invokes the existing checkpointed Cloud worker exactly once and that retries resume the same request.
3. Update Android decision handling to trigger the worker, persist DELETE_FAILED/DELETED outcomes, and complete local cleanup only after Cloud success.
4. Add web/Firebase and Android tests for project-admin authorization, first-write-wins, decision-to-worker wiring, retry, and media preservation.
5. Run web tests/typecheck/build, Android targeted tests/compile, review the real diff, validate the task, and record D1-D13 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-implement for task 08. Owned scope: Firebase/web authorization and worker wiring plus Android DELETE decision completion; preserve unrelated worktree changes.
Implementation complete and reviewed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass. Verification: web npm test 32/32 pass; web TypeScript noEmit pass; :data/:project/:app compileDebugKotlin pass. Review verdict PASS with no blocking findings. System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — project-admin authorization, decision-to-worker wiring, first-write-wins, checkpoint retry, tombstone/media preservation, and Cloud-success-gated local completion are now implemented.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass
System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked evidence confirms the approved local-first lifecycle.
📚 Extracted reusable lifecycle, concurrency, and restore-state lessons to @doc/learnings/learning-local-first-project-deletion-lifecycle.
<!-- SECTION:NOTES:END -->

