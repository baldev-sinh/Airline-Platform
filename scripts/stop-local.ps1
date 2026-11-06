<#
.SYNOPSIS
  Stops the services started by scripts/start-local.ps1. Databases and Kafka keep running; stop them with
  `docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env down`.
#>
$root = Split-Path $PSScriptRoot -Parent
$pidFile = Join-Path $root '.local-run/pids.txt'
if (-not (Test-Path $pidFile)) {
    Write-Host 'Nothing to stop (no .local-run/pids.txt).'
    exit 0
}
foreach ($line in Get-Content $pidFile) {
    $id, $name = $line -split ' ', 2
    try {
        Stop-Process -Id ([int]$id) -ErrorAction Stop
        Write-Host "  stopped $name (pid $id)"
    } catch {
        Write-Host "  $name (pid $id) was not running"
    }
}
Remove-Item $pidFile
