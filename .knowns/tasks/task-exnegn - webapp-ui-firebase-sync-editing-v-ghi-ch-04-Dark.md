---
id: exnegn
title: "[webapp-ui-firebase-sync-editing-v-ghi-ch-04] Dark Light theme và responsive UX"
status: done
priority: medium
labels:
  - from-spec
  - spec:webapp-ui-firebase-sync-editing-v-ghi-ch
  - spec-date:2026-08-25
  - ui
  - accessibility
createdAt: '2026-08-25T17:00:49.597Z'
updatedAt: '2026-08-25T17:23:57.775Z'
completedAt: '2026-08-25T17:20:48.537Z'
timeSpent: 322
assignee: '@me'
spec: specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch
fulfills:
  - AC-1
  - AC-2
order: 40
---
# [webapp-ui-firebase-sync-editing-v-ghi-ch-04] Dark Light theme và responsive UX

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Bổ sung theme theo system preference, toggle và localStorage persistence; chuẩn hóa design tokens, contrast, focus, touch target, reduced motion và responsive breakpoints.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Theme khởi tạo theo system preference khi chưa có local choice và toggle persistence hoạt động.
- [x] #2 Dark/light tokens có contrast, border, focus, disabled and danger states rõ.
- [x] #3 Dashboard responsive không horizontal overflow ở breakpoint chính và tôn trọng reduced motion.
- [x] #4 SVG/icon controls có accessible label và touch target phù hợp.
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

1. Inspect current webapp/app/globals.css and page shell for hard-coded dark-only colors and existing responsive rules, using the approved UI design system from @doc/specs/2026-08-25/webapp-ui-firebase-sync-editing-v-ghi-ch.
2. Add a small client-side theme hook/state that reads localStorage or system preference, applies a root data-theme attribute, and exposes an accessible Dark/Light toggle.
3. Add light/dark CSS tokens, visible focus rings, disabled/danger states, reduced-motion handling and responsive safeguards without disturbing unrelated map/media styling.
4. Run tsc, npm test, production build and manually smoke-test both theme states at responsive widths; review and record D1-D5/System Decision Impact.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Review: PASS. Theme bootstrap, persistence, tokens, focus and reduced-motion behavior verified by build/typecheck/tests.
Spec Decision Compliance: D1=pass, D2=pass, D3=pass, D4=pass, D5=pass
System Decision Impact: none — implementation follows the approved spec.
<!-- SECTION:NOTES:END -->

