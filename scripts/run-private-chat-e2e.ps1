[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot,
    [Parameter(Mandatory = $true)]
    [string]$EvidencePath,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-f0-9]{32}$')]
    [string]$Nonce,
    [string]$NodePath = (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'),
    [string]$MavenPath = (Join-Path $env:USERPROFILE '.m2\maven-home\bin\mvn.cmd')
)

# Called by verify-account-recovery-mysql.ps1 after import, migration and ownership
# checks. This script owns only its captured Java/Node processes, never the database.
Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$root = [IO.Path]::GetFullPath($ProjectRoot)
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$target = Join-Path $backend 'target'
$evidence = [IO.Path]::GetFullPath($EvidencePath)
$node = [IO.Path]::GetFullPath($NodePath)
$maven = [IO.Path]::GetFullPath($MavenPath)
$coordinatorPath = Join-Path $evidence 'e2e-coordinator.json'
$classpathPath = Join-Path $evidence 'e2e-classpath.txt'
$summaryPath = Join-Path (Join-Path $frontend 'output\playwright\private-chat-e2e') (Join-Path $Nonce 'summary.json')
$browser = $null
$application = $null
$primaryError = $null
$cleanupErrors = New-Object 'System.Collections.Generic.List[string]'
$ownedPrograms = New-Object 'System.Collections.Generic.List[object]'

function Assert-EvidenceDirectory {
    $prefix = [IO.Path]::GetFullPath($target).TrimEnd('\') + '\'
    if (-not $evidence.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $evidence -PathType Container)) {
        throw 'E2E evidence must be an existing directory within backend/target.'
    }
    $directory = $evidence
    while ($directory.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        $item = Get-Item -LiteralPath $directory
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'E2E evidence must not pass through a reparse point.'
        }
        $directory = [IO.Path]::GetDirectoryName($directory)
    }
}

function ConvertTo-NativeArgument([string]$Value) {
    if ($Value -match '[\r\n"]') { throw 'Unexpected character in a native process argument.' }
    # All supplied arguments are paths or fixed switches. Double a trailing slash
    # so the closing quote is not consumed by Windows command-line parsing.
    return '"' + [regex]::Replace($Value, '(\\+)$', '$1$1') + '"'
}

function Start-OwnedProgram([string]$FilePath, [string[]]$Arguments,
        [string]$WorkingDirectory, [string]$LogName, [hashtable]$EnvironmentOverrides,
        [switch]$InputPipe) {
    Assert-EvidenceDirectory
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $FilePath
    $start.Arguments = (($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' ')
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.RedirectStandardInput = [bool]$InputPipe
    $start.StandardOutputEncoding = $utf8
    $start.StandardErrorEncoding = $utf8
    foreach ($name in $EnvironmentOverrides.Keys) {
        $start.EnvironmentVariables[$name] = [string]$EnvironmentOverrides[$name]
    }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    $record = $null
    try {
        [void]$process.Start()
        [void]$process.Handle
        $record = [pscustomobject]@{
            Process = $process
            Started = $process.StartTime
            FilePath = [IO.Path]::GetFullPath($FilePath)
            OutputTask = $process.StandardOutput.ReadToEndAsync()
            ErrorTask = $process.StandardError.ReadToEndAsync()
            OutputPath = Join-Path $evidence ($LogName + '.stdout.log')
            ErrorPath = Join-Path $evidence ($LogName + '.stderr.log')
            LogsWritten = $false
        }
        $ownedPrograms.Add($record)
        [IO.File]::AppendAllText((Join-Path $evidence 'e2e-processes.log'),
            ('started label=' + $LogName + '; PID=' + $process.Id + '; StartTime=' +
                $record.Started.ToUniversalTime().ToString('o') + '; path=' + $record.FilePath + [Environment]::NewLine), $utf8)
        [IO.File]::WriteAllText($record.OutputPath, '', $utf8)
        [IO.File]::WriteAllText($record.ErrorPath, '', $utf8)
        return $record
    }
    catch {
        if ($null -eq $record) { $process.Dispose() }
        throw
    }
}

function Save-OwnedProgramLogs($Record) {
    if ($Record.LogsWritten) { return }
    if (-not $Record.Process.HasExited) { throw 'Cannot collect logs while the owned process is running.' }
    if (-not $Record.OutputTask.Wait(10000) -or -not $Record.ErrorTask.Wait(10000)) {
        throw 'Owned process output did not close within 10 seconds.'
    }
    Assert-EvidenceDirectory
    [IO.File]::WriteAllText($Record.OutputPath, $Record.OutputTask.GetAwaiter().GetResult(), $utf8)
    [IO.File]::WriteAllText($Record.ErrorPath, $Record.ErrorTask.GetAwaiter().GetResult(), $utf8)
    $Record.LogsWritten = $true
}

function Wait-OwnedProgram($Record, [int]$Seconds, [string]$Label, $BackendProcess) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while (-not $Record.Process.WaitForExit(500)) {
        if ($null -ne $BackendProcess -and $BackendProcess.Process.HasExited) {
            throw 'E2E backend exited before browser verification completed; see e2e-backend logs.'
        }
        if ([DateTime]::UtcNow -ge $deadline) { throw "$Label timed out after $Seconds seconds." }
    }
    Save-OwnedProgramLogs $Record
    return [int]$Record.Process.ExitCode
}

