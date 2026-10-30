$ErrorActionPreference = 'Stop'
$previewPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
if (-not (Test-Path -LiteralPath $previewPython)) { throw 'Codex bundled Python runtime is missing.' }
& $previewPython (Join-Path $PSScriptRoot 'product-preview\manage.py') stop
if ($LASTEXITCODE -ne 0) { throw "Preview stop failed ($LASTEXITCODE)." }
