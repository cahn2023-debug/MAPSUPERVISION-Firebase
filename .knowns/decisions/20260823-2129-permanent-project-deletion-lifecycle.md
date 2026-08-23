---
id: 20260823-2129-permanent-project-deletion-lifecycle
title: Permanent Project Deletion Lifecycle
status: draft
supersedes: []
supersededBy: []
tags:
  - project
  - deletion
  - firebase
  - data-lifecycle
  - security
sources:
  - specs/2026-08-23/firebase-project-sync-approval-approved
  - patterns/project-scoped-database
  - specs/2026-08-23/permanent-project-deletion
relatedDocs:
  - specs/2026-08-23/firebase-project-sync-approval-approved
  - patterns/project-scoped-database
  - specs/2026-08-23/permanent-project-deletion
relatedTasks:
  - qwr70h
  - ut3bss
verification: []
reviewState: needs_evidence
reviewBlockers:
  - 'linked task "ut3bss" is "in-progress"; all linked tasks must be done before accepting decision "20260823-2129-permanent-project-deletion-lifecycle"'
reviewMatches: []
reviewAllowedResolutions: []
reviewEvaluatedAt: '2026-08-23T15:38:10.547Z'
createdAt: '2026-08-23T14:29:04.581Z'
updatedAt: '2026-08-23T15:38:10.548Z'
---

## Context

MapSupervision currently exposes project archiving and a scoped local clear operation, but it lacks a safe, auditable permanent deletion lifecycle across Firebase, local project databases, offline member devices, and project access state. The feature must preserve Google Drive media while deleting application data.

## Decision

Introduce an admin-only, idempotent permanent project deletion workflow. The project enters DELETING, access is blocked, Firebase application data and access metadata are removed while a minimal tombstone/audit record remains, and the initiating admin's local data is deleted only after cloud deletion succeeds. Other devices learn of deletion on reconnect; users may keep a read-only local copy and export it, but cannot edit or sync it.

## Alternatives Considered

Keep only a soft archive; delete local data only; delete media together with application data; block until all pending outbox work completes; perform cloud and local deletion synchronously.

## Consequences

Requires server-enforced authorization for project creator/super-admin, recent reauthentication, typed project confirmation, background deletion with resumable retry and failure state, conflict/idempotency controls, local database lifecycle coordination, offline deletion prompts, and tests across Firebase rules, repositories, workers, and UI. Google Drive media remains and needs an explicit retention/access policy outside this deletion transaction.
