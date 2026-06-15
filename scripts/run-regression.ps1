$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host ''
Write-Host '========================================'
Write-Host '  不高山选课通 — 一键回归测试'
Write-Host '========================================'
Write-Host ''

$failed = $false

# ---- 1. Git branch check ----
Write-Host '[1/8] 检查分支...'
$branch = & git -C $root rev-parse --abbrev-ref HEAD
Write-Host "  当前分支: $branch"

# ---- 2. Environment check ----
Write-Host '[2/8] 检查环境...'
try {
  $javaVer = & java -version 2>&1 | Select-Object -First 1
  Write-Host "  java : $javaVer"
} catch { Write-Host '  java : NOT FOUND'; $failed = $true }

try {
  $javacVer = & javac -version 2>&1
  Write-Host "  javac: $javacVer"
} catch { Write-Host '  javac: NOT FOUND'; $failed = $true }

try {
  $mvnVer = & mvn -version 2>&1 | Select-String 'Java version' | ForEach-Object { $_.Line.Trim() }
  Write-Host "  mvn  : $mvnVer"
} catch { Write-Host '  mvn  : NOT FOUND'; $failed = $true }

try {
  $nodeVer = & node -v
  $npmVer = & npm -v
  Write-Host "  node : $nodeVer, npm: $npmVer"
} catch { Write-Host '  node/npm: NOT FOUND'; $failed = $true }

# ---- 3. Service check ----
Write-Host '[3/8] 检查服务端口...'
$mysqlOk = (Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue).Count -gt 0
$redisOk = (Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue).Count -gt 0
Write-Host "  MySQL (3306): $(if ($mysqlOk) { 'RUNNING' } else { 'NOT RUNNING - 部分测试可能失败' })"
Write-Host "  Redis (6379): $(if ($redisOk) { 'RUNNING' } else { 'NOT RUNNING - 后端测试可能失败' })"

# ---- 4. Frontend npm ci ----
Write-Host '[4/8] 前端依赖安装...'
Push-Location "$root/frontend"
try {
  $ciOut = & npm ci 2>&1
  Write-Host '  npm ci: OK'
} catch {
  Write-Host "  npm ci: FAILED - $_"
  $failed = $true
}
Pop-Location

# ---- 5. Frontend contract tests ----
Write-Host '[5/8] 前端契约测试...'
Push-Location "$root/frontend"
try {
  & npm run test:contracts
  Write-Host '  test:contracts: PASS'
} catch {
  Write-Host '  test:contracts: FAIL'
  $failed = $true
}
Pop-Location

# ---- 6. Frontend build ----
Write-Host '[6/8] 前端生产构建...'
Push-Location "$root/frontend"
try {
  & npm run build
  Write-Host '  build: PASS'
} catch {
  Write-Host '  build: FAIL'
  $failed = $true
}
Pop-Location

# ---- 7. Backend tests ----
Write-Host '[7/8] 后端单元测试...'
Push-Location "$root/backend"
try {
  & mvn test
  Write-Host '  mvn test: PASS'
} catch {
  Write-Host '  mvn test: FAIL'
  $failed = $true
}

# ---- 8. Backend package ----
Write-Host '[8/8] 后端打包...'
Push-Location "$root/backend"
try {
  & mvn clean package -DskipTests
  Write-Host '  mvn package: PASS'
} catch {
  Write-Host '  mvn package: FAIL'
  $failed = $true
}
Pop-Location

# ---- Result ----
Write-Host ''
Write-Host '========================================'
if ($failed) {
  Write-Host '  回归测试: 未通过 (有失败项)'
} else {
  Write-Host '  回归测试: 全部通过'
}
Write-Host '========================================'
Write-Host ''

exit $(if ($failed) { 1 } else { 0 })
