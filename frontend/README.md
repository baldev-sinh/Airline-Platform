# Baldev Airways web app

The customer website for the airline platform: search flights, pick a fare, seats, meals and bags, pay,
and manage trips. Angular 22, TypeScript (strict), SCSS. No UI library: the design system lives in
`src/styles` and is small enough to read in one sitting.

```
npm install        # once
npm start          # http://localhost:5173, API calls proxied to the gateway on :5000
npm test           # unit tests (Vitest), re-run on save
npm run lint       # ESLint, including accessibility rules for templates
npm run build      # production build into dist/
```

You need Node 24 (`.nvmrc`) and, for real data, the backend running (see the root README;
`scripts/start-local.ps1` starts it with the offline payment stub).

## The customer journey

| Page | Route | What happens | Backend calls |
|---|---|---|---|
| Home | `/` | Search form | `GET /api/airports` |
| Results | `/flights?from=BOM&to=DEL&date=…&pax=2&cabin=ECONOMY` | Flights for one day, filter and sort | `GET /api/flights/search` |
| Checkout | `/book/:flightInstanceId` | 5 steps: fare, travellers, seats, extras, review & pay | flight instance, cabin seats, fares, meals, extras, then `POST /api/bookings` |
| Payment | Razorpay's page | Customer pays | n/a |
| Payment return | `/booking-success/:bookingId` | Verify, then wait for confirmation | `POST /api/payments/verify`, poll `GET /api/bookings/:id` |
| My trips | `/trips`, `/trips/:id` | Bookings, e-tickets, pay now, cancel unpaid, print | `GET /api/bookings/user/history`, `PATCH …/cancel` |
| Account | `/login`, `/signup`, `/account` | Sign in and profile | `/auth/login`, `/auth/signup`, `/api/users/profile` |

Checkout, trips and account need sign-in. A guest who clicks "Select" goes to sign-in and then comes
straight back to the checkout (`?returnUrl=`).

For a full manual walkthrough (seeding data, every page, edge cases, the container), see
[docs/FRONTEND_TESTING.md](../docs/FRONTEND_TESTING.md).

### Trying a payment locally

With the backend's `payment-stub` profile (the default in `start-local.ps1`), "Pay" sends you to
`/booking-success/<id>?razorpay_payment_id=pay_STUBOK<paymentId>`, which confirms the booking without
real money. Edit the URL to `pay_STUBFAIL…` or `pay_STUBLOW…` to see a failed payment.

## How the code is organised

```
src/
  styles/                 design tokens (colours, spacing...), base styles, shared classes (.btn, .card, .field)
  environments/           build-time settings (API base URL, trusted payment hosts)
  testing/fixtures.ts     test data factories (tests only)
  app/
    app.ts, app.html      page frame: skip link, header, <router-outlet>, footer
    app.config.ts         app-wide wiring: router, HTTP client, interceptors, locale, error handler
    app.routes.ts         every page and its URL (all lazy-loaded)
    core/                 used everywhere, created once
      models/             TypeScript shapes of the API's JSON (mirror the Java DTOs)
      api/                one class per backend area; only HTTP calls, no state
      auth/               AuthStore (who is signed in), guards, JWT expiry
      http/               interceptors (token, retries, errors) and ApiError
      layout/             header, footer, page titles
      util/               safe redirects, safe localStorage
    shared/               small reusable pieces: money pipe, field errors, loading/error states
    features/             one folder per area of the site
      home/ flight-search/ checkout/ payment/ trips/ auth/ account/ help/ not-found/
```

Rule of thumb: **features** may use **core** and **shared**; **shared** never uses **features**.

## Key ideas, explained

**Signals for state.** A signal is a value that tells Angular when it changes:
`count = signal(0)`, read with `count()`, change with `count.set(1)`. A `computed(() => …)` derives a
value from other signals and updates itself. Templates that read signals re-render exactly when needed.
This app is *zoneless* and uses *OnPush* change detection (both Angular 22 defaults): the screen updates
because a signal changed, not after every click "just in case".

**`rxResource` for loading data.** Pages load data like this:

```ts
protected readonly booking = rxResource({
  params: () => Number(this.bookingId()),            // re-runs when the route param changes
  stream: ({ params: id }) => this.bookingApi.get(id), // an Observable from an Api class
});
```

and the template switches on `booking.isLoading()`, `booking.error()` and `booking.hasValue()`.
Check `hasValue()` before reading `value()`: in an error state `value()` throws.

