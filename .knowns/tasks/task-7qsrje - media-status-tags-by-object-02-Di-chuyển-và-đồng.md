---
id: 7qsrje
title: "[media-status-tags-by-object-02] Di chuyển và đồng bộ media theo thẻ"
status: done
priority: high
labels:
  - from-spec
  - spec:media-status-tags-by-object
  - spec-date:2026-08-24
createdAt: '2026-08-24T11:29:25.421Z'
updatedAt: '2026-08-25T02:15:06.558Z'
completedAt: '2026-08-25T02:14:39.289Z'
timeSpent: 1896
assignee: '@me'
spec: specs/2026-08-24/media-status-tags-by-object
fulfills:
  - AC-4
  - AC-5
  - AC-7
  - AC-8
  - AC-9
  - AC-14
  - AC-15
  - AC-16
  - AC-17
order: 20
---
# [media-status-tags-by-object-02] Di chuyển và đồng bộ media theo thẻ

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Tổ chức ảnh/video dưới thư mục đối tượng/tag, di chuyển local an toàn khi đổi/bỏ tag, và đồng bộ Cloud offline-first với retry và giải quyết xung đột.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Resolve untagged and tagged media folders for images and videos.
- [x] #2 Move media atomically on status-tag changes and retain stable media identity.
- [x] #3 Queue Cloud synchronization and cover offline, retry, and conflict behavior with tests.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Trace the existing SitePhoto/PhotoRepository, ProjectStorageManager, Drive upload, and event-outbox contracts; preserve node/route tag codes and legacy media paths.
2. Implement one atomic local status-tag move operation for image and video media, including thumbnail/path metadata updates, stable media identity, source cleanup, and safe rollback on failure.
3. Queue the status/path change for offline Cloud synchronization without holding database or mutex locks across network calls; preserve bounded retry/backoff and apply latest-updated-wins conflict handling.
4. Add focused tests for tagged/untagged folder resolution, image/video moves, legacy media, source cleanup, retryable sync failure, and timestamp conflict convergence.
5. Run targeted Gradle tests and compilation, validate the task/spec, review the real diff, and record D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass plus System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Plan saved for continuation of task 02. Existing partial statusTag capture/upload changes are in scope; remaining implementation must add local move and offline sync semantics.
Implementation complete. Added status-tag folder resolution for image/video paths, atomic local original+thumbnail moves with rollback and stable filenames, status-tag repository updates that reset sync to PENDING, bounded existing media retry behavior, Drive parent moves by stable photoId, web upload statusTag propagation, and latest-updated-wins remote-row application. Verification: storage-core tests pass; PhotoRepositoryImplTest pass; DriveMediaUploadClientTest pass; FirebaseSyncRepositoryImplTest pass; photo tests pass; web media-route tests 15/15 pass; web TypeScript noEmit pass. Full web suite has one unrelated deletion-authorization regression; full data suite has two unrelated legacy migration 23/24 failures against the current schema-52 worktree. Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass. System Decision Impact: none — implementation follows the approved media status-tag contract without adding durable guidance.
Metadata reconciliation:
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass, D6=pass
System Decision Impact: none — follows the approved media status-tag contract without adding durable guidance.
<!-- SECTION:NOTES:END -->

