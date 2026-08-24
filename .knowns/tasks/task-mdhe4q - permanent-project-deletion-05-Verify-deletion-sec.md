---
id: mdhe4q
title: "[permanent-project-deletion-05] Verify deletion security, retry, offline, and media preservation"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
createdAt: '2026-08-23T14:33:40.057Z'
updatedAt: '2026-08-24T09:52:01.170Z'
completedAt: '2026-08-23T17:13:24.585Z'
timeSpent: 259
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
order: 50
---
# [permanent-project-deletion-05] Verify deletion security, retry, offline, and media preservation

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Viết và chạy unit/repository/worker/UI/Firebase rules/integration tests cho authorization, race/idempotency, active guard, checkpoint retry, local lifecycle, offline prompt/read-only export, audit/tombstone, cross-project isolation và bảo toàn Google Drive media; chạy build/lint/SDD validation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Web deletion tests, TypeScript, production build, and Firebase rules dry-run pass.
- [x] #2 Relevant Android data/project/app unit tests pass.
- [x] #3 Cross-project, tombstone, retry, pending-media, and Google Drive preservation paths are covered by implementation/tests or verified inspection.
- [x] #4 SDD validation and task compliance/impact metadata pass.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read the linked permanent-deletion spec and inspect existing web/Firebase/Android deletion tests and build scripts.
2. Add focused regression coverage for retry/idempotency, authorization, active-project guard, pending media confirmation, tombstone read-only behavior, and cross-project/media isolation where gaps are concrete.
3. Run targeted Android unit tests, web tests/typecheck/build, and Firebase rules dry-run; fix only regressions from this feature.
4. Run broad relevant Gradle verification and diff hygiene checks.
5. Validate the task and SDD, record D1-D4 compliance and System Decision Impact, then complete the task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-flow for final verification wave. Scope: security, retry/idempotency, offline tombstone/read-only, media preservation, rules, and integrated build validation.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — implementation/verification preserves the approved permanent deletion lifecycle and its security/data-retention invariants.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — verification confirms the approved deletion lifecycle invariants
Final verification completed:
- Web tests: 24/24 pass
- webapp TypeScript noEmit pass
- webapp Next production build pass
- Firebase Firestore rules dry-run compile pass
- Android :data:testDebugUnitTest, :project:testDebugUnitTest, :app:testDebugUnitTest pass
- Android :data:compileDebugKotlin, :project:compileDebugKotlin, :app:compileDebugKotlin pass
- Feature-file git diff check pass; repository-wide diff check reports pre-existing blank-line-at-EOF in app/proguard-rules.pro and CameraOverlay.kt
- SDD validation passes with no errors; only informational pre-existing missing decision reference remains
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed)
Reconciled with the approved local-first spec revision: detached from the current spec execution set and retained as completed cloud-first baseline history. No implementation files changed.
<!-- SECTION:NOTES:END -->

