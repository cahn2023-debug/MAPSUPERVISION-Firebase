# Task Ledger — Sửa lỗi đăng ký/đăng nhập Google + đăng ký email treo

Spec: @doc/specs/2026-08-22/google-email-auth-fix.md (approved)
Flow: `/kn-flow @doc/specs/2026-08-22/google-email-auth-fix.md` · Bắt đầu 2026-08-22
Ghi chú: Knowns MCP/CLI không khả dụng trong phiên này → task ledger lưu tại đây. Agent không chạy được Gradle/JDK17 trên VM → mọi build/test do user chạy trên Windows, agent soạn lệnh + verify qua log/file (theo [[release-build-environment]]).

## Tasks

### [gaf-01] Data layer: timeout + ánh xạ lỗi tiếng Việt + log chẩn đoán
- Fulfills: **AC-3**, **AC-4**, **AC-5** (phần backend của luồng); hỗ trợ AC-8
- Phạm vi sửa: `data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt` + file mới `FirebaseAuthErrorMapper.kt` (cùng thư mục, KHÔNG import Firebase — giữ boundary) + test mới `data/src/test/.../sync/FirebaseAuthErrorMapperTest.kt`
- Kế hoạch:
  1. Thêm `withAuthTimeout` (30s, bọc `withTimeout`, chuyển `TimeoutCancellationException` thành `AuthTimeoutException` với thông điệp tiếng Việt có dấu).
  2. Bọc các lời gọi: `createUserWithEmailAndPassword`, `sendEmailVerification`, `signInWithEmailAndPassword`, `signInWithCredential`, `getIdToken` (trong `buildSession`).
  3. Mapper `mapFirebaseAuthError(error)`: EMAIL_EXISTS / WEAK_PASSWORD / INVALID_EMAIL / INVALID_LOGIN_CREDENTIALS+WRONG_PASSWORD+USER_NOT_FOUND (gộp thành "Email hoặc mật khẩu không đúng") / TOO_MANY_REQUESTS / lỗi mạng / fallback.
  4. Sửa chuỗi ASCII không dấu hiện có trong repo file này thành tiếng Việt có dấu.
  5. Log từng bước: register start/user_created/verification_sent; sign-in start/success; kèm `AppLogger.e` mã gốc khi thất bại; bọc lỗi bằng `Exception(mappedMessage, original)` để ViewModel hiển thị đúng.
  6. Unit test thuần cho mapper (không cần Android).
- Phụ thuộc: none
- Status: **done** (2026-08-22) — code + unit test hoàn tất; nghiệm thu runtime thuộc gaf-04.
  - File mới: `FirebaseAuthErrorMapper.kt` (mapper KHÔNG import Firebase — nhận diện qua tên lớp/message để test thuần Kotlin, giữ boundary); `withAuthTimeout(timeoutMs=30_000)` bọc `withTimeout`, chuyển `TimeoutCancellationException` → `AuthTimeoutException`.
  - `FirebaseAccessRepositoryImpl.kt`: bọc timeout cho createUser/sendEmailVerification/signInWithEmail/signInWithCredential/getIdToken; thêm log start/user_created/verification_sent/success/failed từng luồng; mọi lỗi được wrap bằng `Exception(thông điệp tiếng Việt, gốc)`; chuỗi ASCII không dấu thay bằng tiếng Việt có dấu; register-failure signOut best-effort tránh kẹt trạng thái nửa vời.
  - Test mới: `FirebaseAuthErrorMapperTest.kt` — 11 case (mỗi nhóm mã lỗi, verbatim AppAuthMessage, wrap giữ nguyên cause, timeout thật 10ms→AuthTimeoutException, within-timeout trả kết quả).
  - Review: đã rà 4 góc nhìn; fix dead code nhánh `no_credentials` trong mapGoogleCredentialError (P3); xác nhận JUnit 4.13.2 có assertThrows, firebase-auth trên classpath test.
- Spec Decision Compliance: D2=pass (đưa vào phạm vi này), D3=pass (giữ luồng xác thực email, chỉ thêm timeout/mapper/log), FR-4..FR-6 phủ đủ.
- System Decision Impact: none — hợp đồng interface `:domain` không đổi, chỉ bổ sung hành vi nội bộ `:data`.

