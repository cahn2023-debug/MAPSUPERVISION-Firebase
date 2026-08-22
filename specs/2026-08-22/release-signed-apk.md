# Release Signed APK — Finalize v1.1 (versionCode 2)

Tags: `spec`, `approved` · Created: 2026-08-22 · Status: **approved (2026-08-22)**

Runbook thực thi: @doc/specs/2026-08-22/release-signed-apk-runbook.md

## Overview

Đóng gói bản release đầu tiên có kiểm soát của MapSupervision theo đúng cấu hình hiện có trong repo: chạy đủ release gate, sau đó build `:app:assembleRelease` với signing từ `local.properties`, xác minh chữ ký và nghiệm thu bằng cài thử trên máy thật.

Spec này **không sửa code ứng dụng**. Các điểm nghi vấn phát hiện khi audit phần debug được ghi nhận thành work item riêng (xem Spillover), không nằm trong phạm vi lần này.

## Locked Decisions

- **D1 — Phạm vi:** Chỉ đóng gói APK release ký tên. Không đụng code: `AppLogger` (vẫn ghi DEBUG ra file ở release), `DebugQueryPlanInspector` (chưa nơi nào gọi), `proguard-rules.pro` giữ nguyên.
- **D2 — Xác minh:** Chạy đủ `scripts/release_gate.sh` (3 nhóm unit test + `lint assembleDebug enforceModuleBoundaries`) xanh trước, rồi mới `:app:assembleRelease`.
- **D3 — Phiên bản:** Gi nguyên `versionCode = 2`, `versionName = "1.1"` như hiện tại trong `app/build.gradle.kts`.
- **D4 — Thực thi:** Người dùng chạy build/trên Windows theo runbook do agent soạn; agent kiểm chứng kết quả qua log/APK người dùng cung cấp. (Agent VM không có JDK 17, Android SDK và không tiếp cận keystore `C:\AndroidKeys\mapsupervision-release.jks`.)
- **D5 — Nghiệm thu:** `apksigner verify` pass cho cả 2 APK ABI **và** cài thử máy thật, chạy smoke test tối thiểu theo `docs/release_gate_runbook.md` §5.

## System Decision Impact

- Impact: **none** — không thay đổi quyết định hệ thống nào.
- Các draft decision liên quan giữ nguyên trạng thái draft, không tự động accept: Firebase SDK confined to `:data`; Serialized low-memory Gradle profile (`daemon=false`, `workers.max=1`) phải tôn trọng trong toàn bộ bước build của spec này.
- Acceptance gate: không áp dụng (không có decision draft mới).

## Requirements

### Functional Requirements

- **FR-1:** `scripts/release_gate.sh` chạy hết với exit code 0 trên Windows (Git Bash/sh), gồm: `:app:testDebugUnitTest`, `:storage-import:testDebugUnitTest`, `:data:testDebugUnitTest`, `lint`, `assembleDebug`, `enforceModuleBoundaries`, và kiểm tra tồn tại 3 file tài liệu bắt buộc.
- **FR-2:** Sau `:app:assembleRelease`, thư mục `app/build/outputs/apk/release/` chứa APK đã ký cho cả hai ABI đang bật trong split: `arm64-v8a` và `armeabi-v7a`.
- **FR-3:** APK output mang `versionCode = 2`, `versionName = "1.1"`, `applicationId = com.mapsupervision`.
- **FR-4:** Cả hai APK pass `apksigner verify --print-certs` với chứng chỉ từ keystore release (`mapsupervision-release.jks`).
- **FR-5:** APK `arm64-v8a` cài thành công lên thiết bị thật và smoke test runbook §5 pass đủ mục.
- **FR-6:** Không có thay đổi nào vào file nguồn (`*.kt`, `*.kts`, `*.pro`, manifest) trong work item này — `git status` sau cùng chỉ chứa artifact của spec/tài liệu.

### Non-Functional Requirements

- **NFR-1:** Signing material chỉ đọc từ `local.properties` (4 key `RELEASE_*`); không bao giờ commit keystore/mật khẩu vào repo hay log ra output.
- **NFR-2:** Mọi lệnh Gradle tôn trọng build profile tuần tự, bộ nhớ thấp hiện có (không bật daemon/parallel/workers mới).
- **NFR-3:** Kết quả lần chạy được ghi lại: đường dẫn artifact, dung lượng, SHA-256 của từng APK.

## Acceptance Criteria

