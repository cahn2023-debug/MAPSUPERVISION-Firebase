---
id: fdot1g
title: Import picker no-response guard
layer: project
category: failure
status: proposed
tags:
  - debug
  - import
  - picker
  - android
createdAt: '2026-08-23T09:48:30.113Z'
updatedAt: '2026-08-23T09:48:30.113Z'
---

Root cause pattern: empty picker callbacks and an uncaught exception at the top of the import coroutine can make file import appear inert. Provide immediate picker feedback, catch picker launch failures, and convert import coroutine failures into ImportStatus.FAILED plus a user-facing message. MediaProvider revoke_uri_permission warnings during storage-provider file moves are system warnings, not proof of the import failure.
