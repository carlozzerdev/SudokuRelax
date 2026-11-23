[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

Assert-DebugApk
$adb = Get-Adb
$usb = @('-d')

Write-Host 'Comprobando el telefono conectado por USB...'
$state = Invoke-AndroidAdb ($usb + @('get-state'))
if ($state.ExitCode -ne 0 -or $state.Output -notmatch '(?m)^device\s*$') {
    $advice = switch -Regex ($state.Output) {
        'unauthorized' { 'Desbloquea el telefono y acepta Permitir depuracion por USB.'; break }
        'offline' { 'Desbloquea el telefono y vuelve a conectar el USB.'; break }
        'more than one' { 'Deja conectado solo el telefono que quieres probar.'; break }
        default { 'Comprueba la depuracion USB, el cable de datos y el controlador USB del fabricante.' }
    }
    throw "No se pudo conectar con el telefono por USB. $advice`n$($state.Output)"
}

Install-And-SmokeTest -DeviceArgs $usb -Target 'el telefono USB'
