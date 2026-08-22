# Sửa lỗi đăng nhập/đăng ký Google + đăng ký email treo (Google Credential Manager Migration)

Tags: `spec`, `approved` · Created: 2026-08-22 · Status: **approved (2026-08-22)**

## Overview

Sửa hai lỗi xác thực đang gặp trên thiết bị:

1. **Google Sign-In hỏng:** màn hình chọn tài khoản Google hiện ra rất chậm; sau khi chọn tài khoản thì không có gì xảy ra. Nguyên nhân cấu hình đã xác minh trong repo: `app/google-services.json` chỉ chứa OAuth client kiểu Web (`client_type: 3`), **không có Android OAuth client (`client_type: 1`) gắn SHA-1/SHA-256** cho package `com.mapsupervision` → Google không cấp được idToken cho app (DEVELOPER_ERROR/12500, token rỗng hoặc im lặng).
2. **Đăng ký email treo:** bấm "Tạo tài khoản" → spinner quay vô tận, không bao giờ nhận được email xác thực. Nguyên nhân: `createUserWithEmailAndPassword(...).await()` và `sendEmailVerification().await()` trong `FirebaseAccessRepositoryImpl` không có timeout và mã lỗi Firebase chưa được ánh xạ sang thông báo dễ hiểu.

Spec này gồm 2 phần công việc theo quyết định người dùng: **(a)** sửa cấu hình Firebase (đăng ký SHA của cả debug và release keystore), **(b)** migrate màn hình đăng nhập từ GoogleSignIn legacy sang **Android Credential Manager**, kèm sửa luồng đăng ký email (timeout + ánh xạ lỗi tiếng Việt + log chẩn đoán).

## Locked Decisions

- **D1 — Hướng sửa Google Sign-In:** Làm cả hai: sửa cấu hình (thêm SHA-1/SHA-256 vào Firebase console, tải lại `google-services.json`) **và** migrate code sang Credential Manager (`androidx.credentials` + `googleid` — dependency đã khai báo sẵn trong `app/build.gradle.kts` dòng 147–149 nhưng chưa dùng).
- **D2 — Phạm vi luồng email:** Đưa việc đăng ký email treo (spinner vô tận, thiếu email xác thực) vào phạm vi sửa triệt để của spec này, không tách work item.
- **D3 — Luồng đăng ký email sau sửa:** Giữ nguyên luồng hiện tại (đăng ký → gửi email xác thực → về màn đăng nhập với thông báo nhắc kiểm tra email). Bổ sung: timeout cho các lời gọi Firebase, ánh xạ mã lỗi Firebase → tiếng Việt có dấu, log chẩn đoán từng bước để biết treo ở bước nào.
- **D4 — Dọn dẹp khi migrate:** Xóa client ID hardcode trong `FirebaseAccessGate.kt` (hàm `resolveGoogleServerClientId`, chuỗi `735767087959-2868…`); chỉ đọc `default_web_client_id` từ resources. Bỏ hoàn toàn usage của legacy `GoogleSignIn`/`play-services-auth` khỏi luồng đăng nhập; gỡ dependency nếu không còn tham chiếu nào khác trong repo.

## System Decision Impact

- Impact: **none** — không thay đổi quyết định hệ thống hiện hành nào. Ràng buộc kiến trúc giữ nguyên: Firebase SDK chỉ nằm trong `:data/sync` (3 file đã biết); Credential Manager là UI-side API nên thuộc `:app`, không vi phạm boundary map.
- Ghi nhận (không bắt buộc thành decision): từ bản này, Google Sign-In trên Android chuẩn hóa qua Credential Manager; `repository.signInWithGoogle(idToken)` ở `:domain`/`:data` giữ nguyên hợp đồng — chỉ thay cách lấy idToken ở tầng UI.

## Requirements

### Functional Requirements

