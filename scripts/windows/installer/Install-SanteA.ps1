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

function New-LauncherCmd {
    param(
        [string]$InstallDir,
        [string]$AppDir,
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
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$launcherPs1Path" -AppDir "$AppDir" -RepoUrl "$RepoUrl" -Branch "$Branch" -GitUsername "$GitUsername" -GitToken "$GitToken" -GitTokenFallback "$GitTokenFallback"
set EXITCODE=%ERRORLEVEL%
endlocal & exit /b %EXITCODE%
"@

    Set-Content -Path $launcherCmdPath -Value $cmdContent -Encoding ASCII
}

function New-LauncherVbs {
    param(
        [string]$InstallDir,
        [string]$AppDir,
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
command = "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$launcherPs1Path"" -AppDir ""$AppDir"" -RepoUrl ""$RepoUrl"" -Branch ""$Branch"" -GitUsername ""$GitUsername"" -GitToken ""$GitToken"" -GitTokenFallback ""$GitTokenFallback"""
shell.Run command, 0, False
"@

    Set-Content -Path $launcherVbsPath -Value $vbsContent -Encoding ASCII
}

function Install-AppIcon {
    param(
        [string]$AppDir,
        [string]$InstallDir
    )

    $sourceLogo = Join-Path $AppDir "javafx-app\src\main\resources\com\santea\images\heart.png"
    $targetIcon = Join-Path $InstallDir "SanteA.ico"
    New-AppIcon -SourcePng $sourceLogo -DestinationIco $targetIcon
}

$repoRoot = Join-Path $InstallDir "app-repo"

New-LauncherCmd -InstallDir $InstallDir -AppDir $repoRoot -RepoUrl $RepoUrl -Branch $Branch -GitUsername $GitUsername -GitToken $GitToken -GitTokenFallback $GitTokenFallback
New-LauncherVbs -InstallDir $InstallDir -AppDir $repoRoot -RepoUrl $RepoUrl -Branch $Branch -GitUsername $GitUsername -GitToken $GitToken -GitTokenFallback $GitTokenFallback

# Build initial local image so the application starts immediately after installation.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $InstallDir "SanteA-Launcher.ps1") -AppDir $repoRoot -RepoUrl $RepoUrl -Branch $Branch -GitUsername $GitUsername -GitToken $GitToken -GitTokenFallback $GitTokenFallback -BuildOnly
if ($LASTEXITCODE -ne 0) {
    throw "Echec de la construction initiale de l'application."
}

Install-AppIcon -AppDir $repoRoot -InstallDir $InstallDir

Write-Host "Installation SanteA terminee."
