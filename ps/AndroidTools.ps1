$projectRoot = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $projectRoot '.gradle/toolchain'
$apkPath = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
$appId = 'com.sudokulab'

function Get-SdkCandidate {
    Join-Path $toolchain 'sdk'
    $env:ANDROID_HOME
    $env:ANDROID_SDK_ROOT
    if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}

function Get-Adb {
    foreach ($sdk in (Get-SdkCandidate)) {
        if (-not $sdk) { continue }
        $candidate = Join-Path $sdk 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    $command = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    throw 'No se encuentra adb.exe. Instala Android SDK Platform-Tools y configura ANDROID_HOME o PATH.'
}

function Assert-DebugApk {
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
        throw "Falta el APK en $apkPath. Ejecuta primero Compilar.ps1."
    }
}

function Invoke-AndroidAdb {
    param([string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    $output = & $adb @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    }
}

function Install-And-SmokeTest {
    param([string[]]$DeviceArgs, [string]$Target)

    Write-Host "Instalando SudokuLab en $Target..."
    $install = Invoke-AndroidAdb ($DeviceArgs + @('install', '-r', $apkPath))
    if ($install.ExitCode -ne 0 -or $install.Output -notmatch '(?m)^Success\s*$') {
        $advice = switch -Regex ($install.Output) {
            'UPDATE_INCOMPATIBLE|signatures do not match' { 'Usa un APK firmado con la misma clave.'; break }
            'VERSION_DOWNGRADE' { 'El dispositivo tiene instalada una version mas reciente.'; break }
            'INSUFFICIENT_STORAGE' { 'Libera espacio en el dispositivo.'; break }
            'USER_RESTRICTED|CANCELED_BY_USER|FAILED_ABORTED' { 'Desbloquea el dispositivo y revisa si pide confirmar la instalacion.'; break }
            'OLDER_SDK' { 'SudokuLab requiere Android 6.0 o posterior (API 23).'; break }
            default { 'Comprueba la conexion ADB y el APK.' }
        }
        throw "No se pudo instalar SudokuLab en ${Target}. $advice`n$($install.Output)"
    }

    Write-Host "Comprobando el arranque en $Target..."
    $launch = Invoke-AndroidAdb ($DeviceArgs + @('shell', 'am', 'start', '-W', '-n', "$appId/.MainActivity"))
    if ($launch.ExitCode -ne 0 -or $launch.Output -notmatch '(?im)^Status:\s+ok\s*$' -or
        $launch.Output -match '(?im)^(Error\b|Exception\b)') {
        throw "No se pudo abrir SudokuLab en ${Target}.`n$($launch.Output)"
    }

    Start-Sleep -Seconds 2
    # Check the process with ps because pidof is absent on some API 23 devices.
    $process = Invoke-AndroidAdb ($DeviceArgs + @('shell', 'ps'))
    if ($process.ExitCode -ne 0 -or $process.Output -notmatch "(?m)\s$([regex]::Escape($appId))\s*$") {
        throw "El proceso de SudokuLab no sigue activo en $Target tras el arranque. Revisa adb logcat con el dispositivo seleccionado."
    }
    Write-Host "SudokuLab instalado y prueba de arranque superada en $Target." -ForegroundColor Green
}
