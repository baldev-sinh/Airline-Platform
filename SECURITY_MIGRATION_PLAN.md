# Security migration plan: hand-written JWT handling → Spring Security

> Status: approved. Decisions taken during planning: **keep the `/api/**` catch-all** and **keep both
> error-code sets exactly**. A "What changed and why" section is added at the end once the migration is done.

## Context

Today, token verification and access control are custom code. The gateway runs `AuthenticationFilter` with an
`AccessPolicy` table. Each service runs a servlet `JwtAuthenticationFilter` that rewrites `X-User-*` headers, and
`CurrentUser` is read back from those headers. The goal is to use standard Spring Security components (OAuth2
Resource Server, `authorizeHttpRequests`, entry points, validators) with **identical** externally visible
behaviour. Custom code stays only where Spring Security has no equivalent (revocation, the JWKS fetch through
Eureka, problem-detail bodies), and it plugs in through Spring Security's or Nimbus's extension points.

**Versions** (from the POMs): Spring Boot **4.1.2**, which manages Spring Security **7.1.2** and Spring Framework
7.0.10; Spring Cloud 2025.1.4; Java 21; jjwt 0.13.0. Security 7 supports only the lambda DSL and
`SecurityFilterChain` beans, and request matching is path-pattern based (`PathPatternRequestMatcher`; the Ant/Mvc
matchers are gone). The Boot 4 starter is `spring-boot-starter-security-oauth2-resource-server`; I'll confirm the
exact artifact name with Maven at the start of Phase 2.

## Findings that differ from the prompt (reported, not guessed)

