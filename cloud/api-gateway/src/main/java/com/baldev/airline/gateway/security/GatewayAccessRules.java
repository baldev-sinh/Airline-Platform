package com.baldev.airline.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.SingleResultAuthorizationManager;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Coarse-grained, edge-level authorization: which endpoints are public, which need a signed-in user, an airline
 * owner or a system administrator, and which do not exist for the outside world. Rules are evaluated top to
 * bottom; the first match wins, and anything unmatched is denied (404). Fine-grained checks (does this user own
 * this booking? does this airline own this flight?) stay in the services.
 *
 * <p>This list is the single place where public and privileged endpoints are declared. It feeds Spring
 * Security's {@code authorizeHttpRequests} in {@link GatewaySecurityConfig}, and also tells it which requests
 * carry no token worth reading ({@link #publicRequests()}) and which to hide with 404 ({@link #hiddenRequests()}).
 */
public final class GatewayAccessRules {

    public enum Access {
        /** No token required; a token that is sent is not even looked at. */
        PUBLIC,
        /** Any valid token. */
        AUTHENTICATED,
        /** ROLE_AIRLINE_OWNER (or system admin). */
        AIRLINE_OWNER,
        /** ROLE_SYSTEM_ADMIN only. */
        ADMIN,
        /** Never reachable from outside (responds 404). */
        DENY
    }

    static final String ROLE_ADMIN = "ROLE_SYSTEM_ADMIN";
    static final String ROLE_AIRLINE_OWNER = "ROLE_AIRLINE_OWNER";

    private static final Set<HttpMethod> ANY = Set.of();
    private static final Set<HttpMethod> GET = Set.of(HttpMethod.GET);
    private static final Set<HttpMethod> POST = Set.of(HttpMethod.POST);
    private static final Set<HttpMethod> DELETE = Set.of(HttpMethod.DELETE);
    private static final Set<HttpMethod> WRITE = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    /** Airline-managed catalogue: anyone signed in can read, only airline owners can change. */
    private static final List<String> AIRLINE_MANAGED = List.of(
            "/api/aircrafts/**", "/api/flights/**", "/api/flight-instances/**", "/api/flight-schedules/**",
            "/api/fares/**", "/api/fare-rules/**", "/api/baggage-policies/**",
            "/api/cabin-classes/**", "/api/seat-maps/**", "/api/flight-instance-cabins/**",
            "/api/meals/**", "/api/ancillaries/**", "/api/insurance-coverages/**",
            "/api/flight-meals/**", "/api/flight-cabin-ancillaries/**");

    /** One row of the table; {@code matcher} matches the method set (or any method) and the path pattern. */
    public record Rule(Set<HttpMethod> methods, String pattern, Access access, RequestMatcher matcher) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // ---- Never exposed ----
        rule(ANY, "/internal/**", Access.DENY);
        rule(ANY, "/actuator/**", Access.DENY);
        rule(POST, "/api/fares/batch-by-ids", Access.DENY);

        // ---- Public ----
        rule(POST, "/auth/login", Access.PUBLIC);
        rule(POST, "/auth/signup", Access.PUBLIC);
        // Authenticated by the refresh token in the body (single-use, rotated), not by an access token.
        rule(POST, "/auth/refresh", Access.PUBLIC);
        rule(POST, "/auth/logout", Access.PUBLIC);
        rule(GET, "/api/flights/search", Access.PUBLIC);
        rule(GET, "/api/cities/**", Access.PUBLIC);
        rule(GET, "/api/airports/**", Access.PUBLIC);
        rule(GET, "/api/airlines/dropdown", Access.PUBLIC);
        // Authenticated by the provider's HMAC signature, not by a user token.
        rule(POST, "/api/payments/webhooks/razorpay", Access.PUBLIC);

        // ---- System administration ----
        rule(WRITE, "/api/cities/**", Access.ADMIN);
        rule(WRITE, "/api/airports/**", Access.ADMIN);
        rule(GET, "/api/airlines", Access.ADMIN);
        rule(POST, "/api/airlines/{id}/approve", Access.ADMIN);
        rule(POST, "/api/airlines/{id}/suspend", Access.ADMIN);
        rule(POST, "/api/airlines/{id}/ban", Access.ADMIN);
        rule(GET, "/api/users/profile", Access.AUTHENTICATED);
        rule(POST, "/api/users/me/logout-all", Access.AUTHENTICATED);
        rule(ANY, "/api/users/**", Access.ADMIN);
        rule(GET, "/api/payments", Access.ADMIN);
        rule(DELETE, "/api/bookings/{id}", Access.ADMIN);

        // ---- Airline back office ----
        rule(GET, "/api/bookings/airline", Access.AIRLINE_OWNER);
        rule(WRITE, "/api/airlines", Access.AIRLINE_OWNER);
        rule(DELETE, "/api/airlines/{id}", Access.AIRLINE_OWNER);
        // Fare search is a read even though it uses POST for its body.
        rule(POST, "/api/fares/search", Access.AUTHENTICATED);
        for (String pattern : AIRLINE_MANAGED) {
            rule(WRITE, pattern, Access.AIRLINE_OWNER);
        }

        // ---- Everything else under /api requires sign-in; anything not listed is denied ----
        rule(ANY, "/api/**", Access.AUTHENTICATED);
    }

    private GatewayAccessRules() {
    }

    public static List<Rule> rules() {
        return List.copyOf(RULES);
    }

    /** The access level of the first rule matching {@code request}; DENY if none does. */
    public static Access resolve(HttpServletRequest request) {
        for (Rule rule : RULES) {
            if (rule.matcher().matches(request)) {
                return rule.access();
            }
        }
        return Access.DENY;
    }

    /** Requests whose first matching rule is PUBLIC: their bearer token is ignored. */
    public static RequestMatcher publicRequests() {
        return request -> resolve(request) == Access.PUBLIC;
    }

    /** Requests the outside world must not even learn exist: the first matching rule is DENY, or none matches. */
    public static RequestMatcher hiddenRequests() {
        return request -> resolve(request) == Access.DENY;
    }

    /** Registers the table, in order, with Spring Security; unmatched requests are denied. */
    public static void apply(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry registry) {
        for (Rule rule : RULES) {
            registry.requestMatchers(rule.matcher()).access(manager(rule.access()));
        }
        registry.anyRequest().denyAll();
    }

    private static AuthorizationManager<RequestAuthorizationContext> manager(Access access) {
        return switch (access) {
            case PUBLIC -> SingleResultAuthorizationManager.permitAll();
            case AUTHENTICATED -> AuthenticatedAuthorizationManager.authenticated();
            case AIRLINE_OWNER -> AuthorityAuthorizationManager.hasAnyAuthority(ROLE_ADMIN, ROLE_AIRLINE_OWNER);
            case ADMIN -> AuthorityAuthorizationManager.hasAuthority(ROLE_ADMIN);
            case DENY -> SingleResultAuthorizationManager.denyAll();
        };
    }

    private static void rule(Set<HttpMethod> methods, String pattern, Access access) {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher matcher = methods.isEmpty()
                ? paths.matcher(pattern)
                : new OrRequestMatcher(methods.stream().map(m -> (RequestMatcher) paths.matcher(m, pattern)).toList());
        RULES.add(new Rule(methods, pattern, access, matcher));
    }
}
