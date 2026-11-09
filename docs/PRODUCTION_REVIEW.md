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

## Second phase: remaining gaps closed

The gaps left open by the first phase, plus centralized configuration, a distributed cache and an event-driven
notification service. All of it has tests; the Testcontainers tests run against real MySQL, Kafka and Redis.

| # | Gap | What was built | Where |
|---|---|---|---|
| 2 | Events lost between commit and send | **Transactional outbox** in common-lib: events are inserted in the business transaction and relayed to Kafka after commit (ordered per key, `SELECT ... FOR UPDATE` at READ COMMITTED, permanent failures parked as `FAILED`, metrics `outbox_events{state}`, trace context carried over). Used by booking, payment, flight-ops and user-service | `common/outbox`, `V*__transactional_outbox.sql` |
| 3 | No refunds | **Refund workflow.** Cancelling a ticketed booking applies the fare's rules (`RefundPolicy`: departed, non-refundable, deadline, fee per passenger), voids tickets, releases seats and publishes `refund.requested`; payments that arrive after cancellation or with the wrong amount are refunded too. payment-service refunds through Razorpay, idempotent per refund key (unique key, atomic claim, key stored in the provider's notes), finished by webhook (`refund.processed/failed`) or reconciliation, and publishes `payment.refunded` / `payment.refund-failed`. Tickets become `REFUNDED`; the customer is told | booking `RefundPolicy`, payment `RefundService`, `refund` table |
| 4 | Services trusted gateway headers; no refresh or revocation | **RS256 tokens with JWKS** (`/.well-known/jwks.json`, key rotation by `kid`), verified by the gateway and every service (see *Third phase* below: until then only user-service actually did), token relay on Feign calls. **Refresh tokens**: opaque, hashed at rest, single-use, rotated per family; reuse of a spent token revokes the family and all access tokens. **Revocation** in Redis (logout, "sign out everywhere"). Access tokens now live 15 minutes; the web app refreshes silently (shared within a tab, serialized across tabs) | `common/security`, user `SigningKeys`, `RefreshTokenService` |
| 5 | No circuit breakers | **Resilience4j** where a degraded answer exists: airline/aircraft/airport enrichment in flight-ops. An outage yields `partial: true` responses (never cached) instead of 500s; 4xx answers do not open the breaker. Runs on the caller's thread so token relay and tracing keep working | flight-ops `ReferenceData` |
| 6 | No rate limiting | **Distributed rate limits** at the gateway: Redis token buckets (atomic Lua, Redis clock) per client IP, with `X-Forwarded-For` believed only from trusted proxies. Policies for login, signup, token refresh, search and everything else; webhooks exempt; 429 with `Retry-After` | gateway `ratelimit`, `config-repo/api-gateway.yaml` |
| 7 | Cross-tenant writes | **Ownership checks in-service** for cabin classes, seat maps, flight-instance cabins, meals, ancillaries, flight meals, flight-cabin ancillaries, insurance coverages, fare rules and baggage policies (404 for foreign resources). The owning airline is stamped server-side instead of read from the request | `SeatOwnership`, `AncillaryOwnership`, `FlightOwnershipVerifier` |
| 8 | common-lib held every API's DTOs | **API models moved to their owners** (`<service>.payload.*`); consumers keep their own copies (`<service>.client.dto`). common-lib keeps only event contracts, enums, embeddables and cross-cutting infrastructure | all services |
| 9 | No integration tests | **Testcontainers** saga tests: booking (create, `payment.completed`, cancel, `refund.requested`, `payment.refunded`), payment (verify once, refund once), seat (held, booked, released), notification (Kafka to e-mail and inbox), cache (location), rate limiter (Redis). They run in CI | `*IntegrationTest`, `RateLimitTest` |
| 10 | Databases created by `ddl-auto` | One-off `FLYWAY_BASELINE_ON_MIGRATE=true` (see below) | `config-repo/application.yaml` |
| - | Config scattered in 11 jars | **Spring Cloud Config server** (`cloud/config-server`, port 8888, HTTP basic auth). `config-repo/` holds platform-wide, per-service and per-profile files; jars only carry bootstrap settings. Native backend by default (baked into the image, mounted in Compose), Git backend with `SPRING_PROFILES_ACTIVE=git`. A test fails the build if a plaintext secret is committed to `config-repo/` | `config-repo/`, `cloud/config-server` |
| - | No cache | **Redis distributed cache** (`RedisCachingAutoConfiguration`): typed caches declared with `CacheSpec` (no polymorphic deserialization), per-service key namespace and version, TTL per cache, eviction after commit, fails open when Redis is down. Airlines (by id and by owner, used by every service), aircraft, airports, cities, fares and flights | `common/cache`, `*CacheConfig` |
| - | No customer notifications | **notification-service** (port 5012): consumes `user.registered`, `booking.confirmed`, `booking.cancelled`, `booking.refunded`; idempotent per event and channel; HTML e-mails (Thymeleaf) over SMTP with retries and backoff (an SMTP outage never blocks Kafka); in-app inbox `GET /api/notifications`; personal data purged after 180 days. Mailpit catches mail locally | `services/notification-service` |

Fixed along the way: location `updateCity` rejected keeping a city's own code; `createAirport` never evicted the
airport lists; fare rules lookup returned 500 instead of 404 when a fare had none; baggage policies never stored
their airline, so listing an airline's policies always came back empty.

## Third phase: Spring Security

