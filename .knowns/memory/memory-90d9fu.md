---
id: 90d9fu
title: Public Project Slug and Name Normalization Matching
layer: project
category: failure
status: proposed
tags:
  - debug
  - public-api
  - firestore
createdAt: '2026-08-26T08:19:52.506Z'
updatedAt: '2026-08-26T08:19:52.506Z'
---

Root cause: findPublicProject used strict equality normalize(value) === normalize(slug) which failed to match project 'Dự án 269 - 2026' (normalized as 'duan2692026') and slug 'd-n-269---2026' ('dn2692026') against target '2692026'. Fix: filter out deleted/tombstone projects, check for normalized inclusion and multi-token matching ('269' & '2026'), and wrap public API handlers in try/catch with GOOGLE_SERVICE_ACCOUNT_JSON fallback in firebase-admin.ts.
