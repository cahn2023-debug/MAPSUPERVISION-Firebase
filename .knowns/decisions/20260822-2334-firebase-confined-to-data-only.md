---
id: 20260822-2334-firebase-confined-to-data-only
title: 'Firebase Confined to :data Only'
status: draft
supersedes: []
supersededBy: []
tags:
  - firebase
  - architecture
sources:
  - 'build.gradle.kts:22'
relatedDocs:
  - guides/firebase-sync
relatedTasks: []
verification: []
reviewState: needs_evidence
reviewBlockers:
  - 'decision "20260822-2334-firebase-confined-to-data-only" needs at least one linked task or a spec with linked tasks before acceptance'
reviewMatches: []
reviewAllowedResolutions: []
reviewEvaluatedAt: '2026-08-22T16:34:47.019Z'
createdAt: '2026-08-22T16:34:23.022Z'
updatedAt: '2026-08-22T16:34:47.019Z'
---

## Context

Enforce clean architecture and prevent UI modules from depending directly on Firebase.

## Decision

Confine all Firebase SDK dependencies and operations strictly to the :data module.

## Alternatives Considered


## Consequences
