---
id: ut3bss
title: "[permanent-project-deletion-02] Enforce Firebase deletion authorization and resumable cloud cleanup"
status: done
priority: high
labels:
  - from-spec
  - spec:permanent-project-deletion
  - spec-date:2026-08-23
createdAt: '2026-08-23T14:33:39.869Z'
updatedAt: '2026-08-25T01:33:44.952Z'
completedAt: '2026-08-23T15:56:24.555Z'
timeSpent: 2023
order: 20
---
# [permanent-project-deletion-02] Enforce Firebase deletion authorization and resumable cloud cleanup

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Triển khai server/Firebase enforcement cho creator hoặc super-admin, reauthentication evidence, typed project identity check, idempotent deletion request, DELETING/DELETE_FAILED state, checkpoint/retry, xóa application data/access/outbox, giữ tombstone và audit tối thiểu, không xóa Google Drive media.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Unauthorized or non-creator deletion attempts are rejected; active projects, stale reauth, and mismatched typed identity are rejected.
- [x] #2 A valid request transitions project to DELETING exactly once and retrying the same requestId resumes existing checkpoint.
- [x] #3 All listed application/access/catalog data is deleted with checkpoint persistence; Google Drive media is never addressed.
- [x] #4 Partial failure persists DELETE_FAILED and keeps project locked; retry resumes without a duplicate request.
- [x] #5 Firestore rules compile and deny project reads/writes/media during deletion while allowing admin-only tombstone/audit reads.
- [x] #6 Automated web tests, TypeScript check, and production build pass.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Chuẩn hóa helper và route deletion để dùng kiểu Firebase Admin hợp lệ, xác thực creator/super-admin, reauth, typed identity, active-project guard và idempotency.
2. Hoàn thiện cloud cleanup theo checkpoint/resume: application collections, members/access requests, user catalog references, project catalog; giữ Google Drive media.
3. Bảo đảm DELETE_FAILED retry và tombstone/audit không làm lộ business data, đồng thời giữ project locked trong mọi trạng thái xoán.
4. Sửa và kiểm tra Firestore rules để chặn đọc/ghi/media/sync khi project không ACTIVE, chỉ cho audit/tombstone admin đọc.
5. Thêm test/helper coverage cho authorization, stale reauth, identity mismatch, duplicate request, checkpoint, failure mapping và media preservation.
6. Chạy typecheck/build/lint/test/rules validation, validate task và ghi compliance/impact trước khi review.

Spec decisions: D1=pass; D2=pass (server tombstone contract consumed by offline task); D3=pass; D4=pass.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan saved; ownership taken for Firebase authorization and resumable cloud cleanup.
Implementation complete: route/helper, Firestore rules, project creator ownership, and deletion authorization/checkpoint tests added.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass.
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — formalizes cloud-first deletion checkpoint, lock state, authorization, and media-preservation contract already captured by approved spec.
Review: PASS with P2 deferrals — route endpoint integration/rules race tests remain follow-up; lease fencing prevents stale workers from committing, while a very long single recursiveDelete may still duplicate underlying work after lease expiry.
Final verification: web tests 24/24 pass; TypeScript noEmit pass; Next production build pass; Firebase rules dry-run compile pass; npm lint remains unavailable because next lint prompts interactively.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass. System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — implementation/verification preserves the approved permanent deletion lifecycle and its security/data-retention invariants.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: candidate @decision/20260823-2129-permanent-project-deletion-lifecycle (changed) — verification confirms the approved deletion lifecycle invariants
Reconciled with the approved local-first spec revision: detached from the current spec execution set and retained as completed cloud-first baseline history. No implementation files changed.
SDD metadata cleanup: removed stale fulfills links because this completed task is retained as detached historical baseline after the local-first spec revision; implementation history and acceptance criteria remain unchanged.
SDD metadata cleanup completed: stale fulfills links cleared because this completed task is retained as detached historical baseline after the local-first spec revision; implementation history and acceptance criteria remain unchanged.
<!-- SECTION:NOTES:END -->

