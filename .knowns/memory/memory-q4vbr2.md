---
id: q4vbr2
title: 'Signed Release APK Packaging & Firebase Google Auth Fingerprint Match'
layer: project
category: convention
status: active
tags:
  - release
  - apk
  - keystore
  - sha
  - powershell
createdAt: '2026-08-22T16:48:30.117Z'
updatedAt: '2026-08-23T03:18:38.618Z'
---

Production release APKs use the four RELEASE_* signing properties in local.properties, as consumed by app/build.gradle.kts. On Windows, scripts/build-release.ps1 validates those properties and the keystore path, then runs :app:assembleRelease --no-daemon without printing passwords. Register the SHA-1 and SHA-256 fingerprints of both debug and release certificates in Firebase so Google Sign-In works across build variants. Full reference: @doc/build-conventions
