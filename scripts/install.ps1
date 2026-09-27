param([string]$Serial)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
$cliPath = Join-Path $projectRoot '.tools\android.exe'
if (-not (Test-Path -LiteralPath $cliPath)) {
    $cliCommand = Get-Command android -ErrorAction SilentlyContinue
    if (-not $cliCommand) { throw 'Android CLIをインストールしてください。README.mdを参照してください。' }
    $cliPath = $cliCommand.Source
}
if (-not $Serial) {
    $devices = @(& $adbPath devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($devices.Count -ne 1) { throw '端末を1台接続するか、-Serialで端末を指定してください。' }
    $Serial = $devices[0]
}
$apk = Join-Path $projectRoot 'android-app\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw '先にscripts/build.ps1を実行してください。' }
& $cliPath --no-metrics "--sdk=$sdkPath" run "--apks=$apk" "--device=$Serial"
if ($LASTEXITCODE -ne 0) { throw "Android CLI failed: $LASTEXITCODE" }
