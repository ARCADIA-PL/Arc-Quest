@echo off

setlocal



set "ROOT_DIR=%~dp0"

set "NPM_CMD="



cd /d "%ROOT_DIR%"



call :resolve_npm

if errorlevel 1 goto :missing_npm



echo [1/3] Installing dependencies if needed...

if not exist "node_modules" (

  call "%NPM_CMD%" install

  if errorlevel 1 goto :fail

)



echo [2/3] Building standalone HTML...

call "%NPM_CMD%" run build:standalone

if errorlevel 1 goto :fail



echo [3/3] Renaming output for sharing...

if exist "dist\arc-quest-editor.html" del /f /q "dist\arc-quest-editor.html"

if exist "dist\index.html" ren "dist\index.html" "arc-quest-editor.html"

if errorlevel 1 goto :fail



echo.

echo Done. Output:

echo %ROOT_DIR%dist\arc-quest-editor.html

echo Opening dist folder...

start "" "%ROOT_DIR%dist"

goto :eof



:resolve_npm

where npm >nul 2>nul

if not errorlevel 1 (

  set "NPM_CMD=npm"

  exit /b 0

)



where npm.cmd >nul 2>nul

if not errorlevel 1 (

  set "NPM_CMD=npm.cmd"

  exit /b 0

)



if exist "%ProgramFiles%\nodejs\npm.cmd" (

  set "NPM_CMD=%ProgramFiles%\nodejs\npm.cmd"

  exit /b 0

)



if defined ProgramFiles(x86) if exist "%ProgramFiles(x86)%\nodejs\npm.cmd" (

  set "NPM_CMD=%ProgramFiles(x86)%\nodejs\npm.cmd"

  exit /b 0

)



exit /b 1



:missing_npm

echo.

echo Build failed: npm was not found.

echo Please install the official Node.js for Windows, or add npm to PATH.

echo Expected command: npm -v

exit /b 1



:fail

echo.

echo Build failed.

exit /b 1

