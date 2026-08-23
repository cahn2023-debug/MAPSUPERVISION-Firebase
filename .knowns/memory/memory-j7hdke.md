---
id: j7hdke
title: Firestore rules deployment versus dry-run
layer: project
category: failure
status: proposed
tags:
  - debug
  - firebase
  - firestore
  - android
  - deployment
createdAt: '2026-08-23T13:11:23.809Z'
updatedAt: '2026-08-23T13:11:23.809Z'
---

Root cause: a Firebase Android catalog can return PERMISSION_DENIED when local Firestore rules only passed --dry-run and the older cloud release remains active. Fix: deploy firestore.rules separately before indexes; remove composite indexes containing only a field plus __name__ when Firestore reports they are unnecessary. The GoogleApiManager 'Unknown calling package name com.google.android.gms' warning is separate from Firestore authorization.
