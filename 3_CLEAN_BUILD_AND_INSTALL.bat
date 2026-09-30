@echo off
chcp 65001 >nul
title PC Master - 1-Click Clean Build & Install
echo ========================================================
echo   ? 1-Click Pipeline: Clean - Build - Install - Launch
echo ========================================================
cd /d "D:\pcmaster\pc-client_rebuild_20260506141355\android-app"

set "JAVA_HOME=C:\Users\tsrih\.jdks\jbr-17.0.14"
if not exist "%JAVA_HOME%" set "JAVA_HOME=C:\Users\tsrih\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
set "PATH=%JAVA_HOME%\bin;D:\platform-tools;%PATH%"
set "ADB=D:\platform-tools\adb.exe"
set "APK=D:\pcmaster\pc-client_rebuild_20260506141355\android-app\app\build\outputs\apk\debug\app-debug.apk"
set "MAIN_ACTIVITY=com.pcmaster.mobile/.MainActivity"

echo [1/3] Cleaning & Building APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ? Build failed!
    pause
    exit /b 1
)

echo.
echo [2/3] Installing to connected device...
"%ADB%" install -r "%APK%"

echo.
echo [3/3] Launching App on device...
"%ADB%" shell am start -n %MAIN_ACTIVITY%

echo.
echo ? SUCCESS! App built and launched on device!
echo.
pause