- [ ] **AC-1:** Log chạy `scripts/release_gate.sh` hiển thị `[release-gate] Release gate passed` với exit code 0.
- [ ] **AC-2:** `app/build/outputs/apk/release/` có đúng 2 APK: `app-arm64-v8a-release.apk` và `app-armeabi-v7a-release.apk` (tên theo pattern split ABI).
- [ ] **AC-3:** `apksigner verify --print-certs` báo "Verifed" (OK) cho cả 2 APK; certs khớp alias release cấu hình trong `local.properties`.
- [ ] **AC-4:** Trên máy thật: cài OK → mở app → vào workspace → chuyển project active → tab data import 1 file mẫu → map/dashboard cập nhật → tab reports tạo preview/export (đủ checklist runbook §5).
- [ ] **AC-5:** `git status` sạch về mặt code: không thay đổi `*.kt` / `*.kts` / `*.pro` / manifest so với commit `72d9f05`.

## Scenarios

### Scenario 1: Happy Path
**Given** working tree ổn định tại commit mới nhất, `local.properties` đủ 4 key `RELEASE_*` trỏ tới keystore hợp lệ
**When** chạy release gate rồi `:app:assembleRelease`
**Then** gate passed, 2 APK ABI được tạo, `apksigner verify` pass, cài thử máy thật pass smoke test → release sẵn sàng bàn giao.

### Scenario 2: Gate đỏ
**Given** một trong các bước gate fail (test/lint/boundary)
**When** chạy `scripts/release_gate.sh`
**Then** script dừng với exit code ≠ 0; KHÔNG chạy assembleRelease; chẩn đoán nguyên nhân trước khi thử lại (spec này bị block, không "nới lỏng" gate).

### Scenario 3: Lỗi signing
**Given** thiếu/không đúng key `RELEASE_*` hoặc keystore không tồn tại
**When** chạy `:app:assembleRelease`
**Then** build fail ở bước signing; kiểm tra lại `local.properties` và đường dẫn keystore; không fallback sang APK debug/unsigned.

### Scenario 4: Crash runtime sau minify
**Given** APK cài OK nhưng crash khi mở/smoke test (rủi ro có thật: `proguard-rules.pro` hiện rất mỏng — chỉ TensorFlow Lite, MapLibre, Room/Domain models)
**When** chạy app bản release trên máy thật
**Then** capture `adb logcat`; đánh dấu release BLOCKED; tạo work item riêng để bổ sung keep-rules; KHÔNG sửa proguard nóng trong scope này (vi phạm D1/D6-spillover).

## Technical Notes

Runbook thực thi trên Windows PowerShell (agent soạn chi tiết ở bước kế tiếp sau khi spec được duyệt):

```powershell
# 0) Tiền điều kiện: keystore tồn tại tại C:\AndroidKeys\mapsupervision-release.jks,
#    local.properties đủ RELEASE_STORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD
sh ./scripts/release_gate.sh          # hoặc Git Bash; nếu chỉ có PowerShell thì chạy từng lệnh gradle tương đương
.\gradlew.bat :app:assembleRelease
# Verify chữ ký (thay <ver> bằng build-tools version thực tế, ví dụ 35.0.0)
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<ver>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-arm64-v8a-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<ver>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-armeabi-v7a-release.apk
# Smoke test
adb install -r app\build\outputs\apk\release\app-arm64-v8a-release.apk
```

Phát hiện audit (chỉ ghi nhận, không xử lý trong scope): `AppLogger.d()` vẫn append DEBUG ra `files/logs/app.1.log` kể cả khi `debug=false` (chỉ Timber mới bị tắt); `DebugQueryPlanInspector` là dead code chưa có caller; `proguard-rules.pro` 18 dòng chưa cover Hilt/WorkManager/Coil/Firebase explicit keeps (mặc định AGP đã xử lý consumer rules, nhưng rủi ro runtime vẫn tồn tại — xem Scenario 4).

## Task Links

Tasks được quản lý tại @doc/specs/2026-08-22/release-signed-apk-tasks.md:

- [release-signed-apk-01] Release gate xanh trên Windows — in-progress
- [release-signed-apk-02] Build APK ký tên + verify — todo
- [release-signed-apk-03] Smoke test máy thật + nghiệm thu — todo

## Open Questions

- [ ] Version build-tools cài trên máy Windows là gì (cần cho đường dẫn apksigner)?
- [ ] Thiết bị nghiệm thu dùng máy nào / Android version nào?
- [ ] APK armeabi-v7a có cần cài thử trên thiết bị 32-bit thật, hay chỉ verify chữ ký là đủ?

## Spillover (work item riêng, ngoài scope)

1. Release-safe logging: `AppLogger` tắt ghi DEBUG ra đĩa ở bản release (+ cân nhắc level gate).
2. Xử lý `DebugQueryPlanInspector` dead code (bỏ hoặc wire-in có chủ đích).
3. Hardening `proguard-rules.pro` + R8 full-mode audit nếu Scenario 4 xảy ra hoặc trước lần release kế tiếp.
