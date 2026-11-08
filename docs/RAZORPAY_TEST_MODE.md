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
4. **Webhooks (recommended locally, required in production):** Razorpay also calls
   `POST /api/payments/webhooks/razorpay` (signed with your webhook secret) the moment a link is paid, expires or
   is cancelled. It doesn't depend on the customer's browser, so it settles payments even if the customer closes
   the tab, and it is the only *instant* report of expired or cancelled links.
5. **Reconciliation (safety net):** every 2 minutes payment-service asks Razorpay about payments still pending
   after 2 minutes, and settles or fails them exactly as steps 3 and 4 would. It catches anything both of them
   missed, such as a closed tab with no webhook configured, or a webhook sent while the service was down.

All three use the same checks (captured, amount, currency) and are safe to overlap: a payment is settled once,
whichever path gets there first.

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

## 5. Webhooks with a permanent address (optional, recommended)

Razorpay can only call your laptop through a public URL. ngrok's free plan gives every account **one
permanent address** (e.g. `your-name.ngrok-free.app`), so you register the webhook **once** and never change it.

1. Sign up at https://ngrok.com (free) and install the agent:
   ```powershell
   winget install --id Ngrok.Ngrok
   ```
   Open a new terminal afterwards so the `ngrok` command is found.
2. In the ngrok dashboard copy your **authtoken** (Getting Started → Your Authtoken) and your **domain**
   (Domains: the free `*.ngrok-free.app` address assigned to your account).
3. Add both to `docker-compose/.env`:
   ```
   NGROK_AUTHTOKEN=<your authtoken>
   NGROK_DOMAIN=your-name.ngrok-free.app
   ```
4. In the Razorpay dashboard (Test Mode): **Account & Settings → Webhooks → Add New Webhook**:

   | Field | Value |
   |---|---|
   | Webhook URL | `https://your-name.ngrok-free.app/api/payments/webhooks/razorpay` |
   | Secret | `RAZORPAY_WEBHOOK_SECRET` from step 3 |
   | Active events | `payment_link.paid`, `payment_link.expired`, `payment_link.cancelled` |

That's the last time you touch a URL: `start-local.ps1 -Tunnel` (step 6) starts the tunnel on this same address
every time. To restart only the tunnel while the services keep running, use `.\scripts\start-tunnel.ps1`.

The tunnel exposes your local gateway on the internet while it runs. Every API except the webhook and public
search still requires sign-in, and only Razorpay's webhook calls normally go through it (your browser keeps using
`localhost`), well within the free plan's monthly limits.

Without webhooks, paying and returning to the site still confirms the booking straight away. If the customer
closes the tab after paying, or a link expires, nothing reports it instantly; the reconciliation job (step 5 of
"How it works") finds it within about 2–4 minutes and confirms or cancels the booking. Webhooks make that instant,
which is why production must have them.

**No ngrok account?** Cloudflare's quick tunnel needs none, but gets a new random address every time it starts,
so you must update the Razorpay webhook URL after each start:

```powershell
winget install --id Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:5000
```

## 6. Start the stack in test mode

```powershell
.\scripts\stop-local.ps1
.\mvnw.cmd -B -DskipTests package      # rebuild the jars after pulling code changes (needs the services stopped)
.\scripts\start-local.ps1 -RealPayments -Tunnel   # leave out -Tunnel if you skipped step 5
```

The script reads the keys from `docker-compose/.env` and **refuses to start** if they are missing, still the
stub placeholders, or live keys (`rzp_live_...`). A local run can never take real money. When everything is up
it prints `Payments: Razorpay TEST mode`. With `-Tunnel` it also prints `ngrok tunnel is up` and the webhook URL;
`stop-local.ps1` stops the tunnel together with the services.

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
| `start-local.ps1 -Tunnel` stops with "ngrok is not installed" or "Set NGROK_AUTHTOKEN and NGROK_DOMAIN" | Install ngrok (step 5) and open a new terminal, or add the two values to `docker-compose/.env` |
| "The ngrok tunnel did not come up" | Usually a wrong authtoken, or `NGROK_DOMAIN` isn't the domain shown in *your* ngrok dashboard. The script prints the last lines of `.local-run/ngrok.log` |
| Webhooks show failures in the Razorpay dashboard | Tunnel not running (`.\scripts\start-tunnel.ps1` restarts it), or with Cloudflare's quick tunnel the URL changed (update the webhook). A `401` means the webhook secret differs from `RAZORPAY_WEBHOOK_SECRET` |
| "We could not open the secure payment page" | The payment link URL wasn't on the allow-list in `frontend/src/environments/`. Razorpay links use `rzp.io`; add the host shown in the error log if Razorpay changes it |
