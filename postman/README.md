# Testing the flows with Postman

`airline-platform.postman_collection.json` walks through every flow in order, from sign-up to payment,
cancellation and back office. It runs **without a Razorpay account**. Each request has tests, and saves
the ids it creates (tokens, airline, flight, seats, bookings…) for the requests after it.

| Folder | Flow |
|---|---|
| 00 Prepare run | Generates unique emails and codes, so you can run the collection again and again |
| 01 Sign up and log in | Admin login, two customers, one airline owner, plus security checks (spoofed headers, forged token, hidden `/internal`) |
| 02 Reference data | Admin creates cities and airports |
| 03 Airline onboarding | Owner creates an airline (PENDING_APPROVAL), admin approves it |
| 04 Fleet and seat setup | Aircraft, Economy cabin, 60-seat map |
| 05 Publishing flights | Flight, one dated instance, an overnight schedule, seat inventory built through Kafka |
| 06 Fares and extras | Saver fare (₹5,300), a meal (₹350), an extra bag (₹1,200) |
| 07 Flight search | Public search finds the flight with its lowest fare |
| 08 Checkout and pay by webhook | Booking, seat HELD, signed Razorpay webhook, CONFIRMED with e-ticket, seat BOOKED |
| 09 Checkout and pay by browser verify | The `/api/payments/verify` path, and that it is idempotent |
| 10 Underpayment is rejected | Provider reports ₹1 less, so the payment fails and the seat is released |
| 11 Payment link expires | `payment_link.expired` webhook cancels the booking and frees the seat |
| 12 Customer cancels before paying | Cancel, cancel again (idempotent), seat freed |
| 13 Checkout validation | Duplicate seat, sold seat, invalid meal (seat released by compensation), empty passengers |
| 14 Back office | History, airline bookings, admin payments and users, purge |

## 1. Start the databases and Kafka

```powershell
copy docker-compose\.env.example docker-compose\.env   # then fill it in (see below)
docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env `
  up -d userdb airlinecoredb flightopsdb locationdb pricingdb ancillarydb bookingdb paymentdb seatdb kafka
```

For local testing, `docker-compose/.env` needs at least these values (they match the services' `local` profile):

```
MYSQL_ROOT_PASSWORD=local-root-only
DB_PASSWORD=airline
JWT_SECRET=local-dev-only-jwt-secret-change-me-0123456789abcdef
PAYMENT_SPRING_PROFILES=payment-stub
RAZORPAY_KEY_ID=rzp_test_stub
RAZORPAY_KEY_SECRET=stub
RAZORPAY_WEBHOOK_SECRET=local-stub-webhook-secret
```

## 2. Build and start the services

```powershell
./mvnw -B -DskipTests package
./scripts/start-local.ps1        # starts registry, 9 services and the gateway; takes a few minutes
```

Logs are written to `.local-run/<service>.log`. Stop everything with `./scripts/stop-local.ps1`.

## 3. Import into Postman

1. **File → Import**, then select both files in this folder.
2. In the top-right environment selector, choose **Airline local**.

## 4. Run it

- **All at once:** right-click the collection → **Run collection** → **Run**. All 99 requests should pass.
- **Step by step (best for learning):** run **00 Prepare run** first. Then open the folders in order and
  click **Send** on each request. Read the **Tests** tab to see what is checked, and the **Scripts** tab to
  see what gets saved. Saved values are under the collection's **Variables** tab.
- **From the command line:**
  ```powershell
  npx newman run postman/airline-platform.postman_collection.json -e postman/airline-local.postman_environment.json
  ```

Some requests wait for Kafka events (for example "Booking becomes CONFIRMED"). They retry themselves for up to
about 20 seconds, so a request may run several times in the Runner. That's expected.

## Testing payments without Razorpay

Without credentials, payment-service would fail when it tries to create a Razorpay payment link. The
**`payment-stub`** Spring profile swaps the Razorpay adapter for `StubPaymentGateway`, an offline stand-in.
`scripts/start-local.ps1` turns it on by default. Everything else in the payment flow is the real code:
amount checks, ownership checks, row locks, idempotency, Kafka events and webhook signature checks.

**Payment links** are created offline with the id `plink_STUB<paymentId>`.

**Browser verify** (`POST /api/payments/verify`) accepts these payment ids:

| `razorpayPaymentId` | The stub reports | What should happen |
|---|---|---|
| `pay_STUBOK<paymentId>` | captured, full amount | Payment SUCCESS, booking CONFIRMED |
| `pay_STUBLOW<paymentId>` | captured, ₹1 short | Payment FAILED (amount mismatch), booking CANCELLED |
| `pay_STUBFAIL<paymentId>` | failed | Payment FAILED, booking CANCELLED |
| `pay_STUBWAIT<paymentId>` | authorized, not captured | Stays PENDING (a webhook settles it later) |

`<paymentId>` is the `paymentId` returned by `POST /api/bookings`.

**Webhooks** (`POST /api/payments/webhooks/razorpay`) are checked for real. Razorpay signs the raw body with
HMAC-SHA256 using the webhook secret, and so do the webhook requests in this collection: their pre-request
script builds the body and computes `X-Razorpay-Signature` with the secret `local-stub-webhook-secret`.
Open "Razorpay webhook: payment_link.paid" in folder 08 to see how. Change one character of the body and the
service answers 401.

> **Never enable `payment-stub` in production.** It simulates payments; no money is collected. The service
> logs a loud warning at startup when it is active.

### Using Razorpay test mode instead (optional)

1. Create a Razorpay account and switch the dashboard to **Test Mode**. Test keys are free and never charge
   real money.
2. Generate an API key (Settings → API Keys) and set `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` in your
   environment.
3. Webhooks need a public URL. Expose the gateway with a tunnel (for example `ngrok http 5000`), add a webhook
   in the dashboard pointing to `https://<tunnel>/api/payments/webhooks/razorpay` for `payment_link.paid`,
   `payment_link.expired` and `payment_link.cancelled`, and set the same secret as `RAZORPAY_WEBHOOK_SECRET`.
4. Start with `./scripts/start-local.ps1 -RealPayments`. Open the `checkoutUrl` from `POST /api/bookings` in a
   browser and pay with one of Razorpay's test cards or test UPI ids from their documentation.

In real mode, set the `webhookSecret` variable in the Postman environment to your Razorpay webhook secret if
you also want to send simulated webhooks.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `503` or connection refused right after startup | The gateway is still discovering services through Eureka. Wait 30 seconds and retry. |
| A service never becomes healthy | See `.local-run/<service>.log`. Usually the database or Kafka container isn't running (`docker ps`). |
| `401` on everything | Tokens expire after 1 hour. Run folder 01 again. |
| Admin login fails | The bootstrap admin comes from `BOOTSTRAP_ADMIN_*` (local default `admin@airline.local` / `ChangeMe!Local123`). It's created only if the account doesn't exist. |
| Start from empty databases | `./scripts/stop-local.ps1`, then `docker compose -f docker-compose/docker-compose.yml --env-file docker-compose/.env down -v` and start again. |
