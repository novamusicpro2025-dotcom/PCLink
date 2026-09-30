[Setup]
AppName=PC Master Control Center
AppVersion=2.5.0 Pro
AppPublisher=Beyond Edge & PC Master Team
DefaultDirName={autopf}\PcMasterControl
DefaultGroupName=PC Master Control Center
OutputDir=.
OutputBaseFilename=PcMasterControlSetup
Compression=lzma2/ultra64
SolidCompression=yes
PrivilegesRequired=admin
SetupIconFile=pcmaster.ico
UninstallDisplayIcon={app}\pcmaster.ico

[Files]
Source: "publish_output_v8\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\PC Master Control Center"; Filename: "{app}\PcClient.exe"; IconFilename: "{app}\pcmaster.ico"
Name: "{group}\Uninstall PC Master"; Filename: "{uninstallexe}"
Name: "{autodesktop}\PC Master Control Center"; Filename: "{app}\PcClient.exe"; IconFilename: "{app}\pcmaster.ico"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"; Flags: checkablealone
Name: "startup"; Description: "Run at Windows startup (recommended for seamless connection)"; GroupDescription: "Startup options:"; Flags: checkablealone

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "PC Master Control Center"; ValueData: """{app}\PcClient.exe"""; Tasks: startup

[Run]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""PC Master Hub TCP (8099)"" dir=in action=allow protocol=TCP localport=8099"; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""PC Master Hub UDP (8091)"" dir=in action=allow protocol=UDP localport=8091"; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "http add urlacl url=http://*:8099/ sddl=""D:(A;;GX;;;WD)"""; Flags: runhidden
Filename: "{app}\PcClient.exe"; Description: "Launch PC Master Control Center Now"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""PC Master Hub TCP (8099)"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""PC Master Hub UDP (8091)"""; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "http delete urlacl url=http://*:8099/"; Flags: runhidden
