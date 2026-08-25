---
id: wxn6p8
title: "[fac-01] Sửa bộ giải mã Private Key trong webapp/lib/firebase-admin.ts"
status: done
priority: high
labels: []
createdAt: '2026-08-25T12:37:57.986Z'
updatedAt: '2026-08-25T12:39:27.004Z'
completedAt: '2026-08-25T12:39:27.004Z'
timeSpent: 0
spec: specs/2026-08-25/firebase-admin-catalog-visibility-cloud-deletion-fix
fulfills:
  - AC-1
order: 1
---
# [fac-01] Sửa bộ giải mã Private Key trong webapp/lib/firebase-admin.ts

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Chuẩn hóa và sanitize chuỗi private_key trong webapp/lib/firebase-admin.ts để hỗ trợ CRLF, escaped newlines, unescaped PEM headers, ngăn lỗi OpenSSL 3.0+ DECODER unsupported và đảm bảo Firebase Admin khởi tạo thành công.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Đã bổ sung hàm sanitizePrivateKey và sanitizeServiceAccount trong webapp/lib/firebase-admin.ts để xử lý CRLF, escaped newlines, Windows line endings, unescaped PEM headers và base64 JSON string. Unit test tests/firebase-admin.test.ts pass 100%.
Spec Decision Compliance: D1=pass
System Decision Impact: none — credential decoding utility improvement
<!-- SECTION:NOTES:END -->

