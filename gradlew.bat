@echo off
setlocal
set SCRIPT_DIR=%~dp0
call "%SCRIPT_DIR%java-library\gradlew.bat" -p "%SCRIPT_DIR%" %*
