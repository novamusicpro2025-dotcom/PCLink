@echo off
chcp 65001 >nul
:menu
cls
echo ================================================================
echo    ????? PC MASTER SUITE - DEDICATED DEVELOPER CONTROL PANEL
echo           D:\pcmaster\pc-client_rebuild_20260506141355
echo ================================================================
echo.
echo   [1] ?? Build Android Debug APK (gradlew assembleDebug)
echo   [2] ?? Install APK to Phone & Launch (ADB)
echo   [3] ? 1-Click Clean Build + Install + Launch
echo   [4] ?? Launch PC Client (Windows WPF Admin Hub)
echo   [5] ?? Check ADB Phone Connection & Status
echo   [6] ??? Health Check & Diagnostics (Ports, Files, Logs)
echo   [7] ?? Ask Nemotron AI (Free Unlimited Code Review/Fix)
echo   [8] ?? Open Project in File Explorer
echo   [0] ?? Exit
echo.
echo ================================================================
set /p choice="?? Select an option (0-8): "

if "%choice%"=="1" (
    call "D:\pcmaster\pc-client_rebuild_20260506141355\1_BUILD_ANDROID_APK.bat"
    goto menu
)
if "%choice%"=="2" (
    call "D:\pcmaster\pc-client_rebuild_20260506141355\2_INSTALL_TO_PHONE.bat"
    goto menu
)
if "%choice%"=="3" (
    call "D:\pcmaster\pc-client_rebuild_20260506141355\3_CLEAN_BUILD_AND_INSTALL.bat"
    goto menu
)
if "%choice%"=="4" (
    call "D:\pcmaster\pc-client_rebuild_20260506141355\4_RUN_PC_CLIENT.bat"
    goto menu
)
if "%choice%"=="5" (
    echo.
    echo ?? Checking ADB devices...
    D:\platform-tools\adb.exe devices -l
    echo.
    echo ?? Checking PC Hub port 8099...
    netstat -ano | findstr :8099
    echo.
    pause
    goto menu
)
if "%choice%"=="6" (
    D:\OxAlphaAI\python\python.exe -c "import os; print('MainWindow.xaml.cs:', os.path.exists(r'D:\pcmaster\pc-client_rebuild_20260506141355\MainWindow.xaml.cs')); print('MainActivity.kt:', os.path.exists(r'D:\pcmaster\pc-client_rebuild_20260506141355\android-app\app\src\main\java\com\pcmaster\mobile\MainActivity.kt')); print('APK:', os.path.exists(r'D:\pcmaster\pc-client_rebuild_20260506141355\android-app\app\build\outputs\apk\debug\app-debug.apk')); print('PcClient.exe:', any(os.path.exists(rf'D:\pcmaster\pc-client_rebuild_20260506141355\{v}\PcClient.exe') for v in ['publish_output_v6', 'publish_output_v5', 'publish_output_v4', 'publish_output']))"
    pause
    goto menu
)
if "%choice%"=="7" (
    D:\OxAlphaAI\python\python.exe D:\OxAlphaAI\interactive_nemotron.py
    goto menu
)
if "%choice%"=="8" (
    explorer "D:\pcmaster\pc-client_rebuild_20260506141355"
    goto menu
)
if "%choice%"=="0" exit /b
goto menu
