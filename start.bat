@echo off
setlocal
cd /d "%~dp0"

echo =============================================
echo   bugaoshan-course-system START
echo =============================================

set "JAR=backend\target\bugaoshan-backend-0.0.1-SNAPSHOT.jar"
set "RUN_DIR=%CD%\.run"

if exist ".env.local" (
    for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env.local") do (
        if not "%%A"=="" set "%%A=%%B"
    )
)

if not defined JAVA_HOME (
    echo JAVA_HOME is not configured. Set it to a JDK 21 installation.
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo java.exe was not found under JAVA_HOME: %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not defined DB_PASSWORD (
    echo DB_PASSWORD is not configured. Add it to .env.local.
    exit /b 1
)

if not defined JWT_SECRET (
    for /f "delims=" %%S in ('powershell -NoProfile -Command "([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))"') do set "JWT_SECRET=%%S"
)

if not exist "%RUN_DIR%" mkdir "%RUN_DIR%"

netstat -ano | find ":6379 " | find "LISTENING" >nul 2>&1
if errorlevel 1 (
    if defined REDIS_SERVER_PATH (
        if exist "%REDIS_SERVER_PATH%" start "" /min "%REDIS_SERVER_PATH%"
    )
)

netstat -ano | find ":6379 " | find "LISTENING" >nul 2>&1
if errorlevel 1 (
    echo Redis is not listening on port 6379. Start Redis or set REDIS_SERVER_PATH.
    exit /b 1
)

echo Building backend...
pushd backend
call mvn package "-Dmaven.test.skip=true" -q
if errorlevel 1 (
    popd
    echo Backend build failed. Startup aborted.
    exit /b 1
)
popd

echo Starting backend...
powershell -NoProfile -Command "$p = Start-Process -FilePath '%JAVA_HOME%\bin\java.exe' -ArgumentList '-jar','%CD%\%JAR%' -WorkingDirectory '%CD%' -WindowStyle Hidden -PassThru; Set-Content -LiteralPath '%RUN_DIR%\backend.pid' -Value $p.Id"
if errorlevel 1 exit /b 1

echo Preparing frontend...
pushd frontend
if not exist "node_modules\" call npm.cmd ci
if errorlevel 1 (
    popd
    powershell -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
    echo Frontend dependency installation failed. Startup aborted.
    exit /b 1
)
popd

echo Starting frontend...
powershell -NoProfile -Command "$p = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev' -WorkingDirectory '%CD%\frontend' -WindowStyle Hidden -PassThru; Set-Content -LiteralPath '%RUN_DIR%\frontend.pid' -Value $p.Id"
if errorlevel 1 (
    powershell -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
    exit /b 1
)

timeout /t 6 /nobreak >nul
start "" http://localhost:3000
echo Started. Open http://localhost:3000
endlocal
