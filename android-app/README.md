# PC Master - Android App

A minimalistic Android app (WebView wrapper) for the PC Master Control Center.

## Features
- Connects to PC Master server via WebView
- Auto-saves server URL and auth token
- Full mobile web UI (dashboard, remote control, touchpad, file sharing, clipboard sync, screenshot, process manager)
- Dark theme matching the PC client
- Status indicator (connected/disconnected)

## Build

### Option 1: Android Studio
1. Open this folder in Android Studio
2. Build → Build Bundle(s) / APK(s) → Build APK(s)
3. Install the APK on your device

### Option 2: Command Line
```bash
cd android-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Setup
1. Install the APK on your Android device
2. Open the app
3. Tap the ⚙ settings button
4. Enter your PC's IP address and port (e.g. `192.168.1.100:8099`)
5. (Optional) Enter the auth token shown in the PC client's log
6. Tap Connect

## Requirements
- Android 7.0+ (API 24)
- Same WiFi network as the PC running PC Master
