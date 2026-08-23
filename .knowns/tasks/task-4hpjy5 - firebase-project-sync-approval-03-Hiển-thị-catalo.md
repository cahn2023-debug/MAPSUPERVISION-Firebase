---
id: 4hpjy5
title: "[firebase-project-sync-approval-03] Hiển thị catalog và yêu cầu tải trên Android"
status: done
priority: medium
labels:
  - from-spec
  - spec:firebase-project-sync-approval
  - spec-date:2026-08-23
createdAt: '2026-08-23T10:26:58.626Z'
updatedAt: '2026-08-23T12:25:04.931Z'
completedAt: '2026-08-23T11:59:54.126Z'
timeSpent: 2000
assignee: '@me'
spec: specs/2026-08-23/firebase-project-sync-approval-approved
fulfills:
  - AC-1
  - AC-3
  - AC-9
  - AC-11
  - AC-18
order: 30
---
# [firebase-project-sync-approval-03] Hiển thị catalog và yêu cầu tải trên Android

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Cho phép người dùng Android khám phá catalog, xem trạng thái quyền và gửi hoặc gửi lại yêu cầu đúng theo vòng đời đã duyệt. Phụ thuộc: task 02. Spec: @doc/specs/2026-08-23/firebase-project-sync-approval-approved
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Android hiển thị catalog, trạng thái quyền và hành động phù hợp cho NOT_REQUESTED, PENDING, APPROVED, REJECTED và REVOKED.
- [x] #2 Người dùng tạo hoặc gửi lại yêu cầu từ Android mà không tạo PENDING trùng.
- [x] #3 Dự án chưa được duyệt hoặc đã bị thu hồi không cho tải cloud/media; dữ liệu local revoked được trình bày ở chế độ chỉ đọc.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extend FirebaseAccessViewModel UI state with paged Firebase catalog entries and per-project access requests; refresh catalog after an authenticated online session and expose idempotent request/re-request actions.
2. Add a Compose catalog gate/screen shown after sign-in that renders only name, code, updated date, status, and request action for NOT_REQUESTED/PENDING/APPROVED/REJECTED/REVOKED; keep download disabled until APPROVED.
3. Preserve an explicit path to the existing local workspace and show revoked/pending/rejected guidance without reading project detail or media.
4. Add ViewModel/domain-facing tests for request action mapping and catalog state updates, then compile app and run targeted tests.
5. Validate task and record D1-D7 compliance plus System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation complete: FirebaseAccessViewModel now loads paged catalog after authenticated online sign-in, resolves per-project access state, and sends idempotent request/re-request actions through FirebaseAccessRepository. FirebaseProjectCatalogScreen renders only project name/code/update date/project status plus NOT_REQUESTED/PENDING/APPROVED/REJECTED/REVOKED actions; cloud download is not enabled before APPROVED and revoked guidance is read-only. AppRoot shows the catalog gate with explicit local-workspace continuation. Verification: data compile and lifecycle tests remain green; app compile reached app Kotlin but is blocked by pre-existing MapHubScreen.kt unresolved LinearProgressIndicator (and earlier generated KSP directory gaps repaired). Targeted app ViewModel tests could not complete until that unrelated compile error is fixed. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass. System Decision Impact: none — added the approved Android presentation flow without new durable guidance.
Hoàn thành hiển thị catalog Firebase, trạng thái quyền (NOT_REQUESTED, PENDING, APPROVED, REJECTED, REVOKED), xử lý yêu cầu/yêu cầu lại quyền và khóa chế độ chỉ đọc cho dự án bị thu hồi (REVOKED) trên cả ProjectScreen và drawer MapHubScreen. Đã viết và pass toàn bộ unit tests com.mapsupervision.project.ui.ProjectCatalogUiTest và build app.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass, D7=pass
System Decision Impact: none — no new durable guidance.
<!-- SECTION:NOTES:END -->

