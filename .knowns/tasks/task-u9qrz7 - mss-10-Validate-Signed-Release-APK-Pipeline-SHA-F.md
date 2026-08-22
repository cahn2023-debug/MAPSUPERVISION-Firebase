---
id: u9qrz7
title: "[mss-10] Validate Signed Release APK Pipeline & SHA Fingerprint Match"
status: done
priority: medium
labels:
  - from-spec
  - spec:master-system-specification
  - wave:3
createdAt: '2026-08-22T16:36:49.124Z'
updatedAt: '2026-08-22T16:44:27.089Z'
completedAt: '2026-08-22T16:38:19.951Z'
timeSpent: 0
spec: specs/2026-08-22/master-system-specification
---
# [mss-10] Validate Signed Release APK Pipeline & SHA Fingerprint Match

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Validate KeyStore configuration, SHA-1/SHA-256 fingerprint matching with Firebase, and ProGuard/R8 rules
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Release signing configuration verified in local.properties
- [x] #2 ProGuard/R8 preserves Room entities, MapLibre, and LiteRT binaries
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Validated signed release APK runbooks, SHA fingerprint verification scripts, and ProGuard/R8 keep rules.

Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass
System Decision Impact: none — verified release pipeline.
<!-- SECTION:NOTES:END -->

