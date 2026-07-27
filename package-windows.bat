@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ========================================
echo   DesktopPet Windows Release Builder
echo ========================================
echo.

REM ---- Check JDK ----
where javac >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] JDK not found in PATH
    exit /b 1
)

for /f "tokens=1-4 delims=." %%a in ('javac -version 2^>^&1 ^| findstr /r "[0-9][0-9]*\.[0-9][0-9]*"') do (
    set JAVA_VER_MAJOR=%%b
)
if !JAVA_VER_MAJOR! lss 21 (
    echo [ERROR] JDK 21+ required, found: !JAVA_VER_MAJOR!
    exit /b 1
)
echo [INFO] JDK !JAVA_VER_MAJOR! detected

REM ---- Clean previous output ----
set RELEASE_DIR=target\release\DesktopPet
if exist "%RELEASE_DIR%" (
    echo [INFO] Cleaning previous release...
    rmdir /s /q "%RELEASE_DIR%"
)
if exist target\jre (
    echo [INFO] Cleaning previous JRE...
    rmdir /s /q target\jre
)

REM ---- Step 1: Build fat JAR ----
echo.
echo [Step 1/4] Building fat JAR...
call mvn package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed
    exit /b 1
)
echo [INFO] Fat JAR built: target\desktoppet-1.0.jar

REM ---- Step 2: jlink minimal JRE ----
echo.
echo [Step 2/4] Creating minimal JRE via jlink...
call jlink ^
    --add-modules java.base,java.desktop,jdk.unsupported ^
    --strip-debug ^
    --no-man-pages ^
    --no-header-files ^
    --compress=zip-6 ^
    --output target\jre
if %ERRORLEVEL% neq 0 (
    echo [ERROR] jlink failed
    exit /b 1
)
echo [INFO] Minimal JRE created: target\jre

REM ---- Step 3: jpackage app-image ----
echo.
echo [Step 3/4] Creating app image via jpackage...
call jpackage ^
    --type app-image ^
    --name DesktopPet ^
    --app-version 1.0 ^
    --description "Desktop Pet - 桌面宠物" ^
    --vendor DesktopPet ^
    --input target\ ^
    --main-jar desktoppet-1.0.jar ^
    --main-class pet.Main ^
    --dest target\release ^
    --runtime-image target\jre ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Xmx256m"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] jpackage failed
    exit /b 1
)
echo [INFO] App image created: target\release\DesktopPet

REM ---- Step 4: Copy models ----
echo.
echo [Step 4/4] Copying model assets...
if exist models\ (
    xcopy models\* "%RELEASE_DIR%\models\" /E /I /Q /Y
    echo [INFO] Models copied to %RELEASE_DIR%\models\
) else (
    echo [WARN] models/ directory not found, skipping
)

REM ---- Done ----
echo.
echo ========================================
echo   Build Complete!
echo.
echo   Output: %RELEASE_DIR%
echo   Run:    %RELEASE_DIR%\DesktopPet.exe
echo ========================================
endlocal
