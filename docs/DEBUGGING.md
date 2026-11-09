# Debugging locally

How to see what's running, read the logs, and step through code with breakpoints, in the backend and the
frontend. This guide assumes the usual local setup:

| Part | Started by | Runs |
|---|---|---|
| Databases, Kafka, Kafka UI, Prometheus, Grafana | `docker compose ... up -d <names>` (below) | in Docker |
| Registry, gateway, 9 business services | `scripts/start-local.ps1` | as hidden `java` processes on your machine |
| Website | `npm start` in `frontend/` | Angular dev server on http://localhost:5173 |

`start-local.ps1` starts **only the Java services**. It doesn't start Docker or the website.

## 1. Start the infrastructure

```powershell
docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env `
  up -d userdb airlinecoredb flightopsdb locationdb pricingdb ancillarydb bookingdb paymentdb seatdb kafka kafka-ui prometheus grafana
```

- **Always list the service names.** `docker-compose.yml` also defines the 11 backend services and the
  website as containers. A bare `up -d` starts those too, and they use the same ports as
  `start-local.ps1`, so the two clash. Run a bare `up -d` only when you want the whole stack in Docker.
- `kafka-ui`, `prometheus` and `grafana` are optional. Leave them out to save memory (up to 512 MB each).
- Grafana won't start without `GRAFANA_ADMIN_PASSWORD` in `docker-compose/.env`.
- See what's running: `docker compose -f docker-compose/docker-compose.yml ps`.
- Stop it all: replace `up -d ...` with `down`. Data survives, because it's kept in Docker volumes.

Then build and start the Java services:

```powershell
./mvnw -B -DskipTests package
./scripts/start-local.ps1          # add -RealPayments for Razorpay test mode
cd frontend; npm start             # in a second terminal
```

## 2. See what's running

| What | Where |
|---|---|
| **Eureka dashboard**: every registered service and its status | http://localhost:8761 |
| Gateway: all API traffic goes through it | http://localhost:5000 |
| Gateway health (management port) | http://localhost:5100/actuator/health |
| Each service's health | `http://localhost:<port>/actuator/health` |
| Website | http://localhost:5173 |
| Kafka UI: messages between services (read-only) | http://localhost:8090 |
| Prometheus: *Status > Target health* shows which services it can reach | http://localhost:9090 |
| Grafana: user `admin`, password from `.env`, "Airline services" dashboard | http://localhost:3000 |

**Service ports:** service-registry 8761, user 5001, airline-core 5002, location 5004, pricing 5005,
seat 5006, ancillary 5008, booking 5009, payment 5010, flight-ops 5011, api-gateway 5000 (management 5100).

**Database ports:** one MySQL database per service, user `airline`, password `airline`:
userdb 3302, airlinecoredb 3303, flightopsdb 3304, locationdb 3305, pricingdb 3307, ancillarydb 3308,
bookingdb 3309, paymentdb 3310, seatdb 3311. IntelliJ's *Database* tool window can open them directly.

Check every backend service at once:

```powershell
5001,5002,5004,5005,5006,5008,5009,5010,5011,5100 | % { $s = try { (iwr -UseBasicParsing -TimeoutSec 2 "http://localhost:$_/actuator/health").StatusCode } catch { 'DOWN' }; "$_  $s" }
```

`.local-run/pids.txt` lists the processes `start-local.ps1` started, one per line, as
`<pid> <service-name> <start-time>`.

## 3. Read the logs

Each service writes to `.local-run/<service>.log`. Startup crashes often go to `.local-run/<service>.log.err`.

```powershell
Get-Content .local-run\booking-service.log -Wait -Tail 50   # follow live, like tail -f
Get-Content .local-run\booking-service.log.err -Tail 50
Select-String -Path .local-run\*.log -Pattern 'ERROR'       # search every service's log at once
```

- The `local` profile sets `com.baldev.airline` to **DEBUG**, so the project's own code logs in detail.
- If a service never becomes healthy, `start-local.ps1` says which log to open.
- Tracing samples every request locally, and the gateway accepts the `traceparent` header. Log lines written
  during a request should include a trace ID. Search for that ID across `.local-run/*.log` to follow one
  request through every service it touched.

