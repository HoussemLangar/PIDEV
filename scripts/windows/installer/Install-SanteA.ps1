param(
    [Parameter(Mandatory = $true)]
    [string]$InstallDir,
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
        [string]$AppDir
    )

    $launcherCmdPath = Join-Path $InstallDir "SanteA Launcher.cmd"
    $launcherPs1Path = Join-Path $InstallDir "SanteA-Launcher.ps1"

    $cmdContent = @"
@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$launcherPs1Path" -AppDir "$AppDir"
set EXITCODE=%ERRORLEVEL%
endlocal & exit /b %EXITCODE%
"@

    Set-Content -Path $launcherCmdPath -Value $cmdContent -Encoding ASCII
}

function New-LauncherVbs {
    param(
        [string]$InstallDir,
        [string]$AppDir
    )

    $launcherVbsPath = Join-Path $InstallDir "SanteA Launcher.vbs"
    $launcherPs1Path = Join-Path $InstallDir "SanteA-Launcher.ps1"
    $vbsContent = @"
Set shell = CreateObject("Wscript.Shell")
command = "powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$launcherPs1Path"" -AppDir ""$AppDir"""
shell.Run command, 0, False
"@

    Set-Content -Path $launcherVbsPath -Value $vbsContent -Encoding ASCII
}

function Install-AppIcon {
    param(
        [string]$AppDir,
        [string]$InstallDir
    )

    $sourceLogo = Join-Path $AppDir "src\main\resources\com\santea\images\heart.png"
    $targetIcon = Join-Path $InstallDir "SanteA.ico"
    New-AppIcon -SourcePng $sourceLogo -DestinationIco $targetIcon
}

New-LauncherCmd -InstallDir $InstallDir -AppDir $InstallDir
New-LauncherVbs -InstallDir $InstallDir -AppDir $InstallDir

# Build initial local image so the application starts immediately after installation.
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $InstallDir "SanteA-Launcher.ps1") -AppDir $InstallDir -BuildOnly
if ($LASTEXITCODE -ne 0) {
    throw "Echec de la construction initiale de l'application."
}

Install-AppIcon -AppDir $InstallDir -InstallDir $InstallDir

Write-Host "Installation SanteA terminee."
