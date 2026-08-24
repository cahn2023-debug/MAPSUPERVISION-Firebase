---
id: avrsg3
title: "[permanent-project-deletion-10] Verify local-first deletion contract end to end"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
  - revision
createdAt: '2026-08-24T08:22:09.769Z'
updatedAt: '2026-08-24T09:52:58.824Z'
completedAt: '2026-08-24T09:52:58.824Z'
timeSpent: 1568
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
fulfills:
  - AC-1
  - AC-2
  - AC-3
  - AC-4
  - AC-5
  - AC-6
  - AC-7
  - AC-8
  - AC-9
  - AC-10
  - AC-11
  - AC-12
order: 100
---
# [permanent-project-deletion-10] Verify local-first deletion contract end to end

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add or revise tests and run integrated verification for branch classification, administrator authorization, retain/restore, local and Cloud failure/retry, race/idempotency, offline prompt/read-only/export, audit/tombstone retention, cross-project isolation, rules, and Google Drive preservation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Web tests, TypeScript verification, and Firebase rules validation pass for the local-first deletion contract.
- [x] #2 Android data/project/app tests and relevant compilation pass.
- [x] #3 Integrated inspection confirms all AC-1 through AC-12 paths, D1-D13 compliance, and no Google Drive media or cross-project regression.
- [x] #4 Task and SDD validation pass with explicit System Decision Impact metadata.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Inspect the revised local-first deletion implementation, linked tests, Firebase rules, and media/isolation safeguards against AC-1 through AC-12 and D1-D13.
2. Run web tests and TypeScript verification, Firebase rules dry-run/compile, Android data/project/app unit tests, and relevant Kotlin compilation.
3. Review the integrated git diff and test coverage for branch classification, admin authorization, retain/restore, local/Cloud failure and retry, race/idempotency, offline member behavior, tombstone/audit retention, cross-project isolation, and Google Drive preservation; fix only regressions from this feature.
4. Validate the task/spec, record Spec Decision Compliance and System Decision Impact, then stop the timer and complete the task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
End-to-end verification complete. Web npm test: 32/32 pass; web TypeScript noEmit pass; Firebase Firestore rules dry-run compile pass. Android :data:testDebugUnitTest forced pass; :data:compileDebugKotlin forced pass; :project:compileDebugKotlin and :app:compileDebugKotlin pass. Integrated diff review found and fixed the restore-completion gap: successful RETAIN restore now returns local state to ACTIVE, CLOUD_RETAINED/RESTORE_PENDING retry restore on demand, and all non-ACTIVE states hide stale project actions. Feature-only git diff check passes; unrelated CameraOverlay/video-minimap and Knowns changes preserved. Google Drive deletion search found no deletion command; deletion response/tests retain mediaPreserved=true. Review verdict PASS after fix with no remaining P1/P2 findings. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass. System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — final verification and restore-completion fix confirm the local-first lifecycle, retry/state guards, authorization, tombstone/audit, project isolation, and Google Drive preservation contract.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass
System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked evidence confirms the approved local-first lifecycle.
<!-- SECTION:NOTES:END -->

