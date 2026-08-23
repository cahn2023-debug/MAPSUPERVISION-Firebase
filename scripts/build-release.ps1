[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$localPropertiesPath = Join-Path $repoRoot "local.properties"
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$releaseOutputDir = Join-Path $repoRoot "app\build\outputs\apk\release"

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "gradlew.bat was not found at $gradleWrapper"
}

if (-not (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf)) {
    throw "local.properties was not found. Configure release signing before building."
}

$properties = @{}
Get-Content -LiteralPath $localPropertiesPath | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=(.*)$') {
        $properties[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

$requiredKeys = @(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
)
$missingKeys = @($requiredKeys | Where-Object {
    -not $properties.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($properties[$_])
})

if ($missingKeys.Count -gt 0) {
    throw "Missing release signing properties: $($missingKeys -join ', ')"
}

$storeFile = [Environment]::ExpandEnvironmentVariables($properties["RELEASE_STORE_FILE"])
if (-not [IO.Path]::IsPathRooted($storeFile)) {
    $storeFile = Join-Path (Join-Path $repoRoot "app") $storeFile
}

if (-not (Test-Path -LiteralPath $storeFile -PathType Leaf)) {
    throw "Release keystore was not found: $storeFile"
}

Write-Host "Repository: $repoRoot"
Write-Host "Release signing configuration: OK"
Write-Host "Building :app:assembleRelease..."

Push-Location $repoRoot
try {
    & $gradleWrapper "--no-daemon" ":app:assembleRelease"
    if ($LASTEXITCODE -ne 0) {
        throw ":app:assembleRelease failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$apks = @(Get-ChildItem -LiteralPath $releaseOutputDir -Filter "*.apk" -File -ErrorAction SilentlyContinue)
if ($apks.Count -eq 0) {
    throw "Build succeeded but no APK was found in $releaseOutputDir"
}

Write-Host ""
Write-Host "Release APKs:"
$apks | ForEach-Object {
    Write-Host ("- {0} ({1:N0} bytes)" -f $_.FullName, $_.Length)
}
