@echo off
echo === Clear Gradle transforms cache ===
echo.
echo IMPORTANT: Close Android Studio and Cursor (or any IDE using this project) first.
echo Then run this script from an external Command Prompt or Explorer.
echo.
pause

echo Stopping Gradle daemons...
call "%~dp0gradlew.bat" --stop 2>nul
if errorlevel 1 echo (gradlew --stop had an issue, continuing anyway)

echo Waiting 3 seconds for processes to release files...
timeout /t 3 /nobreak >nul

set "TRANSFORMS=%USERPROFILE%\.gradle\caches\8.14\transforms"
echo Deleting: %TRANSFORMS%
rd /s /q "%TRANSFORMS%" 2>nul

if exist "%TRANSFORMS%" (
    echo.
    echo FAILED: Folder still exists. A process is still locking files.
    echo.
    echo Try: 1^) Reboot the PC
    echo       2^) Run this script again RIGHT AFTER login, before opening any IDE
    echo.
    echo Or use Sysinternals Handle to find what is locking the .jar files:
    echo   https://learn.microsoft.com/en-us/sysinternals/downloads/handle
    echo   Run: handle activity
    echo.
) else (
    echo.
    echo SUCCESS: Transforms cache deleted. Gradle will recreate it on next build.
    echo You can reopen Android Studio and sync the project.
    echo.
)

pause
