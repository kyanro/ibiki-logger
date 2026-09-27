param([string]$Serial)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$capturePackage = 'com.kyanro.ibiki_logger.screenshots'
$testPackage = "$capturePackage.test"

if (-not $Serial) {
    $devices = @(& $adbPath devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($LASTEXITCODE -ne 0 -or $devices.Count -ne 1) { throw 'Connect one unlocked device, or specify -Serial.' }
    $Serial = $devices[0]
}
if ($Serial -notmatch '^[a-zA-Z0-9._:-]+$') { throw 'Invalid device serial.' }
$deviceState = & $adbPath -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') { throw 'Device is not available.' }
foreach ($package in @($capturePackage, $testPackage)) {
    $installed = @(& $adbPath -s $Serial shell pm path $package)
    if ($LASTEXITCODE -ne 0 -and $installed.Count -gt 0) { throw 'Could not query the device.' }
    if ($installed -match '^package:') { throw "$package is already installed. Remove that capture-only app before retrying." }
}

& (Join-Path $PSScriptRoot 'build.ps1') -Tasks @('assembleScreenshots', 'assembleScreenshotsAndroidTest', '-PreadmeScreenshots=true')
$apkDirectory = Join-Path $projectRoot 'android-app\app\build\outputs\apk'
$metadata = Get-Content -Raw -LiteralPath (Join-Path $apkDirectory 'screenshots\output-metadata.json') | ConvertFrom-Json
if ($metadata.applicationId -ne $capturePackage) { throw 'Refusing to install an APK with an unexpected application ID.' }
$stagingDirectory = Join-Path $projectRoot 'artifacts\readme-screenshots'
$imageDirectory = Join-Path $projectRoot 'docs\images'
New-Item -ItemType Directory -Force -Path $stagingDirectory, $imageDirectory | Out-Null
$appInstalled = $false
$testInstalled = $false
try {
    & $adbPath -s $Serial install -r -t (Join-Path $apkDirectory 'screenshots\app-screenshots.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Capture app installation failed.' }
    $appInstalled = $true
    & $adbPath -s $Serial install -r -t (Join-Path $apkDirectory 'androidTest\screenshots\app-screenshots-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Capture runner installation failed.' }
    $testInstalled = $true
    $result = @(& $adbPath -s $Serial shell am instrument -w -r -e class com.kyanro.ibiki_logger.ReadmeScreenshots "$testPackage/androidx.test.runner.AndroidJUnitRunner")
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(1 test\)') {
        $result | Write-Output
        throw 'Screenshot capture failed.'
    }
    $names = @('home.png', 'timeline.png', 'clips.png')
    foreach ($name in $names) {
        $staged = Join-Path $stagingDirectory $name
        & $adbPath -s $Serial pull "/sdcard/Android/data/$capturePackage/files/readme-screenshots/$name" $staged
        if ($LASTEXITCODE -ne 0) { throw "Could not retrieve $name." }
        $bytes = [System.IO.File]::ReadAllBytes($staged)
        if ($bytes.Length -lt 24 -or [BitConverter]::ToString($bytes, 0, 8) -ne '89-50-4E-47-0D-0A-1A-0A') { throw "$name is not a PNG." }
    }
    foreach ($name in $names) { Copy-Item -LiteralPath (Join-Path $stagingDirectory $name) -Destination (Join-Path $imageDirectory $name) }
    Write-Output "Updated the three documentation screenshots in $imageDirectory."
} finally {
    if ($testInstalled) { & $adbPath -s $Serial uninstall $testPackage }
    if ($appInstalled) { & $adbPath -s $Serial uninstall $capturePackage }
}
