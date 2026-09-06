@echo off
setlocal

REM Work from the project root no matter where this was invoked from.
REM setlocal restores the caller's directory on exit.
cd /d "%~dp0"

if /i "%~1"=="release" goto :release

echo ===================================
echo 1. Building the App (Debug)
echo ===================================
call "%~dp0gradlew.bat" assembleDebug
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
goto :eof


REM ============================================================
REM  run.bat release X.Y.Z
REM  Builds a signed release APK into dist\ for upload to a
REM  GitHub Release. No device or ADB needed.
REM ============================================================
:release

set "VERSION=%~2"
if "%VERSION%"=="" (
    echo [ERROR] Usage: run.bat release ^<version^>
    echo         e.g.  run.bat release 1.0.0
    exit /b 1
)

echo %VERSION%| findstr /r /c:"^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo [ERROR] Version must be MAJOR.MINOR.PATCH, e.g. 1.0.0  ^(got "%VERSION%"^)
    exit /b 1
)

if not exist "keystore.properties" (
    echo [ERROR] keystore.properties not found.
    echo         Without it the release build silently falls back to the debug key,
    echo         and a debug-signed APK can never update a properly signed install.
    echo         See RELEASING.md.
    exit /b 1
)

for /f "tokens=1,2,3 delims=." %%a in ("%VERSION%") do (
    set "MAJOR=%%a"
    set "MINOR=%%b"
    set "PATCH=%%c"
)

REM Leading zeros would be read as octal by set /a, giving a wrong versionCode.
for %%p in ("%MAJOR%" "%MINOR%" "%PATCH%") do (
    echo %%~p| findstr /r /c:"^0[0-9]" >nul
    if not errorlevel 1 (
        echo [ERROR] Version parts must not have leading zeros ^(use 1.0.8, not 1.0.08^)
        exit /b 1
    )
)

if %MINOR% gtr 99 (
    echo [ERROR] MINOR must be ^<= 99 to keep versionCode monotonic
    exit /b 1
)
if %PATCH% gtr 99 (
    echo [ERROR] PATCH must be ^<= 99 to keep versionCode monotonic
    exit /b 1
)

REM Same formula the release workflow uses: 1.2.3 -> 10203
set /a VCODE=%MAJOR% * 10000 + %MINOR% * 100 + %PATCH%

echo.
echo ===================================
echo 1. Building signed release APK
echo    version %VERSION%  (versionCode %VCODE%)
echo ===================================
if exist "app\build\outputs\apk\release" rd /s /q "app\build\outputs\apk\release"
call "%~dp0gradlew.bat" assembleRelease -PversionName=%VERSION% -PversionCode=%VCODE%
if %errorlevel% neq 0 (
    echo [ERROR] Build failed!
    exit /b %errorlevel%
)

set "APK="
for /f "delims=" %%f in ('dir /b "app\build\outputs\apk\release\*.apk" 2^>nul') do set "APK=app\build\outputs\apk\release\%%f"
if not defined APK (
    echo [ERROR] No APK was produced.
    exit /b 1
)

echo.
echo ===================================
echo 2. Verifying the signature
echo ===================================
set "APKSIGNER="
if defined ANDROID_HOME for /f "delims=" %%f in ('dir /b /s "%ANDROID_HOME%\build-tools\apksigner.bat" 2^>nul') do set "APKSIGNER=%%f"

if not defined APKSIGNER (
    echo [WARN] apksigner not found under ANDROID_HOME - skipping the check.
    goto :stage
)

REM apksigner is a .bat - without "call" it would take this script down with it.
set "CERTS=%TEMP%\automate-nova-certs.txt"
call "%APKSIGNER%" verify --print-certs "%APK%" > "%CERTS%" 2>&1
if errorlevel 1 (
    type "%CERTS%"
    del "%CERTS%" >nul 2>&1
    echo [ERROR] APK failed signature verification.
    exit /b 1
)
type "%CERTS%"
findstr /c:"CN=Android Debug" "%CERTS%" >nul
if not errorlevel 1 (
    del "%CERTS%" >nul 2>&1
    echo [ERROR] This APK is signed with the DEBUG key - do not publish it.
    echo         Check keystore.properties.
    exit /b 1
)
del "%CERTS%" >nul 2>&1

:stage
echo.
echo ===================================
echo 3. Staging into dist\
echo ===================================
if not exist "dist" mkdir "dist"
copy /y "%APK%" "dist\automate-nova-%VERSION%.apk" >nul
if %errorlevel% neq 0 (
    echo [ERROR] Could not copy the APK into dist\
    exit /b 1
)

echo.
echo ===================================
echo Done: dist\automate-nova-%VERSION%.apk
echo ===================================
echo.
echo Next, publish it so Obtainium can see it:
echo   1. Open https://github.com/mahesaJenar01/automate-nova/releases/new
echo   2. Tag:   v%VERSION%      (choose "Create new tag on publish")
echo   3. Title: v%VERSION%
echo   4. Drag dist\automate-nova-%VERSION%.apk onto "Attach binaries..."
echo   5. Publish release
echo.
echo Then pull to refresh in Obtainium.
echo.
goto :eof
