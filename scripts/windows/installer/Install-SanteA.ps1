param(
    [Parameter(Mandatory = $true)]
    [string]$InstallDir,

    [Parameter(Mandatory = $false)]
    [string]$RepoUrl = "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA.git",

    [Parameter(Mandatory = $false)]
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"

function Ensure-Git {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) {
        return
    }

    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        throw "Git n'est pas installe et winget est introuvable. Installez Git manuellement puis relancez."
    }

    Write-Host "Installation de Git via winget..."
    & $winget.Source install --id Git.Git -e --source winget --silent --accept-package-agreements --accept-source-agreements | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Echec installation Git via winget."
    }

    $env:PATH = "$env:ProgramFiles\\Git\\cmd;${env:ProgramFiles(x86)}\\Git\\cmd;$env:PATH"
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "Git reste introuvable apres installation."
    }
}

function New-LauncherCmd {
    param(
        [string]$InstallDir,
        [string]$RepoUrl,
        [string]$Branch
    )

    $launcherCmdPath = Join-Path $InstallDir "SanteA Launcher.cmd"
    $launcherPs1Path = Join-Path $InstallDir "SanteA-Launcher.ps1"

    $cmdContent = @"
@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$launcherPs1Path" -RepoUrl "$RepoUrl" -Branch "$Branch"
set EXITCODE=%ERRORLEVEL%
endlocal & exit /b %EXITCODE%
"@

    Set-Content -Path $launcherCmdPath -Value $cmdContent -Encoding ASCII
}

Ensure-Git
New-LauncherCmd -InstallDir $InstallDir -RepoUrl $RepoUrl -Branch $Branch

$launcherPs1 = Join-Path $InstallDir "SanteA-Launcher.ps1"
Write-Host "Pre-synchronisation du projet..."
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcherPs1 -RepoUrl $RepoUrl -Branch $Branch -SyncOnly | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Echec de la pre-synchronisation du projet."
}

Write-Host "Installation SanteA terminee."
