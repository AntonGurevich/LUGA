@echo off
REM Creates upload-keystore.jks for release signing.
REM Run this in the project root. You will be prompted for:
REM   - Keystore password (enter twice)
REM   - Key password (can be same as keystore; enter twice)
REM Then add to local.properties (do not commit):
REM   RELEASE_STORE_FILE=upload-keystore.jks
REM   RELEASE_STORE_PASSWORD=your_keystore_password
REM   RELEASE_KEY_ALIAS=upload
REM   RELEASE_KEY_PASSWORD=your_key_password

set KEYSTORE=upload-keystore.jks
if exist "%KEYSTORE%" (
  echo %KEYSTORE% already exists. Delete it first if you want to recreate.
  pause
  exit /b 1
)

keytool -genkey -v -keystore "%KEYSTORE%" -keyalg RSA -keysize 2048 -validity 10000 -alias upload -dname "CN=Acteamity, OU=Mobile, O=Acteamity Limited, L=London, ST=England, C=GB"
if %ERRORLEVEL% neq 0 (
  echo keytool failed.
  pause
  exit /b 1
)

echo.
echo Keystore created: %KEYSTORE%
echo Add these lines to local.properties (use your real passwords):
echo   RELEASE_STORE_FILE=%KEYSTORE%
echo   RELEASE_STORE_PASSWORD=your_keystore_password
echo   RELEASE_KEY_ALIAS=upload
echo   RELEASE_KEY_PASSWORD=your_key_password
pause
