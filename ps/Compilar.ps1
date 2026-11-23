[CmdletBinding()]
param([switch]$Offline)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

$localJdk = Get-ChildItem -LiteralPath $toolchain -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
    Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') -PathType Leaf } |
    Select-Object -First 1
$studioJdk = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
if ($localJdk) {
    $env:JAVA_HOME = $localJdk.FullName
} elseif (-not $env:JAVA_HOME -and -not (Get-Command java.exe -ErrorAction SilentlyContinue) -and
    (Test-Path -LiteralPath (Join-Path $studioJdk 'bin/java.exe') -PathType Leaf)) {
    $env:JAVA_HOME = $studioJdk
}

$sdk = Get-SdkCandidate | Where-Object {
    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platforms/android-35/android.jar') -PathType Leaf)
} | Select-Object -First 1
if ($sdk) {
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
} elseif (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties') -PathType Leaf)) {
    throw 'No se encontro Android SDK Platform 35. Instalalo y configura ANDROID_HOME o sdk.dir en local.properties.'
}

$localGradleHome = Join-Path $projectRoot '.gradle/user-home'
if (-not $env:GRADLE_USER_HOME -and (Test-Path -LiteralPath $localGradleHome -PathType Container)) {
    $env:GRADLE_USER_HOME = $localGradleHome
}

$gradle = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    $gradle = (Get-Command gradle.bat -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1).Source
    if (-not $gradle) {
        $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
        $gradle = Get-ChildItem -Path (Join-Path $gradleHome 'wrapper/dists/gradle-8.9-*/*/gradle-8.9/bin/gradle.bat') -File -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $gradle) {
        throw 'No se encontro Gradle. Anade Gradle 8.9 a PATH o usa el wrapper del proyecto si esta disponible.'
    }
}

$gradleArgs = @(':app:assembleDebug', '--console=plain')
if ($Offline) { $gradleArgs += '--offline' }

Push-Location -LiteralPath $projectRoot
try {
    $PSNativeCommandUseErrorActionPreference = $false
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle fallo con el codigo $LASTEXITCODE." }
    Assert-DebugApk
    Write-Host "APK generado: $apkPath" -ForegroundColor Green
} finally {
    Pop-Location
}
