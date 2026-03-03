@echo off
setlocal
set SCRIPT_DIR=%~dp0
"%SCRIPT_DIR%java-library\gradlew.bat" -p "%SCRIPT_DIR%java-library" %*
