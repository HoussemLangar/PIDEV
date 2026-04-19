param(
    [Parameter(Mandatory = $false)]
    [string]$GitRepoUrl = "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA.git",

    [Parameter(Mandatory = $false)]
    [string]$GitBranch = "main",

    [Parameter(Mandatory = $false)]
    [string]$InnoSetupCompiler
)

$ErrorActionPreference = "Stop"

function Find-Iscc {
    param([string]$ExplicitPath)

    if ($ExplicitPath -and (Test-Path $ExplicitPath)) {
        return $ExplicitPath
    }

    $candidates = @(
        "C:\\Program Files (x86)\\Inno Setup 6\\ISCC.exe",
        "C:\\Program Files\\Inno Setup 6\\ISCC.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $cmd = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    return $null
}

$iscc = Find-Iscc -ExplicitPath $InnoSetupCompiler
if (-not $iscc) {
    throw "ISCC.exe introuvable. Installez Inno Setup 6 puis relancez ce script."
}

$installerDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$iss = Join-Path $installerDir "SanteAInstaller.iss"

if (-not (Test-Path $iss)) {
    throw "Fichier .iss introuvable: $iss"
}

Push-Location $installerDir
try {
    & $iscc "/DGitRepoUrl=$GitRepoUrl" "/DGitBranch=$GitBranch" $iss | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Compilation de l'installateur en echec."
    }

    $exe = Join-Path $installerDir "SanteA-Setup.exe"
    if (-not (Test-Path $exe)) {
        throw "Installateur genere introuvable: $exe"
    }

    Write-Host "Installateur genere: $exe"
}
finally {
    Pop-Location
}
