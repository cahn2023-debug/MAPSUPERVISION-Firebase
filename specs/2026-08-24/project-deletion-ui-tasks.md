# Tasks: Bổ sung nút & quy trình Xóa Project (Danger Zone) cho Android và Web (UI/UX Pro Max)

Spec: @doc/specs/2026-08-24/project-deletion-ui.md  
Created: 2026-08-24 · Status: **completed**

## Task List

- [x] **[project-deletion-ui-01]** Xây dựng hàm `deleteProjectDocument` & dọn dẹp Firestore trên Webapp
  - Fulfills: AC-1, AC-5, FR-2, D1
  - Scope: `webapp/lib/sync.ts`
  - Order: 10
  - Status: **completed**

- [x] **[project-deletion-ui-02]** Thiết kế Component Danger Zone & Glassmorphic Delete Modal trên Webapp (UI/UX Pro Max)
  - Fulfills: AC-1, AC-2, AC-3, AC-6, FR-1, FR-2, FR-4, D2
  - Scope: `webapp/app/page.tsx`, `webapp/app/globals.css`
  - Order: 20
  - Status: **completed**

- [x] **[project-deletion-ui-03]** Nâng cấp Giao diện & Luồng Xóa Dự án trong Android Compose (UI/UX Pro Max)
  - Fulfills: AC-1, AC-4, AC-5, AC-6, FR-3, FR-4, D2, D3
  - Scope: `app/src/main/java/com/mapsupervision/app/workspace/MapHubScreen.kt`, `app/src/main/java/com/mapsupervision/app/WorkspaceAppShell.kt`
  - Order: 30
  - Status: **completed**

- [x] **[project-deletion-ui-04]** Kiểm thử tự động, xác thực build và trải nghiệm UI/UX
  - Fulfills: AC-7, NFR-1, NFR-2, NFR-3
  - Scope: Web Next.js build/lint verification & Android unit tests
  - Order: 40
  - Status: **completed**

## Schedule & Compliance

- **Wave 1 (project-deletion-ui-01):** Webapp sync library Firestore deletion logic (`deleteProjectDocument`).
- **Wave 2 (project-deletion-ui-02):** Webapp UI/UX Pro Max Danger Zone & Modal.
- **Wave 3 (project-deletion-ui-03):** Android Jetpack Compose UI/UX polish & Outbox detection.
- **Wave 4 (project-deletion-ui-04):** Build verification & Automated tests.
- **Spec Decision Compliance:** D1=pass, D2=pass, D3=pass.
- **System Decision Impact:** none — Tuân thủ schema hiện hành của Firestore rules và Room database invariants.
