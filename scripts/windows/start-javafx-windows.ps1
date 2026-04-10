param(
    [Parameter(Mandatory = $false)]
    [string]$VmName = "PiDev",

    [Parameter(Mandatory = $false)]
    [string]$VmConfigPath = "C:\Users\hazem\Documents\Virtuel Machines\PiDev\PiDev.vmx"
)

$ErrorActionPreference = "Stop"

function Find-Vmrun {
    $candidate = Get-Command vmrun -ErrorAction SilentlyContinue
    if ($candidate) { return $candidate.Source }

    $paths = @(
        "C:\Program Files (x86)\VMware\VMware Workstation\vmrun.exe",
        "C:\Program Files\VMware\VMware Workstation\vmrun.exe"
    )
    foreach ($p in $paths) {
        if (Test-Path $p) { return $p }
    }

    return $null
}

try {
    $vmrun = Find-Vmrun
    if (-not $vmrun) {
        throw "vmrun introuvable. Installez VMware Workstation."
    }

    if (-not (Test-Path $VmConfigPath)) {
        throw "Fichier VMX introuvable: $VmConfigPath"
    }

    $resolvedVmx = (Resolve-Path $VmConfigPath).Path
    $running = & $vmrun list
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de lire les VMs en cours via vmrun."
    }

    if ($running -contains $resolvedVmx) {
        Write-Host "VM deja demarree: $VmName"
    } else {
        Write-Host "Demarrage VM: $VmName"
        & $vmrun start $resolvedVmx gui | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Echec du demarrage de la VM $VmName"
        }
    }

    Write-Host "JavaFX est lancee depuis la VM via l'autostart Linux."
}
catch {
    Write-Host "Erreur lancement JavaFX: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host "Appuyez sur Entree pour fermer"
    exit 1
}