1. **The catch-all rule:** `ANY /api/** → AUTHENTICATED` is the last rule, so unlisted `/api` paths need sign-in rather than being denied. **Kept**, as you decided.
2. **Service count:** there are **10** business services (notification-service is the tenth), plus gateway, registry and config-server.
3. **Error codes differ by layer:**
   - Gateway: `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `SERVICE_UNAVAILABLE`. These come from a `ResponseStatusException` rendered by `GlobalExceptionHandler.handleResponseStatus`.
   - Services: `INVALID_TOKEN`, `TOKEN_REVOKED`, `AUTH_UNAVAILABLE` from `JwtAuthenticationFilter`, and `UNAUTHORIZED` from `CurrentUserArgumentResolver`.

   **Both sets are kept** byte-for-byte, as you decided.
4. **`exp` is not strictly required today.** jjwt validates it only when present. Your prompt requires it, which is a *tightening*. user-service always sets `exp`, so no real token is affected.
5. **A token without `kid` is rejected today** (`JwksKeyResolver.resolve(null)` returns null). Nimbus would try every key, so a custom `JWSKeySelector` keeps the rejection.
6. **Public routes ignore the token at the gateway.** Today an expired token on `/auth/refresh` or `/api/flights/search` is ignored there. A stock resource server would answer 401 and break silent refresh, so a `BearerTokenResolver` must skip public paths. The *service* layer, however, does 401 an invalid token on any `/api/**` path today, public or not, and that is kept.
7. **Services are not authorization points today.** Public search makes **anonymous** Feign calls to `/api/airlines/{id}`, `/api/airports/{id}`, `/api/fares/lowest/...` and `/api/cabin-classes/...`. Services therefore must keep `permitAll` and authenticate only *when a token is present*. Requiring authentication everywhere would break search.
8. **12 controller parameters read `@RequestHeader("X-User-Id")` directly**, in airline-core's `AirlineController` and `AircraftController` and ancillary's `AncillaryController` and `MealController`. Five Feign `AirlineClient`s send `X-User-Id` explicitly to `/api/airlines/admin`. Today this is only safe because the service filter overwrites the header with the token's identity. After the migration nobody rewrites headers, so these parameters **must** move to `CurrentUser`. Otherwise they would trust an unverified header.
9. **Services expose actuator on their main port** (Prometheus scrapes `booking-service:5009`, for example), including `POST /actuator/refresh`. The new service chain must keep it open, and CSRF must stay off. The committed `docs/diagrams/airline-architecture.drawio` wrongly says `:51xx`, so I'll fix that in Phase 3.
10. **Stale comment:** user-service `SecurityConfig` says it "does not re-authenticate". It does, through the common-lib servlet filter that runs before Spring Security.
11. **Tests use a header-trust mode:** `airline.security.service-auth.mode=headers` (env `SERVICE_AUTH_MODE`) is a switch that trusts headers, and five Testcontainers tests use it. It will be **removed**, which is a tightening, and the tests will send real tokens or use `spring-security-test`'s `jwt()`.
12. **The gateway's security headers cover 429 responses.** `EdgeWebConfig.securityHeadersFilter` runs at `HIGHEST_PRECEDENCE`, before the rate limiter. Spring Security's header writer would run *after* the rate limiter and miss those 429s. So this filter **stays**, and Spring Security's own header writing is disabled in the gateway chain to avoid conflicting `Cache-Control` values.
13. **`/api/seats/**` is routed but not in the owner-only list.** It only has `GET` endpoints, so this is not a gap.

## a. Parity table: AccessPolicy → gateway `authorizeHttpRequests` (first match wins, same order)

`WRITE` = POST, PUT, PATCH, DELETE. `ANY` = every method. The new code builds every matcher with `PathPatternRequestMatcher`, the same pattern syntax `AccessPolicy` uses today.

| # | Method | Path | Today | Spring Security |
|---|---|---|---|---|
| 1 | ANY | `/internal/**` | DENY → 404 | `denyAll()` + 404 entry point / denied handler for "deny" requests |
| 2 | ANY | `/actuator/**` | DENY → 404 | same as 1 |
| 3 | POST | `/api/fares/batch-by-ids` | DENY → 404 | same as 1 |
| 4–7 | POST | `/auth/login`, `/auth/signup`, `/auth/refresh`, `/auth/logout` | PUBLIC | `permitAll()` + bearer token ignored |
| 8 | GET | `/api/flights/search` | PUBLIC | `permitAll()` + bearer token ignored |
| 9 | GET | `/api/cities/**` | PUBLIC | same as 8 |
| 10 | GET | `/api/airports/**` | PUBLIC | same as 8 |
| 11 | GET | `/api/airlines/dropdown` | PUBLIC | same as 8 |
| 12 | POST | `/api/payments/webhooks/razorpay` | PUBLIC | same as 8 |
| 13–14 | WRITE | `/api/cities/**`, `/api/airports/**` | ADMIN | `hasAuthority("ROLE_SYSTEM_ADMIN")` |
| 15 | GET | `/api/airlines` | ADMIN | same as 13 |
| 16–18 | POST | `/api/airlines/{id}/approve`, `/suspend`, `/ban` | ADMIN | same as 13 |
| 19 | GET | `/api/users/profile` | AUTHENTICATED | `authenticated()` |
| 20 | POST | `/api/users/me/logout-all` | AUTHENTICATED | `authenticated()` |
| 21 | ANY | `/api/users/**` | ADMIN | `hasAuthority("ROLE_SYSTEM_ADMIN")` |
| 22 | GET | `/api/payments` | ADMIN | same as 21 |
| 23 | DELETE | `/api/bookings/{id}` | ADMIN | same as 21 |
| 24 | GET | `/api/bookings/airline` | AIRLINE_OWNER | `hasAnyAuthority("ROLE_SYSTEM_ADMIN","ROLE_AIRLINE_OWNER")` |
| 25 | WRITE | `/api/airlines` | AIRLINE_OWNER | same as 24 |
| 26 | DELETE | `/api/airlines/{id}` | AIRLINE_OWNER | same as 24 |
| 27 | POST | `/api/fares/search` | AUTHENTICATED | `authenticated()` |
| 28–42 | WRITE | the 15 `AIRLINE_MANAGED` patterns (aircrafts, flights, flight-instances, flight-schedules, fares, fare-rules, baggage-policies, cabin-classes, seat-maps, flight-instance-cabins, meals, ancillaries, insurance-coverages, flight-meals, flight-cabin-ancillaries `/**`) | AIRLINE_OWNER | same as 24 |
| 43 | ANY | `/api/**` | AUTHENTICATED | `authenticated()` (catch-all kept) |
| 44 | ANY | anything else | DENY → 404 | `anyRequest().denyAll()` + 404 |

The rules live in one ordered list, `GatewayAccessRules`. It feeds both `authorizeHttpRequests` and a
`RequestMatcher` that is true when "the first matching rule is DENY, or nothing matches". That matcher selects the
404 entry point and denied handler. A plain `denyAll()` would give anonymous callers **401**, because Spring
routes anonymous denials to the entry point, so the 404 must be wired explicitly. Exact role matching is unchanged:
`ROLE_SYSTEM_ADMIN_TRAINEE` does not match. Error and async dispatches are permitted so an error response keeps
its original status. Every row above becomes a parameterized parity test case; the existing `AccessPolicyTest`
CSV rows are reused.

## b. Behaviour table (must stay identical; each row is a parity test)

| Behaviour | Gateway today → after | Services today → after |
|---|---|---|
| DENY and unlisted paths | 404 `NOT_FOUND` → 404 via the deny entry point / denied handler | n/a (not routed) |
| Missing token on a protected rule | 401 `UNAUTHORIZED` → same body, written by `ProblemDetailsAuthenticationEntryPoint` | Anonymous; an endpoint needing `CurrentUser` → 401 `UNAUTHORIZED` "Authentication required" (unchanged) |
| Invalid, expired, wrong `iss`/`aud`, `alg=none`, HS256, no `kid`, non-numeric `sub` | 401 `UNAUTHORIZED` "Missing or invalid access token" | 401 `INVALID_TOKEN` + `WWW-Authenticate: Bearer` |
| Revoked (jti, or issued before `revoked-before`) | 401 `UNAUTHORIZED` "Access token has been revoked" | 401 `TOKEN_REVOKED` |
| Valid token, wrong role | 403 `FORBIDDEN` "Insufficient permissions" + the same INFO log line → `ProblemDetailsAccessDeniedHandler` | n/a (in-service `ForbiddenException` checks unchanged) |
| Signing keys unavailable, nothing cached | 503 `SERVICE_UNAVAILABLE` → failure handler maps `AuthenticationServiceException` to 503 | 503 `AUTH_UNAVAILABLE` |
| Issuer down, keys cached | keeps working | keeps working |
| Error bodies | RFC 9457 problem details; the parity tests first record the exact current JSON | same |
| Client `X-User-*` headers | always stripped before proxying → Gateway MVC built-in `removeRequestHeader` route filter | never read; identity comes only from the verified `Jwt` in the `SecurityContext` |
| Algorithm and claims | RS256 only; `iss`, `aud` required; 30 s skew; `exp` now required (tightening, finding 4); `kid` required | same |
| Revocation | Redis keys `airline:auth:revoked-jti:*` and `revoked-before:*`, fail-open configurable → `RevocationValidator` (`OAuth2TokenValidator<Jwt>`) on the same keys | same |
| Paths that ignore a token | public rules (token not even parsed) | everything outside `/api/**` (`/internal`, `/actuator`, `/auth`, `/.well-known`) |
| Key cache | 10 min max-age; refetch on unknown `kid` at most every 30 s; last keys survive an outage → a Nimbus `JWKSource` built with these exact settings | same |
| CORS | allow-list from `CORS_ALLOWED_ORIGINS`, same methods, headers, exposed headers and max-age → a `CorsConfigurationSource` used by `http.cors()`; preflights never need a token | n/a |
| Security headers | `EdgeWebConfig` filter kept (finding 12) | n/a |
| Gateway actuator | only on management port 5100; `/actuator/**` on 5000 → 404 → management chain matched by local port, `permitAll` | actuator on the main port stays open (finding 9) |
| Rate limit before authentication | `RateLimitFilter` at `HIGHEST+10` runs before Spring Security (-100) | n/a |
| Stateless, no CSRF, no form login, basic auth or sessions | n/a | same; `POST /actuator/refresh` and webhooks keep working |

## c. Roles and identity

- **Converter:** `JwtAuthenticationConverter` with `JwtGrantedAuthoritiesConverter`, `authoritiesClaimName("roles")`
  and `authorityPrefix("")`. The claim already holds `ROLE_*`, so authorities are exactly `ROLE_USER`,
  `ROLE_AIRLINE_OWNER` and `ROLE_SYSTEM_ADMIN`. The principal claim is `sub`.
- **`CurrentUser` stays**, because 19 controllers use it. `CurrentUserArgumentResolver` now builds it from the
  `JwtAuthenticationToken` in the `SecurityContext` (`sub` → id, `email`, `roles`). If there is none, it throws
  the same 401 `UNAUTHORIZED` "Authentication required". New code may use `@AuthenticationPrincipal Jwt`.
- **The 12 `@RequestHeader("X-User-Id")` parameters become `CurrentUser`** (finding 8). The five Feign
  `getAirlineByOwner` methods drop their `X-User-Id` parameter; airline-core takes the owner from the relayed token,
  which is exactly what happens today.
- **Ownership checks are untouched**: `SeatOwnership`, `AncillaryOwnership`, `FlightOwnershipVerifier`, booking's
  own-booking checks and `UserController` checks.

## d. Service-to-service calls

- **Today:**
  - Feign's `bearerTokenRelay` copies the caller's `Authorization` header.
  - `/api/**` callees verify that token; with no token the call is anonymous.
  - `/internal/**` is trusted by network isolation only; the gateway never routes it.
  - Kafka and scheduled jobs call only endpoints that need no user.
- **After:** the same. The relay stays, `/api/**` callees authenticate the relayed token through the resource
  server, and `/internal/**` stays `permitAll` with any token ignored. That matters because a relayed token can
  expire in the middle of a request and must not 401 an internal call.
- **Options for later (not in this migration):**

  | Option | For | Against |
  |---|---|---|
  | A. User-token relay (current, kept) | No new infrastructure; keeps the user's identity end to end | `/internal` stays network-trusted; no identity for jobs |
  | B. Client-credentials service tokens | Every call authenticated, including `/internal` and jobs | Needs an authorization server (see e) and secrets per service |
  | C. mTLS mesh (Istio/Linkerd, already listed as a gap) | Transport identity with no code change | Platform work; no user identity |

  Recommendation: A now, then C.

## e. Token issuing

- **Kept:** user-service stays the issuer. jjwt signing in `JwtProvider` is unchanged, as are the JWKS endpoint,
  `SigningKeys` and the refresh, logout and logout-all logic. Token bytes are unchanged. Swapping jjwt for
  Spring's `NimbusJwtEncoder` could change `aud` and `typ` serialization, which counts as a token-format change
  and needs your approval, so it is out of scope.
- **Alternatives:**

  | Option | For | Against |
  |---|---|---|
  | Spring Authorization Server | Standard OAuth2/OIDC: discovery, client credentials for option d.B, consent | Refresh rotation, reuse detection, family revocation and our response shape must be rebuilt with customizers; new endpoints change the API |
  | Keycloak | Mature: MFA, social login, admin UI, brute-force protection | Extra infrastructure; migrating users and bcrypt hashes; a claim mapper for numeric `sub` and `roles`; different login API; refresh semantics change |

  Recommendation: keep user-service now. Re-evaluate when service credentials or MFA are needed.

## f. Files and commit sequence (branch `feature/spring-security-migration`, build green after each commit)

1. **`docs: add SECURITY_MIGRATION_PLAN.md`**: this plan.
2. **`test: gateway security parity tests`**: `cloud/api-gateway/src/test/.../GatewaySecurityParityTest.java`.
   - A black-box `@SpringBootTest` with MockMvc, plus RANDOM_PORT for the management-port case.
   - A JDK `HttpServer` stub serves the JWKS and echoes the headers it receives.
   - Simple-discovery instances point `user-service` and every routed service at that stub.
   - Redis comes from Testcontainers, with `disabledWithoutDocker`.
   - Tokens are minted in the test.
   - Assertions cover status, the **exact problem body**, the headers forwarded downstream, and every row of a and b.
   - These tests touch no internal classes, so they pass unchanged before and after the migration.
3. **`test: service security parity tests`**: `common-lib/src/test/.../ServiceSecurityParityTest.java`, a minimal
   test app with `/api/me` (`CurrentUser`), `/api/public`, `/internal/x` and a POST endpoint. It covers anonymous
   access, spoofed headers, invalid, revoked, keys unavailable, no CSRF and `/internal` ignoring tokens. It also
   adds `@RequestHeader` → `CurrentUser` guard tests for airline-core and ancillary. Green on the current code.
4. **`refactor: read caller identity via CurrentUser, not X-User-Id`**: changes the 12 parameters in the
   airline-core and ancillary controllers and the five Feign `AirlineClient`s, and adds a CI step that fails if
   `"X-User-` appears in `services/*/src/main`. Green on the current code, because the filter still rewrites headers.
5. **`feat(common-lib): resource-server building blocks`** in the new package `common/security/resource/`:
   - `AirlineJwtDecoders`: Nimbus `DefaultJWTProcessor` with an RS256 `JWSVerificationKeySelector` that requires
     `kid`, and a `JWKSource` from `JWKSourceBuilder` (cache 10 min, rate-limited 30 s, outage-tolerant) using a
     load-balanced `ResourceRetriever`. Wrapped as `new NimbusJwtDecoder(processor)`.
   - Validators: issuer, audience, `JwtTimestampValidator(30s)` with `exp` required, numeric `sub`, and `RevocationValidator`.
   - The roles converter.
   - `ProblemDetailsAuthenticationEntryPoint`, `ProblemDetailsAccessDeniedHandler` and a 503 failure handler. Each
     has a "gateway" and a "service" code profile.
   - `PathAwareBearerTokenResolver`.
   - `AirlineResourceServerConfigurer`, a `Customizer<HttpSecurity>`.
   - `TokenRevocations` keeps its Redis key format, but its signature changes to take `Jwt`.

   Unit tests for each class. Nothing is wired yet.
6. **`feat(services): verify tokens with Spring Security resource server`**:
   - Adds the starter in `services/pom.xml`.
   - A `ServiceSecurityAutoConfiguration` provides a default `SecurityFilterChain`: stateless, `permitAll`,
     resource server on `/api/**`, actuator open.
   - `CurrentUserArgumentResolver` reads the `SecurityContext`.
   - The servlet `JwtAuthenticationFilter` registration and the headers mode are removed.
   - user-service `SecurityConfig` applies the shared customizer and a `JwtDecoder` over `SigningKeys` (an
     `ImmutableJWKSet` of current and previous keys). `AuthServiceImpl.logout` uses `JwtDecoder`.
   - The five Testcontainers tests switch to real tokens or `jwt()`.
7. **`feat(gateway): authorize with Spring Security`**:
   - `GatewaySecurityConfig` holds the main chain from `GatewayAccessRules` and the management-port chain.
   - `RouteConfig` replaces `.before(authenticationFilter)` with built-in `removeRequestHeader` filters for the three `X-User-*` headers.
   - CORS moves to a `CorsConfigurationSource` bean (`EdgeWebConfig` keeps the headers filter).
   - Adds the resource-server starter and removes jjwt from the gateway POM.
8. **`chore: remove the hand-written token classes`**. Deleted:
   - `AccessPolicy`, `AuthenticationFilter`, `AuthenticationFilterTest`
   - `AccessTokenVerifier`, `JwksKeyResolver` (its `Jwk` record moves to user-service, so the JWKS JSON is unchanged)
   - `JwtAuthenticationFilter`, `JwtAuthenticationFilterTest`
   - the old beans in `TokenSecurityAutoConfiguration`
   - `UserServiceConfig.jwksKeyResolver`
   - jjwt in common-lib (kept as a test dependency for minting tokens)

   `AccessPolicyTest`'s rows have already moved into the parity test. `RequestHeaders` stays, for the stripping.
9. **`docs: security migration results`**: README security section; `PRODUCTION_REVIEW.md` (what changed and the
   remaining gaps); the plan's "What changed and why" section for a junior developer, mapping each removed class
   to its Spring component; the drawio `:51xx` fix.

Config: no new secrets. `airline.security.jwt.*` and `revocation.*` keep their env vars (`JWT_ISSUER`,
`JWT_AUDIENCE`, `JWT_JWKS_URI`, `TOKEN_REVOCATION_FAIL_OPEN`); `SERVICE_AUTH_MODE` is removed. The app fails to
start if the issuer, audience or JWKS URI is blank.

## g. Risks and open questions

- **APIs I couldn't check offline:** the exact Security 7.1.2 API shapes (`JwtTimestampValidator` requiring `exp`,
  an audience validator, `JWKSourceBuilder` outage tolerance, the default `typ` check, the Boot 4
  `EndpointRequest` package and the starter name) are not in the local Maven cache. I'll verify them against the
  downloaded jars before coding step 5. If Security 7 rejects our tokens' `typ` by default, I'll stop and tell you
  rather than change the token format.
- **Subtle parity points:** `denyAll()` for anonymous callers gives 401, so the 404 handling needs care. An
  `AuthenticationServiceException` is rethrown by default, so the 503 needs a failure handler. Spring's default
  `WWW-Authenticate` header has more detail than ours. The parity tests lock all three down.
- **Header-based tests:** existing tests and tools that inject `X-User-Id` stop working, by design. The
  `postman/` collection goes through the gateway with tokens, so it is unaffected; I'll check it anyway.
- **Rollout:** deploying the services and the gateway in either order is safe, because each layer verifies
  independently and the token format is unchanged.
- **Ownership coupling:** airline-core's `/api/airlines/admin` now trusts only the token. Callers already relay the
  user's token, so behaviour is identical. A caller without a request context already fails today (missing header).

## Verification

- After commits 2–3, the parity tests pass on the **current** code; I'll show you the output.
- After every commit: `./mvnw -B verify` with Docker running, so the Testcontainers tests run. Same parity tests, no edits to their assertions.
- Final: the full build, `scripts/e2e-flow.py` against the local stack (`start-local.ps1`; per your notes I use
  `mvn test` and won't repackage while it runs), the frontend checks (silent refresh, expired token on public
  search), and a parity-results table in the PR.
