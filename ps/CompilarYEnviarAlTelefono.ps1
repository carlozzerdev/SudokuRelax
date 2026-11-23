[CmdletBinding()]
param([switch]$Offline)

$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'Compilar.ps1') -Offline:$Offline
& (Join-Path $PSScriptRoot 'ApkToPhone.ps1')
