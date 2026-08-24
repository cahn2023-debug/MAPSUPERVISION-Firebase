---
id: gh61ke
title: "[firebase-project-catalog-recovery-05] Khôi phục Firebase production có kiểm soát"
status: done
priority: high
labels:
  - from-spec
  - spec:firebase-project-catalog-recovery
  - spec-date:2026-08-24
createdAt: '2026-08-24T03:03:50.709Z'
updatedAt: '2026-08-24T04:07:54.430Z'
completedAt: '2026-08-24T04:07:54.430Z'
timeSpent: 0
spec: specs/2026-08-24/firebase-project-catalog-recovery-approved
fulfills:
  - AC-1
  - AC-2
  - AC-3
  - AC-6
  - AC-8
  - AC-9
  - AC-10
  - AC-13
order: 50
---
# [firebase-project-catalog-recovery-05] Khôi phục Firebase production có kiểm soát

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Thực hiện dry-run, review, xác nhận execute và kiểm tra hậu migration trên Firebase production theo @doc/specs/2026-08-24/firebase-project-catalog-recovery-approved. Không được ghi production trước xác nhận rõ ràng. Phụ thuộc: i7odyl.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Production dry-run hoàn tất không ghi dữ liệu, dùng fallback owner hợp lệ và báo cáo được review trước mọi execute.
- [x] #2 Execute chỉ chạy sau xác nhận rõ ràng và tạo kết quả idempotent cho toàn bộ project eligible.
- [x] #3 Project ACTIVE/ARCHIVED hiện có xuất hiện với đúng metadata cho một user thường; project đang/đã xóa không xuất hiện.
- [x] #4 Discrepancy còn lại được lưu và hiển thị cho admin; lỗi truy vấn có retry và access request/open/download được kiểm tra hậu migration.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Blocked pending prerequisite i7odyl Emulator/security verification and explicit production authorization. Dry-run requires a valid Firebase Auth fallback owner UID; execute must not run without explicit confirmation. No production writes performed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed) — production gate remains enforced.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass
Production migration completed after explicit user confirmation. Dry-run run af977d94-25ec-48a6-9722-9717c7a32c35: eligible 7, create 7, warning 7, discrepancy 0, no writes. Execute run e60cdb09-1004-4470-89e5-c00dd244cbce: 7 catalog entries created and admin-only report persisted with COMPLETED_WITH_WARNINGS. Post-execute dry-run f4d8c610-a2ec-48cd-a695-4eaee75173e6: eligible 7, create 0, unchanged 7, update/delete 0, proving idempotency. Spec Decision Compliance: D1=pass, D2=pass, D3=pass. System Decision Impact: candidate @decision/20260824-0931-public-project-catalog-ownership-metadata-and-recovery (changed).
<!-- SECTION:NOTES:END -->

