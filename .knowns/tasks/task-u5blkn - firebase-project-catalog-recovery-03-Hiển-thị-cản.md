---
id: u5blkn
title: "[firebase-project-catalog-recovery-03] Hiển thị cảnh báo admin và lỗi catalog chính xác"
status: done
priority: medium
labels:
  - from-spec
  - spec:firebase-project-catalog-recovery
  - spec-date:2026-08-24
createdAt: '2026-08-24T03:03:50.605Z'
updatedAt: '2026-08-24T03:52:12.591Z'
completedAt: '2026-08-24T03:51:22.664Z'
timeSpent: 0
spec: specs/2026-08-24/firebase-project-catalog-recovery-approved
fulfills:
  - AC-9
  - AC-10
  - AC-13
order: 30
---
# [firebase-project-catalog-recovery-03] Hiển thị cảnh báo admin và lỗi catalog chính xác

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Hiển thị migration warning cho admin trên Android/web và phân biệt lỗi truy vấn với empty state, đồng thời giữ nguyên access flows theo @doc/specs/2026-08-24/firebase-project-catalog-recovery-approved. Phụ thuộc: ry4iav, ly12b4.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Admin thấy cảnh báo và chi tiết hành động phù hợp cho migration COMPLETED_WITH_WARNINGS trên Android và web; user thường không thấy báo cáo nội bộ.
- [x] #2 Lỗi Firestore/rules hiện error state có retry, không bị trình bày như danh sách Cloud rỗng hợp lệ.
- [x] #3 Response catalog thành công rỗng vẫn dùng empty state; catalog có dữ liệu hiển thị đầy đủ metadata đã duyệt.
- [x] #4 Request, approve, open và download project tiếp tục giữ hành vi của spec Firebase Project Sync Approval.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented Android and web catalog error/empty separation and admin-only migration warning. Android now queries latest catalogMigrations report and shows COMPLETED_WITH_WARNINGS banner only to admins; catalog query failures render retry state instead of empty success. Web subscription surfaces project query errors with retry and admin migration warning; regular users do not see report. Validation: ./gradlew :domain:compileDebugKotlin :data:compileDebugKotlin and :app:compileDebugKotlin pass; webapp npm test 31/31 and npx tsc --noEmit pass; git diff --check pass. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed) — admin warning and explicit catalog error-state behavior.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass
<!-- SECTION:NOTES:END -->

