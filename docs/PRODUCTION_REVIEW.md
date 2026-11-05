# Production readiness review

Scope: the whole repository (11 deployables, about 250 Java files), reviewed against common practice for airline
reservation backends: PNR-centric bookings, inventory holds during checkout, server-side pricing,
gateway-verified payments and idempotent event processing.

Each phase is a separate commit, so any change can be reviewed or reverted on its own (`git log`).

## Critical findings (fixed)

| # | Area | Finding | Impact | Fix |
|---|---|---|---|---|
| 1 | Gateway | Client-supplied `X-User-Id` / `X-User-Roles` were forwarded; the gateway *appended* its own values and services read the first one | Anyone could impersonate any user or become admin by sending a header | Gateway always strips identity headers before setting verified ones |
| 2 | Payments | `POST /api/payments/initiate` was public and took `amount` from the client | Pay 1 rupee for any booking | Endpoint moved to `/internal`; amount computed by booking-service |
| 3 | Payments | Verify trusted any captured payment and re-published `payment.completed` on every call | Replay, double confirmation | Amount, currency and owner verified against Razorpay; row lock plus idempotent settlement; unique provider ids |
| 4 | Pricing | Booking total omitted the fare entirely (`meals + seats + ancillaries`) | Flights sold for the price of a seat upgrade | `BookingPriceCalculator`, fare x passengers, unit-tested |
| 5 | Pricing | `Fare.getTotalPrice()` added `currentPrice`, which already contained base + taxes + fees | Fares double-counted | Single definition of the all-inclusive price |
| 6 | Seats | No hold at checkout, and the "mark seats booked" path read a hard-coded empty list | Same seat sold many times; seats never shown as taken | Atomic all-or-nothing holds (row locks, TTL, expiry job), confirm/release via events |
| 7 | AuthZ | Only `GET /api/airlines` was admin-guarded; `/approve`, `/ban`, city/airport writes etc. were open to any user | Any customer could approve or ban airlines | Central deny-by-default `AccessPolicy`, plus service-side checks |
| 8 | AuthZ | Owners could set their airline's `status` on create/update | A banned airline could un-ban itself | Status is admin-only; new airlines start `PENDING_APPROVAL` |
| 9 | AuthZ | No ownership checks on bookings, flights, schedules, instances or fares | Read or cancel other people's bookings; edit other airlines' flights and fares | Ownership enforced in services (404 for foreign resources) |
| 10 | Secrets | Razorpay key/secret, DB password and JWT key committed; admin `test123` hard-coded | Credential compromise | All secrets from the environment, no defaults; opt-in bootstrap admin |
| 11 | Auth | Signup accepted any role but admin; login errors revealed whether an email exists; tokens logged | Privilege abuse, account enumeration, token leakage | Validated `SignupRequest`, generic errors with constant work, no token logging |

## High / medium findings (fixed)

- **Schema management:** `ddl-auto: update` in every service replaced by Flyway baselines + `validate`; three
  enums stored as ordinals and native MySQL `ENUM` columns converted to `VARCHAR`.
- **Error handling:** about 100 `throw new Exception(...)` calls produced 500s for "not found". There is now a
  typed hierarchy rendered as RFC 9457 problem details, and Feign keeps downstream 404/409/422 meanings.
- **Kafka:** hand-rolled factories had no acks or idempotence, no keys, no retry and no dead-letter topic, and
  topics were auto-created. Now there are `acks=all` idempotent producers keyed by aggregate id, retry plus DLT,
  declared topics and idempotent consumers.
- **Events published inside transactions** could announce rolled-back state; they are now published after commit.
- **Booking** created passengers outside the booking (the FK was never written), issued tickets before payment,
  and its read model returned empty placeholders. It now keeps a PNR snapshot, tickets after payment, compensation
  on failure, and paged airline search.
- **Flight ops:** `updateFlight` never saved; schedule expansion passed an airline id where a user id was expected;
  overnight flights arrived before departing; 4xN remote calls per page; `availableSeats` was never updated.
- **Money** used `double` everywhere; it is now `BigDecimal` with explicit currency.
- **Misc:** `orElseThrow(null)` NPE, a no-op duplicate check (`if (...);`), a misnested Eureka config in
  booking-service, user-service pointing at the location database, a `"contanct"` typo dropping customer phone
  numbers, entities serialized directly in `GET /api/fares`, and an off-by-one passenger age.
- **Build/ops:** 11 duplicate Maven wrappers, unversioned plugins, Jib images running as root, Compose Kafka
  unreachable from containers, "localhost only" DB ports bound to all interfaces, and no CI, health probes,
  metrics, tracing or tests.

## Remaining gaps (recommended next steps)

These need decisions or infrastructure beyond the codebase:

1. **Rotate the leaked credentials now.** The Razorpay test key and secret, the DB password and the JWT key were
   in source control. Treat them as compromised even though they are gone from the current tree. They are still in
   the first commit of this repository (`Baseline snapshot before production hardening`). Before pushing it
   anywhere, rotate them, and either start from a fresh history or remove that commit's secrets
   (e.g. `git filter-repo`).
2. **Transactional outbox.** Events are published after commit; a crash in between loses one (the Razorpay
   webhook and idempotent consumers limit the damage). Add an outbox table plus relay (or Debezium) in
   payment-service and booking-service.
3. **Refunds and cancellation of ticketed bookings.** Cancelling a `CONFIRMED` booking is currently rejected
   (`REFUND_REQUIRED`); paid-after-expiry cases are logged as `ACTION REQUIRED`. Both need a refund workflow
   (Razorpay refunds API, `TicketStatus.REFUNDED`, seat release).
4. **Service-to-service trust.** Services trust gateway headers because they are network-isolated. In
   production, add mTLS (service mesh) or have services verify the JWT themselves (RS256 + JWKS from
   user-service), and use refresh tokens with revocation.
5. **Resilience.** Feign has strict timeouts but no circuit breakers. Global breakers were removed deliberately:
   without fallbacks they wrap every client error and hide 404/409 from callers. Add Resilience4j per client
   where a degraded answer exists (for example, search without enrichment).
6. **Rate limiting and WAF** on `/auth/login`, `/auth/signup` and search, at the ingress or with the gateway's
   Bucket4j filter.
7. **Remaining cross-tenant writes.** Ownership is enforced for flights, schedules, instances, fares, airlines and
   aircraft. Cabin classes, seat maps, meals, ancillaries, fare rules and baggage policies are restricted to airline
   owners at the gateway but should also verify the owning airline in-service.
8. **Shared DTO module.** `common-lib` holds every service's API DTOs, which couples deployments. Move each API's
   DTOs into the owning service (consumers keep their own client models) and keep only event contracts shared,
   ideally with a schema registry.
9. **Integration tests** with Testcontainers (MySQL + Kafka) for the booking -> payment -> seat saga. Unit tests
   cover the critical rules; the Flyway/validate boot was verified manually for all nine services.
10. **Data migration.** The schema baseline is new. Existing development databases created by
    `ddl-auto: update` must be recreated, or baselined with `flyway baseline` after manually aligning them.
11. **API changes for the frontend:**
    - `GET /api/bookings/airline` and `GET /api/users` are now paged.
    - Payment verify accepts only `razorpayPaymentId`.
    - Signup takes the new validated fields (password at least 10 characters).
    - Seat-instance price and `/price/total` endpoints moved to `/internal`.
    - Error bodies are problem details.
