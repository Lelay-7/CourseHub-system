$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$runDir = Join-Path $root '.run'

foreach ($name in @('frontend', 'backend')) {
    $pidFile = Join-Path $runDir "$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        Write-Host "${name}: no recorded process"
        continue
    }

    $processId = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        & taskkill.exe /PID $processId /T /F | Out-Null
        Write-Host "$name stopped (PID $processId)"
    } else {
        Write-Host "${name}: recorded PID $processId is not running"
    }
    Remove-Item -LiteralPath $pidFile -Force
}

if ((Test-Path -LiteralPath $runDir) -and -not (Get-ChildItem -LiteralPath $runDir -Force)) {
    Remove-Item -LiteralPath $runDir -Force
}
