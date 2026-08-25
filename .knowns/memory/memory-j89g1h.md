---
id: j89g1h
title: ''
layer: project
category: build-release
status: proposed
tags: []
createdAt: '2026-08-25T17:20:54.005Z'
updatedAt: '2026-08-25T17:20:54.005Z'
---

When installing release APKs on Android devices, if ABI split is enabled without isUniversalApk = true or without explicit V2/V3 signing, Android PackageInstaller reports 'Chưa cài đặt được ứng dụng do gói có vẻ không hợp lệ' (INSTALL_PARSE_FAILED_NO_CERTIFICATES / ABI mismatch). Fixed by enabling isUniversalApk = true, enableV1Signing = true, enableV2Signing = true, enableV3Signing = true in app/build.gradle.kts and using app-universal-release.apk.
