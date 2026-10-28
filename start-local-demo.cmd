@echo off
setlocal
set "DEMO_PYTHON=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if not exist "%DEMO_PYTHON%" (
  echo Codex bundled Python is missing. See docs/local-demo-guide.md.
  pause
  exit /b 1
)
"%DEMO_PYTHON%" "%~dp0scripts\product-preview\local_demo.py" start %*
set "DEMO_RESULT=%ERRORLEVEL%"
if not "%DEMO_NO_PAUSE%"=="1" pause
exit /b %DEMO_RESULT%
