---
id: b5bfq7
title: Firebase Admin ưu tiên service-account file khi JSON env bị hỏng
layer: project
category: failure
status: proposed
tags:
  - debug
  - firebase
  - webapp
  - credentials
  - environment
createdAt: '2026-08-25T15:40:29.083Z'
updatedAt: '2026-08-25T15:40:29.083Z'
---

Root cause: FIREBASE_SERVICE_ACCOUNT_JSON có thể parse được JSON nhưng private_key bị cắt hoặc PEM không hợp lệ, khiến firebase-admin cert() báo ERR_OSSL_UNSUPPORTED. Fix: nếu FIREBASE_SERVICE_ACCOUNT_FILE hoặc service-account file mặc định tồn tại và hợp lệ, ưu tiên credential file trước; chỉ dùng JSON env làm fallback. Không ghi credential hoặc private key vào log.
