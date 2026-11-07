# Paying with Razorpay test mode

By default the local stack uses an offline payment stub. This guide switches it to **real Razorpay in test
mode**: customers go to Razorpay's actual payment page and pay with Razorpay's test cards or test UPI IDs.
It's the real integration end to end, but no real money ever moves.

Commands are for PowerShell on Windows, run from the repository root.

## How it works

1. **Pay:** the customer clicks **Pay** and payment-service creates a Razorpay **Payment Link** for the
   booking amount (valid 16 minutes). The site sends the customer to the link's `https://rzp.io/...` page.
2. **Return:** after paying, Razorpay redirects the browser to
   `http://localhost:5173/booking-success/<bookingId>?razorpay_payment_id=pay_...&razorpay_payment_link_id=plink_...`.
3. **Verify:** the site calls `POST /api/payments/verify`. payment-service **re-fetches the payment from Razorpay
   with its own keys**, then checks that it is captured, belongs to this booking, and matches the amount and
   currency. Only then does it confirm the booking (via Kafka). The browser is never trusted.
4. **Webhooks (optional, recommended):** Razorpay also calls
   `POST /api/payments/webhooks/razorpay` (signed with your webhook secret) when a link is paid, expires or is
   cancelled. This settles payments even if the customer closes the tab.

## 1. Create a Razorpay account and switch to Test Mode

1. Sign up at https://dashboard.razorpay.com/signup. Test mode works without completing KYC.
2. In the dashboard, set the **Test Mode** toggle at the top. Everything below must be done in Test Mode.

## 2. Generate test API keys

1. **Account & Settings → API Keys → Generate Key** (in Test Mode).
2. Copy the **Key Id** (`rzp_test_...`) and the **Key Secret**. The secret is shown only once; if you lose
   it, regenerate the key.

## 3. Choose a webhook secret

The webhook secret is a password you make up; Razorpay uses it to sign webhook calls. Generate one:

```powershell
-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
```

payment-service needs this value even if you skip webhooks (step 5).

## 4. Put the keys in `docker-compose/.env`

`docker-compose/.env` is git-ignored, so the keys never get committed. Set:

```
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
RAZORPAY_WEBHOOK_SECRET=<the value from step 3>
PAYMENT_CALLBACK_BASE_URL=http://localhost:5173
```

Leave `PAYMENT_SPRING_PROFILES=payment-stub` as it is: the default (stub) runs keep using it, and
`-RealPayments` overrides it.

## 5. Webhooks (optional, recommended)

Razorpay can only call your laptop through a public URL, so you need a tunnel to the gateway on port 5000.
Cloudflare's quick tunnel needs no account:

```powershell
winget install --id Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:5000
```

It prints a URL like `https://random-words.trycloudflare.com` (a new one every time it starts). Then in the
Razorpay dashboard (Test Mode): **Account & Settings → Webhooks → Add New Webhook**:

| Field | Value |
|---|---|
| Webhook URL | `https://random-words.trycloudflare.com/api/payments/webhooks/razorpay` |
| Secret | the value from step 3 |
| Active events | `payment_link.paid`, `payment_link.expired`, `payment_link.cancelled` |

The tunnel makes your local gateway reachable from the internet while it runs. Every API except the webhook
and public search still requires sign-in, but stop the tunnel (Ctrl+C) when you're done.

Without webhooks, paying and returning to the site still works. What you lose: a customer who closes the tab
after paying stays "Awaiting payment" until they reopen the booking, and expired links aren't reported (the
seat hold still expires on its own after 20 minutes).

## 6. Start the stack in test mode

```powershell
.\scripts\stop-local.ps1
.\mvnw.cmd -B -DskipTests package      # rebuild the jars after pulling code changes (needs the services stopped)
.\scripts\start-local.ps1 -RealPayments
```

The script reads the keys from `docker-compose/.env` and **refuses to start** if they are missing, still the
stub placeholders, or live keys (`rzp_live_...`). A local run can never take real money. When everything is up
it prints `Payments: Razorpay TEST mode`.

Then start the website (`cd frontend`, `npm start`) and book a flight as usual
(see [FRONTEND_TESTING.md](FRONTEND_TESTING.md)).

## 7. Pay on the Razorpay page

After **Pay**, you land on Razorpay's page, which shows a **Test Mode** banner. Pay with:

- **UPI:** `success@razorpay` succeeds, `failure@razorpay` fails.
- **Cards:** any card from Razorpay's [test card list](https://razorpay.com/docs/payments/payments/test-card-details/),
  with any future expiry date and any CVV. On the bank page that follows, choose **Success** or **Failure**.

**What you should see:**

| You do | The site shows |
|---|---|
| Pay successfully | "Confirming your payment", then **"You're booked!"** with the booking reference and e-tickets |
| Fail the payment, then close Razorpay's page | My trips shows the booking as **Awaiting payment**, with **Complete payment** to try again |
| Don't pay for 16+ minutes | The link expires. With webhooks, the booking turns **Cancelled** and the seats are freed |

Every payment also appears in the Razorpay dashboard under **Transactions → Payments** (Test Mode).

## Switching back to the stub

```powershell
.\scripts\stop-local.ps1
.\scripts\start-local.ps1
```

Without `-RealPayments`, payment-service uses the offline stub again, whatever is in `.env`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `start-local.ps1` stops with "-RealPayments needs a real Razorpay TEST value" | A key in `docker-compose/.env` is empty or still a stub placeholder. Fill in step 4 |
| "Payment provider is unavailable" when clicking Pay | Razorpay rejected the keys. Check the key id and secret (Test Mode keys), then restart. The cause is in `.local-run/payment-service.log` |
| After paying, the page stays on "We're still confirming your payment" | The verify call failed. Look for `verify` or `Razorpay` in `.local-run/payment-service.log`. With webhooks set up, the booking still confirms within seconds |
| Webhooks show failures in the Razorpay dashboard | Tunnel not running, or the URL changed (quick tunnels get a new URL each start: update the webhook). A `401` means the webhook secret differs from `RAZORPAY_WEBHOOK_SECRET` |
| "We could not open the secure payment page" | The payment link URL wasn't on the allow-list in `frontend/src/environments/`. Razorpay links use `rzp.io`; add the host shown in the error log if Razorpay changes it |
