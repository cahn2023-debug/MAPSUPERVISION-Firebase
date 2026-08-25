---
id: c8ijdc
title: Local-first project deletion lifecycle
layer: project
category: pattern
status: proposed
tags:
  - firebase
  - android
  - deletion
  - data-lifecycle
createdAt: '2026-08-24T10:28:06.957Z'
updatedAt: '2026-08-24T10:28:06.957Z'
---

For project deletion, classify confirmed Cloud presence first: never-uploaded projects are local-only; uploaded projects delete local data first, then wait for an authorized administrator's first-write-wins retain or resumable Cloud-delete decision. Keep local and Cloud retries independent, preserve Google Drive media/permissions, and normalize successful restore to ACTIVE. Full reference: @doc/learnings/learning-local-first-project-deletion-lifecycle
