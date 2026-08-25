---
id: oxdv32
title: Next.js webpack cache ENOENT do nhiều dev server dùng chung .next
layer: project
category: failure
status: proposed
tags:
  - debug
  - nextjs
  - webpack
  - dev-server
  - cache
createdAt: '2026-08-25T16:06:25.741Z'
updatedAt: '2026-08-25T16:06:25.741Z'
---

Root cause: nhiều tiến trình `next dev` của cùng web app chạy đồng thời và cùng ghi `webapp/.next/cache/webpack`, khiến một tiến trình rename/xóa pack tạm trước tiến trình khác và gây ENOENT. Fix: dừng các dev process trùng lặp, chỉ chạy một `npm run dev`; nếu cache còn stale, dọn riêng `.next/cache` sau khi đã dừng server.
