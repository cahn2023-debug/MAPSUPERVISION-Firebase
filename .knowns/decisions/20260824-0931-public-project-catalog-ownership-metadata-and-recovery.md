---
id: 20260824-0931-public-project-catalog-ownership-metadata-and-recovery
title: Public Project Catalog Ownership Metadata and Recovery
status: draft
supersedes: []
supersededBy: []
tags:
  - firebase
  - catalog
  - migration
  - security
  - android
sources:
  - specs/2026-08-24/firebase-project-catalog-recovery
  - specs/2026-08-23/firebase-project-sync-approval-approved
  - 'firestore.rules:160'
  - 'data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt:353'
  - 'webapp/lib/sync.ts:375'
  - specs/2026-08-24/firebase-project-catalog-recovery-approved
relatedDocs:
  - specs/2026-08-24/firebase-project-catalog-recovery
  - specs/2026-08-23/firebase-project-sync-approval-approved
  - specs/2026-08-24/firebase-project-catalog-recovery-approved
relatedTasks:
  - 93i0ba
  - 4hpjy5
  - ry4iav
  - ly12b4
  - u5blkn
  - i7odyl
  - gh61ke
verification: []
reviewState: needs_evidence
reviewBlockers:
  - 'linked task "ry4iav" is "todo"; all linked tasks must be done before accepting decision "20260824-0931-public-project-catalog-ownership-metadata-and-recovery"'
reviewMatches: []
reviewAllowedResolutions: []
reviewEvaluatedAt: '2026-08-24T03:04:41.396Z'
createdAt: '2026-08-24T02:31:14.795Z'
updatedAt: '2026-08-24T03:04:41.397Z'
---

## Context

Android can return an empty Cloud project list because legacy projects may exist only in /projects, while current projectCatalog writers include createdByUid but Firestore exact-shape rules reject that field. The approved catalog contract previously excluded creator UID.

## Decision

Expand the authenticated projectCatalog projection to include public createdByUid, keep that UID immutable, align all writers and rules, and use an idempotent Firebase Admin SDK migration with mandatory production dry-run to reconcile existing ACTIVE/ARCHIVED projects. Persist partial discrepancies as admin-only migration warnings.

## Alternatives Considered

Keep creator UID private and authorize deletion only through a server lookup; personalize catalog with isCreator; depend on admin client login for backfill; repair only future projects; fail the whole migration on any discrepancy.

## Consequences

Every authenticated user can observe project creator UIDs. Rules and writers must enforce exact shape and owner immutability. Production rollout requires rules-first deployment, validated fallback Firebase Auth UID, dry-run/confirmation, migration reporting, admin warning UI, and emulator coverage.
