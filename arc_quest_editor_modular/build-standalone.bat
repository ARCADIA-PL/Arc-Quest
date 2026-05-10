@echo off
setlocal

set "ROOT_DIR=%~dp0"
cd /d "%ROOT_DIR%"

echo [1/3] Installing dependencies if needed...
if not exist "node_modules" (
  call npm install
  if errorlevel 1 goto :fail
)

echo [2/3] Building standalone HTML...
call npm run build:standalone
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

:fail
echo.
echo Build failed.
exit /b 1
