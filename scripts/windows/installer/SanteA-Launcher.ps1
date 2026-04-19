param(
    [Parameter(Mandatory = $false)]
    [string]$AppDir = (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "javafx-app"),

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

    $mavenHome = Join-Path $env:LOCALAPPDATA "SanteA\tools\apache-maven-3.9.9"
    $mvnCmd = Join-Path $mavenHome "bin\mvn.cmd"
    if (Test-Path $mvnCmd) {
        return @{ Command = $mvnCmd; IsWrapper = $false }
    }

    Write-Log "Telechargement local de Maven 3.9.9"
    New-Item -Path $mavenHome -ItemType Directory -Force | Out-Null

    $tmpZip = Join-Path $env:TEMP "apache-maven-3.9.9-bin.zip"
    $tmpExtract = Join-Path $env:TEMP "apache-maven-3.9.9"

    if (Test-Path $tmpZip) { Remove-Item $tmpZip -Force }
    if (Test-Path $tmpExtract) { Remove-Item $tmpExtract -Recurse -Force }

    $mavenUrl = "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
    Invoke-WebRequest -Uri $mavenUrl -OutFile $tmpZip
    Expand-Archive -Path $tmpZip -DestinationPath $tmpExtract -Force

    $extractedDir = Get-ChildItem -Path $tmpExtract -Directory | Select-Object -First 1
    if (-not $extractedDir) {
        throw "Archive Maven invalide."
    }

    if (Test-Path $mavenHome) {
        Remove-Item -Path $mavenHome -Recurse -Force
    }

    Move-Item -Path $extractedDir.FullName -Destination $mavenHome

    if (-not (Test-Path $mvnCmd)) {
        throw "Maven local introuvable apres extraction."
    }

    return @{ Command = $mvnCmd; IsWrapper = $false }
}

function Run-App {
    param(
        [string]$AppDir,
        [string]$JavaHome
    )

    $javafxDir = $AppDir
    $pom = Join-Path $javafxDir "pom.xml"
    $buildTool = Ensure-Maven -RepoRoot $AppDir

    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\\bin;$env:PATH"
    $env:MAVEN_SKIP_RC = "1"

    Write-Log "Demarrage JavaFX"
    Push-Location $javafxDir
    try {
        $logFile = Join-Path $javafxDir "logs\\javafx-launch.log"
        New-Item -Path (Split-Path -Parent $logFile) -ItemType Directory -Force | Out-Null
        & $buildTool.Command -e -f $pom javafx:run *>&1 | Tee-Object -FilePath $logFile | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Execution JavaFX en echec (code $LASTEXITCODE). Consultez $logFile"
        }
    }
    finally {
        Pop-Location
    }
}

try {
    Write-Log "Verification des prerequis"
    $gitPath = Ensure-Git

    $javaHome = Ensure-Jdk -JavafxDir $AppDir

    if ($SyncOnly) {
        Write-Log "Synchronisation terminee"
        exit 0
    }

    Run-App -AppDir $AppDir -JavaHome $javaHome
}
catch {
    $message = "Erreur: $($_.Exception.Message)"
    [System.Windows.Forms.MessageBox]::Show($message, "SanteA", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
    exit 1
}
