---
id: llpwuu
title: 'Low-Memory Build Configuration & JVM Daemon Heap Control'
layer: project
category: convention
status: active
tags:
  - build
  - gradle
  - memory
  - jvm
createdAt: '2026-08-22T16:48:26.011Z'
updatedAt: '2026-08-22T16:48:26.011Z'
---

To prevent out-of-memory errors on constrained development machines, Gradle JVM heap is capped at -Xmx1536m in gradle.properties and root build.gradle.kts. maxParallelForks is set to 1 to serialize unit test execution. Modules should not run unconstrained parallel daemons.
