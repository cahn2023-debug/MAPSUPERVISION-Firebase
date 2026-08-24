---
id: t9fie1
title: "[permanent-project-deletion-09] Propagate decision states and offline member behavior"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
  - revision
createdAt: '2026-08-24T08:22:09.722Z'
updatedAt: '2026-08-24T09:52:38.755Z'
completedAt: '2026-08-24T09:26:35.587Z'
timeSpent: 672
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-5
  - AC-8
  - AC-10
  - AC-11
  - AC-12
order: 90
---
# [permanent-project-deletion-09] Propagate decision states and offline member behavior

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Propagate Cloud-retained/deleted states and tombstones to Android devices. Handle reconnect ordering, member acknowledgement, local purge versus read-only/export, mutation/upload/media/sync guards, and independent retry without affecting other projects.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Reconnect applies the Cloud tombstone before presenting a member prompt and never treats offline state as Cloud deletion success.
- [x] #2 A member can keep local data read-only/exportable or explicitly purge local data; mutation, upload, media request, and sync remain blocked.
- [x] #3 All revised deletion states render actionable status and do not expose stale delete/clone/settings actions.
- [x] #4 Other projects and Google Drive media remain unaffected.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Trace the existing tombstone pull, local acknowledgement, SQL mutation guards, and project-management status rendering against the revised state set.
2. Extend Android state guards and UI labels/actions for CLOUD_DECISION_PENDING, CLOUD_RETAINED, RESTORE_PENDING, and LOCAL_DELETE_FAILED without weakening read-only/offline safety.
3. Add focused tests for reconnect tombstone ordering, member read-only/export versus purge, state guards, and cross-project isolation.
4. Run Android data/project/app tests and compile verification, review the real diff, validate the task, and record D1-D13 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-implement for task 09. Owned scope: Android sync/tombstone/member state guards and UI; preserve existing user changes outside deletion files.
Implementation and verification complete. Reconnect ordering applies the Cloud tombstone before member presentation; member keep-read-only/export versus local purge is enforced while mutation/upload/media/sync remain blocked; revised lifecycle states have actionable labels and guards; cross-project and Google Drive isolation are preserved. Verification: :data:testDebugUnitTest all 122 tests pass; :project:testDebugUnitTest pass; :data:compileDebugKotlin, :project:compileDebugKotlin, :app:compileDebugKotlin pass; web tests 32/32 pass; web TypeScript noEmit pass. Review verdict PASS with no blocking findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass. System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — Android offline/member state propagation, local read-only/purge safeguards, and cross-project/media isolation now implement the durable local-first lifecycle.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass
System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked evidence confirms the approved local-first lifecycle.
<!-- SECTION:NOTES:END -->

