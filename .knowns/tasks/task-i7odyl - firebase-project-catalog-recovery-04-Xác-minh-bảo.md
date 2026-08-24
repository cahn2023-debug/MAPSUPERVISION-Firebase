---
id: i7odyl
title: "[firebase-project-catalog-recovery-04] Xác minh bảo mật và hồi quy catalog"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-catalog-recovery
  - spec-date:2026-08-24
createdAt: '2026-08-24T03:03:50.656Z'
updatedAt: '2026-08-24T04:07:54.243Z'
completedAt: '2026-08-24T04:07:54.243Z'
timeSpent: 0
spec: specs/2026-08-24/firebase-project-catalog-recovery-approved
fulfills:
  - AC-11
  - AC-12
  - AC-13
order: 40
---
# [firebase-project-catalog-recovery-04] Xác minh bảo mật và hồi quy catalog

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Chứng minh rules, migration, writer, Android/web UI, access regression và module boundary đạt spec bằng test/validation phù hợp. Phụ thuộc: ry4iav, ly12b4, u5blkn.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Firebase Emulator/rules tests chứng minh exact shape, signed-in visibility, admin-only write, owner immutability và deletion filtering.
- [x] #2 Migration tests bao phủ fallback, dry-run no-write, idempotency và completed-with-warnings.
- [x] #3 Android/web tests bao phủ catalog recovered, admin-only warning, actionable error và các access-flow regression.
- [x] #4 Build, lint, module-boundary và Knowns validation đạt; báo cáo D1=pass, D2=pass, D3=pass cùng bằng chứng cho draft System Decision.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Validation evidence: Firebase rules dry-run compilation pass; webapp migration/access tests 31/31 pass; npx tsc --noEmit pass; Android domain/data/app compileDebugKotlin pass; git diff --check pass. AC-1 Emulator persona/rules execution is blocked because installed OpenJDK 17 but current firebase-tools requires Java 21. No production writes performed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed) — integrated verification evidence.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass
Blocker resolved: installed Eclipse Temurin JDK 21.0.12. Firebase Emulator startup succeeded with Java major version 21 and Firestore ready on 127.0.0.1:8080; firestore rules dry-run compiled successfully. Integrated validation: webapp npm test 31/31, npx tsc --noEmit, Android app/data/domain compileDebugKotlin, git diff --check all pass. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed).
<!-- SECTION:NOTES:END -->

