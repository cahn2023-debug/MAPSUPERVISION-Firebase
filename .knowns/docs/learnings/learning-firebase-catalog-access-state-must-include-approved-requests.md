---
id: doc-7caf63657d28fbda63878b564dd31929
title: 'Learning: Firebase catalog access state must include approved requests'
description: Reusable debugging pattern for Firebase catalog visibility versus project data access
createdAt: '2026-08-24T06:33:35.679Z'
updatedAt: '2026-08-24T06:33:35.679Z'
tags:
  - learning
  - firebase
  - android
---

## Problem

A Firebase project catalog can load while opening the project shows no data.

## Root Cause

The catalog is public, but project data is protected by approved access. Android previously populated its local permission state only from projects/{projectId}/projectMembers, while the approval flow stored approvals in ccessRequests/{projectId}__{uid}. The UI also launched pullChanges() in a detached coroutine and ignored failures, masking permission errors as an empty project.

## Fix

When building Android access state, merge active projectMembers and APPROVED access requests. When opening a project, await pullChanges() and surface AppResult.Error before leaving the catalog screen. Keep Firestore rules aligned: missing request documents may be read by an authenticated user because they contain no data; existing request documents remain owner-only/admin-readable.

## Verification

./gradlew :data:compileDebugKotlin :app:compileDebugKotlin and FirebaseAccessViewModelTest passed.

## Provenance

Commit 400ed0e; approved specs @doc/specs/2026-08-24/firebase-project-catalog-recovery-approved and @doc/specs/2026-08-23/firebase-project-sync-approval-approved.