## 4. Breakpoints in a backend service

You rarely need to debug all 11 services. **Stop the one you care about and run it from IntelliJ in
Debug mode.** The others keep running. Your copy registers with Eureka, and the gateway routes traffic to it.

1. Find the service's PID in `.local-run\pids.txt` (for example `41080 booking-service ...`), then stop it:
   `Stop-Process -Id 41080`.
2. In IntelliJ, open the service's `*Application` class (for example `BookingServiceApplication`), click
   the green arrow and choose **Debug**.
3. In the run configuration, set **Active profiles** to `local`. For payment-service, use
   `local,payment-stub`, or just `local` with Razorpay keys in the environment. You don't need any other
   environment variables: the `local` profile provides the database URLs, the JWT secret and the rest.
4. Wait about 30 seconds for the gateway to find the new copy. Then use the website or Postman (`postman/`
   holds the collection and a local environment, both pointing at the gateway on port 5000). Your
   breakpoints will hit.

Afterwards, `stop-local.ps1` reports that service as "was not running". That's harmless. Stop the IntelliJ
copy yourself.

### Alternative: attach to a running copy

Start the jar with a debug port open, then in IntelliJ choose *Run > Attach to Process*, or use a *Remote JVM
Debug* configuration on port 5005:

```powershell
java "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" "-Dspring.profiles.active=local" -jar services\booking-service\target\booking-service-0.0.1-SNAPSHOT.jar
```

Use `suspend=y` to make the service wait for the debugger before it starts, for example to debug startup.
To debug two services at once, give each one a different debug port.

### Gotchas in this project

- **Pause one thread, not the whole service.** Right-click the breakpoint and set *Suspend: Thread*. If
  everything pauses, the service stops sending heartbeats to Eureka (one every 10 seconds), and after about
  90 seconds Eureka removes it, so the gateway stops routing to it.
- **The gateway gives up after 15 seconds.** If you stay at a breakpoint longer than that, the browser gets
  a 504 even though your code is still paused.
- **The same GET can hit your breakpoint three times.** The frontend's `retryInterceptor` retries a GET
  request twice after a 502, 503 or 504. POST and other write requests are never retried.
- **Some steps go through Kafka.** For example, payment confirmation travels from payment-service to
  booking-service through Kafka. Use Kafka UI to check whether the message was sent and what it contained.
  That tells you which side has the bug.
- **Payments left pending** are reconciled with the payment provider every 2 minutes by
  `PaymentReconciliationJob`. A breakpoint there fires on that schedule, not when you make a request.

## 5. Debugging the frontend

Run `npm start` in `frontend/`. API calls go through the dev server's proxy to the gateway on port 5000.

**Chrome DevTools (F12):**
- **Network:** every `/api/...` call, with its status code and its error body (`code`, `detail`). Many
  "frontend bugs" turn out to be a failed API call.
- **Sources:** press Ctrl+P and type a file name such as `checkout-store.ts`, then click a line number to set
  a breakpoint. You see the real TypeScript, because the dev build includes source maps. You can also add
  a temporary `debugger;` statement to the code.
- **Console:** uncaught errors, printed in full by `GlobalErrorHandler` in development.
- **Application > Local Storage:** `baldev.session` holds the sign-in. Delete it to sign out.

**Angular DevTools** (a Chrome extension): click a component to see its inputs and current signal values,
and use its profiler to see what re-rendered and why.

**Unit tests:** `npm test` runs the Vitest tests and reruns them every time you save.

## 6. Finding a bug, step by step

1. Reproduce the bug in the browser with DevTools open, and look at the Network tab.
2. Did a request fail? The path tells you which service owns the endpoint (for example `/api/bookings` goes to
   booking-service), and the error body says what went wrong.
3. Open that service's log (section 3). If the step goes through messages, open Kafka UI.
4. Still unclear? Stop that service and run it under the IntelliJ debugger (section 4).
5. Wrong data? Look at the rows themselves in that service's database (section 2).
