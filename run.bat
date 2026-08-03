@echo off
echo ===================================
echo 1. Building the App (Debug)
echo ===================================
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    exit /b %errorlevel%
)

echo.
echo ===================================
echo 2. Installing the App
echo ===================================
echo Installing/Updating the App...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %errorlevel% neq 0 (
    echo [ERROR] Installation failed! Make sure your device is connected.
    exit /b %errorlevel%
)

echo.
echo ===================================
echo 3. Restarting Accessibility Service
echo ===================================
adb shell settings put secure enabled_accessibility_services null
adb shell settings put secure enabled_accessibility_services com.nova.automate/com.nova.automate.service.NovaAccessibilityService
adb shell settings put secure accessibility_enabled 1

echo.
echo ===================================
echo 4. Clearing old logs
echo ===================================
adb logcat -c

echo.
echo ===================================
echo 5. Watching Logcat for "NovaAutomation"
echo (Press Ctrl+C to stop)
echo ===================================
adb logcat NovaAutomation:D AndroidRuntime:E *:S
