---
title: AI Engine Orchestration Pattern
description: The 6-engine AiOrchestrator architecture, fallback routing, safety gating, and where Gemma/RAG fit across the ai-* modules.
tags: [ai, orchestration, gemma, rag, pattern]
---

# AI Engine Orchestration Pattern

Verified against source on 2026-08-22.

## Module split

| Module | Responsibility | Notable contents |
|---|---|---|
| `:ai-core` | Contracts only (engine interfaces, domain-facing AI types) | engine interfaces, request/response models |
| `:ai-model` | Concrete engine implementations + vendor SDKs | 6 engines; SDK pins at `ai-model/build.gradle.kts:52-59` |
| `:ai-prompt` | Prompt assets/templates as versioned code | prompt builders |
| `:ai-rag` | Retrieval-augmented generation layer | TextEmbeddingEngine, RagDocumentBuilder, RagChatAnswerFormatter |
| `:ai-agent` | Orchestration consumed by features | AiOrchestrator, SummaryAggregator + rich unit tests |

## The six engines (`:ai-model`)

CloudGeminiEngine (`com.google.ai.client.generativeai:generativeai:0.9.0`) · LocalLiteRtEngine (`org.tensorflow:tensorflow-lite:2.17.0`) · MediaPipeLlmEngine (Gemma via `com.google.mediapipe:tasks-text:0.10.32` + `com.google.ai.edge.litertlm:litertlm-android:0.13.1`) · MlKitVisionEngine (`text-recognition`, `barcode-scanning`) · RuleBasedEngine (deterministic fallback) · TfliteVisionEngine.

## Routing rules

1. **AiOrchestrator** picks engines by capability + availability, with fallback routing — cloud → local → rules-based degradation is the intended order for text work.
2. **LiteRtSafetyGate** guards LiteRT invocations (input shape/memory checks) before execution.
3. Vision tasks route to MlKit/TFLite engines; the orchestrator's fallback contract means every caller must handle a "no engine available" outcome via AppResult.Error — never assume an engine is present on all devices.
4. Feature modules never import vendor SDKs; they see orchestrator + `:ai-core` types only.

## RAG pipeline

`:ai-rag` embeds project documents (TextEmbeddingEngine) → RagDocumentBuilder assembles context chunks → prompt assembly in `:ai-prompt` → answer formatting back through RagChatAnswerFormatter. When touching retrieval quality, test through `:ai-agent`'s SummaryAggregator tests rather than module-local fixtures.

## Rules of thumb

- New capability = new method on an `:ai-core` interface + orchestrator routing entry; not a new direct SDK call in a feature module.
- Any new vendor dependency lands in `:ai-model/build.gradle.kts` and must respect the low-memory profile (see @doc/build-conventions) — model loading must be lazy/releaseable.
