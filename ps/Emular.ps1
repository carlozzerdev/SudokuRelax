[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Puerto = 5555,
    [ValidateRange(1, 600)]
    [int]$Espera = 120,
    [switch]$Compilar,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

if ($Offline -and -not $Compilar) { throw 'Usa -Offline junto con -Compilar.' }
if ($Compilar) { & (Join-Path $PSScriptRoot 'Compilar.ps1') -Offline:$Offline }

Assert-DebugApk
$adb = Get-Adb
$device = "127.0.0.1:$Puerto"
$target = @('-s', $device)

if (-not (Get-Process -Name 'HD-Player' -ErrorAction SilentlyContinue)) {
    $player = Join-Path $env:ProgramFiles 'BlueStacks_nxt/HD-Player.exe'
    if (-not (Test-Path -LiteralPath $player -PathType Leaf)) {
        throw 'Abre BlueStacks 5 desde el menu Inicio y vuelve a ejecutar Emular.ps1.'
    }
    Write-Host 'Abriendo BlueStacks 5...'
    Start-Process -FilePath $player -WindowStyle Hidden
}

Write-Host "Esperando a BlueStacks en $device..."
$timer = [Diagnostics.Stopwatch]::StartNew()
do {
    $connection = Invoke-AndroidAdb @('connect', $device)
    $state = Invoke-AndroidAdb ($target + @('get-state'))
    if ($state.ExitCode -eq 0 -and $state.Output -match '(?m)^device\s*$') {
        $boot = Invoke-AndroidAdb ($target + @('shell', 'getprop', 'sys.boot_completed'))
        if ($boot.ExitCode -eq 0 -and $boot.Output.Trim() -eq '1') {
            Install-And-SmokeTest -DeviceArgs $target -Target 'BlueStacks'
            return
        }
    }
    Start-Sleep -Seconds 2
} while ($timer.Elapsed.TotalSeconds -lt $Espera)

throw "BlueStacks no esta listo en $device. Activa Android Debug Bridge en Ajustes > Avanzado y usa -Puerto con el puerto de la instancia.`n$($connection.Output)`n$($state.Output)"
