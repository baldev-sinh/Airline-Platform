<#
.SYNOPSIS
  Starts the config server, the registry, every business service and the gateway on this machine with the
  `local` profile.

.DESCRIPTION
  Prerequisites:
    1. Databases and Kafka running:
         docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env `
           up -d userdb airlinecoredb flightopsdb locationdb pricingdb ancillarydb bookingdb paymentdb seatdb notificationdb `
           kafka kafka-ui redis mailpit prometheus grafana
    2. Jars built:  ./mvnw -B -DskipTests package

  By default payment-service runs with the offline `payment-stub` profile, so no Razorpay account is needed.
  Pass -RealPayments to use Razorpay test mode instead (keys from the environment or docker-compose/.env).

  Pass -Tunnel as well to start an ngrok tunnel on your permanent ngrok address, so Razorpay webhooks reach
  this machine at the same URL every time (NGROK_AUTHTOKEN and NGROK_DOMAIN in docker-compose/.env).
  See docs/RAZORPAY_TEST_MODE.md.

  Configuration comes from config-repo/ through config-server (started first). REDIS_PASSWORD is read from
  docker-compose/.env. E-mails land in Mailpit: http://localhost:8025.

  Logs go to .local-run/<service>.log. Stop everything with scripts/stop-local.ps1.

.EXAMPLE
  ./scripts/start-local.ps1 -RealPayments -Tunnel
#>
param(
    [switch]$RealPayments,
    [switch]$Tunnel,
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

# Razorpay test mode: take the keys from the environment, or else from docker-compose/.env (git-ignored).
# Refuse to start with missing, placeholder or LIVE keys: a local run must never be able to take real money,
# and a placeholder would otherwise only fail later, when someone tries to pay.
$dotEnv = Read-DotEnv (Join-Path $root 'docker-compose/.env')

# Every service reads its configuration from config-server and uses Redis (cache, token revocations).
$env:CONFIG_REPO_LOCATION = 'file:' + ((Join-Path $root 'config-repo') -replace '\\', '/') + '/'
if ([string]::IsNullOrWhiteSpace((Resolve-Setting 'REDIS_PASSWORD' $dotEnv))) {
    throw 'Set REDIS_PASSWORD in docker-compose/.env (the redis container requires it).'
}
$null = Resolve-Setting 'CONFIG_SERVER_PASSWORD' $dotEnv   # optional locally: the local profile has a default

if ($RealPayments) {
    foreach ($name in 'RAZORPAY_KEY_ID', 'RAZORPAY_KEY_SECRET', 'RAZORPAY_WEBHOOK_SECRET') {
        $value = Resolve-Setting $name $dotEnv   # also exported, so the services started below inherit it
        if ([string]::IsNullOrWhiteSpace($value) -or $value -in 'stub', 'placeholder', 'rzp_test_stub', 'rzp_test_placeholder', 'local-stub-webhook-secret') {
            throw "-RealPayments needs a real Razorpay TEST value for $name. Set it in docker-compose/.env (see docs/RAZORPAY_TEST_MODE.md)."
        }
    }
    if ($env:RAZORPAY_KEY_ID -notlike 'rzp_test_*') {
        throw "RAZORPAY_KEY_ID must be a TEST key (rzp_test_...). Live keys (rzp_live_...) are never used for local runs."
    }
    Write-Host "Razorpay TEST mode with key $($env:RAZORPAY_KEY_ID.Substring(0, [Math]::Min(14, $env:RAZORPAY_KEY_ID.Length)))..." -ForegroundColor Yellow
}

# ngrok tunnel: check everything it needs now, before minutes of service startup, and fail with a clear message.
if ($Tunnel) {
    & (Join-Path $PSScriptRoot 'start-tunnel.ps1') -CheckOnly
    if (-not $RealPayments) {
        Write-Host '-Tunnel without -RealPayments: the offline stub needs no webhooks, but starting the tunnel anyway.' -ForegroundColor Yellow
    }
}

# name, jar folder, port, extra profiles
$services = @(
    @{ Name = 'config-server';        Dir = 'cloud/config-server';            Port = 8888; Profiles = 'local' },
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
    @{ Name = 'notification-service'; Dir = 'services/notification-service';  Port = 5012; Profiles = 'local' },
    @{ Name = 'api-gateway';          Dir = 'cloud/api-gateway';              Port = 5100; Profiles = 'local' }  # 5100 = management port; API on 5000
)

function Wait-Ready($name, $port) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            # Readiness, not overall health: a Redis or SMTP hiccup must not count as "not started".
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://localhost:$port/actuator/health/readiness"
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

Write-Host 'Starting config server and service registry...'
Start-One $services[0]
Start-One $services[1]
Wait-Ready $services[0].Name $services[0].Port
Wait-Ready $services[1].Name $services[1].Port

# Start in small batches: nine JVMs booting at once can starve the CPU and time out database connections.
Write-Host 'Starting business services...'
$business = $services[2..($services.Count - 2)]
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

if ($Tunnel) {
    & (Join-Path $PSScriptRoot 'start-tunnel.ps1')
}

Write-Host ''
Write-Host 'All services are up. API gateway: http://localhost:5000   Eureka: http://localhost:8761' -ForegroundColor Green
Write-Host 'Config server: http://localhost:8888 (user config)   E-mails: http://localhost:8025 (Mailpit)' -ForegroundColor Green
if (-not $RealPayments) {
    Write-Host 'Payments: OFFLINE STUB (no money moves). Webhook secret: local-stub-webhook-secret' -ForegroundColor Yellow
} else {
    Write-Host 'Payments: Razorpay TEST mode (test cards and UPI only, no real money). Guide: docs/RAZORPAY_TEST_MODE.md' -ForegroundColor Yellow
}
