---
title: Repo Docs Map
description: Index of the pre-existing Vietnamese docs under repo-root docs/ — what each covers and when to read it, so agents don't duplicate them.
tags: [docs, index, reference]
---

# Repo Docs Map

The repository ships a rich Vietnamese doc set under `docs/` (authored before the Knowns layer existed). Knowns docs summarize and point at these; don't copy their content into `.knowns/docs`.

| Doc | Covers |
|---|---|
| `docs/android_kien_truc_tong_quan.md` | Full Android architecture walkthrough (layers, modules) |
| `docs/database.md` | Room schema history and per-project DB rationale |
| `docs/module_matrix_chi_tiet.md` | Detailed per-module responsibility matrix |
| `docs/release_gate_runbook.md` | Release gate checklist/runbook |
| `docs/adr/` | Architecture decision records |

When a Knowns doc and a `docs/` file disagree, treat the source code as truth, note the conflict, and fix the stale doc through the normal doc workflow.
