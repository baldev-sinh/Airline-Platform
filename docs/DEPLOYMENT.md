# Running the production stack

How to run the platform on a Linux server with `docker-compose.prod.yml`. Why it is set up this way, and what
is still planned, is in [DEPLOYMENT_PLAN.md](DEPLOYMENT_PLAN.md).

All commands run on the server, in the folder that holds `docker-compose.yml`, `docker-compose.prod.yml`, the
`caddy/` and `seed-demo/` folders (from `docker-compose/caddy` and `scripts/seed-demo`) and `.env`. A shortcut keeps them short:

```bash
alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env'
```

## What runs where

| Container | Reachable from |
|---|---|
| `caddy` | The internet, ports 80 (redirects to HTTPS) and 443. Gets and renews the HTTPS certificate itself |
| `web` (nginx + Angular app) | Caddy only. Proxies `/api` and `/auth` to the gateway |
| `api-gateway`, the 10 other services, 9 MySQL databases, Kafka | Inside Docker only: no published ports |
| `kafka-ui`, `prometheus`, `grafana` | Only with `--profile monitoring`, on `127.0.0.1` (use an SSH tunnel) |

## First start

1. Server prerequisites: Docker with Compose 2.24 or newer, ports 22/80/443 open, and the hostname in
   `PUBLIC_HOSTNAME` pointing at the server's public IP (e.g. a DuckDNS subdomain).
2. Create `.env`:
   ```bash
   scripts/generate-prod-env.sh .env     # generates DB passwords, JWT secret, webhook secret; chmod 600
   nano .env                             # fill in PUBLIC_HOSTNAME, PUBLIC_BASE_URL, Razorpay keys, admin email
   ```
3. Start everything:
   ```bash
   dc pull
   dc up -d
   dc ps            # wait until every container is "healthy" (2–3 minutes on first start)
   ```
4. Check it from your laptop:
   ```bash
   curl -I https://baldevairways.duckdns.org/healthz      # 200
   curl https://baldevairways.duckdns.org/api/airports    # JSON
   ```
5. Load the demo network (airports, airlines, aircraft, flights, fares, meals; see "Demo data" below):
   ```bash
   docker run --rm --network airline_edge --env-file .env -v "$PWD/seed-demo:/seed:ro"      node:24-alpine node /seed/seed-demo.mjs --api http://api-gateway:5000
   ```
6. In Razorpay (Test Mode) add the webhook `https://<PUBLIC_HOSTNAME>/api/payments/webhooks/razorpay` with the
   `RAZORPAY_WEBHOOK_SECRET` from `.env`, for `payment_link.paid`, `payment_link.expired` and
   `payment_link.cancelled`. This is done once; the URL never changes.

## Deploying a new version

CI publishes every image tagged with the commit SHA, plus `main` for the latest build of the main branch.

```bash
dc pull && dc up -d                          # IMAGE_TAG=main in .env: latest main build
IMAGE_TAG=<commit-sha> dc up -d              # a specific version
```

Only containers whose image changed are restarted. Database changes are applied by Flyway when a service
starts.

**Rollback:** run the second command with the previous commit's SHA (listed in GitHub under Actions or in
`git log`). Flyway migrations are not rolled back, so a version that changed a database must stay compatible.

## Day to day

```bash
dc ps                                   # status and health of every container
dc logs -f --tail 100 booking-service   # follow one service's logs (JSON, rotated: 3 x 10 MB per container)
dc restart payment-service              # restart one service
dc exec bookingdb mysql -uairline -p airline_booking_db    # SQL shell (password: DB_PASSWORD)
```

**Monitoring** is private. Start it, then open an SSH tunnel from your laptop:

```bash
dc --profile monitoring up -d                    # on the server
ssh -L 3000:localhost:3000 -L 8090:localhost:8090 user@server    # on your laptop
```

Then Grafana is at http://localhost:3000 (user `admin`, password `GRAFANA_ADMIN_PASSWORD`) and Kafka UI at
http://localhost:8090.

## Demo data

`scripts/seed-demo/seed-demo.mjs` creates a bookable network through the public API: 7 Indian airports, two
fictional airlines (Baldev Airways with 4 A320neo, Coastal Wings with 2 ATR 72), 20 daily flights with Saver,
Flexi and Business fares, fare rules, baggage, meals and extras. The data is in `demo-data.mjs`.

- It needs `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (or `SEED_ADMIN_*`) and `DEMO_OWNER_PASSWORD` from
  `.env`. The airline owner accounts (`ops@baldevairways.example`, `ops@coastalwings.example`) are created with
  that password on the first run.
- It is safe to run any number of times: existing things are found, not created again.
- Flights are scheduled `--days` ahead (default 45). Each run only adds the missing days, so a daily run keeps
  the schedule rolling:
  ```bash
  # crontab -e   (every night at 03:00)
  0 3 * * * cd /opt/airline && docker run --rm --network airline_edge --env-file .env -v "$PWD/seed-demo:/seed:ro" node:24-alpine node /seed/seed-demo.mjs --api http://api-gateway:5000 >> /var/log/airline-seed.log 2>&1
  ```

Locally (services from `scripts/start-local.ps1`, Node installed): `node scripts/seed-demo/seed-demo.mjs`. It reads
`docker-compose/.env` itself.

## Changing the public address

Change `PUBLIC_HOSTNAME` and `PUBLIC_BASE_URL` in `.env`, point the new hostname at the server, run
`dc up -d`, and update the Razorpay webhook URL. Nothing else stores the address.

## Testing the production setup on a laptop

With Docker Desktop you can run the same files locally. Set `PUBLIC_HOSTNAME=localhost` and
`PUBLIC_BASE_URL=https://localhost` in a separate env file: Caddy then uses its own local certificate (your
browser warns about it once). Stop `scripts/start-local.ps1` first, because the full stack needs the memory.