- **FR-1 — Cấu hình SHA cho Firebase:** Đăng ký SHA-1 và SHA-256 của **cả hai** keystore vào Firebase project `mapsupervision`: debug keystore (`~/.android/debug.keystore` trên máy user) và release keystore (`C:\AndroidKeys\mapsupervision-release.jks`; SHA-256 đã biết: `d05f5b73d1a3008723e7615813492da61baaa1b7b0c0f900537351f952ebe34a`). Sau đó tải lại `google-services.json` mới thay vào repo — file mới phải chứa ít nhất một `oauth_client` với `client_type: 1` (Android) cùng certificate_hash.
- **FR-2 — Migrate Credential Manager:** Màn hình `FirebaseSignInScreen` dùng `CredentialManager` với `GetGoogleIdOption(serverClientId = <default_web_client_id>, filterByAuthorizedAccounts = false)`; nhận `GoogleIdTokenCredential` → truyền `idToken` vào `FirebaseAccessViewModel.signInWithGoogle(idToken)` như cũ. Xử lý `NoCredentialException` (không có tài khoản Google trên máy) và `GetCredentialCancellationException` (user hủy) bằng thông báo phù hợp, không coi là lỗi nghiêm trọng.
- **FR-3 — Bỏ hardcode client ID:** Xóa hàm `resolveGoogleServerClientId()` kèm chuỗi fallback hardcode. Nếu resource `default_web_client_id` thiếu (google-services.json cũ/chưa regenerate), app báo lỗi cấu hình rõ ràng thay vì dùng giá trị cũ.
- **FR-4 — Timeout lời gọi Firebase:** Mọi lời gọi await trong luồng auth (`createUserWithEmailAndPassword`, `sendEmailVerification`, `signInWithEmailAndPassword`, `signInWithCredential`, `getIdToken`) bọc timeout ~30 giây (`withTimeout`). Khi quá thời gian: hủy chờ, trả lỗi có thông điệp rõ (kiểm tra Internet / thử lại), spinner phải dừng.
- **FR-5 — Ánh xạ lỗi tiếng Việt:** Ánh xạ mã lỗi phổ biến sang tiếng Việt có dấu: `EMAIL_EXISTS`, `WEAK_PASSWORD`, `INVALID_EMAIL`, `INVALID_LOGIN_CREDENTIALS`/`WRONG_PASSWORD`, `USER_NOT_FOUND`, `TOO_MANY_REQUESTS`, lỗi mạng. Đồng thời sửa các chuỗi ASCII không dấu hiện có ("Tai khoan chua xac thuc email.", "Khong tai duoc phien dang nhap.", …) thành tiếng Việt có dấu.
- **FR-6 — Log chẩn đoán:** Thêm log `AppLogger` tại từng bước: register start / user created / verification sent, sign-in start / credential obtained / firebase result, kèm mã lỗi gốc khi thất bại — phục vụ chẩn đoán từ xa vì agent không chạy được app.
- **FR-7 — Phản hồi UI:** Spinner `isBusy` phải dừng trên **mọi** đường lỗi (bao gồm timeout và cancel của Google picker); lỗi hiển thị tại khu vực thông báo hiện có của màn hình.

### Non-Functional Requirements

- **NFR-1 — Module boundary:** Không thêm import `com.google.firebase.*` ngoài `:data/sync`; dependency Credential Manager chỉ dùng trong `:app`. `enforceModuleBoundaries` phải xanh.
- **NFR-2 — Kiểm thử:** Cập nhật `FirebaseAccessViewModelTest` cho các đường lỗi mới nếu ảnh hưởng; `scripts/release_gate.sh` phải xanh trước khi kết thúc.
- **NFR-3 — Hai variant:** Cả build debug lẫn release (ABI arm64-v8a, armeabi-v7a) phải đăng nhập Google được sau khi cả hai SHA đã đăng ký.

## Acceptance Criteria

- [ ] **AC-1:** Trên thiết bị thật, bấm "Đăng nhập với Google" → bottom sheet/account picker xuất hiện trong ~5 giây; chọn tài khoản → đăng nhập thành công, vào thẳng `WorkspaceAppShell`.
- [ ] **AC-2:** Tài khoản Google lần đầu tiên dùng app (chưa từng đăng ký) vẫn vào được app ngay (auto-provision qua `signInWithCredential`, đúng luồng hiện có).
- [ ] **AC-3:** Đăng ký email mới: spinner kết thúc trong ≤30 giây với thông báo thành công "Kiểm tra email…" hoặc lỗi cụ thể; email xác thực đến được hộp thư.
- [ ] **AC-4:** Trường hợp mạng bị chặn giữa chừng: sau ~30 giây hiện thông báo lỗi timeout (không treo spinner vô tận).
- [ ] **AC-5:** Đăng nhập sai mật khẩu hiển thị thông báo tiếng Việt có dấu, dễ hiểu (không còn mã lỗi thô).
- [ ] **AC-6:** Trong `:app` không còn import `com.google.android.gms.auth.api.signin.*`; chuỗi client ID hardcode đã biến mất khỏi source (`grep -r "735767087959-" app/src` trống).
- [ ] **AC-7:** `app/google-services.json` trong repo chứa `oauth_client` `client_type: 1` với certificate_hash khớp cả debug và release SHA.
- [ ] **AC-8:** `scripts/release_gate.sh` xanh (unit tests + lint + enforceModuleBoundaries).

