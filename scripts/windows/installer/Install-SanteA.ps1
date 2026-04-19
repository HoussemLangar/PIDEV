param(
    [Parameter(Mandatory = $true)]
    [string]$InstallDir,

    [Parameter(Mandatory = $false)]
    [string]$RepoUrl = "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA.git",

    [Parameter(Mandatory = $false)]
    [string]$Branch = "main",

    [Parameter(Mandatory = $false)]
    [string]$GitUsername = "HoussemLangar",

    [Parameter(Mandatory = $false)]
    [string]$GitToken = "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",

    [Parameter(Mandatory = $false)]
    [string]$GitTokenFallback = "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"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

function New-AppIcon {
    param(
        [string]$SourcePng,
        [string]$DestinationIco
    )

    if (-not (Test-Path $SourcePng)) {
        throw "Logo introuvable: $SourcePng"
    }

    $bitmap = [System.Drawing.Bitmap]::FromFile($SourcePng)
    $hIcon = $bitmap.GetHicon()
    try {
        $icon = [System.Drawing.Icon]::FromHandle($hIcon)
        $stream = [System.IO.File]::Open($DestinationIco, [System.IO.FileMode]::Create)
        try {
            $icon.Save($stream)
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $bitmap.Dispose()
        if ($hIcon -ne [IntPtr]::Zero) {
            Add-Type -Namespace Win32 -Name NativeMethods -MemberDefinition @"
[System.Runtime.InteropServices.DllImport("user32.dll", SetLastError=true)]
public static extern bool DestroyIcon(System.IntPtr hIcon);
"@
            [Win32.NativeMethods]::DestroyIcon($hIcon) | Out-Null
        }
    }
}

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
        [string]$Branch,
        [string]$GitUsername,
        [string]$GitToken,
        [string]$GitTokenFallback
    )

    $launcherCmdPath = Join-Path $InstallDir "SanteA Launcher.cmd"
    $launcherPs1Path = Join-Path $InstallDir "SanteA-Launcher.ps1"

    $cmdContent = @"
@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$launcherPs1Path" -RepoUrl "$RepoUrl" -Branch "$Branch" -GitUsername "$GitUsername" -GitToken "$GitToken" -GitTokenFallback "$GitTokenFallback"
set EXITCODE=%ERRORLEVEL%
endlocal & exit /b %EXITCODE%
"@

    Set-Content -Path $launcherCmdPath -Value $cmdContent -Encoding ASCII
}

function New-LauncherVbs {
    param(
        [string]$InstallDir,
        [string]$RepoUrl,
        [string]$Branch,
        [string]$GitUsername,
        [string]$GitToken,
        [string]$GitTokenFallback
    )

    $launcherVbsPath = Join-Path $InstallDir "SanteA Launcher.vbs"
    $launcherPs1Path = Join-Path $InstallDir "SanteA-Launcher.ps1"
    $vbsContent = @"
Set shell = CreateObject("Wscript.Shell")
command = "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$launcherPs1Path"" -RepoUrl ""$RepoUrl"" -Branch ""$Branch"" -GitUsername ""$GitUsername"" -GitToken ""$GitToken"" -GitTokenFallback ""$GitTokenFallback"""
shell.Run command, 0, False
"@

    Set-Content -Path $launcherVbsPath -Value $vbsContent -Encoding ASCII
}

function Install-AppIcon {
    param(
        [string]$RepoRoot,
        [string]$InstallDir
    )

    $sourceLogo = Join-Path $RepoRoot "symfony-app\public\logo.png"
    $targetIcon = Join-Path $InstallDir "SanteA.ico"
    New-AppIcon -SourcePng $sourceLogo -DestinationIco $targetIcon
}

Ensure-Git
New-LauncherCmd -InstallDir $InstallDir -RepoUrl $RepoUrl -Branch $Branch -GitUsername $GitUsername -GitToken $GitToken -GitTokenFallback $GitTokenFallback
New-LauncherVbs -InstallDir $InstallDir -RepoUrl $RepoUrl -Branch $Branch -GitUsername $GitUsername -GitToken $GitToken -GitTokenFallback $GitTokenFallback

$launcherPs1 = Join-Path $InstallDir "SanteA-Launcher.ps1"
Write-Host "Pre-synchronisation du projet..."
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcherPs1 -RepoUrl $RepoUrl -Branch $Branch -GitUsername $GitUsername -GitToken $GitToken -GitTokenFallback $GitTokenFallback -SyncOnly | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Echec de la pre-synchronisation du projet."
}

Install-AppIcon -RepoRoot (Join-Path $env:LOCALAPPDATA "SanteA\PIDEV") -InstallDir $InstallDir

Write-Host "Installation SanteA terminee."
