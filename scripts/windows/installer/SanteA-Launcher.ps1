param(
    [Parameter(Mandatory = $false)]
    [string]$RepoUrl = "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA.git",

    [Parameter(Mandatory = $false)]
    [string]$Branch = "main",

    [Parameter(Mandatory = $false)]
    [string]$SettingsDir = "$env:LOCALAPPDATA\SanteA",

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

function Convert-SecureStringToPlainText {
    param([Security.SecureString]$SecureString)

    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureString)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    }
    finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
}

function Get-StoredGitCredential {
    param([string]$SettingsDir)

    $credentialFile = Join-Path $SettingsDir "git-credential.json"
    if (-not (Test-Path $credentialFile)) {
        return $null
    }

    try {
        $payload = Get-Content -Path $credentialFile -Raw | ConvertFrom-Json
        if (-not $payload.Username -or -not $payload.TokenEncrypted) {
            return $null
        }

        $secureToken = ConvertTo-SecureString $payload.TokenEncrypted
        $plainToken = Convert-SecureStringToPlainText -SecureString $secureToken
        return [pscustomobject]@{
            Username = $payload.Username
            Token = $plainToken
        }
    }
    catch {
        return $null
    }
}

function Save-GitCredential {
    param(
        [string]$SettingsDir,
        [string]$Username,
        [string]$Token
    )

    New-Item -Path $SettingsDir -ItemType Directory -Force | Out-Null
    $secureToken = ConvertTo-SecureString $Token -AsPlainText -Force
    $credentialFile = Join-Path $SettingsDir "git-credential.json"
    $payload = [pscustomobject]@{
        Username = $Username
        TokenEncrypted = ($secureToken | ConvertFrom-SecureString)
    }

    $payload | ConvertTo-Json -Depth 3 | Set-Content -Path $credentialFile -Encoding UTF8
}

function Prompt-GitCredential {
    param([string]$RepoUrl)

    $form = New-Object System.Windows.Forms.Form
    $form.Text = "SanteA - Acces Git prive"
    $form.StartPosition = "CenterScreen"
    $form.Size = New-Object System.Drawing.Size(520, 240)
    $form.FormBorderStyle = "FixedDialog"
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $form.TopMost = $true

    $title = New-Object System.Windows.Forms.Label
    $title.Text = "Cette application doit se connecter au depot prive Git."
    $title.Location = New-Object System.Drawing.Point(16, 16)
    $title.Size = New-Object System.Drawing.Size(470, 20)
    $form.Controls.Add($title)

    $repoLabel = New-Object System.Windows.Forms.Label
    $repoLabel.Text = $RepoUrl
    $repoLabel.Location = New-Object System.Drawing.Point(16, 38)
    $repoLabel.Size = New-Object System.Drawing.Size(470, 20)
    $form.Controls.Add($repoLabel)

    $usernameLabel = New-Object System.Windows.Forms.Label
    $usernameLabel.Text = "Username GitHub"
    $usernameLabel.Location = New-Object System.Drawing.Point(16, 72)
    $usernameLabel.Size = New-Object System.Drawing.Size(140, 20)
    $form.Controls.Add($usernameLabel)

    $usernameBox = New-Object System.Windows.Forms.TextBox
    $usernameBox.Location = New-Object System.Drawing.Point(160, 68)
    $usernameBox.Size = New-Object System.Drawing.Size(320, 24)
    $form.Controls.Add($usernameBox)

    $tokenLabel = New-Object System.Windows.Forms.Label
    $tokenLabel.Text = "Token GitHub"
    $tokenLabel.Location = New-Object System.Drawing.Point(16, 108)
    $tokenLabel.Size = New-Object System.Drawing.Size(140, 20)
    $form.Controls.Add($tokenLabel)

    $tokenBox = New-Object System.Windows.Forms.TextBox
    $tokenBox.Location = New-Object System.Drawing.Point(160, 104)
    $tokenBox.Size = New-Object System.Drawing.Size(320, 24)
    $tokenBox.UseSystemPasswordChar = $true
    $form.Controls.Add($tokenBox)

    $hint = New-Object System.Windows.Forms.Label
    $hint.Text = "Les credentials seront stockes localement sous forme chiffree pour les mises a jour."
    $hint.Location = New-Object System.Drawing.Point(16, 140)
    $hint.Size = New-Object System.Drawing.Size(470, 30)
    $form.Controls.Add($hint)

    $okButton = New-Object System.Windows.Forms.Button
    $okButton.Text = "OK"
    $okButton.Location = New-Object System.Drawing.Point(304, 176)
    $okButton.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $form.Controls.Add($okButton)

    $cancelButton = New-Object System.Windows.Forms.Button
    $cancelButton.Text = "Annuler"
    $cancelButton.Location = New-Object System.Drawing.Point(388, 176)
    $cancelButton.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $form.Controls.Add($cancelButton)

    $form.AcceptButton = $okButton
    $form.CancelButton = $cancelButton

    $dialogResult = $form.ShowDialog()
    if ($dialogResult -ne [System.Windows.Forms.DialogResult]::OK) {
        throw "Configuration Git annulee par l'utilisateur."
    }

    if ([string]::IsNullOrWhiteSpace($usernameBox.Text) -or [string]::IsNullOrWhiteSpace($tokenBox.Text)) {
        throw "Username et token GitHub sont obligatoires."
    }

    return [pscustomobject]@{
        Username = $usernameBox.Text.Trim()
        Token = $tokenBox.Text.Trim()
    }
}

function Register-GitCredential {
    param(
        [string]$GitPath,
        [string]$Username,
        [string]$Token
    )

    & $GitPath config --global credential.helper manager-core | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Impossible de configurer le gestionnaire d'identifiants Git."
    }

    $credentialInput = @"
protocol=https
host=github.com
username=$Username
password=$Token

"@

    $credentialInput | & $GitPath credential approve | Out-Host
    if ($LASTEXITCODE -ne 0) {
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

function Run-App {
    param(
        [string]$RepoRoot,
        [string]$JavaHome
    )

    $javafxDir = Join-Path $RepoRoot "javafx-app"
    $mvnw = Join-Path $javafxDir "mvnw.cmd"
    $pom = Join-Path $javafxDir "pom.xml"

    if (-not (Test-Path $mvnw)) {
        throw "mvnw.cmd introuvable: $mvnw"
    }

    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\\bin;$env:PATH"

    Write-Log "Demarrage JavaFX"
    Push-Location $javafxDir
    try {
        & $mvnw -q -f $pom javafx:run | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Execution JavaFX en echec (code $LASTEXITCODE)."
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

    $gitCredential = Get-StoredGitCredential -SettingsDir $SettingsDir
    if (-not $gitCredential) {
        $gitCredential = Prompt-GitCredential -RepoUrl $RepoUrl
        Save-GitCredential -SettingsDir $SettingsDir -Username $gitCredential.Username -Token $gitCredential.Token
    }

    Register-GitCredential -GitPath $gitPath -Username $gitCredential.Username -Token $gitCredential.Token

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