## Scenarios

### Scenario 1: Happy path Google (lần đầu)

**Given** Thiết bị đã cài app bản debug/release mới, có tài khoản Google, Firebase đã đăng ký đúng SHA
**When** Người dùng bấm "Đăng nhập với Google" và chọn tài khoản
**Then** Credential Manager trả idToken → `signInWithGoogle` thành công → session + permissions load → vào màn workspace; log `provider=google isNewUser=true`.

### Scenario 2: Google picker bị hủy

**Given** Account picker đang mở
**When** Người dùng bấm back/hủy
**Then** App quay lại màn đăng nhập bình thường, spinner dừng, không hiện lỗi đỏ (chỉ message nhẹ "Đã hủy đăng nhập Google").

### Scenario 3: Thiết bị sai cấu hình SHA

**Given** Cài APK chưa đăng ký SHA (ví dụ build bằng keystore khác)
**When** Thử đăng nhập Google
**Then** Hiện lỗi cấu hình rõ ràng (dẫn dắt kiểm tra SHA trong Firebase console) thay vì treo/im lặng; log ghi mã lỗi gốc.

### Scenario 4: Đăng ký email thành công sau sửa

**Given** Email mới, mạng ổn định
**When** Nhập email/mật khẩu/xác nhận, bấm "Tạo tài khoản"
**Then** Trong ≤30s: user được tạo, email xác thực được gửi, quay về chế độ đăng nhập kèm thông báo nhắc xác thực.

### Scenario 5: Đăng ký email treo do mạng

**Given** Mạng bị chặn tới firebaseio sau khi bấm đăng ký
**When** 30 giây trôi qua không có phản hồi
**Then** Lời gọi bị hủy bởi timeout, hiện thông báo lỗi mạng/thử lại, spinner dừng; log ghi bước nào vượt thời hạn.

## Technical Notes

- File dự kiến thay đổi:
  - `app/src/main/java/com/mapsupervision/app/FirebaseAccessGate.kt` — thay launcher legacy bằng Credential Manager; xóa `resolveGoogleServerClientId`, `mapGoogleSignInError` chỉnh lại theo exception mới.
  - `data/src/main/java/com/mapsupervision/data/sync/FirebaseAccessRepositoryImpl.kt` — helper `awaitWithTimeout` + mapper lỗi; giữ nguyên hợp đồng interface `:domain`.
  - `app/build.gradle.kts` — gỡ `play-services-auth` nếu sau migrate không còn tham chiếu (giữ `credentials`, `credentials-play-services-auth`, `googleid`).
  - `app/google-services.json` — thay bằng file regenerate từ Firebase console sau khi thêm SHA.
- Lệnh lấy SHA chạy trên máy Windows của user (agent VM không có keystore/JDK17):
  - Debug: `keytool -list -v -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android`
  - Release: `keytool -list -v -alias <RELEASE_KEY_ALIAS> -keystore C:\AndroidKeys\mapsupervision-release.jks`
  - So sánh release SHA-256 với giá trị đã ghi nhận ở trên để tránh thêm nhầm chứng chỉ cũ.
- `serverClientId` của Credential Manager = Web client ID (`client_type: 3`) trong google-services.json — chính là giá trị resource `default_web_client_id` được plugin google-services sinh ra.
- Triệu chứng "picker hiện rất chậm" cần đo lại sau khi sửa cấu hình; nếu vẫn chậm thì điều tra riêng (nghi vấn Play Services trên thiết bị), không nằm trong phạm vi code của spec này.

## Task Links

*(Sẽ liên kết sau khi chạy `/kn-plan --from @doc/specs/2026-08-22/google-email-auth-fix.md`.)*

## Open Questions

- [ ] Sau khi đăng ký SHA xong, picker Google có nhanh lên không hay vẫn chậm do thiết bị/Play Services? (đo lại khi nghiệm thu AC-1)
- [ ] Có cần hỗ trợ thêm nút "Gửi lại email xác thực" không? (hiện đánh giá là ngoài phạm vi — D3 chọn giữ luồng tối giản)
