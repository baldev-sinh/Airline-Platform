<#
.SYNOPSIS
  Stops the services started by scripts/start-local.ps1. Databases and Kafka keep running; stop them with
  `docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env down`.

.DESCRIPTION
  Only stops a recorded PID if it is still the java process start-local.ps1 started (same start time). After a
  reboot Windows may have given that PID to another program, which is left alone.
#>
. (Join-Path $PSScriptRoot 'local-run-common.ps1')
$root = Split-Path $PSScriptRoot -Parent
$pidFile = Get-PidFilePath $root
if (-not (Test-Path $pidFile)) {
    Write-Host 'Nothing to stop (no .local-run/pids.txt).'
    exit 0
}
foreach ($line in Get-Content $pidFile) {
    if (-not $line.Trim()) { continue }
    $name = Get-TrackedServiceName $line
    $process = Get-TrackedProcess $line
    if (-not $process) {
        Write-Host "  $name was not running"
        continue
    }
    try {
        Stop-Process -Id $process.Id -ErrorAction Stop
        Write-Host "  stopped $name (pid $($process.Id))"
    } catch {
        Write-Host "  could not stop $name (pid $($process.Id)): $($_.Exception.Message)" -ForegroundColor Yellow
    }
}
Remove-Item $pidFile
