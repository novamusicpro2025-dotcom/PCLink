@echo off
chcp 65001 >nul
title "PC Master - Clean & Build Android APK"
echo ========================================================
echo   ?? Building PC Master Android Debug APK...
echo ========================================================
cd /d "D:\pcmaster\pc-client_rebuild_20260506141355\android-app"

set "JAVA_HOME=C:\Users\tsrih\.jdks\jbr-17.0.14"
if not exist "%JAVA_HOME%" set "JAVA_HOME=C:\Users\tsrih\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
set "PATH=%JAVA_HOME%\bin;D:\platform-tools;%PATH%"

call gradlew.bat assembleDebug
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ? APK BUILT SUCCESSFULLY!
    echo ?? Location: D:\pcmaster\pc-client_rebuild_20260506141355\android-app\app\build\outputs\apk\debug\app-debug.apk
) else (
    echo.
    echo ? BUILD FAILED! Error code: %ERRORLEVEL%
)
echo.
pause
