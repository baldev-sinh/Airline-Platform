<#
.SYNOPSIS
  Starts an ngrok tunnel from your permanent ngrok address to the local API gateway, so Razorpay webhooks reach
  this machine at the same URL every time.

.DESCRIPTION
  Reads NGROK_AUTHTOKEN and NGROK_DOMAIN from the environment or docker-compose/.env (git-ignored).
  NGROK_DOMAIN is the free permanent domain ngrok assigns to your account (dashboard: Domains), e.g.
  your-name.ngrok-free.app. Register https://<NGROK_DOMAIN>/api/payments/webhooks/razorpay in Razorpay once.

  Called by start-local.ps1 -Tunnel; also works on its own to (re)start only the tunnel while the services run.
  The tunnel is recorded in .local-run/pids.txt, so stop-local.ps1 stops it. Running it twice is harmless.

.PARAMETER CheckOnly
  Only check that ngrok and its settings are present (used by start-local.ps1 before starting services).
#>
param(
    [switch]$CheckOnly,
    [int]$Port = 5000,
    [int]$TimeoutSeconds = 45
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-run-common.ps1')
$root = Split-Path $PSScriptRoot -Parent
$runDir = Join-Path $root '.local-run'
New-Item -ItemType Directory -Force $runDir | Out-Null
$pidFile = Get-PidFilePath $root

# ---- Checks ----
if (-not (Get-Command ngrok -ErrorAction SilentlyContinue)) {
    throw "ngrok is not installed. Install it with: winget install --id Ngrok.Ngrok   (then open a new terminal)"
}
$dotEnv = Read-DotEnv (Join-Path $root 'docker-compose/.env')
$token = Resolve-Setting 'NGROK_AUTHTOKEN' $dotEnv     # exported: the ngrok agent reads it from the environment
$domain = (Resolve-Setting 'NGROK_DOMAIN' $dotEnv) -replace '^https?://', '' -replace '/+$', ''
if ([string]::IsNullOrWhiteSpace($token) -or [string]::IsNullOrWhiteSpace($domain)) {
    throw "Set NGROK_AUTHTOKEN and NGROK_DOMAIN in docker-compose/.env (see docs/RAZORPAY_TEST_MODE.md)."
}
if ($domain -notmatch '^[a-z0-9-]+(\.[a-z0-9-]+)+$') {
    throw "NGROK_DOMAIN should be a hostname like your-name.ngrok-free.app, not '$domain'."
}
if ($CheckOnly) { return }

$publicUrl = "https://$domain"
$webhookUrl = "$publicUrl/api/payments/webhooks/razorpay"

# ---- Already running? ----
if (Test-Path $pidFile) {
    $existing = Get-Content $pidFile | Where-Object { (Get-TrackedServiceName $_) -eq 'ngrok' -and (Get-TrackedProcess $_) }
    if ($existing) {
        Write-Host "ngrok tunnel already running: $publicUrl" -ForegroundColor Green
        Write-Host "  Razorpay webhook URL: $webhookUrl"
        return
    }
}

# ---- Start ----
# ngrok 3.16+ takes the address as --url; older versions use --domain. Use whichever this version knows.
$help = (& ngrok http --help 2>&1 | Out-String)
$addressArgs = if ($help -match '--url\b') { @('--url', $publicUrl) } else { @('--domain', $domain) }

$log = Join-Path $runDir 'ngrok.log'
$ngrokArgs = @('http', "$Port") + $addressArgs + @('--log', 'stdout', '--log-format', 'logfmt')
$process = Start-Process ngrok -ArgumentList $ngrokArgs -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
    -WindowStyle Hidden -PassThru
Add-Content $pidFile (Format-PidLine $process 'ngrok')
Write-Host ("  started {0,-22} pid {1,-6} {2} -> localhost:{3}" -f 'ngrok', $process.Id, $publicUrl, $Port)

# ---- Wait until the tunnel really reaches the gateway ----
# The header skips the warning page ngrok's free plan shows to browsers; webhooks never see that page.
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    if ($process.HasExited) { break }
    try {
        $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Headers @{ 'ngrok-skip-browser-warning' = 'true' } "$publicUrl/api/airports"
        if ($r.StatusCode -eq 200 -and $r.Content.TrimStart().StartsWith('[')) {
            Write-Host "ngrok tunnel is up: $publicUrl" -ForegroundColor Green
            Write-Host "  Razorpay webhook URL (register once): $webhookUrl"
            return
        }
    } catch { }
    Start-Sleep -Seconds 2
}

Write-Host "The ngrok tunnel did not come up within $TimeoutSeconds s. Last log lines (.local-run/ngrok.log):" -ForegroundColor Red
Get-Content $log, "$log.err" -Tail 8 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "  $_" }
throw "ngrok tunnel failed to start. Check NGROK_AUTHTOKEN and that NGROK_DOMAIN is the domain shown in your ngrok dashboard."
