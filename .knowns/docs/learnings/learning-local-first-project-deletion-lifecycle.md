---
id: doc-5552a2952ff80ae71093bbd4647e5d1f
title: 'Learning: Local-first project deletion lifecycle'
description: Reusable lifecycle, concurrency, and recovery lessons from permanent project deletion
createdAt: '2026-08-24T10:27:53.097Z'
updatedAt: '2026-08-24T10:27:53.097Z'
tags:
  - learning
  - firebase
  - android
  - deletion
  - data-lifecycle
  - security
---

# Learning: Local-first project deletion lifecycle

## Patterns

### Separate local cleanup from the Cloud decision
- **What:** Classify projects by confirmed Cloud presence. Never-uploaded projects use local-only deletion. Uploaded projects delete local data first, then remain Cloud-accessible until an authorized administrator chooses retain/restore or resumable Cloud deletion.
- **When to use:** Destructive workflows where local databases, shared Cloud state, and offline devices have different lifecycles.
- **Source:** @task-930kkg, @doc/specs/2026-08-23/permanent-project-deletion

### First-write-wins with checkpointed Cloud cleanup
- **What:** Let any authorized project administrator record the first Cloud decision, then keep one active worker/checkpoint owner for that deletion request. Retries resume the same request while tombstone/audit records remain intact.
- **When to use:** Multi-admin workflows with concurrent decisions or failures during destructive Cloud cleanup.
- **Source:** @task-930kkg, @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision

## Decisions

### Local-first deletion is the current lifecycle
- **Chose:** Delete local data first for uploaded projects, then require an explicit administrator Cloud decision.
- **Over:** Cloud-first deletion, automatic Cloud deletion after local cleanup, or blocking all Cloud access while waiting.
- **Tag:** GOOD_CALL / TRADEOFF
- **Outcome:** The verified implementation supports retain/restore and independently retryable Cloud deletion while preserving Google Drive media and permissions.
- **Recommendation:** Keep local cleanup and Cloud cleanup as independently resumable state machines.
- **Source:** @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision

### Google Drive media remains outside application-data deletion
- **Chose:** Preserve Google Drive media and permissions during project application-data deletion.
- **Over:** Deleting or mutating Drive media as part of the project deletion transaction.
- **Tag:** GOOD_CALL
- **Outcome:** Cloud cleanup remains scoped to application data, access metadata, tombstone, and audit requirements.
- **Recommendation:** Treat external media retention as an explicit boundary in future deletion designs.
- **Source:** @task-930kkg, @doc/specs/2026-08-23/permanent-project-deletion

## Failures

### Restore success must normalize the local lifecycle state
- **What went wrong:** A successful Cloud-retain/restore path could leave the local project in a non-"ACTIVE" state, allowing stale lifecycle actions to remain visible.
- **Root cause:** The success transition and action-visibility guard were not both enforced at the restore boundary.
- **Time lost:** Not recorded.
- **Prevention:** Assert that successful restore returns to "ACTIVE", make retain/restore retryable, and hide stale actions for every non-"ACTIVE" state.
- **Source:** @task-930kkg, @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision

## Canonical references

- Accepted System Decision: @decision/20260824-1520-local-first-project-deletion-with-administrator-cloud-decision
- Specification: @doc/specs/2026-08-23/permanent-project-deletion
- Project-scoped database pattern: @doc/patterns/project-scoped-database
