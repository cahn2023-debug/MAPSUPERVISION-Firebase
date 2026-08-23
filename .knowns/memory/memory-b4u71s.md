---
id: b4u71s
title: Knowns Doc Section Update and Watcher Hash Pitfall
layer: project
category: failure
status: proposed
tags:
  - knowns
  - docs
  - watcher
  - windows
  - sdd
createdAt: '2026-08-23T10:28:04.475Z'
updatedAt: '2026-08-23T10:28:04.475Z'
---

On Knowns v0.30.0 for this Windows workspace, newly created docs may receive a watcher-normalized canonical hash. Before updating, re-read the current document and use its latest canonicalHash. A docs.update call with section replaces the whole section including its heading, so the replacement content must include the Markdown heading; otherwise repair by replacing full content through Knowns APIs. Never edit .knowns-managed markdown directly.
