param()
$ErrorActionPreference = 'Stop'
# The suite validates the URL and ownership nonce before creating/resetting fixture tables.
# Required: COURSEHUB_MATERIAL_MYSQL_URL, USER, NONCE; optional PASSWORD.
# Database: coursehub_material_check_<32 hex>; guard: material_test_guard(nonce VARCHAR(32)).
foreach ($key in @('COURSEHUB_MATERIAL_MYSQL_URL', 'COURSEHUB_MATERIAL_MYSQL_USER', 'COURSEHUB_MATERIAL_MYSQL_NONCE')) {
    if (-not [Environment]::GetEnvironmentVariable($key, 'Process')) { throw "Missing $key" }
}
$taskJava = Join-Path $env:USERPROFILE '.jdks\ms-21.0.9'
$taskMaven = Join-Path $env:USERPROFILE '.m2\maven-home\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath (Join-Path $taskJava 'bin\java.exe'))) { throw 'Verified Java runtime is unavailable' }
if (-not (Test-Path -LiteralPath $taskMaven)) { throw 'Verified Maven runtime is unavailable' }
$previousJava = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $taskJava
    Push-Location -LiteralPath (Join-Path $PSScriptRoot '..\backend')
    try {
        & $taskMaven '-o' 'test' '-Dtest=MaterialReviewMySqlIT' '-DargLine=-Dfile.encoding=UTF-8'
        if ($LASTEXITCODE -ne 0) { throw "Material review MySQL verification failed ($LASTEXITCODE)" }
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJava }
