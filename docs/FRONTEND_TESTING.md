# Testing the web app by hand

A step-by-step walkthrough of the customer website (`frontend/`) against the real backend. You can do it
in about 30 minutes, and it covers the main booking journey, the less common paths, mobile and
accessibility checks, and the production container.

Commands are for PowerShell on Windows, run from the repository root unless stated.

## Part 1: Start the backend and seed data (one time, ~10 minutes)

A fresh database has no flights, so first seed test data with the Postman collection.

**1. Start Docker Desktop.**

**2. Create the env file** (skip if `docker-compose/.env` already exists):

```powershell
copy docker-compose\.env.example docker-compose\.env
```

Make sure it contains these local values (see [postman/README.md](../postman/README.md)):

```
MYSQL_ROOT_PASSWORD=local-root-only
DB_PASSWORD=airline
REDIS_PASSWORD=local-redis-only
CONFIG_SERVER_PASSWORD=local-config-password
PAYMENT_SPRING_PROFILES=payment-stub
RAZORPAY_KEY_ID=rzp_test_stub
RAZORPAY_KEY_SECRET=stub
RAZORPAY_WEBHOOK_SECRET=local-stub-webhook-secret
```

No token signing key is needed locally: user-service generates one at startup (so restarting it signs everyone
out). E-mails the app sends (welcome, booking confirmation, cancellation, refund) appear in Mailpit at
http://localhost:8025.

**3. Start databases, Kafka, Redis and Mailpit, build, and start the services:**

```powershell
docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env up -d userdb airlinecoredb flightopsdb locationdb pricingdb ancillarydb bookingdb paymentdb seatdb notificationdb kafka kafka-ui redis mailpit prometheus grafana
.\mvnw.cmd -B -DskipTests package
.\scripts\start-local.ps1
```

Wait 1–2 minutes after the script finishes, so the gateway can discover all the services. Then check the
gateway really answers:

```powershell
curl.exe http://localhost:5000/api/airports
```

A JSON list (`[]` on a fresh database) means you're ready. Anything else: see [Troubleshooting](#troubleshooting).

**4. Seed test data.** Quickest: the demo seed creates a realistic network (real airport codes, two airlines,
20 daily flights for the next 45 days) and is safe to re-run:

```powershell
node scripts/seed-demo/seed-demo.mjs
```

It needs `DEMO_OWNER_PASSWORD` (any 10+ characters) in `docker-compose/.env`. Then search, for example, `DEL` → `BOM`
tomorrow. Alternatively, run only folders 00 to 07 of the Postman collection:

```powershell
npx newman run postman/airline-platform.postman_collection.json -e postman/airline-local.postman_environment.json `
  --folder "00 Prepare run" --folder "01 Sign up and log in" --folder "02 Reference data (admin)" `
  --folder "03 Airline onboarding" --folder "04 Fleet and seat setup" --folder "05 Publishing flights" `
  --folder "06 Fares and extras" --folder "07 Flight search"
```

Or, in the Postman **desktop** app (the web version can't reach `localhost` without the desktop agent):

1. File → Import, and select both files in `postman/`. They appear as the collection **Airline Platform**
   and the environment **Airline local**.
2. Pick **Airline local** in the environment dropdown (top right). Without it, every request fails.
3. Hover **Airline Platform** in the sidebar, click **⋯**, then **Run collection**.
4. Untick every request in folders **08–14** (they make bookings and payments, which you'll do in the website),
   keep the default order, and click **Run Airline Platform**.
5. Everything should be green. Requests in folder 05 may repeat while they wait for Kafka; that's normal.
6. The collection's **Variables** tab shows `runId` (your cities are `Mumbai <runId>` / `Delhi <runId>`),
   `flightDate`, and `customerEmail` (a ready-made customer, password `correct-horse-battery`).

Check the seed worked: `curl.exe http://localhost:5000/api/airports` should now list airports with random
three-letter codes and cities such as `Mumbai lq3x9a`.

This creates:

- Two cities called **"Mumbai \<runId\>"** and **"Delhi \<runId\>"**
- A 60-seat flight **5 days from today, 06:10–08:25**
- Overnight flights at 23:30 on Mon/Wed/Fri, 10–16 days from today
- A **Saver fare (₹5,300)**, a **meal (₹350)** and an **extra bag (₹1,200, max 2)**

**5. Start the website:**

```powershell
cd frontend
npm install
npm start
```

Open **http://localhost:5173**.

## Part 2: The main booking journey

