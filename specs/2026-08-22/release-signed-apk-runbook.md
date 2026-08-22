# Runbook: Release Signed APK v1.1 (versionCode 2)

Spec: @doc/specs/2026-08-22/release-signed-apk.md (approved 2026-08-22)
Nơi chạy: **Windows PowerShell**, tại root repo `D:\Code Antinigaty\MAPSUPERVISION-Firebase`
Nguyên tắc: làm theo đúng thứ tự; dừng ở bước nào fail thì gửi lại output của bước đó trước khi đi tiếp.

## Bước 0 — Tiền điều kiện (kiểm tra ~1 phút)

```powershell
# Keystore tồn tại?
Test-Path C:\AndroidKeys\mapsupervision-release.jks   # phải trả về True

# local.properties đủ 4 key RELEASE_*?
Get-Content local.properties | Select-String "RELEASE_"
```

Mong đợi: `True` và đủ `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Bước 1 — Release gate

Ưu tiên chạy qua script (cần Git Bash):

```powershell
sh ./scripts/release_gate.sh
```

Nếu máy không có `sh`, chạy tương đương từng lệnh:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :storage-import:testDebugUnitTest
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat lint assembleDebug enforceModuleBoundaries
Test-Path docs\release_gate_runbook.md; Test-Path docs\tab_nhap_lieu_data_hub.md; Test-Path production-ready-roadmap.md
```

Điểm đạt: lệnh cuối in `[release-gate] Release gate passed` (hoặc cả 3 `Test-Path` đều `True`).
⚠️ Lưu ý: build profile tuần tự/ít RAM — bước này có thể mất nhiều phút, đừng ngắt giữa chừng.

## Bước 2 — Build release ký tên

```powershell
.\gradlew.bat :app:assembleRelease
```

Điểm đạt: `BUILD SUCCESSFUL` và có APK tại `app\build\outputs\apk\release\`.

```powershell
Get-ChildItem app\build\outputs\apk\release\*.apk | Select-Object Name, Length
```

Mong đợi đúng 2 file: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`.

## Bước 3 — Xác minh chữ ký + version

Tìm build-tools version đang có:

```powershell
Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Select-Object -ExpandProperty Name
```

Dùng version cao nhất (ví dụ `35.0.0`) trong các lệnh dưới:

```powershell
$bt = "$env:LOCALAPPDATA\Android\Sdk\build-tools\<ver>"

# Chữ ký + certs
& "$bt\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-arm64-v8a-release.apk
& "$bt\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-armeabi-v7a-release.apk

# Version + applicationId (mỗi dòng phải ra versionCode=2, versionName=1.1, package=com.mapsupervision)
& "$bt\aapt.exe" dump badging app\build\outputs\apk\release\app-arm64-v8a-release.apk | Select-String "package:"
& "$bt\aapt.exe" dump badging app\build\outputs\apk\release\app-armeabi-v7a-release.apk | Select-String "package:"
```

Điểm đạt: `Verifed` / `Verified using ... scheme` OK cho cả 2 APK; certs khớp alias release.
Kèm ghi nhận SHA-256 (NFR-3):

```powershell
Get-FileHash app\build\outputs\apk\release\*.apk -Algorithm SHA256
```

## Bước 4 — Smoke test trên máy thật (arm64)

Bật USB debugging, kết nối điện thoại:

```powershell
adb devices                                          # thấy thiết bị, trạng thái device
adb install -r app\build\outputs\apk\release\app-arm64-v8a-release.apk
adb logcat -c                                        # xoá log cũ trước khi test
```

Chạy tay trên máy (checklist runbook §5 — giữ logcat mở trên PC):

1. Mở app → vào workspace thành công.
2. Chuyển project active.
3. Tab **data** → import 1 file mẫu.
4. Xác nhận map, dashboard, imported files cập nhật.
5. Tab **reports** → tạo preview/export.
6. (Nếu tiện) thử luồng media: capture photo hoặc share intent.

Nếu crash bất kỳ bước nào:

```powershell
adb logcat -d *:E > release-crash.log     # gửi lại file này
```

→ Dừng, đánh dấu release **BLOCKED** theo Scenario 4 của spec (không sửa proguard nóng).

## Bước 5 — Báo lại kết quả

Gửi lại cho agent:

1. Output Bước 1 (dòng `[release-gate] Release gate passed`).
2. Danh sách APK + dung lượng (Bước 2).
3. Output `apksigner verify` + `dump badging` + SHA-256 (Bước 3).
4. Kết quả checklist smoke test từng mục (Bước 4), kèm `release-crash.log` nếu có.

Agent đối chiếu AC-1…AC-5 của spec và chốt trạng thái release.
