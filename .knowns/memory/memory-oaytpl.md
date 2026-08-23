---
id: oaytpl
title: Tabular import commit and metadata filtering pattern
layer: project
category: pattern
status: proposed
tags:
  - import
  - database
  - metadata
  - rollback
createdAt: '2026-08-23T09:11:43.329Z'
updatedAt: '2026-08-23T09:11:43.329Z'
---

Tabular imports validate all rows before confirmation, pass duplicate policy/key through mapping, and commit imported file plus geometry in one project-scoped Room transaction. Only user-confirmed unmapped columns are appended under ExtendedData; unconfirmed columns are omitted.
