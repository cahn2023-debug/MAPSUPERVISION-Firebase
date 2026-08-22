# run_release_check.ps1
# Spec: specs/2026-08-22/release-signed-apk.md (approved) — Wave 1 (gate) + Wave 2 (build + verify)
# Chạy MỘT lệnh từ bất kỳ đâu:
#   powershell -ExecutionPolicy Bypass -File "D:\Code Antinigaty\MAPSUPERVISION-Firebase\specs\2026-08-22\run_release_check.ps1"
# Log tự lưu tại specs\2026-08-22\runs\release-run-<timestamp>.log (đã gitignore qua *.log)
# Quy tắc: gate đỏ => KHÔNG chạy assembleRelease (Scenario 2 của spec).

$ErrorActionPreference = "Continue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $root
$runsDir = Join-Path $PSScriptRoot "runs"
New-Item -ItemType Directory -Force -Path $runsDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $runsDir "release-run-$stamp.log"
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

# ---------- Step 0: Preconditions ----------
Step "0. Preconditions"
$lp = @{}
Get-Content (Join-Path $root "local.properties") | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') { $lp[$Matches[1].Trim()] = $Matches[2].Trim() }
}
$needKeys = @("RELEASE_STORE_FILE","RELEASE_STORE_PASSWORD","RELEASE_KEY_ALIAS","RELEASE_KEY_PASSWORD")
$missing = $needKeys | Where-Object { [string]::IsNullOrWhiteSpace($lp[$_]) }
Check "local.properties du 4 key RELEASE_*" ($missing.Count -eq 0)
if ($missing.Count -gt 0) { Write-Host "  Thieu: $($missing -join ', ')" }
$storeFile = ($lp["RELEASE_STORE_FILE"] -replace '\\:', ':') -replace '\\\\', '\'
$keystoreOk = (Test-Path $storeFile)
Check "Keystore ton tai: $storeFile" $keystoreOk
if (-not ($missing.Count -eq 0 -and $keystoreOk)) {
    Write-Host "`nRESULT: BLOCKED o Step 0 - khong tiep tuc." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}

# ---------- Step 1: Release gate (tuong duong scripts/release_gate.sh theo runbook §2.3) ----------
Step "1. Release gate"
$g1 = RunGradle @(":app:testDebugUnitTest");                 Check ":app:testDebugUnitTest" $g1
$g2 = RunGradle @(":storage-import:testDebugUnitTest");      Check ":storage-import:testDebugUnitTest" $g2
$g3 = RunGradle @(":data:testDebugUnitTest");                Check ":data:testDebugUnitTest" $g3
if (-not ($g1 -and $g2 -and $g3)) {
    Write-Host "`nRESULT: GATE DO (unit test) - dung theo Scenario 2, khong chay assembleRelease." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
$g4 = RunGradle @("lint", "assembleDebug", "enforceModuleBoundaries"); Check "lint + assembleDebug + enforceModuleBoundaries" $g4
if (-not $g4) {
    Write-Host "`nRESULT: GATE DO (lint/boundary) - dung theo Scenario 2, khong chay assembleRelease." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
$docs = @("docs\release_gate_runbook.md","docs\tab_nhap_lieu_data_hub.md","production-ready-roadmap.md")
$docsOk = ($docs | Where-Object { -not (Test-Path (Join-Path $root $_)) }).Count -eq 0
Check "Tai lieu bat buoc ton tai" $docsOk
if (-not ($g4 -and $docsOk)) {
    Write-Host "`nRESULT: GATE DO - khong chay assembleRelease." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
Write-Host "`n[release-gate] Release gate passed (PowerShell-equivalent)" -ForegroundColor Green

# ---------- Step 2: Build release ky ten ----------
Step "2. assembleRelease"
$b1 = RunGradle @(":app:assembleRelease"); Check ":app:assembleRelease" $b1
if (-not $b1) {
    Write-Host "`nRESULT: BLOCKED - build release that bai (xem Scenario 3: kiem tra signing)." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
$relDir = Join-Path $root "app\build\outputs\apk\release"
$apks = Get-ChildItem $relDir -Filter *.apk -ErrorAction SilentlyContinue
$apks | ForEach-Object { Write-Host ("  {0}  ({1:N0} bytes)" -f $_.Name, $_.Length) }
$a64  = $apks | Where-Object Name -like "*arm64-v8a*"
$a32  = $apks | Where-Object Name -like "*armeabi-v7a*"
Check "Co APK arm64-v8a"   ($null -ne $a64)
Check "Co APK armeabi-v7a" ($null -ne $a32)
if ($null -eq $a64 -or $null -eq $a32) {
    Write-Host "`nRESULT: BLOCKED - thieu APK theo ABI split." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}

# ---------- Step 3: Verify chu ky + version + SHA-256 ----------
Step "3. apksigner / aapt / SHA-256"
$btRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"
$btDir = Get-ChildItem $btRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^\d+(\.\d+)*$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if ($null -eq $btDir) {
    Write-Host "FAIL: khong tim thay build-tools tai $btRoot" -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
$bt = $btDir.FullName
Write-Host "Dung build-tools: $($btDir.Name)"

foreach ($apk in @($a64, $a32)) {
    Write-Host "`n--- apksigner verify: $($apk.Name) ---"
    & "$bt\apksigner.bat" verify --print-certs $apk.FullName
    Check "apksigner verify $($apk.Name)" ($LASTEXITCODE -eq 0)
    Write-Host "--- badging: $($apk.Name) ---"
    & "$bt\aapt.exe" dump badging $apk.FullName | Select-String "^package:"
    Write-Host "--- SHA-256: $($apk.Name) ---"
    (Get-FileHash $apk.FullName -Algorithm SHA256).Hash
}

# ---------- Step 4: Smoke test (thu cong tren may that) ----------
Step "4. Smoke test - THUC HIEN THU CONG"
Write-Host @"
Chay tay tren may tinh:
  adb install -r "$($a64.FullName)"
  adb logcat -c
Roi kiem tra tren dien thoai (runbook §5):
  1. Mo app -> vao workspace thanh cong
  2. Chuyen project active
  3. Tab data -> import 1 file mau
  4. Map / dashboard / imported files cap nhat
  5. Tab reports -> tao preview/export
  6. (Neu tien) thu capture photo hoac share intent
Neu crash:  adb logcat -d *:E > release-crash.log
"@

# ---------- Summary ----------
Step "Summary"
Write-Host ("Log: " + $logPath)
if ($script:ok) {
    Write-Host "`nRESULT: AUTOMATED STEPS PASSED (Step 0-3). Con lai smoke test thu cong (Step 4)." -ForegroundColor Green
    Stop-Transcript | Out-Null; exit 0
} else {
    Write-Host "`nRESULT: CO BUOC FAIL - xem log cac dong FAIL o tren." -ForegroundColor Red
    Stop-Transcript | Out-Null; exit 1
}
