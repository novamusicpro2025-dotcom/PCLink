@echo off
chcp 65001 >nul
title PC Master - Launch Windows PC Client (Admin Hub)
echo ========================================================
echo   ?? Launching PC Master Windows Client (Admin Mode)...
echo ========================================================
cd /d "D:\pcmaster\pc-client_rebuild_20260506141355"

set "EXE=publish_output_v8\PcClient.exe"
if not exist "%EXE%" set "EXE=publish_output_v7\PcClient.exe"
if not exist "%EXE%" set "EXE=publish_output_v6\PcClient.exe"
if not exist "%EXE%" set "EXE=publish_output_v5\PcClient.exe"
if not exist "%EXE%" set "EXE=publish_output_v4\PcClient.exe"
if not exist "%EXE%" set "EXE=publish_output_v3\PcClient.exe"
if not exist "%EXE%" set "EXE=publish_output\PcClient.exe"
if not exist "%EXE%" set "EXE=publish2\PcClient.exe"
if not exist "%EXE%" set "EXE=PcClientSetup.exe"

if exist "%EXE%" (
    echo Starting %EXE%...
    start "" "%EXE%"
    echo ? PC Client launched!
) else (
    echo ? PC Client executable not found in publish_output!
)
timeout /t 3 >nul
