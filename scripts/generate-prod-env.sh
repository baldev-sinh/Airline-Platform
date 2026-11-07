#!/usr/bin/env bash
# Creates or completes the production .env next to docker-compose.prod.yml.
#
#   scripts/generate-prod-env.sh [path/to/.env]      (default: docker-compose/.env)
#
# - Starts from docker-compose/.env.prod.example if the file doesn't exist yet.
# - Fills in strong random values for empty generated secrets (database passwords, JWT secret,
#   Grafana password, Razorpay webhook secret, demo owner password). Values already set are never changed, so it is safe to re-run.
# - Leaves the rest (hostname, Razorpay keys, admin email) for you, and lists what is still empty.
# - Makes the file readable by its owner only.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
env_file="${1:-$root/docker-compose/.env}"
template="$root/docker-compose/.env.prod.example"

if [[ ! -f "$env_file" ]]; then
  cp "$template" "$env_file"
  echo "Created $env_file from .env.prod.example"
fi
chmod 600 "$env_file"

# Letters and digits only, so values are safe in .env files, YAML and URLs.
random_secret() { openssl rand -base64 64 | tr -dc 'A-Za-z0-9' | head -c "$1"; }

fill_if_empty() {
  local name="$1" length="$2"
  if grep -qE "^${name}=$" "$env_file"; then
    sed -i "s|^${name}=$|${name}=$(random_secret "$length")|" "$env_file"
    echo "  generated $name"
  elif ! grep -qE "^${name}=" "$env_file"; then
    echo "${name}=$(random_secret "$length")" >> "$env_file"
    echo "  generated $name"
  fi
}

fill_if_empty MYSQL_ROOT_PASSWORD 32
fill_if_empty DB_PASSWORD 32
fill_if_empty JWT_SECRET 64          # the gateway and user-service need at least 32 bytes
fill_if_empty GRAFANA_ADMIN_PASSWORD 24
fill_if_empty RAZORPAY_WEBHOOK_SECRET 32
fill_if_empty DEMO_OWNER_PASSWORD 24   # demo airlines' owner accounts (scripts/seed-demo)

missing=$(grep -E '^(PUBLIC_HOSTNAME|PUBLIC_BASE_URL|RAZORPAY_KEY_ID|RAZORPAY_KEY_SECRET|BOOTSTRAP_ADMIN_EMAIL|BOOTSTRAP_ADMIN_PASSWORD)=$' "$env_file" | cut -d= -f1 || true)
if [[ -n "$missing" ]]; then
  echo "Still to fill in by hand:"
  echo "$missing" | sed 's/^/  /'
fi
if grep -qE '^RAZORPAY_KEY_ID=rzp_live_' "$env_file"; then
  echo "WARNING: RAZORPAY_KEY_ID is a LIVE key: real money will be charged." >&2
fi
echo "The webhook secret to enter in Razorpay is RAZORPAY_WEBHOOK_SECRET in $env_file"
