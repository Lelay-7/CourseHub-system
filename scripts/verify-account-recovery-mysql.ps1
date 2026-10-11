[CmdletBinding()]
param(
    [ValidateSet('AccountRecovery','PrivateChat')]
    [string]$Suite = 'AccountRecovery',
    [string]$MysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0',
    [string]$MavenPath = (Join-Path $env:USERPROFILE '.m2\maven-home\bin\mvn.cmd'),
    [ValidateRange(1024, 65535)]
    [int]$Port = 33361,
    [switch]$BrowserE2E,
    [string]$NodePath = (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
)

# Always creates a new local instance. There is deliberately no existing-server mode.
# The default uses AccountRecoveryMySqlIT; BrowserE2E uses local SMTP and browsers.
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$backend = Join-Path $root 'backend'
$target = Join-Path $backend 'target'
$runId = [Guid]::NewGuid().ToString('N')
$nonce = [Guid]::NewGuid().ToString('N')
$suiteSlug = if ($Suite -eq 'PrivateChat') { 'chat' } else { 'recovery' }
$database = 'coursehub_' + $suiteSlug + '_check_' + $runId
$runName = 'coursehub-' + $suiteSlug + '-mysql-' + $runId
$guardTable = if ($Suite -eq 'PrivateChat') { 'private_chat_test_guard' } else { 'recovery_test_guard' }
$envPrefix = if ($Suite -eq 'PrivateChat') { 'COURSEHUB_CHAT' } else { 'COURSEHUB_RECOVERY' }
$testClasses = if ($Suite -eq 'PrivateChat') { @('com.bugaoshan.chat.PrivateChatMySqlIT','com.bugaoshan.chat.PrivateChatMigrationMySqlIT') } else { @('com.bugaoshan.security.AccountRecoveryMySqlIT') }
$evidence = Join-Path $target $runName
$summaryPath = Join-Path $evidence 'verification.log'
$initPath = Join-Path $backend 'src\main\resources\db\init.sql'
$migrationPath = Join-Path $backend 'src\main\resources\db\migrations\2026-10-10-account-recovery.sql'
$mysql = [IO.Path]::GetFullPath((Join-Path $MysqlHome 'bin\mysql.exe'))
$mysqld = [IO.Path]::GetFullPath((Join-Path $MysqlHome 'bin\mysqld.exe'))
$maven = [IO.Path]::GetFullPath($MavenPath)
$server = $null
$serverStarted = $null
$actualServer = $null
$actualServerStarted = $null
$ownedProcessState = @{ Process = $null; Started = $null }
$initialize = $null
$initializeStarted = $null
$initializeActual = $null
$initializeActualStarted = $null
$initializePidSeen = $false
$runDirectory = $null
$dataDirectory = $null
$pidPath = $null
$markerPath = $null
$machineTemp = $null
$databaseCreated = $false
$primaryError = $null
$cleanupErrors = New-Object 'System.Collections.Generic.List[string]'
$previousOutputEncoding = $OutputEncoding
$previousConsoleEncoding = [Console]::OutputEncoding
$savedEnvironment = @{}
foreach ($name in @('MYSQL_PWD', ($envPrefix+'_MYSQL_URL'), ($envPrefix+'_MYSQL_USER'),
        ($envPrefix+'_MYSQL_PASSWORD'), ($envPrefix+'_MYSQL_NONCE'))) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Write-Evidence([string]$Message) {
    [IO.File]::AppendAllText($summaryPath, $Message + [Environment]::NewLine, $utf8)
    Write-Host $Message
}

function Read-SharedUtf8File([string]$Path) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
        ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $reader = New-Object IO.StreamReader($stream, $utf8)
    try { return $reader.ReadToEnd() }
    finally { $reader.Dispose() }
}

