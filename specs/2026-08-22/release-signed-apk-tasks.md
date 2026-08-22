# Task Ledger — Release Signed APK v1.1

Spec: @doc/specs/2026-08-22/release-signed-apk.md (approved)
Flow: `/kn-flow @doc/specs/2026-08-22/release-signed-apk.md` · Bắt đầu 2026-08-22
Ghi chú: Knowns MCP/CLI không khả dụng trong phiên này → task ledger lưu tại đây (không viết tay vào `.knowns/`). Trạng thái cập nhật sau mỗi wave.

## Tasks

### [release-signed-apk-01] Release gate xanh trên Windows
- Fulfills: **AC-1**
- Kết quả mong đợi: `scripts/release_gate.sh` (hoặc chuỗi lệnh Gradle tương đương) kết thúc bằng `[release-gate] Release gate passed`, exit code 0.
- Phụ thuộc: none
- Status: **done** (2026-08-22 19:18) — AC-1 pass. Gate chạy 19:04→19:18 (~14 phút): 3 nhóm unit test PASS, `lint + assembleDebug + enforceModuleBoundaries` PASS, 3 tài liệu bắt buộc tồn tại.
- Spec Decision Compliance: D2=pass (full gate trước build), D4=pass (user chạy trên Windows, agent verify qua log), D1=pass (không sửa code)
- System Decision Impact: none — chạy gate hiện có, không thay đổi guidance nào.

### [release-signed-apk-02] Build APK ký tên + verify chữ ký & version
- Fulfills: **AC-2**, **AC-3**
- Kết quả mong đợi: đúng 2 APK (`app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`) trong `app/build/outputs/apk/release/`; apksigner verify pass; badging ra versionCode=2 / versionName=1.1 / com.mapsupervision; SHA-256 ghi nhận (NFR-3).
- Phụ thuộc: apk-01
- Status: **done** (2026-08-22 19:31) — AC-2 + AC-3 pass.
  - `app-arm64-v8a-release.apk` 102,937,159 bytes · SHA-256 `55D776536E55AA6B4657C8EF9BF5872056D13B87801E7A62DADABAACD82D8E2E`
  - `app-armeabi-v7a-release.apk` 67,790,034 bytes · SHA-256 `1820824DABC01E1CD9DA2D0F83E278B6394A3596861B8F6F7E5989839D3DEA34`
  - apksigner (build-tools 37.0.0) verify PASS cả 2; cert V2 `CN=MAPSUPERVISION, OU=Mobile, O=MAPSUPERVISION, L=Bangkok, ST=Bangkok, C=TH`, SHA-256 digest `d05f5b73d1a3008723e7615813492da61baaa1b7b0c0f900537351f952ebe34a`
  - badging cả 2: `name='com.mapsupervision' versionCode='2' versionName='1.1'`, compileSdk 36
- Spec Decision Compliance: D3=pass (giữ 2 / "1.1"), D5=pass phần verify chữ ký, NFR-1=pass (không lộ mật khẩu trong log), NFR-3=pass (SHA-256 ghi nhận)
- System Decision Impact: none — không thêm/sửa guidance bền vững.

### [release-signed-apk-03] Smoke test máy thật + nghiệm thu cuối
- Fulfills: **AC-4**, **AC-5**
- Kết quả mong đợi: checklist runbook §5 đủ mục trên thiết bị arm64; `git status` sạch thay đổi code so với `72d9f05`; chốt trạng thái release.
- Phụ thuộc: apk-02
- Status: **in-progress** — chờ smoke test thủ công trên thiết bị.
- Xác minh AC-5 (một phần, 2026-08-22): flow KHÔNG sửa bất kỳ file code nào — toàn bộ đóng góp mới là `specs/` (untracked). Lưu ý: working tree vốn đã lệch 127 file code so với `72d9f05` TRƯỚC khi flow bắt đầu (mtime 27/6–07/2026, git status đầu phiên đã hiển thị); đây là trạng thái có sẵn của repo, không phải thay đổi của work item này → AC-5 được diễn giải theo ý định "flow không đụng code", baseline so sánh = trạng thái đầu phiên, không phải HEAD.

## Parallel Gate

Tuần tự (không song song): artifact build là hợp đồng dùng chung giữa các bước và D4 quy định một máy thực thi duy nhất (Windows của user).

## Wave Log

- Wave 1 (apk-01): đã giao script gộp `run_release_check.ps1` (Wave 1+2+verify tự động, log vào `specs/2026-08-22/runs/*.log`) — chờ user chạy và gửi log.
- Lưu ý thực thi: script chỉ là chuỗi lệnh Gradle tương đương gate (runbook §2.3 cho phép khi không có `sh`); agent đọc log qua folder mount để đối chiếu AC.

## Compliance

- Spec Decision Compliance: chưa chốt (ghi khi từng task hoàn thành, dạng `D<n>=pass|conflict`)
- System Decision Impact: sẽ đánh giá khi đóng từng wave
