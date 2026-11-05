@echo off
setlocal
cd /d "%~dp0"
if not exist ".tools\sputnik\de.keksuccino.rinku-neoforge-3.0.4-1.21.1-mod.jar" (
    python tools\setup_sputnik_browser.py
    if errorlevel 1 exit /b 1
)
if exist ".tools\jdk-21\bin\java.exe" set "JAVA_HOME=%CD%\.tools\jdk-21"
call gradlew.bat %*
exit /b %ERRORLEVEL%
