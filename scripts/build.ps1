param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $studioJava = Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'
    if (-not (Test-Path -LiteralPath $studioJava)) { $studioJava = 'C:\Program Files\Android\Android Studio\jbr' }
    if (-not (Test-Path -LiteralPath $studioJava)) { throw 'JDK 17以降をインストールし、JAVA_HOMEを設定してください。' }
    $env:JAVA_HOME = $studioJava
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
Push-Location (Join-Path $projectRoot 'android-app')
try {
    & .\gradlew.bat @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
