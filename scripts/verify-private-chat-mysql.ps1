[CmdletBinding()]
param(
    [string]$MysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$MavenPath = (Join-Path $env:USERPROFILE '.m2\maven-home\bin\mvn.cmd'),
    [ValidateRange(1024,65535)][int]$Port = 33362,
    [switch]$BrowserE2E,
    [string]$NodePath = (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
)
$ErrorActionPreference='Stop'
$runner=Join-Path $PSScriptRoot 'verify-account-recovery-mysql.ps1'
& $runner -Suite PrivateChat -MysqlHome $MysqlHome -MavenPath $MavenPath -Port $Port -BrowserE2E:$BrowserE2E -NodePath $NodePath