function Assert-OwnedDirectory {
    if ([string]::IsNullOrEmpty($runDirectory) -or [string]::IsNullOrEmpty($machineTemp)) {
        throw 'Temporary directory ownership is unavailable.'
    }
    $resolved = [IO.Path]::GetFullPath($runDirectory).TrimEnd('\')
    $parent = [IO.Path]::GetFullPath($machineTemp).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($parent, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolved) -cne $runName -or
            $runName -notmatch ('^coursehub-' + $suiteSlug + '-mysql-[a-f0-9]{32}$')) {
        throw 'Refusing cleanup: temporary path is outside this run.'
    }
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf) -or
            [IO.File]::ReadAllText($markerPath, $utf8) -cne $nonce) {
        throw 'Refusing cleanup: temporary directory marker does not match.'
    }
    $directories = @(Get-Item -LiteralPath $runDirectory) + @(
        Get-ChildItem -LiteralPath $runDirectory -Directory -Recurse -Force)
    foreach ($directory in $directories) {
        if (($directory.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Refusing cleanup: temporary directory contains a reparse point.'
        }
    }
}

function Invoke-MySql([string]$Sql, [string]$Label, [switch]$AllowFailure) {
    # Explicit empty password and endpoint override any client login-path settings.
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $mysql
    $start.Arguments = '--no-defaults --protocol=TCP --host=127.0.0.1 --port=' + $Port +
        ' --user=root --password= --default-character-set=utf8mb4 --batch --raw --skip-column-names --connect-timeout=2'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = $utf8
    $start.StandardErrorEncoding = $utf8
    $client = New-Object Diagnostics.Process
    $client.StartInfo = $start
    try {
        [void]$client.Start()
        $outputTask = $client.StandardOutput.ReadToEndAsync()
        $errorTask = $client.StandardError.ReadToEndAsync()
        # PowerShell 5.1 native pipelines otherwise encode Chinese SQL as ASCII.
        $sqlBytes = $utf8.GetBytes($Sql + "`n")
        $client.StandardInput.BaseStream.Write($sqlBytes, 0, $sqlBytes.Length)
        $client.StandardInput.Close()
        if (-not $client.WaitForExit(60000)) {
            $client.Kill()
            throw "MySQL client timed out during $Label."
        }
        $output = $outputTask.GetAwaiter().GetResult()
        $errorText = $errorTask.GetAwaiter().GetResult()
        if (-not [string]::IsNullOrWhiteSpace($errorText)) {
            [IO.File]::AppendAllText((Join-Path $evidence 'mysql-client.log'),
                "[$Label]`n" + $errorText, $utf8)
        }
        if ($client.ExitCode -ne 0 -and -not $AllowFailure) {
            throw "MySQL client failed during $Label (exit $($client.ExitCode)); see mysql-client.log."
        }
        return [pscustomobject]@{ ExitCode = $client.ExitCode; Output = $output }
    }
    finally { $client.Dispose() }
}

