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
$startScript = Join-Path $scriptDir "start-javafx-windows.ps1"
if (-not (Test-Path $startScript)) {
    throw "Script introuvable: $startScript"
}

$desktopPath = [Environment]::GetFolderPath("Desktop")
$shortcutPath = Join-Path $desktopPath ($ShortcutName + ".lnk")

$wsh = New-Object -ComObject WScript.Shell
$shortcut = $wsh.CreateShortcut($shortcutPath)
$shortcut.TargetPath = "powershell.exe"
$shortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$startScript`" -VmName `"$VmName`" -VmConfigPath `"$VmConfigPath`""
$shortcut.WorkingDirectory = $scriptDir
$shortcut.Description = "Lancer JavaFX SanteA"
$shortcut.IconLocation = "$env:SystemRoot\System32\shell32.dll,220"
$shortcut.Save()

Write-Host "Shortcut creee: $shortcutPath"
