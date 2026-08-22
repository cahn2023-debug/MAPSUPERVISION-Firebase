# Seed Playbook: Decisions & Memories

Chạy các lệnh dưới đây khi Knowns MCP đã online (hoặc qua Claude Code trong repo này).
Tất cả Decision được tạo ở **status: draft** — đúng quy trình kn-extract/kn-implement:
candidate không bao giờ tự động thành current, cần người review và accept.

## A. Draft System Decisions (5 candidates)

```json
mcp_knowns_decision({ "action": "create",
  "title": "Firebase SDK confined to :data module",
  "status": "draft",
  "decision": "Only data/src/main/java/com/mapsupervision/data/sync/{FirebaseRuntime,FirebaseSyncRepositoryImpl,FirebaseAccessRepositoryImpl}.kt may import com.google.firebase. Any new backend capability extends a :domain interface implemented in :data/sync.",
  "sources": ["@doc/guides/firebase-sync"],
  "relatedDocs": ["@doc/architecture-overview", "@doc/learnings/critical-patterns"] })

mcp_knowns_decision({ "action": "create",
  "title": "Serialized low-memory Gradle build profile",
  "status": "draft",
  "decision": "gradle.properties intentionally sets daemon=false, parallel=false, workers.max=1, -Xmx1536m for constrained hardware. Do not raise parallelism or heap without explicit user request; iterate with targeted module tasks.",
  "sources": ["@doc/build-conventions"],
  "relatedDocs": ["@doc/learnings/critical-patterns"] })

mcp_knowns_decision({ "action": "create",
  "title": "Errors cross layer boundaries as AppResult<T>",
  "status": "draft",
  "decision": "Repositories and use cases return AppResult Success/Error; infrastructure failures are mapped at :data / :storage-* edges. Never throw across layers; AI orchestrator callers must handle engine-unavailable as an error outcome.",
  "sources": ["@doc/build-conventions"],
  "relatedDocs": ["@doc/patterns/ai-engine-orchestration"] })

mcp_knowns_decision({ "action": "create",
  "title": "Offline-first mutations go through the event outbox",
  "status": "draft",
  "decision": "Local Room mutations append to the persisted event outbox; WorkManager drains it to Firestore (media via DriveMediaUploadClient). UI/ViewModel code never calls Firestore directly.",
  "sources": ["@doc/guides/firebase-sync"],
  "relatedDocs": ["@doc/patterns/project-scoped-database"] })

mcp_knowns_decision({ "action": "create",
  "title": "Per-project Room database instances",
  "status": "draft",
  "decision": "MapSupervisionDatabase (version 48) is instantiated per-project via ProjectScopedDatabaseProvider. Inject the provider, never raw DAO singletons; workers re-resolve on project switch; destructive migrations are off by default.",
  "sources": ["@doc/patterns/project-scoped-database"],
  "relatedDocs": ["@doc/learnings/critical-patterns"] })
```

## B. Project Memories (4 mục)

```json
mcp_knowns_memory({ "action": "add",
  "title": "Module boundaries enforced by EnforceModuleBoundariesTask",
  "content": "Dependency edges come from allowedProjectDependencies map in root build.gradle.kts:19; the buildSrc task fails builds on undeclared edges. Add edge = edit map first, then module build file. Full table: @doc/architecture-overview",
  "layer": "project", "category": "convention", "tags": ["gradle", "modules"] })

mcp_knowns_memory({ "action": "add",
  "title": "Hilt two-file DI split",
  "content": "Each feature area splits DI into object *Module (@Provides) and abstract *BindModule (@Binds), all on SingletonComponent. Match this when adding providers.",
  "layer": "project", "category": "convention", "tags": ["hilt", "di"] })

mcp_knowns_memory({ "action": "add",
  "title": "Version catalog used inconsistently — skews recorded",
  "content": "[plugins] block of libs.versions.toml is empty; several modules pin versions inline. Known skews: coroutines 1.7.3 (:domain) vs 1.8.1 (catalog); CameraX 1.4.0 (:app) vs 1.6.1 (catalog). Aligning = deliberate task, not drive-by fix. See @doc/build-conventions",
  "layer": "project", "category": "failure", "tags": ["gradle", "versions"] })

mcp_knowns_memory({ "action": "add",
  "title": "Pre-existing Vietnamese docs under repo-root docs/",
  "content": "Rich doc set predates Knowns (architecture overview, database, module matrix, release runbook, adr/). Knowns docs summarize and point at them; never duplicate. Index: @doc/repo-docs-map",
  "layer": "project", "category": "preference", "tags": ["docs"] })
```

## C. Sau khi chạy xong

```bash
knowns validate --plain        # toàn dự án
knowns search "module boundaries" --plain   # kiểm tra semantic index đã nhận nội dung mới
```