Token verification and access control moved from hand-written classes to Spring Security's OAuth2 resource
server, with identical behaviour proven by black-box parity tests written first against the old code (gateway:
every access rule for every kind of caller, invalid tokens, headers, CORS, 503, management port; services:
identity, error codes, anonymous reads, `/internal`). Details, and a plain-language explanation of each part:
[SECURITY_MIGRATION_PLAN.md](../SECURITY_MIGRATION_PLAN.md).

| Before | Now |
|---|---|
| `AccessPolicy` + `AuthenticationFilter` (gateway) | `GatewayAccessRules` + `authorizeHttpRequests` + resource server |
| `AccessTokenVerifier`, `JwksKeyResolver` | `NimbusJwtDecoder` over a Nimbus `JWKSource`, `AccessTokenValidators` |
| `JwtAuthenticationFilter` rewriting `X-User-*` headers | `BearerTokenAuthenticationFilter`; `CurrentUser` from the verified `Jwt` |

**Found and fixed during the migration:**

- **Nine of ten services did not verify tokens.** The old servlet filter was conditional on jjwt, which only
  user-service had on its classpath, so the other services trusted `X-User-*` headers and relied on the gateway
  and network isolation. The second-phase claim above ("verified by every service") was therefore wrong. Every
  service now verifies the token itself.
- Twelve controller parameters and five Feign clients passed the caller's identity as an `X-User-Id` header;
  they now use `CurrentUser` and the relayed token, and CI rejects `X-User-` in service code.
- The gateway's security-headers filter was registered without an effective order and ran after everything
  else; it now runs first and covers every response.
- The header-trusting switch `SERVICE_AUTH_MODE=headers` is gone.

**Behaviour that changed on purpose:**

- A token without `exp` is rejected (before: accepted if otherwise valid; user-service always sets `exp`).
- Unknown paths at the gateway answer the same 404 body as hidden ones (`"detail":"Not found"`,
  `"code":"NOT_FOUND"`) instead of Spring MVC's `"No static resource ..."`. Status and content type unchanged.
- A direct call to a service endpoint that needs a caller, without a token, is 401 (before: 400 for the
  endpoints that read the header directly). Through the gateway this cannot happen.

## What still needs action outside the code

1. **Rotate the leaked credentials.** The Razorpay test key and secret, the DB password and the old JWT secret
   were committed in the first commit (`Baseline snapshot before production hardening`). The JWT secret is no
   longer used (tokens are now RS256), but the other values must be rotated at the provider and in every `.env`.
   Before pushing the history anywhere public, remove them:
   ```bash
   pip install git-filter-repo
   # replacements.txt: one "<old secret>==>REDACTED" line per leaked value
   git filter-repo --replace-text replacements.txt
   ```
   CI now runs a gitleaks secret scan on every push and pull request, so a new leak fails the build.
2. **mTLS between services.** Services verify the caller's JWT themselves (since the third phase), so identity
   cannot be forged even from inside the network. Service-to-service endpoints (`/internal/**`) and Kafka are still protected by network
   isolation only. In Kubernetes, enable a mesh with strict mTLS (Istio `PeerAuthentication: STRICT`, Linkerd)
   and SASL/TLS on Kafka; nothing in the code needs to change.
3. **WAF.** Rate limits are in the gateway; put a WAF (Cloudflare, AWS WAF, or Coraza in front of Caddy) at the
   edge for OWASP rules and bot management. Per-account login throttling (across IPs) would also belong there or
   in user-service.
4. **Schema registry.** Event contracts are shared Java classes in common-lib, versioned with the code and
   backward compatible by convention (only additive fields; consumers ignore unknown ones). A schema registry
   (Confluent, Apicurio) with compatibility checks in CI is the next step once teams deploy independently.
5. **Redis and SMTP in production.** Use a managed Redis with TLS (`REDIS_SSL_ENABLED=true`) and a real SMTP
   provider (SES, Postmark, SendGrid) with SPF/DKIM/DMARC for the sender domain.
6. **Data migration of old databases.** Databases created by Flyway V1 upgrade automatically (V2+ only add tables
   and columns). A database created by the old `ddl-auto: update` must be recreated, or, if its schema matches
   V1 exactly (compare with `src/main/resources/db/migration/V1__baseline_schema.sql`), started once with
   `FLYWAY_BASELINE_ON_MIGRATE=true` and then without it.

## API changes for the frontend and other clients

- Login, signup and `POST /auth/refresh` return `refreshToken`, `accessTokenExpiresAt` and
  `refreshTokenExpiresAt`; the access token (`jwt`) lives 15 minutes. `POST /auth/logout` ends the session;
  `POST /api/users/me/logout-all` ends all of them. The web app handles all of this.
- `PATCH /api/bookings/{id}/cancel` now also cancels **confirmed** bookings (before departure) with a refund
  under the fare's rules. Bookings carry `refundStatus`, `refundAmount` and `refundedAt`.
- New: `GET /api/notifications`, `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read`,
  `POST /api/notifications/read-all`.
- Flight and flight-instance responses may carry `partial: true` when airline/airport details are temporarily
  unavailable.
- Too many requests answer `429` with `Retry-After`; an unreachable token issuer answers `503`, not `401`.
- From the first phase: paged `GET /api/bookings/airline` and `GET /api/users`; payment verify takes only
  `razorpayPaymentId`; validated signup; seat price and `/price/total` moved to `/internal`; problem-details errors.
