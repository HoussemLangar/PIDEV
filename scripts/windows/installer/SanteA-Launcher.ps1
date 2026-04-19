param(
    [Parameter(Mandatory = $false)]
    [string]$RepoUrl = "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA.git",

    [Parameter(Mandatory = $false)]
    [string]$Branch = "main",

    [Parameter(Mandatory = $false)]
    [string]$SettingsDir = "$env:LOCALAPPDATA\SanteA",

    [Parameter(Mandatory = $false)]
    [string]$GitUsername = "HoussemLangar",

    [Parameter(Mandatory = $false)]
    [string]$GitToken = "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",

    [Parameter(Mandatory = $false)]
    [string]$GitTokenFallback = "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",

    [Parameter(Mandatory = $false)]
    [switch]$SyncOnly
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] $Message"
}

function Get-GitPath {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    return $null
}

function Ensure-Git {
    $git = Get-GitPath
    if (-not $git) {
        throw "Git est introuvable. Relancez l'installation SanteA pour installer les dependances."
    }
    return $git
}

function Register-GitCredential {
    param(
        [string]$GitPath,
        [string]$Username,
        [string]$Token,
        [string]$FallbackToken
    )

    & $GitPath config --global credential.helper manager-core | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de configurer le gestionnaire d'identifiants Git."
    }

    if ([string]::IsNullOrWhiteSpace($Token)) {
        throw "Token GitHub manquant."
    }

    $credentialInput = @"
protocol=https
host=github.com
username=$Username
password=$Token

"@

    $credentialInput | & $GitPath credential approve | Out-Host
    if ($LASTEXITCODE -ne 0) {
        if (-not [string]::IsNullOrWhiteSpace($FallbackToken)) {
            $fallbackCredentialInput = @"
protocol=https
host=github.com
username=$Username
password=$FallbackToken

"@
            $fallbackCredentialInput | & $GitPath credential approve | Out-Host
            if ($LASTEXITCODE -eq 0) {
                return
            }
        }

        throw "Impossible d'enregistrer les identifiants Git."
    }
}

function Ensure-SafeDirectory {
    param(
        [string]$GitPath,
        [string]$RepoPath
    )

    $normalizedPath = ($RepoPath -replace '\\', '/')
    $existing = & $GitPath config --global --get-all safe.directory 2>$null
    if ($existing -notcontains $normalizedPath) {
        & $GitPath config --global --add safe.directory $normalizedPath | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Impossible de declarer le depot comme safe.directory: $normalizedPath"
        }
    }
}

function Invoke-Git {
    param(
        [string]$GitPath,
        [string]$RepoPath,
        [string[]]$Arguments
    )

    & $GitPath -C $RepoPath @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Commande git en echec: git -C $RepoPath $($Arguments -join ' ')"
    }
}

function Ensure-Repository {
    param(
        [string]$GitPath,
        [string]$RepoUrl,
        [string]$Branch,
        [string]$RepoRoot
    )

    $repoGitDir = Join-Path $RepoRoot ".git"
    Ensure-SafeDirectory -GitPath $GitPath -RepoPath $RepoRoot
    if (-not (Test-Path $repoGitDir)) {
        if (Test-Path $RepoRoot) {
            Remove-Item -Path $RepoRoot -Recurse -Force
        }
        New-Item -Path (Split-Path -Parent $RepoRoot) -ItemType Directory -Force | Out-Null
        Write-Log "Clonage du depot ($Branch)"
        & $GitPath clone --branch $Branch --single-branch $RepoUrl $RepoRoot | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Impossible de cloner le depot: $RepoUrl"
        }
        return
    }

    Write-Log "Mise a jour depuis la branche $Branch"
    Invoke-Git -GitPath $GitPath -RepoPath $RepoRoot -Arguments @("remote", "set-url", "origin", $RepoUrl)
    Invoke-Git -GitPath $GitPath -RepoPath $RepoRoot -Arguments @("fetch", "origin", $Branch)
    Invoke-Git -GitPath $GitPath -RepoPath $RepoRoot -Arguments @("checkout", $Branch)
    Invoke-Git -GitPath $GitPath -RepoPath $RepoRoot -Arguments @("reset", "--hard", "origin/$Branch")
    Invoke-Git -GitPath $GitPath -RepoPath $RepoRoot -Arguments @("clean", "-fd")
}