**Route params arrive as inputs.** With `withComponentInputBinding()`, `/trips/42` fills
`bookingId = input.required<string>()`, and query params work the same way. They come from the URL,
so treat them as untrusted text and validate them (see `parseCriteria` and `CheckoutPage.request`).

**Typed reactive forms.** `NonNullableFormBuilder` gives forms where TypeScript knows each field's type.
`<app-field-error>` shows the message for a control once it has been touched. Browser validation is
for convenience; the server validates everything again.

**One store per checkout.** `CheckoutStore` holds every choice made during checkout. It's provided by
`CheckoutPage` (not app-wide), so each checkout starts clean and the state disappears when you leave.
The step components all inject that same instance.

**Pure functions for logic.** Pricing (`checkout-pricing.ts`), seat layout (`seat-layout.ts`), filters
(`flight-filters.ts`) and URL parsing (`search-criteria.ts`) are plain functions: easy to read and
trivial to test. Components stay thin.

## Production practices in this codebase

| Practice | Where | Why |
|---|---|---|
| Same-origin API through a reverse proxy | `proxy.conf.json`, `nginx/default.conf.template` | No CORS, simpler cookies and CSP, one public host |
| Token only sent to our API | `core/http/interceptors.ts` | A JWT must never leak to third-party hosts |
| Auto sign-out on expiry or 401, then return to the same page | `AuthStore`, `errorInterceptor` | Expired sessions don't fail silently |
| Retries for GET only, on transient errors | `retryInterceptor` | Reads are safe to repeat; a repeated POST could book twice |
| Friendly, safe error messages | `core/http/api-error.ts` | Customers see help, never stack traces; code branches on stable `code`s |
| Payment redirect allow-list | `core/util/safe-redirect.ts` | Prevents open redirects to fake payment pages |
| `returnUrl` limited to in-app paths | `safeReturnUrl` | Same, for the sign-in page |
| Server is the source of truth for money | `checkout-pricing.ts` | The client shows an estimate; the server computes the charge |
| Strict CSP with a per-request nonce | `nginx/security-headers.conf`, `ngCspNonce` in `index.html` | Injected scripts can't run (XSS defence) |
| Subresource Integrity | `angular.json` (`subresourceIntegrity`) | The browser refuses tampered JS/CSS files |
| Immutable caching for hashed files, `no-cache` for `index.html` | nginx config | Fast repeat visits and instant releases |
| Lazy-loaded routes + preloading, bundle budgets | `app.routes.ts`, `angular.json` | Small first download (~95 kB gzipped); the build fails if it grows too much |
| Self-hosted font | `@fontsource-variable/source-sans-3` | No third-party request, works with the CSP |
| Hidden source maps, removed from the image | `angular.json`, `Dockerfile` | Debuggable via an error tracker, not public |
| Accessibility | throughout | Skip link, focus moved on navigation, labelled fields with linked errors, keyboard combobox, seat buttons with spoken labels, native `<dialog>`, colour contrast >= 4.5:1, reduced motion |
| Chunk-load recovery | `GlobalErrorHandler` | After a release, open tabs reload instead of breaking |
| Non-root container with a health check | `Dockerfile` | Smaller attack surface; orchestrators can probe `/healthz` |
| CI: lint, test, build, `npm audit` | `.github/workflows/ci.yml` | Nothing merges unless it passes |

## Running in a container

```
docker build -t airline-web .
docker run -p 5173:8080 -e API_GATEWAY_URL=http://host.docker.internal:5000 airline-web
```

Or run it with the full stack: the `web` service in `docker-compose/docker-compose.yml` builds this
folder and publishes it on http://localhost:5173.

For a real deployment, set the backend's `PAYMENT_CALLBACK_BASE_URL` to the site's public URL
(Razorpay redirects customers there after paying) and terminate TLS in front of the container
(add HSTS there).

## Changing things

- **Brand name, support contacts:** `BRAND` in `core/config/app-config.ts`.
- **Colours and spacing:** `src/styles/_tokens.scss`. Check text contrast when you change colours.
- **A backend DTO changed:** update `core/models/api-models.ts`; the compiler then shows every place to fix.
- **A new page:** add a component under `features/`, then a route in `app.routes.ts` with `loadComponent`
  and a `title`.

## Not built yet

- Airline back office (fleet, schedules, fares) and system admin screens. The API supports them
  (`AccessPolicy` in the gateway), and they would live under their own lazy-loaded routes and guards.
- Refunds or changes for paid bookings (the API only allows cancelling unpaid bookings online).
- Translations. Text is English-only; Angular's i18n tooling can be added when needed.
- End-to-end browser tests in CI (e.g. Playwright against the Compose stack).
