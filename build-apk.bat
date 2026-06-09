@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

echo ============================================
echo   DiaryApp - Build APK
echo ============================================
echo.

set "ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_HOME=%ANDROID_SDK%"
set "ANDROID_SDK_ROOT=%ANDROID_SDK%"
set "SDK_MANAGER=%ANDROID_SDK%\cmdline-tools\latest\bin\sdkmanager.bat"

set "PROJECT_DIR=%~dp0"
if "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR:~0,-1%"

echo Project dir: "%PROJECT_DIR%"
echo SDK root:    "%ANDROID_SDK%"
echo.

rem ===== Step 1: Download Command Line Tools =====
if not exist "%SDK_MANAGER%" (
    echo [1/5] Downloading Android Command Line Tools...

    set "WORK=%TEMP%\android_setup_!RANDOM!"
    if exist "!WORK!" rmdir /s /q "!WORK!"
    mkdir "!WORK!"

    set "ZIP=!WORK!\cmdline.zip"
    set "URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

    echo  Downloading ^(about 150MB^)...
    powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '!URL!' -OutFile '!ZIP!' -UseBasicParsing"

    if not exist "!ZIP!" (
        echo  ERROR: Download failed!
        echo  Manual download: !URL!
        pause
        exit /b 1
    )

    echo  Extracting...
    powershell -NoProfile -Command "Expand-Archive -Path '!ZIP!' -DestinationPath '!WORK!' -Force"

    set "TARGET=%ANDROID_SDK%\cmdline-tools\latest"
    if not exist "!TARGET!" mkdir "!TARGET!"

    if exist "!WORK!\cmdline-tools\bin" (
        xcopy /E /I /Y "!WORK!\cmdline-tools\*" "!TARGET!\" >nul
    ) else (
        xcopy /E /I /Y "!WORK!\*" "!TARGET!\" >nul
    )

    rmdir /s /q "!WORK!"
    echo  Done.
) else (
    echo [1/5] Command Line Tools already present.
)

rem ===== Step 2: Licenses =====
echo [2/5] Accepting SDK licenses...
set "LDIR=%ANDROID_SDK%\licenses"
if not exist "!LDIR!" mkdir "!LDIR!"
echo 8933bad161af4178b1185d1a37fbf41ea5269c55>"!LDIR!\android-sdk-license"
echo 504667f4c0de7af1a06de2f3649583275df3684e>"!LDIR!\android-sdk-preview-license"
echo  Done.

rem ===== Step 3: Install SDK components =====
echo [3/5] Installing SDK components (platform 35, build-tools 35.0.0)...
call "%SDK_MANAGER%" "platform-tools" "platforms;android-35" "build-tools;35.0.0" --no_https
echo  Done.

rem ===== Step 4: local.properties =====
echo [4/5] Creating local.properties...
set "SDKE=%ANDROID_SDK:\=/%"
echo sdk.dir=!SDKE!>"%PROJECT_DIR%\local.properties"
echo  Done.

rem ===== Step 5: Build APK =====
echo [5/5] Building APK...
echo.

cd /d "%PROJECT_DIR%"

if exist "gradlew.bat" (
    echo Using Gradle Wrapper...
    call gradlew.bat assembleDebug --no-daemon
) else (
    echo No gradlew.bat found. Downloading Gradle 8.9...

    set "WORK=!TEMP!\gradle_setup_!RANDOM!"
    if exist "!WORK!" rmdir /s /q "!WORK!"
    mkdir "!WORK!"

    set "GZIP=!WORK!\gradle.zip"
    set "GURL=https://services.gradle.org/distributions/gradle-8.9-bin.zip"

    echo  Downloading Gradle ^(about 130MB^)...
    powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '!GURL!' -OutFile '!GZIP!' -UseBasicParsing"

    echo  Extracting...
    powershell -NoProfile -Command "Expand-Archive -Path '!GZIP!' -DestinationPath '!WORK!' -Force"

    for /d %%D in ("!WORK!\gradle-*") do set "GRADLE_HOME=%%D"

    echo  Generating Gradle Wrapper...
    call "!GRADLE_HOME!\bin\gradle.bat" wrapper --gradle-version 8.9

    del "!GZIP!"
    rmdir /s /q "!WORK!"

    echo  Building APK...
    call gradlew.bat assembleDebug --no-daemon
)

echo.
echo ============================================

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo   BUILD SUCCESS
    echo.
    echo   APK location:
    echo     %PROJECT_DIR%\app\build\outputs\apk\debug\app-debug.apk
    for %%A in ("%PROJECT_DIR%\app\build\outputs\apk\debug\app-debug.apk") do (
        set /a "SZ=%%~zA/1048576"
        echo   File size: !SZ! MB
    )
    echo.
    echo   Install to device: adb install "app\build\outputs\apk\debug\app-debug.apk"
) else (
    echo   BUILD FAILED - check errors above
)

echo ============================================
echo.
pause
