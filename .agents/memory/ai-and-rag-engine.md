# Hybrid AI & Local RAG Engine Memory

## AiOrchestrator & 6 Execution Engines
- `CloudGeminiEngine`: Cloud-based generative reasoning and complex construction analysis.
- `LocalLiteRtEngine`: High-speed local model inference with `LiteRtSafetyGate` protection.
- `MediaPipeLlmEngine`: On-device Gemma LLM execution without internet connectivity.
- `MlKitVisionEngine`: Text OCR and label recognition on construction photos.
- `TfliteVisionEngine`: Local defect detection models.
- `RuleBasedEngine`: Reliable zero-dependency rule fallback.

## Vietnamese Construction NLP & RAG
- `ChatActionParser`: Parses user text into domain actions (e.g. create note, log work, query progress).
- `ChatDictionaryResolver`: Normalizes colloquial Vietnamese construction terms into canonical project IDs.
- `RagDocumentBuilder`: Chunks project specifications and indexes embeddings in `RagDocumentEmbeddingEntity` for semantic search.
