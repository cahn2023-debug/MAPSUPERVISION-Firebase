# google-auth-sha-check.ps1 — Lấy SHA-1/SHA-256 của debug + release keystore
# Mục đích: đăng ký fingerprint vào Firebase Console để sửa Google Sign-In (DEVELOPER_ERROR).
# Chạy: powershell -ExecutionPolicy Bypass -File specs\2026-08-22\google-auth-sha-check.ps1
# Script KHÔNG in mật khẩu ra màn hình/log.

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# --- Tìm keytool ---
$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if ($null -eq $keytool) {
    $candidates = @(
        "$env:JAVA_HOME\bin\keytool.exe",
        "C:\Program Files\Eclipse Adoptium\jdk-17*\bin\keytool.exe",
        "C:\Program Files\Java\jdk-17*\bin\keytool.exe",
        "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    ) | ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } | Sort-Object FullName -Descending
    if ($candidates.Count -eq 0) { throw "Khong tim thay keytool. Hay chay tu terminal co JDK 17 trong PATH." }
    $keytoolExe = $candidates[0].FullName
} else {
    $keytoolExe = $keytool.Source
}
Write-Host "== keytool: $keytoolExe =="

function Get-Fingerprints([string]$Label, [string]$KeystorePath, [string]$StorePass, [string]$Alias) {
    Write-Host ""
    Write-Host "== $Label =="
    if (-not (Test-Path $KeystorePath)) {
        Write-Warning "Khong tim thay keystore: $KeystorePath"
        return $null
    }
    # Khong de stderr cua keytool dung script; luon thu thap output + exit code.
    $oldEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = (& $keytoolExe -list -v -alias $Alias -keystore $KeystorePath -storepass $StorePass 2>&1) |
            ForEach-Object { $_.ToString() }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldEap
    }
    if ($exitCode -ne 0) {
        Write-Host "[LOI] keytool thoat voi ma $exitCode cho $Label. Thong diep goc:" -ForegroundColor Yellow
        $output | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        Write-Host "  Goi y: kiem tra alias va RELEASE_STORE_PASSWORD trong local.properties" -ForegroundColor Yellow
        Write-Host "  (keytool -list chi can mat khau cua keystore, khong phai mat khau cua key)." -ForegroundColor DarkGray
        return $null
    }
    $sha1 = ($output | Where-Object { $_ -match "SHA1:" } | Select-Object -First 1) -replace "^.*SHA1:\s*", ""
    $sha256 = ($output | Where-Object { $_ -match "SHA256:" } | Select-Object -First 1) -replace "^.*SHA256:\s*", ""
    Write-Host ("  Keystore : {0}" -f $KeystorePath)
    Write-Host ("  Alias    : {0}" -f $Alias)
    Write-Host ("  SHA-1    : {0}" -f $sha1)
    Write-Host ("  SHA-256  : {0}" -f $sha256)
    return [pscustomobject]@{ Label = $Label; Sha1 = $sha1; Sha256 = $sha256 }
}

$results = @()

# --- Debug keystore (mat khau mac dinh cua Android debug keystore) ---
$debugKeystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
$results += Get-Fingerprints -Label "DEBUG BUILD (debug.keystore)" `
    -KeystorePath $debugKeystore -StorePass "android" -Alias "androiddebugkey"

# --- Release keystore (thong tin tu local.properties, khong in pass) ---
$propsMap = @{}
$localPropsPath = Join-Path $repo "local.properties"
if (Test-Path $localPropsPath) {
    Get-Content $localPropsPath | ForEach-Object {
        if ($_ -match "^\s*(RELEASE_[A-Z_]+)\s*=(.*)$") {
            $propsMap[$Matches[1]] = $Matches[2].Trim()
        }
    }
}

if ($propsMap["RELEASE_STORE_FILE"] -and $propsMap["RELEASE_KEY_ALIAS"]) {
    $releaseKeystore = if ([System.IO.Path]::IsPathRooted($propsMap["RELEASE_STORE_FILE"])) {
        $propsMap["RELEASE_STORE_FILE"]
    } else {
        Join-Path $repo $propsMap["RELEASE_STORE_FILE"]
    }
    $storePass = $propsMap["RELEASE_STORE_PASSWORD"]
    $results += Get-Fingerprints -Label "RELEASE BUILD (keystore production)" `
        -KeystorePath $releaseKeystore -StorePass $storePass -Alias $propsMap["RELEASE_KEY_ALIAS"]
} else {
    Write-Warning "Bo qua release: thieu RELEASE_STORE_FILE / RELEASE_KEY_ALIAS trong local.properties."
}

# --- Doi chieu release SHA-256 voi gia tri da biet tu lan dong goi 2026-08-22 ---
$knownReleaseSha256 = "D05F5B73D1A3008723E7615813492DA61BAAA1B7B0C0F900537351F952EBE34A"
$rel = $results | Where-Object { $_.Label -like "RELEASE*" } | Select-Object -First 1
Write-Host ""
if ($rel) {
    $normalized = ($rel.Sha256 -replace ":", "").ToUpper()
    if ($normalized -eq $knownReleaseSha256) {
        Write-Host "[OK] Release SHA-256 khop voi chu ky da ghi nhan khi dong goi v1.1." -ForegroundColor Green
    } else {
        Write-Warning "[CANH BAO] Release SHA-256 KHAC voi chu ky da ghi nhan (d05f5b73...ebe34a). Neu ban da doi keystore hay xac nhan lai truoc khi them vao Firebase."
    }
}

Write-Host ""
Write-Host "===== BUOC TIEP THEO ====="
Write-Host "1. Mo https://console.firebase.google.com > project 'mapsupervision'"
Write-Host "2. Project settings > tab General > app Android 'com.mapsupervision'"
Write-Host "3. Add fingerprint: dan tung SHA-1 va SHA-256 o tren (debug + release)"
Write-Host "4. Save > tai lai google-services.json > thay vao thu muc app\ cua repo"
Write-Host "5. Gui lai file google-services.json moi (hoac bao da thay) de agent verify"
