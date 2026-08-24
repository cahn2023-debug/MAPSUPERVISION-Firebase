---
id: y5uqki
title: "[permanent-project-deletion-07] Add administrator Cloud decision and retain/restore flow"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
  - revision
createdAt: '2026-08-24T08:22:09.631Z'
updatedAt: '2026-08-24T09:52:38.674Z'
completedAt: '2026-08-24T09:04:19.527Z'
timeSpent: 1080
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-3
  - AC-4
  - AC-5
  - AC-7
order: 70
---
# [permanent-project-deletion-07] Add administrator Cloud decision and retain/restore flow

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the idempotent Cloud decision record and Android/admin prompt for authorized project administrators: retain Cloud with CLOUD_RETAINED and automatic local restore, or request Cloud deletion after local-first cleanup. Add RESTORE_PENDING automatic/manual retry without duplicate local packages.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 An authorized project administrator can submit exactly one RETAIN or DELETE Cloud decision for a pending request; later decisions show the recorded outcome.
- [x] #2 RETAIN records CLOUD_RETAINED, leaves Cloud data/permissions/media unchanged, and starts local restore.
- [x] #3 Restore success repopulates the local project from Cloud; offline or failed restore persists RESTORE_PENDING and supports retry without duplicate local storage.
- [x] #4 The Android prompt is visible only for a Cloud-decision-pending project and does not auto-delete Cloud data.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add an idempotent Firebase decision endpoint and shared decision/state helpers for RETAIN versus DELETE requests.
2. Extend the Android sync/repository contract to submit the decision and persist CLOUD_RETAINED/RESTORE_PENDING outcomes.
3. Allow the retained/restore path to pull Cloud rows into a fresh local project database without enabling normal push/mutation while restoration is pending.
4. Add the administrator prompt and retain/delete actions to the existing project management UI, with actionable restore failure/retry messaging.
5. Run web tests/typecheck and Android compile/UI tests, review the real diff, validate the task, and record D1-D13 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-implement for task 07. Owned scope: decision endpoint, Android decision/restore contract, and prompt UI; task 08 owns destructive Cloud authorization details.
Implementation complete and reviewed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass. Verification: web npm test 32/32 pass; web TypeScript noEmit pass; :project:testDebugUnitTest pass; :data/:project/:app compile verification pass. Review verdict PASS with no blocking findings. System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked decision endpoint, retain/restore retry path, local restore pull, and decision prompt are now represented in the durable lifecycle.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass
System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked evidence confirms the approved local-first lifecycle.
<!-- SECTION:NOTES:END -->

