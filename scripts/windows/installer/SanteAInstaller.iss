#define MyAppName "SanteA Desktop"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "SanteA"
#define MyAppURL "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA"

#ifndef GitRepoUrl
  #define GitRepoUrl "https://github.com/HoussemLangar/Esprit-PIDEV-3A41-2026-SANTEA.git"
#endif

#ifndef GitBranch
  #define GitBranch "main"
#endif

[Setup]
AppId={{2B23385E-7FBF-4957-BA90-CF43F95F74B8}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\SanteA Desktop
DisableProgramGroupPage=yes
OutputDir=.
OutputBaseFilename=SanteA-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
UninstallDisplayIcon={app}\SanteA.ico

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"

[Files]
Source: "SanteA-Launcher.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "Install-SanteA.ps1"; DestDir: "{app}"; Flags: ignoreversion

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\Install-SanteA.ps1"" -InstallDir ""{app}"" -RepoUrl ""{#GitRepoUrl}"" -Branch ""{#GitBranch}"""; Flags: waituntilterminated runhidden; StatusMsg: "Installation des prerequis et synchronisation du projet..."

[Icons]
Name: "{autodesktop}\SanteA Desktop"; Filename: "{app}\SanteA Launcher.vbs"; WorkingDir: "{app}"; IconFilename: "{app}\SanteA.ico"
Name: "{autoprograms}\SanteA Desktop"; Filename: "{app}\SanteA Launcher.vbs"; WorkingDir: "{app}"; IconFilename: "{app}\SanteA.ico"