function Ensure-Jdk {
    param([string]$JavafxDir)

    $jdkRoot = Join-Path $JavafxDir ".jdks"
    $jdkCurrent = Join-Path $jdkRoot "jdk-21\\current"
    $javaExe = Join-Path $jdkCurrent "bin\\java.exe"
    if (Test-Path $javaExe) {
        return $jdkCurrent
    }

    Write-Log "Installation locale du JDK 21"
    New-Item -Path $jdkRoot -ItemType Directory -Force | Out-Null

    $tmpZip = Join-Path $env:TEMP "santea-temurin21.zip"
    $tmpExtract = Join-Path $env:TEMP "santea-temurin21"

    if (Test-Path $tmpZip) { Remove-Item $tmpZip -Force }
    if (Test-Path $tmpExtract) { Remove-Item $tmpExtract -Recurse -Force }

    $jdkUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    Invoke-WebRequest -Uri $jdkUrl -OutFile $tmpZip
    Expand-Archive -Path $tmpZip -DestinationPath $tmpExtract -Force

    $extractedDir = Get-ChildItem -Path $tmpExtract -Directory | Select-Object -First 1
    if (-not $extractedDir) {
        throw "Archive JDK invalide."
    }

    if (Test-Path $jdkCurrent) {
        Remove-Item -Path $jdkCurrent -Recurse -Force
    }

    New-Item -Path (Join-Path $jdkRoot "jdk-21") -ItemType Directory -Force | Out-Null
    Move-Item -Path $extractedDir.FullName -Destination $jdkCurrent

    if (-not (Test-Path $javaExe)) {
        throw "Installation JDK echouee. java.exe introuvable."
    }

    return $jdkCurrent
}

function Ensure-Maven {
    param([string]$RepoRoot)

    $javafxDir = Join-Path $RepoRoot "javafx-app"
    $mvnw = Join-Path $javafxDir "mvnw.cmd"
    $wrapperProps = Join-Path $javafxDir ".mvn\wrapper\maven-wrapper.properties"

    if ((Test-Path $mvnw) -and (Test-Path $wrapperProps)) {
        return @{ Command = $mvnw; IsWrapper = $true }
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        return @{ Command = $mvn.Source; IsWrapper = $false }
    }

    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        throw "Maven est introuvable et winget est indisponible pour l'installer."
    }

    Write-Log "Installation locale de Maven"
    & $winget.Source install --id Apache.Maven -e --source winget --silent --accept-package-agreements --accept-source-agreements | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Echec installation Maven via winget."
    }

    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvn) {
        throw "Maven reste introuvable apres installation."
    }

    return @{ Command = $mvn.Source; IsWrapper = $false }
}

function Run-App {
    param(
        [string]$RepoRoot,
        [string]$JavaHome
    )

    $javafxDir = Join-Path $RepoRoot "javafx-app"
    $pom = Join-Path $javafxDir "pom.xml"
    $buildTool = Ensure-Maven -RepoRoot $RepoRoot

    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\\bin;$env:PATH"
    $env:MAVEN_SKIP_RC = "1"

    Write-Log "Demarrage JavaFX"
    Push-Location $javafxDir
    try {
        $logFile = Join-Path $javafxDir "logs\\javafx-launch.log"
        New-Item -Path (Split-Path -Parent $logFile) -ItemType Directory -Force | Out-Null
        if ($buildTool.IsWrapper) {
            & $buildTool.Command -e -f $pom javafx:run *>&1 | Tee-Object -FilePath $logFile | Out-Host
        }
        else {
            & $buildTool.Command -e -f $pom javafx:run *>&1 | Tee-Object -FilePath $logFile | Out-Host
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Execution JavaFX en echec (code $LASTEXITCODE). Consultez $logFile"
        }
    }
    finally {
        Pop-Location
    }
}

$baseDir = Join-Path $env:LOCALAPPDATA "SanteA"
$repoRoot = Join-Path $baseDir "PIDEV"
$javafxDir = Join-Path $repoRoot "javafx-app"

New-Item -Path $baseDir -ItemType Directory -Force | Out-Null

try {
    Write-Log "Verification des prerequis"
    $gitPath = Ensure-Git

    Register-GitCredential -GitPath $gitPath -Username $GitUsername -Token $GitToken -FallbackToken $GitTokenFallback

    Ensure-Repository -GitPath $gitPath -RepoUrl $RepoUrl -Branch $Branch -RepoRoot $repoRoot
    $javaHome = Ensure-Jdk -JavafxDir $javafxDir

    if ($SyncOnly) {
        Write-Log "Synchronisation terminee"
        exit 0
    }

    Run-App -RepoRoot $repoRoot -JavaHome $javaHome
}
catch {
    $message = "Erreur: $($_.Exception.Message)"
    [System.Windows.Forms.MessageBox]::Show($message, "SanteA", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    exit 1
}