### [gaf-02] App layer: migrate Credential Manager + xóa hardcode + dọn dependency
- Fulfills: **AC-1**, **AC-2**, **AC-6** (code side); D3 (spinner dừng mọi đường lỗi), D4
- Status: **done** (2026-08-22) — code hoàn tất; nghiệm thu runtime thuộc gaf-04.
  - `FirebaseAccessGate.kt`: thay launcher legacy bằng `CredentialManager.getCredential` trong `rememberCoroutineScope`; `GetGoogleIdOption(serverClientId, filterByAuthorizedAccounts=false, autoSelectEnabled=false)`; xử lý riêng `NoCredentialException` / `GetCredentialCancellationException` (message nhẹ qua `setAuthMessage`, không đỏ) / `GetCredentialException` (map theo type, hướng dẫn kiểm tra SHA) / token rỗng / credential sai loại.
  - Xóa `resolveGoogleServerClientId` hardcode + hàm cũ; hàm mới trả nullable, thiếu resource → vô hiệu hóa nút Google + hiện lỗi cấu hình rõ ràng.
  - Xóa sạch import legacy (`GoogleSignIn*`, `ApiException`, `CommonStatusCodes`, activity-result launcher). Grep xác nhận: không còn `com.google.android.gms.auth` nào trong :app.
  - `FirebaseAccessViewModel`: thêm `setAuthMessage` (message nhẹ, tắt busy); wire vào call site.
  - `app/build.gradle.kts`: gỡ `com.google.android.gms:play-services-auth:21.2.0`; giữ credentials + credentials-play-services-auth + googleid.
- Spec Decision Compliance: D1=pass (phần migrate code), D4=pass (hardcode đã xóa, legacy lib đã gỡ).
- System Decision Impact: none — cách lấy idToken đổi ở tầng UI, hợp đồng `signInWithGoogle(idToken)` giữ nguyên.

### [gaf-03] Cấu hình Firebase: đăng ký SHA debug + release, thay google-services.json
- Fulfills: **AC-7**
- Phụ thuộc: none về code; làm sau gaf-01/02 để 1 lần cài kiểm thử
- Status: **done** (2026-08-22) — AC-7 pass.
  - Đã thêm 4 fingerprint vào Firebase console (debug + release, mỗi cái SHA-1/SHA-256); release SHA-256 khớp chữ ký v1.1.
  - `app/google-services.json` mới đã thay và được agent verify: chứa 2 oauth_client `client_type: 1` với certificate_hash `e39e3212…` (release) + `ff017581…` (debug), giữ web client `client_type: 3` làm serverClientId cho Credential Manager.
- Spec Decision Compliance: D1=pass (phần cấu hình), D4=pass (không cần hardcode nữa — resource default_web_client_id giờ có thật).
- System Decision Impact: none — thao tác cấu hình console, không đổi guidance kiến trúc.
- Spec Decision Compliance: chưa chốt (chờ kết quả thực thi).
- System Decision Impact: chưa đánh giá.

### [gaf-04] Kiểm chứng: release gate + smoke test thiết bị
- Fulfills: **AC-1**–**AC-6**, **AC-8** nghiệm thu cuối (AC-7 đã pass ở gaf-03)
- Phụ thuộc: gaf-01, gaf-02, gaf-03
- Status: **in-progress**
  - Đã verify tĩnh **AC-6 = pass**: grep `:app` không còn import GMS legacy/hardcode client ID; Firebase vẫn gói trong 3 file `:data/sync`.
  - Kế hoạch 2 giai đoạn (user thực thi, agent verify qua log/báo cáo):
    - **GĐ1 — nhanh:** `gradlew :app:assembleDebug :data:testDebugUnitTest` → cài debug lên máy thật → smoke checklist auth (AC-1/2/3/4/5).
    - **GĐ2 — đầy đủ:** chạy lại `run_release_check.ps1` (gate đủ 3 nhóm test + lint + boundaries, rồi assembleRelease ký tên) → cài bản release → smoke lại luồng chính (NFR-3).
  - Cảnh báo: máy thật đang có v1.1 release — cài đè debug sẽ fail signature mismatch; phải gỡ app cũ trước (mất dữ liệu offline cục bộ nếu có).
- Spec Decision Compliance: chốt sau GĐ2.
- System Decision Impact: chốt sau GĐ2.

## Parallel Gate

gaf-01 (:data) và gaf-02 (:app) rời nhau về phạm vi ghi, hợp đồng runtime không đổi → parallel-safe. Nhưng cả hai là chỉnh sửa nhỏ-trung bình trong cùng main context → chạy tuần tự để tự review tích hợp luôn. gaf-03 là thao tác máy user; gaf-04 bắt buộc sau tất cả.

## Wave Log

- Wave 1 (gaf-01 → gaf-02): agent implement trực tiếp + review tích hợp — **done**, 1 phát hiện P3 đã sửa.
- Wave 2 (gaf-03): script `google-auth-sha-check.ps1` (2 lần fix lỗi PowerShell: parse Char/stderr) — user lấy đủ 4 fingerprint, release SHA khớp v1.1, google-services.json mới đã verify có client_type:1 — **done**.
- Wave 3 (gaf-04): chia 2 giai đoạn; script GĐ1 `run_auth_check_phase1.ps1` đã soạn (:data test + assembleDebug + cài máy thật + checklist S1–S8) — chờ user chạy. GĐ2 = `run_release_check.ps1` tái sử dụng sau khi GĐ1 đạt.

## Compliance

- Spec Decision Compliance: ghi khi đóng từng task (dạng `D<n>=pass|conflict`)
- System Decision Impact: đánh giá khi đóng từng wave (dự kiến: none — hợp đồng repository không đổi)
