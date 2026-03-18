@echo off
where gradle >nul 2>&1
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is required on Windows to run this build.
exit /b 1

