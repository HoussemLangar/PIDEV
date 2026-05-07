param(
    [Parameter(Mandatory = $false)]
    [string]$ShortcutName = "SanteA JavaFX",

    [Parameter(Mandatory = $false)]
    [string]$VmName = "PiDev",

    [Parameter(Mandatory = $false)]
    [string]$VmConfigPath = "C:\Users\hazem\Documents\Virtuel Machines\PiDev\PiDev.vmx"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$realScript = Join-Path $scriptDir "windows\create-javafx-shortcut-windows.ps1"

if (-not (Test-Path $realScript)) {
    throw "Script introuvable: $realScript"
}

& $realScript -ShortcutName $ShortcutName -VmName $VmName -VmConfigPath $VmConfigPath
