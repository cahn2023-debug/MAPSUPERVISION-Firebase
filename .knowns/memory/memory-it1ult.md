---
id: it1ult
title: '18-Module Clean Architecture & Boundary Rules'
layer: project
category: convention
status: proposed
tags:
  - architecture
  - modules
  - gradle
createdAt: '2026-08-22T16:47:25.015Z'
updatedAt: '2026-08-22T16:47:25.015Z'
---

MapSupervision strictly segregates code into 18 Gradle modules. ':app' is the single aggregator depending on all modules. ':core' has empty dependencies. Presentation never depends on Data directly; interactions route via ':domain' interfaces and AppResult<T>. Boundary constraints are automatically validated via './gradlew enforceModuleBoundaries'.