function Stop-OwnedProgram($Record) {
    $process = $Record.Process
    if ($process.HasExited) { return }
    if ($process.StartTime -ne $Record.Started -or
            -not [IO.Path]::GetFullPath($process.MainModule.FileName).Equals($Record.FilePath, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to stop a process whose captured path/start time does not match.'
    }
    # Captured process handle protects against pid reuse; no global enumeration.
    try { $process.Kill() }
    catch { if (-not $process.HasExited) { throw } }
    if (-not $process.WaitForExit(10000)) { throw 'Owned E2E process did not stop within 10 seconds.' }
}

try {
    Assert-EvidenceDirectory
    if ([Environment]::GetEnvironmentVariable('COURSEHUB_CHAT_MYSQL_NONCE', 'Process') -cne $Nonce -or
            [Environment]::GetEnvironmentVariable('COURSEHUB_CHAT_MYSQL_USER', 'Process') -cne 'root' -or
            -not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable('COURSEHUB_CHAT_MYSQL_PASSWORD', 'Process')) -or
            [Environment]::GetEnvironmentVariable('COURSEHUB_CHAT_MYSQL_URL', 'Process') -notmatch
                '^jdbc:mysql://127\.0\.0\.1:[0-9]{4,5}/coursehub_chat_check_[a-f0-9]{32}(?:\?.*)?$') {
        throw 'E2E runner requires the parent disposable MySQL environment and matching nonce.'
    }
    if ([IO.Path]::GetExtension($maven) -ine '.cmd') { throw 'MavenPath must point to mvn.cmd.' }
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    if ([string]::IsNullOrWhiteSpace($javaHome) -or -not [IO.Path]::IsPathRooted($javaHome)) {
        throw 'An absolute JAVA_HOME containing Java 21 is required.'
    }
    $java = [IO.Path]::GetFullPath((Join-Path $javaHome 'bin\java.exe'))
    $vite = Join-Path $frontend 'node_modules\vite\bin\vite.js'
    $browserScript = Join-Path $frontend 'scripts\private-chat-e2e.mjs'
    foreach ($path in @($node, $maven, $java, $vite, $browserScript)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required E2E file is missing: $path" }
    }
    if (Test-Path -LiteralPath $coordinatorPath) { throw 'Refusing to reuse an existing E2E coordinator.' }
    if (Test-Path -LiteralPath $classpathPath) { throw 'Refusing to reuse an existing E2E classpath report.' }
    if (Test-Path -LiteralPath (Split-Path -Parent $summaryPath)) { throw 'Refusing to reuse an existing browser evidence directory.' }

    $version = Start-OwnedProgram $java @('-version') $root 'e2e-java-version' @{}
    if ((Wait-OwnedProgram $version 15 'Java version verification' $null) -ne 0) {
        throw 'Java version verification failed; see e2e-java-version logs.'
    }
    $versionText = [IO.File]::ReadAllText($version.OutputPath, $utf8) + [IO.File]::ReadAllText($version.ErrorPath, $utf8)
    if ($versionText -notmatch '\bversion\s+"21(?:\.|["+\-])') { throw 'JAVA_HOME must identify Java 21.' }

    Write-Host 'Building the frontend with the bundled Node runtime and existing dependencies.'
    $buildEnvironment = @{ VITE_API_BASE_URL = '/api'; VITE_USE_MOCK = 'false' }
    $build = Start-OwnedProgram $node @($vite, 'build') $frontend 'e2e-frontend-build' $buildEnvironment
    if ((Wait-OwnedProgram $build 120 'Frontend build' $null) -ne 0) {
        throw 'Frontend build failed; see e2e-frontend-build logs.'
    }

    $mavenLog = Join-Path $evidence 'e2e-maven.log'
    $mavenStartedUtc = [DateTime]::UtcNow
    $mavenPreference = $ErrorActionPreference
    Push-Location $backend
    try {
        $ErrorActionPreference = 'Continue'
        & $maven 'test-compile' 'dependency:build-classpath' '-Dmdep.includeScope=test' `
            ('-Dmdep.outputFile=' + $classpathPath) 2>&1 |
            ForEach-Object { [IO.File]::AppendAllText($mavenLog, [string]$_ + [Environment]::NewLine, $utf8) }
        $mavenExit = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $mavenPreference
        Pop-Location
    }
    if ($mavenExit -ne 0) { throw "E2E test-classpath compilation failed (exit $mavenExit); see e2e-maven.log." }
    if (-not (Test-Path -LiteralPath $classpathPath -PathType Leaf) -or
            (Get-Item -LiteralPath $classpathPath).LastWriteTimeUtc -lt $mavenStartedUtc.AddSeconds(-2)) {
        throw 'Maven did not create a fresh E2E dependency classpath.'
    }
    $dependencies = [IO.File]::ReadAllText($classpathPath, $utf8).Trim()
    if ([string]::IsNullOrWhiteSpace($dependencies) -or $dependencies -match '[\r\n]') {
        throw 'Maven E2E classpath is missing or invalid.'
    }
    $applicationClass = Join-Path $target 'test-classes\com\bugaoshan\chat\PrivateChatE2eApplication.class'
    if (-not (Test-Path -LiteralPath $applicationClass -PathType Leaf)) {
        throw 'PrivateChatE2eApplication was not compiled into the test classpath.'
    }
    # Other test configurations must never enter component scanning. Copy only the
    # annotation-free launcher, leaving target/test-classes outside the runtime CP.
    $launcherDirectory = Join-Path $evidence 'launcher-classes'
    $launcherPackage = Join-Path $launcherDirectory 'com\bugaoshan\chat'
    [void](New-Item -ItemType Directory -Path $launcherPackage)
    Copy-Item -LiteralPath $applicationClass -Destination (Join-Path $launcherPackage 'PrivateChatE2eApplication.class')
    $classpath = $launcherDirectory + ';' + (Join-Path $target 'classes') + ';' + $dependencies
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        $apiPort = $listener.LocalEndpoint.Port
    }
    finally { $listener.Stop() }

    $browserStartedUtc = [DateTime]::UtcNow
    $browserEnvironment = @{
        COURSEHUB_CHAT_E2E_API = 'http://127.0.0.1:' + $apiPort
        COURSEHUB_CHAT_E2E_NONCE = $Nonce
        COURSEHUB_CHAT_E2E_COORDINATOR = $coordinatorPath
    }
    $browser = Start-OwnedProgram $node @($browserScript) $frontend 'e2e-browser' $browserEnvironment -InputPipe
    $coordinatorDeadline = [DateTime]::UtcNow.AddSeconds(60)
    $coordinator = $null
    while ($null -eq $coordinator) {
        if (Test-Path -LiteralPath $coordinatorPath -PathType Leaf) {
            # Exclusive creation proves ownership; allow the brief interval between
            # file creation and completion of the Node write before parsing JSON.
            try { $coordinator = [IO.File]::ReadAllText($coordinatorPath, $utf8) | ConvertFrom-Json }
            catch [ArgumentException] { $coordinator = $null }
            if ($null -ne $coordinator) { break }
        }
        if ($browser.Process.HasExited) { throw 'Browser runner exited before publishing its local coordinator.' }
        if ([DateTime]::UtcNow -ge $coordinatorDeadline) { throw 'Browser coordinator was not created within 60 seconds.' }
        Start-Sleep -Milliseconds 100
    }
    if ($coordinator.nonce -cne $Nonce -or (Get-Item -LiteralPath $coordinatorPath).LastWriteTimeUtc -lt $browserStartedUtc.AddSeconds(-2)) { throw 'Invalid browser coordinator nonce or creation time.' }
    $applicationEnvironment = @{
        COURSEHUB_CHAT_MYSQL_URL = [Environment]::GetEnvironmentVariable('COURSEHUB_CHAT_MYSQL_URL', 'Process')
        COURSEHUB_CHAT_MYSQL_USER = 'root'
        COURSEHUB_CHAT_MYSQL_PASSWORD = ''
        COURSEHUB_CHAT_MYSQL_NONCE = $Nonce
        COURSEHUB_CHAT_E2E_PORT = [string]$apiPort
        COURSEHUB_CHAT_E2E_EVIDENCE_PATH = $evidence
    }
    $application = Start-OwnedProgram $java @('-cp', $classpath, 'com.bugaoshan.chat.PrivateChatE2eApplication') `
        $backend 'e2e-backend' $applicationEnvironment
    Write-Host "Running two real browser sessions with local backend port=$apiPort without SMTP."
    $remainingSeconds = [Math]::Max(1, [int][Math]::Ceiling(($browserStartedUtc.AddSeconds(240) - [DateTime]::UtcNow).TotalSeconds))
    $browserExit = Wait-OwnedProgram $browser $remainingSeconds 'Real private chat browser E2E' $application
    if ($browserExit -ne 0) { throw "Real private chat browser E2E failed (exit $browserExit); see e2e-browser logs and summary." }
    if (-not (Test-Path -LiteralPath $summaryPath -PathType Leaf) -or
            (Get-Item -LiteralPath $summaryPath).LastWriteTimeUtc -lt $browserStartedUtc.AddSeconds(-2)) {
        throw 'Browser runner did not create a fresh E2E summary.'
    }
    $summary = [IO.File]::ReadAllText($summaryPath, $utf8) | ConvertFrom-Json
    if ($summary.nonce -cne $Nonce -or $summary.passed -isnot [bool] -or $summary.passed -ne $true -or
            @($summary.checks).Count -lt 1) {
        throw 'Browser E2E summary does not prove this run passed.'
    }
    foreach ($check in @($summary.checks)) {
        if ([string]::IsNullOrWhiteSpace([string]$check.name) -or $check.passed -isnot [bool] -or $check.passed -ne $true) {
            throw 'Browser E2E summary contains a failed or invalid check.'
        }
    }
    Copy-Item -LiteralPath $summaryPath -Destination (Join-Path $evidence 'e2e-summary.json')
    Write-Host "Browser E2E checks passed: $(@($summary.checks).Count). Evidence: $evidence"
}
catch { $primaryError = $_ }
finally {
    # Request the browser script's own finally cleanup first, including its SMTP,
    # Vite and Playwright resources, before a captured-handle fallback is used.
    if ($null -ne $browser -and -not $browser.Process.HasExited) {
        try {
            $browser.Process.StandardInput.WriteLine('shutdown')
            $browser.Process.StandardInput.Flush()
            [void]$browser.Process.WaitForExit(10000)
        }
        catch { if (-not $browser.Process.HasExited) { $cleanupErrors.Add('Browser shutdown request: ' + $_.Exception.Message) } }
    }
    foreach ($record in $ownedPrograms) {
        try {
            Stop-OwnedProgram $record
            [IO.File]::AppendAllText((Join-Path $evidence 'e2e-processes.log'),
                ('stopped PID=' + $record.Process.Id + [Environment]::NewLine), $utf8)
            Save-OwnedProgramLogs $record
        }
        catch { $cleanupErrors.Add('Owned process cleanup: ' + $_.Exception.Message) }
        finally { $record.Process.Dispose() }
    }
}

if ($cleanupErrors.Count -gt 0) {
    $message = 'E2E process cleanup requires attention: ' + ($cleanupErrors -join ' | ')
    if ($null -eq $primaryError) { throw $message }
    Write-Warning $message
}
if ($null -ne $primaryError) { throw $primaryError }
