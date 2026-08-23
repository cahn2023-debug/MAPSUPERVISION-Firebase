# run_auth_check_phase1.ps1 — Kiểm chứng nhanh sửa lỗi auth (gaf-04, Giai đoạn 1)
# Spec: specs/2026-08-22/google-email-auth-fix.md — build debug + test :data + cài máy thật
# Chạy MỘT lệnh:
#   powershell -ExecutionPolicy Bypass -File "D:\Code Antinigaty\MAPSUPERVISION-Firebase\specs\2026-08-22\run_auth_check_phase1.ps1"
# Log: specs\2026-08-22\runs\auth-phase1-<timestamp>.log

$ErrorActionPreference = "Continue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $root
$runsDir = Join-Path $PSScriptRoot "runs"
New-Item -ItemType Directory -Force -Path $runsDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $runsDir "auth-phase1-$stamp.log"
Start-Transcript -Path $logPath | Out-Null

$script:ok = $true
function Step($name) { Write-Host "`n===== [$name] $(Get-Date -Format 'HH:mm:ss') =====" -ForegroundColor Cyan }
function Check($name, $passed) {
    if ($passed) { Write-Host "PASS: $name" -ForegroundColor Green }
    else { Write-Host "FAIL: $name" -ForegroundColor Red; $script:ok = $false }
}
function RunGradle($tasks) {
    Write-Host ">> .\gradlew.bat $tasks"
    & .\gradlew.bat $tasks
    return ($LASTEXITCODE -eq 0)
}

Write-Host "Repo root: $root"
Write-Host "Log file : $logPath"

# ---------- Step 1: Unit test :data (mapper + timeout) ----------
Step "1. :data unit tests (FirebaseAuthErrorMapper + toan bo module)"
$t1 = RunGradle @(":data:testDebugUnitTest")
Check ":data:testDebugUnitTest" $t1

# ---------- Step 2: Build debug (Credential Manager compile) ----------
Step "2. assembleDebug"
$b1 = RunGradle @(":app:assembleDebug")
Check ":app:assembleDebug" $b1
if (-not ($t1 -and $b1)) {
    Write-Host "`nRESULT: BUILD/TEST DO - gui log cho agent (khong can cai may)." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}

# ---------- Step 3: Cai len may that ----------
Step "3. Cai debug APK len may that"
$debugDir = Join-Path $root "app\build\outputs\apk\debug"
$apk = Join-Path $debugDir "app-arm64-v8a-debug.apk"
if (-not (Test-Path $apk)) {
    $foundApk = Get-ChildItem -Path $debugDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($foundApk) { $apk = $foundApk.FullName }
}
Check "APK debug ton tai ($apk)" (Test-Path $apk)
if (-not (Test-Path $apk)) { Stop-Transcript | Out-Null; exit 1 }

# Tim adb
$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adb) {
    $adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $adbPath) { $adb = $adbPath } else { $adb = $null }
} else { $adb = $adb.Source }
if ($null -eq $adb) {
    Write-Warning "Khong tim thay adb. Cai thu cong: copy $apk vao may va cai dat."
} else {
    & $adb devices
    $devices = (& $adb devices) | Where-Object { $_ -match "`tdevice$" }
    if ($devices.Count -eq 0) {
        Write-Warning "Khong co may that nao ket noi. Cai thu cong file: $apk"
    } else {
        # May co the dang co v1.1 release (signature khac debug) => phai go truoc
        $pkg = "com.mapsupervision"
        $installed = (& $adb shell pm list packages $pkg) -join ""
        if ($installed -match "package:$pkg") {
            Write-Host "Phat hien $pkg da cai (co the la release v1.1, signature khac debug)." -ForegroundColor Yellow
            $answer = Read-Host "GO app cu de cai debug? Mat du lieu offline cuc bo (y/N)"
            if ($answer -eq "y") {
                & $adb uninstall $pkg
                Check "Go app cu" ($LASTEXITCODE -eq 0)
            } else {
                Write-Host "Bo qua buoc go - ban tu cai thu cong." -ForegroundColor Yellow
            }
        }
        & $adb install -r $apk
        Check "adb install debug" ($LASTEXITCODE -eq 0)
    }
}

# ---------- Step 4: Checklist smoke thu cong ----------
Step "4. SMOKE CHECKLIST - thu tren may that"
Write-Host @"
Cac buoc kiem tra tren dien thoai (ghi lai ket qua tung muc):
  [S1] Mo app -> man hinh Dang nhap hien binh thuong
  [S2] Bam 'Dang nhap voi Google' -> picker/bottom sheet xuat hien trong ~5 giay
  [S3] Chon tai khoan Google -> vao thang man hinh workspace
  [S4] Dang xuat -> bam lai Google -> chon TAI KHOAN KHAC (lan dau dung app) -> van vao duoc
  [S5] Bam 'Tao tai khoan': nhap email moi + mat khau -> spinner ket thuc <=30 giay,
       thong bao 'Kiem tra email...' -> email xac thuc den hop thu
  [S6] Dang nhap sai mat khau -> thong bao tieng Viet co dau 'Email hoac mat khau khong dung...'
  [S7] Mo picker Google roi bam back/huy -> thong bao nhe 'Da huy dang nhap Google', khong loi do
  [S8] (Tuy chon) Bat may bay / tat wifi -> bam dang ky -> sau ~30 giay bao loi timeout, spinner DUNG
Neu loi: chay 'adb logcat -d > auth-smoke.log' va gui agent.
"@

Step "Summary"
Write-Host ("Log: " + $logPath)
if ($script:ok) {
    Write-Host "`nRESULT: BUILD + TEST XANH. Hay thu checklist S1-S8 va bao lai ket qua." -ForegroundColor Green
    Stop-Transcript | Out-Null; exit 0
} else {
    Write-Host "`nRESULT: CO BUOC FAIL - gui log cho agent." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
