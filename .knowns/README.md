# .knowns — Knowledge Store (seeded 2026-08-22)

Kho tri thức Knowns của MAPSUPERVISION-Firebase, được seed bằng dữ liệu đã quét và xác minh từ source code ngày 2026-08-22.

## Đã có gì

| Nội dung | Vị trí |
|---|---|
| Kiến trúc tổng thể + bảng phụ thuộc 18 module | `docs/architecture-overview.md` |
| Convention build/DI/AppResult/skew phiên bản | `docs/build-conventions.md` |
| Firebase sync + security model | `docs/guides/firebase-sync.md` |
| AI orchestrator 6 engines + RAG | `docs/patterns/ai-engine-orchestration.md` |
| Room per-project DB (v48) | `docs/patterns/project-scoped-database.md` |
| Critical patterns (đọc trước khi đụng sync/DB/AI/boundary) | `docs/learnings/critical-patterns.md` |
| Bản đồ docs tiếng Việt có sẵn dưới `docs/` | `docs/repo-docs-map.md` |
| Template tạo ViewModel + module build file | `templates/viewmodel-module/`, `templates/gradle-module/` |
| Playbook lệnh tạo Decision/Memory qua MCP | `imports/seed-decisions-and-memories.md` |

## Quy tắc vận hành

- Không sửa tay markdown do Knowns quản lý sau khi hệ thống online — mọi thay đổi đi qua skill (`/kn-doc`, `/kn-extract`) hoặc API.
- File trong thư mục này hiện được ghi trực tiếp vì CLI/MCP chưa chạy lúc bootstrap. Chạy `knowns validate --plain` để xác nhận; semantic index tự rebuild theo content-hash nên không cần reindex thủ công.
- Decision/Memory **chưa** được tạo trong store — dùng playbook `imports/seed-decisions-and-memories.md` khi MCP online (Decision phải vào ở status draft).
