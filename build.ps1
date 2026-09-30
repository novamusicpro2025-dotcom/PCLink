$vswhere = "C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"
if (Test-Path $vswhere) {
    $msbuild = & $vswhere -latest -requires Microsoft.Component.MSBuild -find MSBuild\**\Bin\MSBuild.exe
    if ($msbuild) {
        & $msbuild "D:\pcmaster\pc-client_rebuild_20260506141355\PcClient.csproj"
    } else { Write-Host "MSBuild not found" }
} else { Write-Host "vswhere not found" }
