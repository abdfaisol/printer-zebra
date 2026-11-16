@echo off
echo ========================================================
echo   Building Print Wareify Desktop EXE (Pure GUI)
echo ========================================================

echo 1. Compiling and Packaging JAR...
call .\mvnw.cmd clean package -DskipTests
if errorlevel 1 (
    echo Error during Maven build.
    exit /b %errorlevel%
)

echo 2. Packaging Pure GUI EXE with jpackage...
if exist "dist\Print Wareify" rmdir /s /q "dist\Print Wareify"
jpackage --name "Print Wareify" --input "target" --main-jar "printprasi-0.0.1-SNAPSHOT.jar" --type app-image --dest "dist" --icon "favicon.ico" --java-options "-Djava.awt.headless=false"
if errorlevel 1 (
    echo Error during jpackage execution.
    exit /b %errorlevel%
)

echo 3. Copying Zebra native DLLs to runtime and app directories...
xcopy /E /I /Y "libs\*" "dist\Print Wareify\runtime\bin\"
xcopy /E /I /Y "libs\*" "dist\Print Wareify\libs\"
xcopy /E /I /Y "libs\*" "dist\Print Wareify\app\libs\"
if exist "config.properties" copy /Y "config.properties" "dist\Print Wareify\"
if exist "printers.json" copy /Y "printers.json" "dist\Print Wareify\"

echo 4. Compiling Installer Setup (.exe)...
set "ISCC_PATH=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if exist "%ISCC_PATH%" (
    echo Building Installer using Inno Setup...
    "%ISCC_PATH%" installer.iss
) else (
    echo Note: Inno Setup ISCC.exe not found.
)

echo ========================================================
echo   BUILD SELESAI!
echo   Portable EXE : dist\Print Wareify\Print Wareify.exe
echo   Installer Setup : dist-installer\Print-Wareify-Setup-v1.3.0.exe
echo ========================================================
