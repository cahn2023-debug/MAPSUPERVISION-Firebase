---
id: vy4got
title: "[permanent-project-deletion-revision] Đặc tả xóa local trước và admin quyết định dữ liệu Cloud"
status: done
priority: high
labels:
  - spec-revision
  - project-deletion
  - android
  - firebase
createdAt: '2026-08-24T02:29:39.776Z'
updatedAt: '2026-08-24T09:52:38.575Z'
completedAt: '2026-08-24T08:21:32.060Z'
timeSpent: 21
assignee: '@me'
spec: specs/2026-08-23/permanent-project-deletion
---
# [permanent-project-deletion-revision] Đặc tả xóa local trước và admin quyết định dữ liệu Cloud

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Sửa đổi @doc/specs/2026-08-23/permanent-project-deletion cho luồng Android: dự án chưa upload chỉ xóa local; dự án đã upload xóa local trước và tạo thông báo để admin đăng nhập quyết định giữ hay xóa dữ liệu Cloud. Đây là work item tách khỏi hồi quy Firebase project catalog.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Dự án chưa từng upload Cloud được xóa hoàn toàn trên thiết bị mà không tạo yêu cầu xóa Cloud.
- [x] #2 Dự án đã upload Cloud được xóa local và tạo một quyết định chờ admin, không tự xóa dữ liệu Cloud.
- [x] #3 Khi admin đăng nhập, Android hiển thị popup idempotent cho phép giữ dự án Cloud hoặc khởi tạo xóa Cloud an toàn.
- [x] #4 Spec sửa đổi xác định rõ quyền, trạng thái, offline/retry, audit và quan hệ với deletion lifecycle hiện có.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Update the canonical spec's approval and System Decision linkage to the local-first lifecycle.
2. Reconcile the locked decisions, requirements, acceptance criteria, scenarios, task links, and open questions with the user's confirmed decisions D1-D13.
3. Create and link a draft System Decision candidate for the durable local-first Cloud-decision contract without auto-accepting it.
4. Validate the spec and task references, then verify the revision against the task acceptance criteria.
5. Record Spec Decision Compliance for D1-D13 and the System Decision Impact marker before completing the documentation revision.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Flow ownership taken for the approved local-first specification revision. Existing active timer ly12b4 belongs to an unrelated task, so no timer was replaced or stopped.
Completed the approved local-first specification revision. Validation: spec SDD valid with 0 errors and 0 warnings; 13 Locked Decisions and draft System Decision Impact declared. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass. System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — establishes the local-first branch, administrator Cloud decision, restore/retry, independent failure handling, race policy, and Google Drive preservation.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass, D8=pass, D9=pass, D10=pass, D11=pass, D12=pass, D13=pass
System Decision Impact: candidate @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision (changed) — linked evidence confirms the approved local-first lifecycle.
<!-- SECTION:NOTES:END -->