| # | Do this | You should see |
|---|---|---|
| 1 | Open the home page | Hero section, search card, three feature tiles |
| 2 | In **From**, type `mum`, then use ↓/↑ and Enter | Suggestions list. Pick the Mumbai with the **newest** runId; each Postman run adds another one |
| 3 | **To**: type `del`, pick it. Date: **5 days from today**. Travellers: **2 adults**. Click **Search flights** | URL becomes `/flights?from=…&to=…`, showing a teal band "Mumbai… to Delhi…" and a flight card at ₹5,300 per adult, ₹10,600 total |
| 4 | Tick **Morning**, then **Night** | The count changes instantly (Night hides the 06:10 flight). Untick it again |
| 5 | Click the next-day / previous-day links | A new search for that day. Most days show "No flights on this date" |
| 6 | Click **Select** while signed out | You're sent to `/login?returnUrl=/book/…` |
| 7 | Click **Create an account** and sign up with any email and a 10+ character password. Try a short password first | Inline error "Use at least 10 characters". After sign-up you land **back on the checkout** |
| 8 | **Fare step:** Saver is pre-selected. Click Continue | Price summary on the right: Saver × 2 = ₹10,600 |
| 9 | **Travellers:** click Continue while it's empty | Red errors, focus jumps to the first invalid field, and the banner "Please check the highlighted…" |
| 10 | Fill both travellers (date of birth in the past). Try the phone as `+91 98765 43210` with spaces | Spaces are accepted; they're stripped when sent |
| 11 | **Seats:** click a seat, then another. Click your own seat again | Numbers 1 and 2 appear on the seats, and the second click frees the seat. Also try **Choose seats for me** |
| 12 | **Extras:** choose a meal for traveller 1 and click **+** on the bag twice | **+** disables at 2 (the airline's max). The total updates |
| 13 | **Review:** click Pay without ticking the box | "Please accept the fare rules…" |
| 14 | Tick the box and click **Pay ₹… securely** | "Securing your seats…". The offline payment stub sends you to `/booking-success/<id>?razorpay_payment_id=pay_STUBOK…` |
| 15 | Wait a few seconds | "Confirming your payment" changes to **"You're booked!"** with the booking reference. The confirmation travels through Kafka, which is why there's a short wait |
| 16 | Click **View trip and e-tickets** | Status Confirmed, seats, an e-ticket number per traveller, a payment breakdown that matches the review total, and **Print itinerary** (the print preview has no header or footer) |
| 17 | Open **My trips** | The booking appears under Upcoming |

## Part 3: The less common paths

### A. Unpaid booking, then cancel or fail it

The stub confirms payment automatically, so you need to stop that call:

1. Press F12, open the Network tab, then More tools → **Network request blocking**.
2. Add the pattern `*/api/payments/verify`.
3. Make a new booking and pay.
4. After about 30 seconds the page says **"We're still confirming your payment"**.
5. In **My trips**, the booking shows **Awaiting payment**, with **Complete payment** and **Cancel booking** buttons.

From there:

- **Cancel booking:** a native dialog opens. Esc closes it; "Yes, cancel" changes the status to Cancelled.
  If you search that flight again, the seats are free.
- **Failed payment:** make another pending booking, then remove the blocking rule. Hover
  **Complete payment** to see its link, which contains `pay_STUBOK<paymentId>`. Visit
  `http://localhost:5173/booking-success/<bookingId>?razorpay_payment_id=pay_STUBFAIL<paymentId>`.
  You should get **"Payment didn't go through"**, and the seats are released.

### B. Two customers want the same seat

1. Open a normal window and an InPrivate window, signed in as two different accounts.
2. Both start booking the same flight, pick the **same seat**, and reach the Review step.
3. Pay in window 1 first, then in window 2.
4. Window 2 is sent back to **Seats** with "…is no longer available. Please choose another seat", and the
   seat now shows as taken.

### C. Sessions and security

- **Two tabs:** sign out in one tab and the other tab's header switches to "Sign in" by itself.
- **Bad return link:** visit `/login?returnUrl=https://google.com` and sign in. You should land on `/`
  (home), not Google.
- **Garbage URL:** visit `/flights?from=XX&to=DEL&date=2001-01-01`. You should see the "search link is
  incomplete or out of date" page.
- **Unknown page:** visit `/nonsense` to see the 404 page.

### D. Backend down

With the site open, stop the gateway (or run `.\scripts\stop-local.ps1`) and search again. You should see
"We couldn't search flights right now" with a **Try again** button, not a blank page.

### E. Mobile and accessibility

- **Phone size:** in DevTools, toggle the device toolbar (Ctrl+Shift+M) and pick a phone. Check the ☰ menu,
  the stacked search form, the filters button on results, and that the seat map scrolls.
- **Keyboard only:** the first Tab shows **Skip to main content**. Book a flight using only Tab, arrow keys,
  Enter and Space.
- **Lighthouse:** run it on the home and results pages (DevTools → Lighthouse) for accessibility and
  performance scores.

## Part 4 (optional): The production build in Docker

This checks the nginx setup, security headers and caching. Stop `npm start` first, because the container
takes port 5173:

```powershell
cd frontend
docker build -t airline-web .
docker run --rm -p 5173:8080 -e API_GATEWAY_URL=http://host.docker.internal:5000 airline-web
```

Open http://localhost:5173 and repeat Part 2, steps 1–16. Then in DevTools → Network:

- Click the page request. The response headers should include `Content-Security-Policy` with a `nonce-…`
  value that changes on every reload.
- `main-*.js` should have `Cache-Control: … immutable`.
- The Console should show **no CSP errors**.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Newman or the website gets answers you don't recognise (e.g. "No mock for /api/…"), or `.local-run/api-gateway.log` says port 5000 is already in use | Another program holds port 5000, so the gateway never started. Find it with `netstat -ano \| findstr :5000` (last column = PID), stop it with `Stop-Process -Id <PID>`, then `.\scripts\stop-local.ps1` and `.\scripts\start-local.ps1` again |
| `npm start` says port 5173 is in use | Another dev server or the Part 4 container is running. Stop it, or check with `netstat -ano \| findstr :5173` |
| `503` or connection refused right after starting | The gateway is still discovering services. Wait 30 seconds and retry |
| The airport list in the search box is empty | The seed step didn't run, or ran against something other than the gateway. Check the `curl.exe` command above, then run the Newman command again |
| Search finds no flights | Use the date **5 days from the day you ran Newman**, and pick the Mumbai/Delhi with the newest runId |
| "You're booked!" never appears, page says "still confirming" | Kafka or booking-service isn't processing events. Check `.local-run/booking-service.log` and `docker ps` (Kafka must be running) |

## When you're done

- Stop the services with `.\scripts\stop-local.ps1`, and stop the website with Ctrl+C in its terminal.
- To start again from empty databases:
  `docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env down -v`.
