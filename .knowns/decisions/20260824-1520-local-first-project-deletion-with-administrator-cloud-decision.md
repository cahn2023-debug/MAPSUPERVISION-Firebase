---
id: 20260824-1520-local-first-project-deletion-with-administrator-cloud-decision
title: Local-first project deletion with administrator Cloud decision
status: draft
supersedes: []
supersededBy: []
tags:
  - project
  - deletion
  - firebase
  - android
  - data-lifecycle
  - security
sources:
  - specs/2026-08-23/permanent-project-deletion
  - specs/2026-08-23/firebase-project-sync-approval-approved
  - patterns/project-scoped-database
relatedDocs:
  - specs/2026-08-23/permanent-project-deletion
  - patterns/project-scoped-database
relatedTasks:
  - vy4got
  - iixn7n
  - y5uqki
  - 930kkg
  - t9fie1
  - avrsg3
verification: []
reviewState: ready_for_review
reviewBlockers: []
reviewMatches: []
reviewAllowedResolutions:
  - accept_new
  - reject_new
reviewEvaluatedAt: '2026-08-24T09:53:05.485Z'
createdAt: '2026-08-24T08:20:40.359Z'
updatedAt: '2026-08-24T09:53:05.485Z'
---

## Context

The existing permanent deletion lifecycle deletes Cloud application data before the initiating device completes local cleanup. The revised approved specification requires local-first handling and an explicit administrator decision for Cloud data.

## Decision

Classify projects by confirmed Cloud presence. Never-uploaded projects are local-only deletions. Uploaded projects delete local data first, then remain Cloud-accessible while any authorized project administrator chooses either Cloud retention with local restore/retry or a separately resumable Cloud deletion. The first recorded decision wins, local and Cloud retries are independent, and Google Drive media and permissions are never changed.

## Alternatives Considered

Keep the previous Cloud-first deletion flow; automatically delete Cloud data after local deletion; block all Cloud access while waiting for an administrator decision.

## Consequences

Requires new lifecycle states and decision records, any-project-admin authorization for the decision, idempotent retain/delete races, local restore retry, independent local/Cloud failure handling, and updated Android/Firebase/UI/test contracts. The prior Cloud-first decision must not be accepted as the current guidance without reconciliation.
