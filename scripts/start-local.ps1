<#
.SYNOPSIS
  Starts the registry, every business service and the gateway on this machine with the `local` profile.

.DESCRIPTION
  Prerequisites:
    1. Databases and Kafka running:
         docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env `
           up -d userdb airlinecoredb flightopsdb locationdb pricingdb ancillarydb bookingdb paymentdb seatdb kafka
    2. Jars built:  ./mvnw -B -DskipTests package

  By default payment-service runs with the offline `payment-stub` profile, so no Razorpay account is needed.
  Pass -RealPayments to use Razorpay test mode instead (set RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET and
  RAZORPAY_WEBHOOK_SECRET in your environment first).

  Logs go to .local-run/<service>.log. Stop everything with scripts/stop-local.ps1.
#>
param(
    [switch]$RealPayments,
    [int]$TimeoutSeconds = 300,
    [int]$BatchSize = 3
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-run-common.ps1')
$root = Split-Path $PSScriptRoot -Parent
$runDir = Join-Path $root '.local-run'
New-Item -ItemType Directory -Force $runDir | Out-Null
$pidFile = Get-PidFilePath $root

# A pids.txt left behind by a reboot or a crash is harmless; only refuse to start if our services still run.
if (Test-Path $pidFile) {
    $running = @(Get-Content $pidFile | Where-Object { Get-TrackedProcess $_ } | ForEach-Object { Get-TrackedServiceName $_ })
    if ($running.Count -gt 0) {
        Write-Host "Services from a previous run are still running: $($running -join ', ')." -ForegroundColor Yellow
        Write-Host 'Stop them first with scripts/stop-local.ps1.' -ForegroundColor Yellow
        exit 1
    }
    Write-Host 'Removing pids.txt left over from a previous run (none of its services is running).'
    Remove-Item $pidFile
}

# name, jar folder, port, extra profiles
$services = @(
    @{ Name = 'service-registry';     Dir = 'cloud/service-registry';         Port = 8761; Profiles = '' },
    @{ Name = 'user-service';         Dir = 'services/user-service';          Port = 5001; Profiles = 'local' },
    @{ Name = 'airline-core-service'; Dir = 'services/airline-core-service';  Port = 5002; Profiles = 'local' },
    @{ Name = 'location-service';     Dir = 'services/location-service';      Port = 5004; Profiles = 'local' },
    @{ Name = 'pricing-service';      Dir = 'services/pricing-service';       Port = 5005; Profiles = 'local' },
    @{ Name = 'seat-service';         Dir = 'services/seat-service';          Port = 5006; Profiles = 'local' },
    @{ Name = 'ancillary-service';    Dir = 'services/ancillary-service';     Port = 5008; Profiles = 'local' },
    @{ Name = 'booking-service';      Dir = 'services/booking-service';       Port = 5009; Profiles = 'local' },
    @{ Name = 'payment-service';      Dir = 'services/payment-service';       Port = 5010; Profiles = $(if ($RealPayments) { 'local' } else { 'local,payment-stub' }) },
    @{ Name = 'flight-ops-service';   Dir = 'services/flight-ops-service';    Port = 5011; Profiles = 'local' },
    @{ Name = 'api-gateway';          Dir = 'cloud/api-gateway';              Port = 5100; Profiles = 'local' }  # 5100 = management port; API on 5000
)

function Wait-Ready($name, $port) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$port/actuator/health"
            if ($r.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "$name did not become healthy on port $port within $TimeoutSeconds s. See .local-run/$name.log"
}

function Start-One($svc) {
    $jar = Get-ChildItem (Join-Path $root "$($svc.Dir)/target") -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
    if (-not $jar) { throw "No jar in $($svc.Dir)/target. Build first: ./mvnw -B -DskipTests package" }
    $log = Join-Path $runDir "$($svc.Name).log"
    $javaArgs = @('-Xmx384m', '-jar', $jar.FullName)
    if ($svc.Profiles) { $javaArgs = @("-Dspring.profiles.active=$($svc.Profiles)") + $javaArgs }
    $p = Start-Process java -ArgumentList $javaArgs -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -WindowStyle Hidden -PassThru
    Add-Content $pidFile (Format-PidLine $p $svc.Name)
    Write-Host ("  started {0,-22} pid {1,-6} port {2}  profiles: {3}" -f $svc.Name, $p.Id, $svc.Port, $svc.Profiles)
}

Write-Host 'Starting service registry...'
Start-One $services[0]
Wait-Ready $services[0].Name $services[0].Port

# Start in small batches: nine JVMs booting at once can starve the CPU and time out database connections.
Write-Host 'Starting business services...'
$business = $services[1..($services.Count - 2)]
for ($i = 0; $i -lt $business.Count; $i += $BatchSize) {
    $batch = $business[$i..([Math]::Min($i + $BatchSize, $business.Count) - 1)]
    foreach ($svc in $batch) { Start-One $svc }
    foreach ($svc in $batch) { Wait-Ready $svc.Name $svc.Port; Write-Host "  ready   $($svc.Name)" -ForegroundColor Green }
}

Write-Host 'Starting API gateway...'
Start-One $services[-1]
Wait-Ready $services[-1].Name $services[-1].Port

# The gateway needs every service in its Eureka cache before routing works; the first refresh can take ~30 s.
Write-Host 'Waiting for the gateway to discover services...'
$deadline = (Get-Date).AddSeconds(90)
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://localhost:5000/api/airlines/dropdown'
        if ($r.StatusCode -eq 200) { break }
    } catch { }
    Start-Sleep -Seconds 3
}

Write-Host ''
Write-Host 'All services are up. API gateway: http://localhost:5000   Eureka: http://localhost:8761' -ForegroundColor Green
if (-not $RealPayments) {
    Write-Host 'Payments: OFFLINE STUB (no money moves). Webhook secret: local-stub-webhook-secret' -ForegroundColor Yellow
}
