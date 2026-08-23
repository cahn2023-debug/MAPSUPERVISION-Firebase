---
id: t8al8x
title: 'Hybrid Edge & Cloud AI Orchestration with LiteRT/Gemma Safety'
layer: project
category: pattern
status: active
tags:
  - ai
  - gemma
  - litert
  - rag
createdAt: '2026-08-22T16:48:09.022Z'
updatedAt: '2026-08-22T16:48:09.022Z'
---

AiOrchestrator coordinates 6 execution engines: CloudGeminiEngine, LocalLiteRtEngine, MediaPipeLlmEngine (Gemma), MlKitVisionEngine, TfliteVisionEngine, and RuleBasedEngine fallback. LocalLiteRtEngine is protected by LiteRtSafetyGate (heap/thermal watchdog). Local RAG matches vector embeddings in RagDocumentBuilder. ChatActionParser and ChatDictionaryResolver interpret Vietnamese construction commands.