function Assert-ServerIdentity {
    $identity = Invoke-MySql 'SELECT @@datadir, @@port, @@version, @@pid_file;' 'instance identity'
    $fields = $identity.Output.TrimEnd("`r", "`n") -split "`t"
    if ($fields.Count -ne 4) { throw 'MySQL returned an invalid instance identity.' }
    $actualData = [IO.Path]::GetFullPath($fields[0].Replace('/', '\')).TrimEnd('\')
    $expectedData = [IO.Path]::GetFullPath($dataDirectory).TrimEnd('\')
    if (-not $actualData.Equals($expectedData, [StringComparison]::OrdinalIgnoreCase) -or
            [int]$fields[1] -ne $Port -or $fields[2] -notmatch '^8\.' -or
            -not [IO.Path]::GetFullPath($fields[3].Replace('/', '\')).Equals($pidPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing database operation: endpoint is not this disposable MySQL 8 instance.'
    }
    Assert-OwnedDirectory
    $pidText = [IO.File]::ReadAllText($pidPath, $utf8).Trim()
    if ($pidText -notmatch '^\d+$') { throw 'Verified MySQL instance has an invalid pid file.' }
    if ($null -eq $ownedProcessState.Process) {
        $candidate = Get-Process -Id ([int]$pidText) -ErrorAction Stop
        [void]$candidate.Handle
        $candidatePath = [IO.Path]::GetFullPath($candidate.MainModule.FileName)
        $candidateStarted = $candidate.StartTime
        if (-not $candidatePath.Equals($mysqld, [StringComparison]::OrdinalIgnoreCase) -or
                $candidateStarted.ToUniversalTime() -lt $serverStarted.ToUniversalTime().AddSeconds(-2)) {
            $candidate.Dispose()
            throw 'Verified MySQL pid does not belong to a process created by this run.'
        }
        # Windows mysqld can restart itself: retain the actual child process handle.
        $ownedProcessState.Process = $candidate
        $ownedProcessState.Started = $candidateStarted
        [IO.File]::AppendAllText($summaryPath,
            "Verified MySQL process=$pidText; launcher=$($server.Id)`n", $utf8)
    }
    elseif ($ownedProcessState.Process.Id -ne [int]$pidText) {
        throw 'Verified MySQL pid changed during this run.'
    }
}

function Invoke-OwnedSql([string]$Sql, [string]$Label) {
    Assert-ServerIdentity
    $result = Invoke-MySql $Sql $Label
    return $result.Output
}

function Assert-DatabaseScope([string]$Sql) {
    foreach ($match in [regex]::Matches($Sql,
            '(?im)\b(?:CREATE|ALTER|DROP)\s+DATABASE\s+(?:IF\s+(?:NOT\s+)?EXISTS\s+)?`?([a-zA-Z0-9_]+)`?|\bUSE\s+`?([a-zA-Z0-9_]+)`?')) {
        $name = $match.Groups[1].Value
        if ([string]::IsNullOrEmpty($name)) { $name = $match.Groups[2].Value }
        if ($name -cne $database) { throw 'SQL references a database outside this disposable run.' }
    }
}

function Stop-OwnedProcess([Diagnostics.Process]$Process, [DateTime]$ExpectedStarted) {
    if ($Process.HasExited) { return }
    if ($Process.StartTime -ne $ExpectedStarted -or
            -not [IO.Path]::GetFullPath($Process.MainModule.FileName).Equals($mysqld, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to stop a process whose captured identity no longer matches.'
    }
    Assert-OwnedDirectory
    # Kill through the captured handle, never by global enumeration or a reused pid.
    $Process.Kill()
    if (-not $Process.WaitForExit(10000)) {
        throw 'Temporary MySQL process did not stop.'
    }
}

function Assert-TargetFile([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    $targetPrefix = [IO.Path]::GetFullPath($target).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($targetPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing report operation outside backend/target.'
    }
    $directory = [IO.Path]::GetDirectoryName($resolved)
    while ($directory.StartsWith($targetPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        if (Test-Path -LiteralPath $directory) {
            $item = Get-Item -LiteralPath $directory
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Refusing report operation through a reparse point.'
            }
        }
        $directory = [IO.Path]::GetDirectoryName($directory)
    }
}

try {
    foreach ($path in @($mysql, $mysqld, $maven, $initPath, $migrationPath)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Required file is missing: $path"
        }
    }
    if ([IO.Path]::GetExtension($maven) -ine '.cmd') { throw 'MavenPath must point to mvn.cmd.' }
    if ($database -notmatch ('^coursehub_' + $suiteSlug + '_check_[a-f0-9]{32}$')) { throw 'Invalid disposable database name.' }
    $machineTemp = [Environment]::GetEnvironmentVariable('TEMP', 'Machine')
    if ([string]::IsNullOrWhiteSpace($machineTemp)) {
        throw 'Machine TEMP is not configured; an existing ASCII machine temporary directory is required.'
    }
    $machineTemp = [Environment]::ExpandEnvironmentVariables($machineTemp)
    $machineTemp = (Get-Item -LiteralPath $machineTemp -ErrorAction Stop).FullName
    if ($machineTemp -match '[^\x20-\x7e]' -or -not (Test-Path -LiteralPath $machineTemp -PathType Container)) {
        throw 'Machine TEMP must be an existing ASCII directory for the installed MySQL Windows binary.'
    }
    $tempItem = Get-Item -LiteralPath $machineTemp
    if (($tempItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Machine TEMP must not be a reparse point.'
    }
    $runDirectory = Join-Path $machineTemp $runName
    $dataDirectory = Join-Path $runDirectory 'data'
    $markerPath = Join-Path $runDirectory '.coursehub-run-marker'
    $pidPath = Join-Path $runDirectory 'mysql.pid'
    if (Test-Path -LiteralPath $runDirectory) { throw 'Random temporary directory already exists; refusing to reuse it.' }
    [void](New-Item -ItemType Directory -Path $runDirectory)
    [IO.File]::WriteAllText($markerPath, $nonce, $utf8)
    [void](New-Item -ItemType Directory -Path $dataDirectory)
    [void](New-Item -ItemType Directory -Path $evidence -Force)
    $OutputEncoding = $utf8
    [Console]::OutputEncoding = $utf8
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')

    # Choose another free loopback port if the preferred port is already in use.
    $listener = $null
    try {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
        $listener.Start()
    }
    catch {
        if ($null -ne $listener) { $listener.Stop() }
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        $listener.Start()
    }
    $Port = $listener.LocalEndpoint.Port
    $listener.Stop()
    Write-Evidence "Run=$runId; MySQL 8 loopback port=$Port; database=$database"

    $mysqlData = $dataDirectory.Replace('\', '/')
    $mysqlBase = [IO.Path]::GetFullPath($MysqlHome).Replace('\', '/')
    $initializeOptions = @{
        FilePath = $mysqld
        ArgumentList = @('--no-defaults', ('--basedir="' + $mysqlBase + '"'),
            ('--datadir="' + $mysqlData + '"'), '--initialize-insecure', '--console')
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = Join-Path $runDirectory 'initialize.stdout.log'
        RedirectStandardError = Join-Path $runDirectory 'initialize.stderr.log'
    }
    $initialize = Start-Process @initializeOptions
    [void]$initialize.Handle
    $initializeStarted = $initialize.StartTime
    $initializeDeadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $initializeDeadline) {
        if (Test-Path -LiteralPath $initializeOptions.RedirectStandardError -PathType Leaf) {
            $initializeLog = Read-SharedUtf8File $initializeOptions.RedirectStandardError
            $initializeMatch = [regex]::Match($initializeLog, 'initializing of server in progress as process (\d+)')
            if ($initializeMatch.Success) {
                $initializeCandidate = Get-Process -Id ([int]$initializeMatch.Groups[1].Value) -ErrorAction SilentlyContinue
                if ($null -ne $initializeCandidate) {
                    [void]$initializeCandidate.Handle
                    $candidateStarted = $initializeCandidate.StartTime
                    if (-not [IO.Path]::GetFullPath($initializeCandidate.MainModule.FileName).Equals($mysqld, [StringComparison]::OrdinalIgnoreCase) -or
                            $candidateStarted.ToUniversalTime() -lt $initializeStarted.ToUniversalTime().AddSeconds(-2)) {
                        $initializeCandidate.Dispose()
                        throw 'Initializer log pid does not match a MySQL process created by this run.'
                    }
                    $initializeActual = $initializeCandidate
                    $initializeActualStarted = $candidateStarted
                }
                $initializePidSeen = $true
                break
            }
        }
        if ($initialize.HasExited -and $null -ne $initialize.ExitCode -and $initialize.ExitCode -ne 0) { break }
        Start-Sleep -Milliseconds 100
    }
    if ($null -ne $initializeActual) {
        $remaining = [Math]::Max(0, [int]($initializeDeadline - [DateTime]::UtcNow).TotalMilliseconds)
        if (-not $initializeActual.WaitForExit($remaining)) {
            throw 'Actual MySQL initializer did not finish within 60 seconds.'
        }
        $actualInitializeExit = $initializeActual.ExitCode
        if ($null -eq $actualInitializeExit) {
            Write-Evidence 'Actual initializer exit code unavailable; server readiness and migration acceptance are still required.'
        }
        elseif ($actualInitializeExit -ne 0) { throw "Actual MySQL initializer failed (exit $actualInitializeExit)." }
    }
    $remaining = [Math]::Max(0, [int]($initializeDeadline - [DateTime]::UtcNow).TotalMilliseconds)
    if (-not $initialize.WaitForExit($remaining)) { throw 'MySQL initialization launcher did not finish within 60 seconds.' }
    $launcherInitializeExit = $initialize.ExitCode
    if ($null -eq $launcherInitializeExit) {
        Write-Evidence 'Initializer launcher exit code unavailable; server readiness and migration acceptance are still required.'
    }
    elseif ($launcherInitializeExit -ne 0) { throw "Temporary MySQL initialization failed (exit $launcherInitializeExit)." }
    if (-not $initializePidSeen) { throw 'MySQL initializer did not report its process within 60 seconds.' }

    $serverOptions = @{
        FilePath = $mysqld
        ArgumentList = @('--no-defaults', ('--basedir="' + $mysqlBase + '"'),
            ('--datadir="' + $mysqlData + '"'), ('--pid-file="' + $pidPath.Replace('\', '/') + '"'),
            '--bind-address=127.0.0.1', "--port=$Port", '--mysqlx=0', '--skip-log-bin',
            '--innodb-buffer-pool-size=64M', '--innodb-lock-wait-timeout=5', '--console')
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = Join-Path $runDirectory 'server.stdout.log'
        RedirectStandardError = Join-Path $runDirectory 'server.stderr.log'
    }
    $server = Start-Process @serverOptions
    [void]$server.Handle
    $serverStarted = $server.StartTime
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        $probe = Invoke-MySql 'SELECT 1;' 'startup probe' -AllowFailure
        if ($probe.ExitCode -eq 0) {
            Assert-ServerIdentity
            $ready = $true
            break
        }
        # The Windows launcher can exit while its child is still starting.
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) { throw 'Temporary MySQL did not become ready within 60 seconds.' }
    Write-Evidence 'Verified @@datadir, @@port, @@pid_file and MySQL 8 version before importing any SQL.'

    $init = [IO.File]::ReadAllText($initPath, $utf8).Replace('bgss_xtt', $database)
    Assert-DatabaseScope $init
    # Set before import so an interrupted import can still clean its partial database.
    $databaseCreated = $true
    [void](Invoke-OwnedSql $init 'complete init.sql import')
    $originalColumns = (Invoke-OwnedSql ("SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
        "WHERE TABLE_SCHEMA='$database' AND TABLE_NAME='user' ORDER BY ORDINAL_POSITION;") 'original user columns').Trim() -split '\r?\n'
    if ($originalColumns.Count -lt 1 -or $originalColumns -notcontains 'user_id') {
        throw 'Initialization did not create the expected user table.'
    }
    foreach ($column in $originalColumns) {
        if ($column -notmatch '^[a-zA-Z0-9_]+$') { throw 'Unexpected original user column name.' }
    }
    [void](Invoke-OwnedSql ("USE $database; CREATE TABLE recovery_user_before LIKE user; " +
        'INSERT INTO recovery_user_before SELECT * FROM user;') 'original user snapshot')
    $beforeCount = [int](Invoke-OwnedSql "SELECT COUNT(*) FROM $database.recovery_user_before;" 'snapshot row count').Trim()
    if ($beforeCount -lt 1) { throw 'Migration must be checked against populated initialization data.' }

    $migration = [IO.File]::ReadAllText($migrationPath, $utf8)
    Assert-DatabaseScope $migration
    [void](Invoke-OwnedSql ("USE $database;`n" + $migration) 'checked-in account recovery migration')
    $afterCount = [int](Invoke-OwnedSql "SELECT COUNT(*) FROM $database.user;" 'migrated user row count').Trim()
    if ($afterCount -ne $beforeCount) { throw 'Migration changed the user row count.' }
    $comparison = New-Object 'System.Collections.Generic.List[string]'
    foreach ($column in $originalColumns) {
        $quoted = '`' + $column + '`'
        $comparison.Add("SELECT '$column', COUNT(*) FROM user u JOIN recovery_user_before b ON u.user_id=b.user_id " +
            "WHERE NOT (BINARY u.$quoted <=> BINARY b.$quoted)")
    }
    $comparison.Add("SELECT 'user_keys', COUNT(*) FROM recovery_user_before b LEFT JOIN user u ON u.user_id=b.user_id WHERE u.user_id IS NULL")
    $comparison.Add("SELECT 'new_defaults', COUNT(*) FROM user WHERE recovery_email IS NOT NULL OR recovery_email_verified_at IS NOT NULL OR NOT (credential_version <=> 0)")
    $rows = (Invoke-OwnedSql ("USE $database; " + ($comparison -join ' UNION ALL ') + ';') 'migration data preservation').Trim() -split '\r?\n'
    [IO.File]::WriteAllLines((Join-Path $evidence 'migration-data-check.tsv'), $rows, $utf8)
    foreach ($row in $rows) {
        $metric, $value = $row -split "`t", 2
        if ($value -ne '0') { throw "Migration data preservation failed for $metric." }
    }
    Write-Evidence "Migration preserved $beforeCount users across $($originalColumns.Count) original columns; new fields are NULL/NULL/0."
    if ($Suite -eq 'PrivateChat') {
        $chatMigration = [IO.File]::ReadAllText((Join-Path $backend 'src\main\resources\db\migrations\2026-10-11-private-chat.sql'),$utf8)
        Assert-DatabaseScope $chatMigration
        [void](Invoke-OwnedSql ("USE $database;`n" + $chatMigration) 'checked-in private chat migration')
    }
    [void](Invoke-OwnedSql ("USE $database; CREATE TABLE $guardTable (nonce VARCHAR(64) NOT NULL); " +
        "INSERT INTO $guardTable(nonce) VALUES('$nonce');") 'test ownership guard')

    [Environment]::SetEnvironmentVariable(($envPrefix+'_MYSQL_URL'),("jdbc:mysql://127.0.0.1:$Port/$database" + '?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'),'Process')
    [Environment]::SetEnvironmentVariable(($envPrefix+'_MYSQL_USER'),'root','Process')
    [Environment]::SetEnvironmentVariable(($envPrefix+'_MYSQL_PASSWORD'),'','Process')
    [Environment]::SetEnvironmentVariable(($envPrefix+'_MYSQL_NONCE'),$nonce,'Process')
    if ($BrowserE2E) {
        Write-Evidence ("Running real backend/browser $Suite E2E with this isolated database.")
        Push-Location $root
        try {
            # Invoke the UTF-8 source without changing PowerShell execution policy.
            $browserRunnerPath = Join-Path $root $(if ($Suite -eq 'PrivateChat') { 'scripts\run-private-chat-e2e.ps1' } else { 'scripts\run-account-recovery-e2e.ps1' })
            $browserRunner = [scriptblock]::Create([IO.File]::ReadAllText($browserRunnerPath, $utf8))
            & $browserRunner -ProjectRoot $root -EvidencePath $evidence -Nonce $nonce -NodePath $NodePath -MavenPath $maven
        }
        finally { Pop-Location }
        Write-Evidence ("Real backend/browser $Suite E2E acceptance passed.")
    }
    else {
    $reports = Join-Path $evidence 'junit'
    [void](New-Item -ItemType Directory -Path $reports)
    # Surefire 3.2.5 reportsDirectory has no user-property mapping in plugin.xml.
    # Use its real default and remove only this class's old report before running.
    foreach ($class in $testClasses) {
        $reportName = 'TEST-' + $class + '.xml'
        $freshReport = Join-Path (Join-Path $target 'surefire-reports') $reportName
        Assert-TargetFile $freshReport
        if (Test-Path -LiteralPath $freshReport -PathType Leaf) {
            Copy-Item -LiteralPath $freshReport -Destination (Join-Path $reports ('previous-' + $reportName))
            Remove-Item -LiteralPath $freshReport -Force
        }
    }
    $mavenLog = Join-Path $evidence 'maven.log'
    $mavenStartedUtc = [DateTime]::UtcNow
    Write-Evidence ('Running ' + $Suite + ' with this isolated database.')
    Push-Location $backend
    $mavenErrorPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 wraps native stderr as ErrorRecord objects.
        # Capture those in the log and decide success from Maven/JUnit results.
        $ErrorActionPreference = 'Continue'
        & $maven ('-Dtest=' + (($testClasses | ForEach-Object { ($_ -split '\.')[-1] }) -join ',')) 'test' 2>&1 |
            ForEach-Object { [IO.File]::AppendAllText($mavenLog, [string]$_ + [Environment]::NewLine, $utf8) }
        $mavenExit = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $mavenErrorPreference
        Pop-Location
    }
    if ($mavenExit -ne 0) { throw "AccountRecoveryMySqlIT Maven invocation failed (exit $mavenExit); see maven.log." }
    $testCount = 0
    $failedCount = 0
    $skippedCount = 0
    foreach ($class in $testClasses) {
        $reportName = 'TEST-' + $class + '.xml'
        $freshReport = Join-Path (Join-Path $target 'surefire-reports') $reportName
        if (-not (Test-Path -LiteralPath $freshReport -PathType Leaf) -or (Get-Item -LiteralPath $freshReport).LastWriteTimeUtc -lt $mavenStartedUtc.AddSeconds(-2)) { throw 'Maven did not create a fresh JUnit report.' }
        Assert-TargetFile $freshReport
        Copy-Item -LiteralPath $freshReport -Destination (Join-Path $reports $reportName)
        [xml]$report = [IO.File]::ReadAllText($freshReport,$utf8)
        foreach ($testSuiteXml in $report.SelectNodes('//testsuite')) {
            if ($testSuiteXml.GetAttribute('name') -cne $class) { throw 'Unexpected JUnit suite.' }
            $testCount += [int]$testSuiteXml.GetAttribute('tests')
            $failedCount += [int]$testSuiteXml.GetAttribute('failures') + [int]$testSuiteXml.GetAttribute('errors')
            $skippedCount += [int]$testSuiteXml.GetAttribute('skipped')
        }
    }
    if ($testCount -lt 1 -or $failedCount -ne 0 -or $skippedCount -ne 0) {
        throw "JUnit acceptance failed: tests=$testCount failures/errors=$failedCount skipped=$skippedCount."
    }
    Write-Evidence "JUnit acceptance passed: tests=$testCount failures/errors=0 skipped=0."
    }
}
catch {
    $primaryError = $_
    if (Test-Path -LiteralPath $evidence -PathType Container) { Write-Evidence ("FAILED: " + $_.Exception.Message) }
}
finally {
    $actualServer = $ownedProcessState.Process
    $actualServerStarted = $ownedProcessState.Started
    # Never send DROP/SHUTDOWN to a listener whose datadir cannot be proved owned.
    if ($null -ne $server) {
        try {
            Assert-ServerIdentity
            if ($databaseCreated) {
                [void](Invoke-OwnedSql "DROP DATABASE IF EXISTS $database;" 'disposable database cleanup')
            }
            [void](Invoke-MySql 'SHUTDOWN;' 'temporary instance shutdown')
            if ($null -ne $actualServer -and -not $actualServer.WaitForExit(10000)) {
                Stop-OwnedProcess $actualServer $actualServerStarted
            }
            if (-not $server.WaitForExit(10000)) { Stop-OwnedProcess $server $serverStarted }
        }
        catch { $cleanupErrors.Add('Database/shutdown cleanup: ' + $_.Exception.Message) }
        try {
            if ($null -ne $actualServer -and -not $actualServer.HasExited) {
                if (-not $actualServer.WaitForExit(10000)) { Stop-OwnedProcess $actualServer $actualServerStarted }
            }
            if (-not $server.HasExited) {
                if (-not $server.WaitForExit(10000)) { Stop-OwnedProcess $server $serverStarted }
            }
            if (Test-Path -LiteralPath $pidPath -PathType Leaf) {
                $pidText = [IO.File]::ReadAllText($pidPath, $utf8).Trim()
                if ($null -eq $actualServer -or $pidText -ne [string]$actualServer.Id) {
                    throw 'Keeping temporary data because its pid file cannot be matched to the captured server.'
                }
            }
        }
        catch { $cleanupErrors.Add('Process cleanup: ' + $_.Exception.Message) }
    }
    # An exception while reading the first log must not leave an untracked child
    # writing into a directory that cleanup then removes.
    if ($null -ne $initialize -and -not $initializePidSeen) {
        try {
            if (-not $initialize.HasExited) { [void]$initialize.WaitForExit(10000) }
            Assert-OwnedDirectory
            $initializeLog = Read-SharedUtf8File (Join-Path $runDirectory 'initialize.stderr.log')
            $initializeMatch = [regex]::Match($initializeLog, 'initializing of server in progress as process (\d+)')
            if (-not $initializeMatch.Success) {
                throw 'Keeping temporary data because the actual initializer could not be identified.'
            }
            $initializeCandidate = Get-Process -Id ([int]$initializeMatch.Groups[1].Value) -ErrorAction SilentlyContinue
            if ($null -ne $initializeCandidate) {
                [void]$initializeCandidate.Handle
                $candidateStarted = $initializeCandidate.StartTime
                if (-not [IO.Path]::GetFullPath($initializeCandidate.MainModule.FileName).Equals($mysqld, [StringComparison]::OrdinalIgnoreCase) -or
                        $candidateStarted.ToUniversalTime() -lt $initializeStarted.ToUniversalTime().AddSeconds(-2)) {
                    $initializeCandidate.Dispose()
                    throw 'Keeping temporary data because the initializer process identity is uncertain.'
                }
                $initializeActual = $initializeCandidate
                $initializeActualStarted = $candidateStarted
            }
            $initializePidSeen = $true
        }
        catch { $cleanupErrors.Add('Initializer identification: ' + $_.Exception.Message) }
    }
    if ($null -ne $initialize -and -not $initialize.HasExited) {
        try {
            if (-not $initialize.WaitForExit(10000)) { Stop-OwnedProcess $initialize $initializeStarted }
        }
        catch { $cleanupErrors.Add('Initializer cleanup: ' + $_.Exception.Message) }
    }
    if ($null -ne $initializeActual -and -not $initializeActual.HasExited) {
        try {
            if (-not $initializeActual.WaitForExit(10000)) { Stop-OwnedProcess $initializeActual $initializeActualStarted }
        }
        catch { $cleanupErrors.Add('Actual initializer cleanup: ' + $_.Exception.Message) }
    }
    if ($null -ne $runDirectory -and (Test-Path -LiteralPath $runDirectory -PathType Container)) {
        try {
            Assert-OwnedDirectory
            if (Test-Path -LiteralPath $evidence -PathType Container) {
                foreach ($log in Get-ChildItem -LiteralPath $runDirectory -Filter '*.log' -File) {
                    Copy-Item -LiteralPath $log.FullName -Destination (Join-Path $evidence $log.Name)
                }
            }
            # A remaining verified process must be stopped before deleting its files.
            if ($cleanupErrors.Count -gt 0 -or ($null -ne $server -and -not $server.HasExited) -or
                    ($null -ne $actualServer -and -not $actualServer.HasExited) -or
                    ($null -ne $initialize -and -not $initialize.HasExited) -or
                    ($null -ne $initializeActual -and -not $initializeActual.HasExited)) {
                throw 'Keeping temporary data because server cleanup could not be fully verified.'
            }
            # The PowerShell provider can refuse the otherwise owned Windows TEMP
            # root after all children are removed. Use the checked absolute path.
            [IO.Directory]::Delete($runDirectory, $true)
        }
        catch { $cleanupErrors.Add('Directory cleanup: ' + $_.Exception.Message) }
    }
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    $OutputEncoding = $previousOutputEncoding
    [Console]::OutputEncoding = $previousConsoleEncoding
    if ($null -ne $server) { $server.Dispose() }
    if ($null -ne $actualServer) { $actualServer.Dispose() }
    if ($null -ne $initialize) { $initialize.Dispose() }
    if ($null -ne $initializeActual) { $initializeActual.Dispose() }
}

if ($cleanupErrors.Count -gt 0) {
    $message = 'Cleanup requires attention: ' + ($cleanupErrors -join ' | ')
    if (Test-Path -LiteralPath $evidence -PathType Container) { Write-Evidence $message }
    if ($null -eq $primaryError) { throw $message }
}
if ($null -ne $primaryError) { throw $primaryError }
Write-Evidence "Acceptance complete. Evidence retained at $evidence"
