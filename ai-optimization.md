# Project Plan: AI Optimization for MAPSUPERVISION

## Overview
Optimizing MAPSUPERVISION's Chat Assistant toward a local-first, rule-first, and user-confirmed data pipeline.

## Project Type
MOBILE (Android field application in Kotlin)

## Success Criteria
1. Vietnamese natural language inputs correctly parsed using enhanced rules or local LLM.
2. No write actions committed directly to database without user confirmation (strict `REQUIRE_CONFIRMATION` or `REJECT` policies).
3. Audit logging implemented via Room `ai_action_log` database table.
4. Structurally aggregate GIS node, route, progress and daily logs data into structured `SummaryRow` records.

## Tech Stack
- Kotlin / Android Jetpack Compose
- Room DB / SQLite
- Mediapipe LiteRT / Gemma 2B Local LLM

## File Structure
- `domain/.../ai/AiContracts.kt` (Add `GENERATE_SUMMARY` & structures)
- `domain/.../ai/ChatActionParser.kt` (Regex enhancements & 3-step pipeline)
- `domain/.../ai/SummaryAggregator.kt` (Summary Aggregator implementation)
- `data/.../db/entity/AiActionLogEntity.kt` (Audit log Entity)
- `data/.../db/dao/AiActionLogDao.kt` (Audit log DAO)
- `data/.../db/MapSupervisionDatabase.kt` (Add entity and migration v25)
- `app/.../workspace/GemmaChatViewModel.kt` (Flow logic & audit logging updates)

## Task Breakdown
- [ ] Task 1: Extend AI contracts with `GENERATE_SUMMARY`, `SummaryRequestDraft`, `SummaryRow`.
- [ ] Task 2: Enhance `ChatActionParser` parsing rules for Vietnamese syntax and 3-stage flow.
- [ ] Task 3: Create Room schema updates (Entity, DAO, Database Migration 24 -> 25).
- [ ] Task 4: Implement `SummaryAggregator` logic to aggregate stats from DB.
- [ ] Task 5: Integrate UI workflow in `GemmaChatViewModel` and enforce confirmation flows.
- [ ] Task 6: Add golden unit tests for Vietnamese parsing, aggregation, and audit logging.

## Verification Checklist
- Run `ChatActionParserTest` -> Pass
- Run database migrations test -> Pass
- Manual validation of confirmation dialog -> Verified
