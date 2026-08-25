---
id: ly12b4
title: "[firebase-project-catalog-recovery-02] Khôi phục catalog bằng migration idempotent"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-catalog-recovery
  - spec-date:2026-08-24
createdAt: '2026-08-24T03:03:50.548Z'
updatedAt: '2026-08-25T06:52:07.949Z'
completedAt: '2026-08-24T03:41:52.575Z'
timeSpent: 98318
assignee: '@me'
spec: specs/2026-08-24/firebase-project-catalog-recovery-approved
fulfills:
  - AC-3
  - AC-4
  - AC-5
  - AC-6
  - AC-9
order: 20
---
# [firebase-project-catalog-recovery-02] Khôi phục catalog bằng migration idempotent

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cung cấp migration Admin SDK, fallback owner, lọc vòng đời xóa và báo cáo discrepancy theo @doc/specs/2026-08-24/firebase-project-catalog-recovery-approved. Phụ thuộc: ry4iav.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Dry-run không ghi dữ liệu và báo đủ eligible, create, update, unchanged, delete, warning và discrepancy.
- [x] #2 Metadata thiếu được suy ra xác định được; owner thiếu dùng fallback Firebase Auth UID hợp lệ và execute bị chặn nếu UID không tồn tại.
- [x] #3 Execute idempotent, không đổi owner hợp lệ, không tạo duplicate và loại catalog của project DELETING, DELETED hoặc có tombstone.
- [x] #4 Run có sai lệch lưu báo cáo admin-only, không chứa business payload và kết thúc bằng COMPLETED_WITH_WARNINGS.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Establish a pure catalog migration model/planner in `webapp/lib/project-catalog-migration.ts` for project envelopes, deterministic metadata fallback, owner fallback, ACTIVE/ARCHIVED eligibility, deletion/tombstone filtering, exact diff counts, warnings and discrepancies.
2. Add an Admin SDK CLI entrypoint under `webapp/scripts/` with explicit `--dry-run`/confirmed execute modes, paginated reads, bounded batches, idempotent writes/deletes, fallback Firebase Auth UID validation, and persisted admin-only run reports.
3. Add the `catalogMigrations` admin-read rule and the package script while keeping service-account handling in the existing `webapp/lib/firebase-admin.ts` boundary.
4. Add focused web tests for deterministic normalization, missing-owner fallback, invalid fallback rejection, idempotent plans, deletion/tombstone filtering, dry-run no-write behavior and COMPLETED_WITH_WARNINGS reports.
5. Run web tests, TypeScript/build checks, Firestore rules compile, validate the task, and record D1=pass, D2=pass, D3=pass plus the existing System Decision candidate before handing off to task 03.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using kn-flow after task ry4iav completed. Starting migration/report implementation; production execute remains out of scope until task 05 confirmation gate.
Plan saved. Production execute is intentionally deferred to task gh61ke; this task implements/test-drives the migration tool and report contract.
Validation: webapp npm test 31/31 pass; npx tsc --noEmit pass; firebase deploy --only firestore:rules --dry-run pass. Reviewer P2 malformed catalog/tombstone handling addressed by preserving catalog document IDs in asCatalogRows; warning status is COMPLETED_WITH_WARNINGS. Dry-run report remains stdout-only by design; execute persists admin-only catalogMigrations report. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed) — migration/report contract and idempotent recovery behavior. Emulator persona verification remains blocked by firebase-tools requiring Java 21 while installed JDK is 17; deferred to integrated verification.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass
<!-- SECTION:NOTES:END -->

