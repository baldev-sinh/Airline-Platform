<#
.SYNOPSIS
  Shared helpers for start-local.ps1 and stop-local.ps1. Dot-source it; do not run it directly.

.DESCRIPTION
  .local-run/pids.txt has one line per started service: "<pid> <service-name> <start-time-utc-ticks>".
  A PID alone is not enough to find our process again: after a reboot or a crash Windows reuses PIDs, so a
  recorded PID may now belong to an unrelated program. A line only matches a live process when that process is
  java AND started at the recorded time.
#>

function Get-PidFilePath([string]$root) {
    Join-Path $root '.local-run/pids.txt'
}

function Format-PidLine([System.Diagnostics.Process]$process, [string]$name) {
    "{0} {1} {2}" -f $process.Id, $name, $process.StartTime.ToUniversalTime().Ticks
}

<#
  Returns the live process a pids.txt line refers to, or $null if that process has exited or the PID now
  belongs to something else. Lines written before start times were recorded ("<pid> <name>") are matched on
  the process name plus the service's jar on its command line.
#>
function Get-TrackedProcess([string]$line) {
    $parts = $line.Trim() -split '\s+'
    if ($parts.Count -lt 2) { return $null }

    $processId = 0
    if (-not [int]::TryParse($parts[0], [ref]$processId)) { return $null }

    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process -or $process.ProcessName -ne 'java') { return $null }

    if ($parts.Count -ge 3) {
        $recordedTicks = 0L
        if (-not [long]::TryParse($parts[2], [ref]$recordedTicks)) { return $null }
        try {
            if ($process.StartTime.ToUniversalTime().Ticks -ne $recordedTicks) { return $null }
        } catch {
            # StartTime is unreadable for processes we do not own, so it cannot be ours.
            return $null
        }
        return $process
    }

    # Older line without a start time: many tools run as java (IDE, Maven, Gradle), so also require the
    # service's jar on the command line, e.g. "...\service-registry-0.0.1-SNAPSHOT.jar".
    $commandLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue).CommandLine
    if (-not $commandLine -or $commandLine -notlike "*$($parts[1])*.jar*") { return $null }
    return $process
}

function Get-TrackedServiceName([string]$line) {
    $parts = $line.Trim() -split '\s+'
    if ($parts.Count -ge 2) { return $parts[1] }
    return '<unknown>'
}
