@echo off
chcp 65001 >nul
title PC Master - Install & Launch APK via ADB
echo ========================================================
echo   ?? Installing PC Master APK to Connected Device...
echo ========================================================
set "ADB=D:\platform-tools\adb.exe"
set "APK=D:\pcmaster\pc-client_rebuild_20260506141355\android-app\app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE=com.pcmaster.mobile"
set "MAIN_ACTIVITY=com.pcmaster.mobile/.MainActivity"

if not exist "%APK%" (
    echo ? Debug APK not found! Please run 1_BUILD_ANDROID_APK.bat first.
    pause
    exit /b 1
)

echo [1/3] Checking connected ADB devices...
"%ADB%" devices
echo.

echo [2/3] Installing APK onto phone (-r)...
"%ADB%" install -r "%APK%"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ?? Install failed. Attempting force uninstall and reinstall...
    "%ADB%" uninstall %PACKAGE%
    "%ADB%" install "%APK%"
)

echo.
echo [3/3] Launching App on phone...
"%ADB%" shell am start -n %MAIN_ACTIVITY%

echo.
echo ? COMPLETE! App is now running on your phone.
echo.
pause
